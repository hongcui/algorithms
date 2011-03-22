/**
 * $Id$
 */
package informationContent;

import java.sql.Connection;
import java.sql.DriverManager;

import java.util.*;
import db.DatabaseAccessor;

/**
 * this class is called when hasReferences box is checked
 * need reference headings and heading style (e.g. all cap for treatises)
 * may need section sequence information to decide which heading follows glossary, if glossary heading is not all cap.
 * 
 * locate all paras between reference heading and set their type to "content-ref"
 * @author hongcui
 *
 */
public class ReferencesFixer {
	private ContentFixer cf = null;
	private String refHeading = null;
	public ReferencesFixer(ContentFixer cf, String refHeading) {
		this.cf = cf;
		this.refHeading = refHeading;
	}

	public void fix(){
		try{
			String condition = "paragraph COLLATE utf8_bin rlike '^[[.space.]]*"+this.refHeading+"[[.space.]]*$'";
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			DatabaseAccessor.selectParagraphs(cf.prefix, condition, "paraID", paraIDs, paras, cf.conn);
			for(int i = 0; i < paraIDs.size(); i++){ //do one glossary at a time
				int refId= Integer.parseInt(paraIDs.get(i)); //start gloss section
				condition = "remark like '%heading%' and paraID >"+refId;
				ArrayList<String> paraIDs1 = new ArrayList<String>();
				ArrayList<String> paras1 = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(cf.prefix, condition, "paraID", paraIDs1, paras1, cf.conn);
				String cond = "";
				if(paraIDs1.size() >= 1){
					int refIdE = Integer.parseInt(paraIDs1.get(0));
					cond = "paraID > "+refId+" and paraID < "+refIdE;
				}else{
					cond = "paraID > "+refId;
				}
				String set = "remark = 'content-ref' ";
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
