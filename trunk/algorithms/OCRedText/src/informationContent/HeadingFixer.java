/**
 * $Id$
 */
/**
 * 
 */
package informationContent;

import db.DatabaseAccessor;

/**
 * @author hongcui
 *
 */
public class HeadingFixer {
	private ContentFixer cf = null;
	private String headingStyle = null;
	/**
	 * 
	 */
	public HeadingFixer(ContentFixer cf, String headingStyle) {
		this.cf = cf;
		this.headingStyle = headingStyle;
	}

	public void fix(){
		if(this.headingStyle.compareTo("ALLCAP")==0){
			String set = "remark = 'content-heading' ";
			String cond = "paragraph COLLATE utf8_bin rlike '^[A-Z ]+$'";
			try{
				DatabaseAccessor.updateParagraph(cf.prefix, set, cond, cf.conn); //update clean_paragraphs table
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
