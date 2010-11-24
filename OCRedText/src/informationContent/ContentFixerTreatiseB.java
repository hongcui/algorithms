/**
 * 
 */
package informationContent;

import java.util.ArrayList;
import java.util.Iterator;

import db.DatabaseAccessor;

/**
 * $Id
 * @author hongcui
 * remove all non-description parts
 * form descriptions
 * one description contains: heading, name, description, discussion, other-info sections
 *
 */
public class ContentFixerTreatiseB extends ContentFixer {
	protected String descriptionStartHeading = "Systematic Descriptions";
	protected String descriptionEndHeading = "REFERENCES";
	private String source = null;
	private int end = 0;
	private int start  = 0;
	private ArrayList<String> paragraphs = null;
	private ArrayList<String> ptypes = null;
	private ArrayList<String> padd2last = null;
	/**
	 * 
	 */
	public ContentFixerTreatiseB(String paraTableName) {
		super(paraTableName);
	}

	protected void makeCleanContent(){
		try{
			ArrayList<String> sources = new ArrayList<String>();
			DatabaseAccessor.selectDistinctSources(this.prefix.replaceFirst("_clean", ""), sources, this.conn);
			Iterator<String> it = sources.iterator();
			while(it.hasNext()){
				//1. select descriptions
				selectDescriptions(source);
				//2. merge description parts
				mergeDescriptionParts(source);
				//3. format descriptions
				structureDescriptions(source);
				//4. output
				outDescriptions(source);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	
	private void outDescriptions(String source2) {
		// TODO Auto-generated method stub
		
	}

	private void structureDescriptions(String source) {
		// TODO Auto-generated method stub
		
	}

	private void mergeDescriptionParts(String source) {
		for(int i = 0; i<paragraphs.size(); i++){
			
		}
		
		
	}

	/**
	 * get from start-heading to end-heading
	 */
	private void selectDescriptions(String source) {
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			String condition = "source = '"+source+"' and paragraph ='"+this.descriptionStartHeading+"' and paraID >"+end;
			String orderby = "";
			DatabaseAccessor.selectParagraphs(prefix.replaceFirst("_clean", ""), condition, orderby, paraIDs, paras, conn);
			if(paraIDs.size()>0){
				start = Integer.parseInt(paraIDs.get(0));
			}else{
				System.err.println("descriptionStartHeading not found");
				System.exit(3);
			}
			
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source = '"+source+"' and paragraph ='"+this.descriptionEndHeading+"' and paraID >"+start;
			orderby = "";
			DatabaseAccessor.selectParagraphs(prefix.replaceFirst("_clean", ""), condition, orderby, paraIDs, paras, conn);
			if(paraIDs.size()>0){
				end = Integer.parseInt(paraIDs.get(0));
			}else{
				System.err.println("descriptionStartHeading not found");
				System.exit(3);
			}
			
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source = '"+source+"' and type in ('content','content_heading') and paraID < "+end +" and paraID > "+start;
			ArrayList<String> types = new ArrayList<String>();
			ArrayList<String> add2last = new ArrayList<String>();
			orderby = "";
			DatabaseAccessor.selectParagraphsTypesAdd2Last(prefix.replaceFirst("_clean", ""), condition, orderby, paraIDs, paras, types, add2last, conn);
			this.paragraphs = paras;
			this.ptypes = types;
			this.padd2last = add2last;
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		

	}

}
