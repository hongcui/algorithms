/**
 * 
 */
package informationContent;

import java.sql.Connection;
import java.sql.DriverManager;

import java.util.*;
import db.DatabaseAccessor;

/**
 * @author hongcui
 * after ContentFetcher, ContentFixer removes non-content items and concatenate add2last paragraphs and provides clean content for further processes.
 * this class perform common operations needed for all OCRedText
 * this class calls other classes to perform collection specific functions.	
 */
public class ContentFixer {
	protected String paraTable = null;
	protected Connection conn = null;
	protected static String url = ApplicationUtilities.getProperty("database.url");
	protected String prefix = null;
	public int lineLength = 78;
	
	
	//collection specific arguments, values obtained from a to-be-create config-GUI
	private boolean hasGlossary = true;
	private String glossHeading = "GLOSSARY";
	private String headingStyle = "ALLCAP";
	private boolean hasReferences = true;
	private String refHeading = "REFERENCES";
	private String Style = "ALLCAP";
	
	/**
	 * 
	 */
	public ContentFixer(String paraTableName) {
		this.paraTable = paraTableName; //test_paragraphs
		this.prefix = paraTableName.replaceAll("_.*$", "")+"_clean"; //test_clean_paragraphs
		try{
			//paraID not null primary key longint, source varchar(50), paragraph text(5000), type varchar(10), add2last varchar(5), remark varchar(50)
			Class.forName(ApplicationUtilities.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
			DatabaseAccessor.createCleanParagraphTable(this.prefix, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * remove noncontent_pagenum, figtbl, prolog, figtbl_txt, epilog
	 * concate add2last paragraphs
	 * category glossaries, abbreviations, and references
	 * some noncontent_shorttext need to be saved
	 */
	public void makeCleanContent(){
		fixFigtbl(); //reset the add2last paragraphs that should be added to the caption of a preceding fig or table. Work on the original table, not the clean_paragraphs
		fixAdd2Last(); //populate clean_paragraphs
		
		//post processing: collection specific features
		//ALLCAP headings
		HeadingFixer hf = new HeadingFixer(this, this.headingStyle);
		hf.fix(); //headings are marked as such.
		//Glossary
		if(this.hasGlossary){
			GlossaryFixer gf = new GlossaryFixer(this, this.glossHeading);
			gf.fix();
		}
		if(this.hasReferences){
			ReferencesFixer rf = new ReferencesFixer(this, this.refHeading);
			rf.fix();
		}
	}
	/**
	 * reset the add2last paragraphs that should be added to the caption of a preceding fig or table. 
	 * scan through add2last paragraphs:
	 *  	for each add2last paragraph
	 *  		if the preceding paragraph is a figtbl
	 *  			determine if the add2last should be added to figtbl paragraph, if so change its type to figtbl and reset add2last flag for it
	 *  				
	 */
	private void fixFigtbl() {
		boolean goon = false;
		do{
			goon = false;
			ArrayList<String> paras = new ArrayList<String> ();
			ArrayList<String> sources = new ArrayList<String> ();
			ArrayList<String> paraIDs_add2 = new ArrayList<String> ();
			String condition = "type ='content' and add2last='yes'";
			try{
				//get all "add2last" paragraphs
				DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "paraID desc", paraIDs_add2, paras, sources, conn);
			}catch(Exception e){
				e.printStackTrace();
			}
			
			Iterator<String> it = paraIDs_add2.iterator();
			while(it.hasNext()){
				int pid = Integer.parseInt((String)it.next());
				int figtblid = pid-1;
				ArrayList<String> paraIDs = new ArrayList<String> ();
				paras = new ArrayList<String> ();
				sources = new ArrayList<String> ();
				condition = "type like '%figtbl' and paraID = "+figtblid;
				try{
					//get all figtbl paragraphs precedes an add2last
					DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "paraID desc", paraIDs, paras, sources, conn);
				}catch(Exception e){
					e.printStackTrace();
				}
				if(paraIDs.size()>=1){//find a case
					String figtblp = (String)paras.get(0);
					if(isIncomplete(figtblp, figtblid)){
						//reset add2last for pid, change type to figtbl for pid
						try{
							String set = "type ='content-figtbl', add2last=''";
							String cond = "paraID = "+pid;
							DatabaseAccessor.updateParagraph(prefix.replaceAll("_clean", ""), set, cond, conn);
							goon = true;
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				}
				
			}
		}while(goon);
		
	}

	
	
	/**
	 * test to see if figtbl paragraph is not a complete sentence
	 * must meet the following conditions:
	 * 1. not end with a period or a )
	 * 2. the last content end with a period or a )
	 * 
	 * @param figtblp
	 * @param figtblid
	 * @return
	 */
	private boolean isIncomplete(String figtblp, int figtblid) {
		//get the last content paragraph
		ArrayList<String> paras = new ArrayList<String> ();
		ArrayList<String> sources = new ArrayList<String> ();
		ArrayList<String> paraIDs = new ArrayList<String> ();
		String condition = "type like 'content%' and paraID<"+figtblid;
		try{
			DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "paraID desc", paraIDs, paras, sources, conn);
			if(paras.size()>=1){
				String p = (String)paras.get(0).trim();
				figtblp = figtblp.trim();
				if(isComplete(p) && ! isComplete(figtblp)){
					return true;
				}else if(!isComplete(p) && isComplete(figtblp)){
					return false;
				}else if(!isComplete(p) && !isComplete(figtblp)){ //both are incomplete
					return true;
				}else{ //both are complete
					if(figtblp.length() > this.lineLength *4/5 ){
						return false;
					}else{
						return true;
					}
				}
				
				
			}else{
				return true; //no content paragraph.
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

	private boolean isComplete(String p) {
		if(p.matches("[?!]$")){
			return true;
		}
		
		if(p.matches("[a-z]\\.$")){ //find.
			return true;
		}
		
		if(p.matches("(\\.\\)|\\).)$")){ // .) or ).
			return true;
		}
		
		
		return false;
	}

	private void fixAdd2Last() {
		ArrayList<String> paraIDs = new ArrayList<String> ();
		ArrayList<String> paras = new ArrayList<String> ();
		ArrayList<String> sources = new ArrayList<String> ();
		
		
		ArrayList<String> paraIDs_add2 = new ArrayList<String> ();
		String condition = "type ='content' and add2last='yes'";
		try{
			//get all "add2last" paragraphs
			DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "paraID desc", paraIDs_add2, paras, sources, conn);
			paras = new ArrayList<String> ();
			sources = new ArrayList<String> ();
			condition = "type ='content'"; //get all content paragraphs
			DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "", paraIDs, paras, sources, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		//use paraIDs_add2 to manipulate the content of paras
		//TODO: should an add2last seg be added to the fig immediately before it, or the paragraph immediately before the fig? Example paraID459-460 in treatise part d
		Iterator<String> it = paraIDs_add2.iterator();
		while(it.hasNext()){
			String add2ID=(String)it.next();
			//fetch and attach in paraIDs
			int index = paraIDs.indexOf(add2ID);
			String add2p = paras.get(index);
			String p = paras.get(index-1);
			
			String add2s = sources.get(index);
			String s = sources.get(index-1);
			if(add2s.compareTo(s) == 0){			
				paras.set(index-1, p+" "+add2p);
				paras.set(index, "");
			}
		}
		
		ArrayList<String> cparaIDs = new ArrayList<String> ();

		//read out paras to clean_paragraphs table
		int ccount = 0;
		for(int i = 0; i<paras.size();){
			if(paras.get(i).compareTo("") != 0){
				cparaIDs.add(ccount+"");
				ccount++;
				i++;
			}else{
				paras.remove(i);
				paraIDs.remove(i);
				sources.remove(i);
			}
		}
		
		if(cparaIDs.size() != paras.size() || paras.size() != paraIDs.size() || paras.size() != sources.size()){
			System.out.print("wrong!");
			System.exit(1);
		}
		try{
			DatabaseAccessor.insertCleanParagraphs(prefix, cparaIDs, paraIDs, paras, sources, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ContentFixer cf = new ContentFixer("treatisetest_paragraphs");
		cf.makeCleanContent();
	}

}
