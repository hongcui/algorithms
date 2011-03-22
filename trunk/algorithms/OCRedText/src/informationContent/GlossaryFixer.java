/**
 * $Id$
 */
package informationContent;

import java.sql.Connection;
import java.sql.DriverManager;

import java.util.*;
import db.DatabaseAccessor;

/**
 * this class is all when hasGlossary box is checked
 * need glossary heading and heading style (e.g. all cap for treatises)
 * may need section sequence information to decide which heading follows glossary, if glossary heading is not all cap.
 * 
 * locate all paras between glossary heading and set their type to "content-gloss"
 * @author hongcui
 *
 */
public class GlossaryFixer {
	private ContentFixer cf = null;
	private String glossHeading = null;
	public GlossaryFixer(ContentFixer cf, String glossHeading) {
		this.cf = cf;
		this.glossHeading = glossHeading;
	}

	public void fix(){
		try{
			String condition = "paragraph COLLATE utf8_bin rlike '^[[.space.]]*"+this.glossHeading+"[[.space.]]*$'";
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			DatabaseAccessor.selectParagraphs(cf.prefix, condition, "paraID", paraIDs, paras, cf.conn);
			for(int i = 0; i < paraIDs.size(); i++){ //do one glossary at a time
				int glossId= Integer.parseInt(paraIDs.get(i)); //start gloss section
				condition = "remark like '%heading%' and paraID >"+glossId;
				ArrayList<String> paraIDs1 = new ArrayList<String>();
				ArrayList<String> paras1 = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(cf.prefix, condition, "paraID", paraIDs1, paras1, cf.conn);
				String cond = "";
				if(paraIDs1.size() >= 1){
					int glossIdE = Integer.parseInt(paraIDs1.get(0));
					cond = "paraID > "+glossId+" and paraID < "+glossIdE;
				}else{
					cond = "paraID > "+glossId;
				}
				String set = "remark = 'content-gloss'";
				DatabaseAccessor.updateParagraph(cf.prefix, set, cond, cf.conn);
			}
		}catch(Exception e){
				e.printStackTrace();
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
