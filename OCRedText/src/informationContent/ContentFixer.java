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
 */
public class ContentFixer {
	private String paraTable = null;
	private Connection conn = null;
	private static String url = ApplicationUtilities.getProperty("database.url");
	private String prefix = null;
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
	 * some noncontent_shorttext need to be saved
	 */
	public void makeCleanContent(){
		ArrayList<String> paraIDs = new ArrayList<String> ();
		ArrayList<String> paras = new ArrayList<String> ();
		ArrayList<String> sources = new ArrayList<String> ();
		
		
		ArrayList<String> paraIDs_add2 = new ArrayList<String> ();
		String condition = "type ='content' and add2last='yes'";
		try{
			DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "paraID desc", paraIDs_add2, paras, sources, conn);
			paras = new ArrayList<String> ();
			sources = new ArrayList<String> ();
			condition = "type ='content'";
			DatabaseAccessor.selectParagraphsSources(prefix.replaceAll("_clean", ""), condition, "", paraIDs, paras, sources, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		//use paraIDs_add2 to manipulate the content of paras
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
		ContentFixer cf = new ContentFixer("bhl_paragraphs");
		cf.makeCleanContent();
	}

}
