/**
 * $Id$
 */
/**
 * 
 */
package informationContent;
/**
 * 
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Hashtable;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jdom.Content;
import org.jdom.Text;








/**
 * @author hongcui
 *
 */
public class TaxonXExtractor {

	/**
	 * @author hongcui
	 * split taxonX documents to paragraphs for description paragraph extraction evaluation.
	 * output a benchmark table and a paragraphs table
	 * 
	 */

	private File source = new File("Z:\\DATA\\Plazi\\2ndFetchFromPlazi\\taxonX-ants");
	private String tableprefix = "plazi_ants_all";
	private String benchmark = "plazi_ants_all_paragraphs_benchmark";
	private String paragraphs = "plazi_ants_all_paragraphs";

	private Connection conn = null;

	public TaxonXExtractor() {
		try{
			Class.forName("com.mysql.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:mysql://localhost/sourcedatasets?user=root&password=root");
			Statement stmt = conn.createStatement();
			stmt.execute("drop table if exists "+benchmark);
			stmt.execute("create table if not exists "+benchmark+" (paraID varchar(100) NOT NULL, paragraph text, isDescription varchar(5), primary key(paraID))");	
			
			stmt.execute("drop table if exists "+paragraphs);
			stmt.execute("create table if not exists "+paragraphs+" (paraID varchar(100) NOT NULL, paragraph text, remark text, flag varchar(20), primary key(paraID))");			
		}catch(Exception e){
			e.printStackTrace();
		}	
	}
		
	public void createEvaluationTables(){
			File[] files =  source.listFiles();
			Hashtable<String, String> filemapping = new Hashtable<String, String>();
	    	//read in taxonX documents from source
			try{
				SAXBuilder builder = new SAXBuilder();
				for(int f = 0; f < files.length; f++) {
					//create renaming mapping table
					String fn = files[f].getName();
					//split by treatment
					Document doc = builder.build(files[f]);
					Element root = doc.getRootElement();

					//remove all xmldata elements
					List<Element> xmls = XPath.selectNodes(root,"//tax:xmldata");
					Iterator<Element> it = xmls.iterator();
					while(it.hasNext()){
						Element x = it.next();
						x.detach();
						//x.getParentElement().removeContent(x);
					}
										
					List<Element> treatments = XPath.selectNodes(root,"/tax:taxonx/tax:taxonxBody/tax:treatment");
					
					for(int t = 0; t<treatments.size(); t++){
						Element e = (Element)treatments.get(t);
						extractFromTreatment(fn, t, e);
					}
				}
			}catch(Exception e){
				e.printStackTrace();
				
			}
}

	private void extractFromTreatment(String fn, int t, Element treatment) {
		int count = 0;
		List<Element> elements = treatment.getChildren();
		StringBuffer sb;
		for(int i = 0; i<elements.size(); i++){
			Element e = elements.get(i);
			if(e.getName().compareTo("nomenclature") != 0){ //div, ref_group
				String type = "";
				if(e.getAttribute("type") !=null){
					type = e.getAttribute("type").getValue(); 
				}
				String isDescription = type.compareTo("description")==0 || type.compareTo("key")==0? "yes" : "no";
				count = extractParagraphs(fn, t, count, e, isDescription); //<tax:p> in element
			}else{
				sb = new StringBuffer();
				String text = getTextFromP(e, sb).toString();
				insert2Tables(fn, t, count, text, "no");
				count++;
			}
		}
			       
	}
	
	private int extractParagraphs(String fn, int t, int count, Element e, String isDescription) {
		List<Element> paras = e.getChildren();
		Iterator<Element> it = paras.iterator();
		while(it.hasNext()){
			Element p = it.next();
			String text = getTextFromP(p, new StringBuffer()).toString();
			insert2Tables(fn, t, count, text, isDescription);
			count++;
		}
		return count;
	}

	

	private StringBuffer getTextFromP(Element p, StringBuffer sb) {
			int size = p.getContentSize();
			//System.out.println(p.toString());
			for(int c = 0; c < size; c++){
				Content cont = p.getContent(c);
				if(cont instanceof Element){
					getTextFromP((Element)cont, sb);
				}else if(cont instanceof Text){
					String t = ((Text)cont).getTextNormalize();
					t = t.trim();
					sb.append(t+" ");
				}
			}
			return sb;
	}


	private void insert2Tables(String fn, int t, int count, String text, String isDescription) {
		try{
			String paraID=fn+"_"+t+"_"+count;
			Statement stmt = conn.createStatement();
			text = text.replaceAll("\\s+", " ").trim().replaceAll("\"", "\\\\\"");
			System.out.println(paraID+":["+isDescription+"]:"+text);
			
			stmt.execute("insert into "+benchmark+" values(\""+paraID+"\",\""+text+"\",\""+isDescription+"\")");
			stmt.execute("insert into "+paragraphs+" values(\""+paraID+"\",\""+text+"\",\"\",\"\")");
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}



		/**
		 * @param args
		 */
		public static void main(String[] args) {
			// TODO Auto-generated method stub
			TaxonXExtractor t4t = new TaxonXExtractor();
			t4t.createEvaluationTables();
		}

	}


