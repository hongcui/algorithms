package informationContent;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import beans.KeyChoice;
import beans.KeyFile;
import beans.KeyStatement;
import beans.Line;
import beans.LineComparator;
import beans.ParagraphBean;
import beans.Taxon;
import beans.Taxon_bees;

import db.DatabaseAccessor;

public class ContentExtract_bees {
	//pattern of index
	public String p_index = "(\\d+\\.\\s?)";
	
	//pattern of key
	//Key to the Subfamilies of the Colletidae
	//
	public String p_key_title = "^(" + p_index + ")?" + "(Practical\\s)?Key\\sto\\s.+";
	public String p_key_combined_title = p_key_title + "([\\w\\s]+|\\(.+\\))$";
	public String p_key_statement_index = "(—\\s|\\d+\\(\\d+\\)|\\d+)\\.\\s";//
	public String p_key_statement_start = "^" + p_key_statement_index + ".+";
	public String p_key_statement_tail = ".*?\\.{3,}.+$";//over 3 continuous dots
	public String p_key_statement = p_key_statement_start + p_key_statement_tail;//start + anything + tail
	
	public String p_normal_title = "^" + p_index + "Notes on.+$";
	
	public String p_distribution = "^(?i)M*(D?C{0,3}|C[DM])(L?X{0,3}|X[LC])(V?I{0,3}|I[VX])\\s.+";
	
	//general word patterns
	public String p_cap_word = "[A-Z][a-z]+";
	//European characters
	public String p_cap_word_wLatin = "[A-Z][a-zàáâãäæèéêëðòôõöùúûü]+";
	
	//pattern of taxon name	
	public String p_taxon_rank = "Phylum|Subphylum|Class|Subclass|Order|Suborder|" +
			"Superfamily|Family|Subfamily|Genus|Tribe|Subgenus|Species";
	/**
	 * taxon name: include rank and name
	 * style 1: rank + name
	 * (not in this doc)style 2: rank + genus name + species name
	 * (only in this doc)style 3: genus name / Subgenus + name
	 * 
	 *   In general: rank? name (\\s/\\sSubgenus\\s)?
	 */
	public String p_taxon_name = "(" + p_taxon_rank + ")?\\s?" +
			p_cap_word + "(\\s/\\sSubgenus\\s" + p_cap_word + ")?";
	
	/**
	 * author is attached to taxon name
	 * style 1: name
	 * style 2: name 1 + and + name 2
	 * style 3: name + s. str.
	 * style 4: name 1 + and name 2 + s. str.
	 * 
	 * name style 1: [A-Z][a-z]+
	 * name style 2: [La][A-Z][a-z]+
	 * name style 3: De\\s[A-Z][a-z]+
	 */
	public String p_taxon_author_single = "(La|[Dd]e\\s)?" + p_cap_word_wLatin;
	public String p_taxon_author = p_taxon_author_single + 
			"(\\sand\\s" + p_taxon_author_single +  ")?" + "(\\ss\\.\\sstr\\.)?";
	
	//public String p_taxon_yearOrPub = "";//index + rank + name + author + year or pub
		
	//pattern of type species
	public String p_type_species_para = "^.+\\.\\sType\\sspecies:.+$";
	
	
	//37. Family/Subfamily/Tribe Colletide
	public String p_taxon_title = "^(" + p_index + ")?" + p_taxon_name + "\\s" + 
			p_taxon_author + "$";
	public String p_taxon_title_no_author = "^(" + p_index + ")?" + p_taxon_name + "$";
	
	
	//figure and table pattern	
	public String p_fig_tbl_cap = "^(Figure|Table)\\s+\\d+(-\\d+)?\\.?\\s.*?";//Figure 10-4.
	public String p_fig_cap = "^(Figure)\\s+\\d+(-\\d+)?\\.?\\s.*?";
	public String p_fig_tbl_txt = "^\\d+(\\.\\d+)?\\s?\\??mm?"
			+ "|(\\d\\d?[a-z]*\\d?)(\\s?\\d\\d?[a-z])*\\'*" + "|[a-z0-9]*"
			+ "|[a-z]+" + "|[a-z][0-9](\\s[a-z][0-9])+" + "|[A-Z]+\\'*"
			+ "|([A-Z][a-z]*(\\s[A-Z][a-z]*)*)"
			+ "|([A-Z][a-z]*\\s)?\\([A-Z][a-z]*\\)"
			+ "|\\([a-z]+(\\s[a-z]+)*\\)";;
	//public Hashtable<String, Boolean> ht_special_fig_cap = null;

	
	//path
	public String filesPath = "D:\\Work\\Data\\bees\\";
	public String sourceXmlFolderName = "xml";
	public String sourceXmlsPath = filesPath + sourceXmlFolderName;
	public String sourceTxtsFolderName = "txt";
	public String outputPath = "D:\\Work\\Data\\bees\\output\\";
	public String cleanTxtPath = "D:\\Work\\Data\\bees\\cleanTxt\\";
	
	//global variables for processing
	public Hashtable<String, String> copiedText = new Hashtable<String, String>();
	
	/*databases*/
	public Connection conn = null;
	public static String url = ApplicationUtilities
			.getProperty("database.url");
	public String cleanTableSurfix = "_clean_paragraphs";
	
	/*file process helpers*/
	public String prefix = "";
	public String source = "";
	public String fileName = "";
	
	public boolean newNextPara = false; 
	public int new_para_indent = 40;
	public int space_between_para = 35;	
	//space_end_para is set to be the threshold of the room at the end of a paragraph
	public int space_end_para = 40;
	
	/*document associated values*/
	public int page_middle_point = 977; //958, 996
	public int left_column_begin = 146;
	public int left_column_end = 958;
	public int right_column_begin = 996;
	public int right_column_end = 1800; 
	public int starting_page = 141;
	public int ending_page = 850;
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		ContentExtract_bees cb =  new ContentExtract_bees();
		//cb.processPdfFiles(cb.sourceXmlsPath);
		
		cb.generateTaxonXmls();
	}
	
	public ContentExtract_bees() throws Exception {
		
	}
	
	/*get each xml file and process it*/
	public void processPdfFiles(String path) throws Exception {
		File directory = new File(path);
		File[] allFiles = directory.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			if (allFiles[i].isFile()) {
				processFile(allFiles[i]);
			} else if (allFiles[i].isDirectory()) {
				processPdfFiles(allFiles[i].getAbsolutePath());
			}			
		}
	}	
	
	public void processFile(File xmlFile) throws Exception {
		/*get prefix*/
		this.source = xmlFile.getName();
		this.fileName = source.replace(".xml", "");
		
		System.out.println("start on file: " + xmlFile.getAbsolutePath());
		
		this.prefix = fileName.replaceAll("-", "_");
		
		/*create tables to assist file processing*/
		createTables();
		
		/*get the matching txt if existed*/
		File txtFile = new File(xmlFile.getAbsolutePath().replaceAll("\\.xml", ".txt")
				.replaceAll(sourceXmlFolderName, sourceTxtsFolderName));
		if (txtFile.exists()) {
			getCopiedText(txtFile);
		}
		
		//read pages for this file
		readPages(xmlFile);		
		
		System.out.println("begin to mark figure table content");
		
		//identify content: mark out figure and related text
		traceBackFigContent();
		traceBackTblContent();
		
		System.out.println("begin to identify page separations");
		
		//combine separated paragraphs
		combinePageColumnSeparation();
		
		System.out.println("begin to mark unsigned to be content");
		
		//set unassigned to content
		setContent();
		
		System.out.println("begin to fix brackets");
		
		//combine paragraphs separated by brackets
		fixBrackets();
		fixSquareBrackets();
		
		System.out.println("begin to insert clean paragraphs");
		
		//output content
		getCleanParagraph();
		
		System.out.println("begin to output txt");
		
		outputCleanContent();
		
		System.out.println(xmlFile.getAbsolutePath() + "finished");
	}
	
	/**
	 * after idContent, fix broken brackets. From the last content para track
	 * back to the beginning record right and left brackets if reading a record
	 * makes right != left, set its add2last to "yes"
	 * 
	 * todo: fix (), not just [] e.g. page369
	 */
	public void fixSquareBrackets() {
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();

		ResultSet rs = null;
		try {
			String condition = "";
			rs = DatabaseAccessor.getParagraphsByCondition(prefix, condition,
					"paraID desc", "*", conn);
			DatabaseAccessor.selectParagraphsTypes(prefix, condition,
					"paraID desc", paraIDs, paras, types, conn);
			int left = 0;
			int right = 0;
			while (rs.next()) {
				int pid = rs.getInt("paraID");
				String para = rs.getString("paragraph");
				String type = rs.getString("type");
				// boolean isCategory = rs.getBoolean("isCategory");
				if (type.contains("taxonname")|| type.contains("key") 
						|| type.contains("title")) {
					// when hit a taxon name, reset left and right to control
					// damage inside one category
					left = 0;
					right = 0;
					continue;
				}
				if (type.startsWith("content")) {
					// if (true) {
					String bs = para.replaceAll("[^\\[\\]]", "").trim()
							.replaceAll("\\[\\]", "").replaceAll("\\[\\]", "")
							.replaceAll("\\[\\]", "");
					if (left == right && bs.indexOf("[") >= 0) {
						// this is an extra left bracket, should not be counted
						right += bs.replaceAll("[^\\]]", "").trim().length();
					} else {
						left += bs.replaceAll("[^\\[]", "").trim().length();
						right += bs.replaceAll("[^\\]]", "").trim().length();
					}

					if (left < right) {
						this.markAdd2Last(pid, "y-[");
					} else if (left > right) {
						// do nothing: there must be some extra left brackets
					} else if (left == right) {
						left = 0;
						right = 0;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			paraIDs = null;
			paras = null;
			types = null;
		}
	}
	
	public void fixBrackets() {
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();

		ResultSet rs = null;
		try {
			String condition = "";
			rs = DatabaseAccessor.getParagraphsByCondition(prefix, condition,
					"paraID desc", "*", conn);
			DatabaseAccessor.selectParagraphsTypes(prefix, condition,
					"paraID desc", paraIDs, paras, types, conn);
			int left = 0;
			int right = 0;
			while (rs.next()) {
				int pid = rs.getInt("paraID");
				String para = rs.getString("paragraph");
				String type = rs.getString("type");
				// boolean isCategory = rs.getBoolean("isCategory");
				if (type.contains("taxonname") || type.contains("key") 
						|| type.contains("title")) {
					// when hit a taxon name, reset left and right to control
					// damage inside one category
					left = 0;
					right = 0;
					continue;
				}
				if (type.startsWith("content")) {
					// if (true) {
					String bs = para.replaceAll("[^\\(\\)]", "").trim()
							.replaceAll("\\(\\)", "").replaceAll("\\(\\)", "")
							.replaceAll("\\(\\)", "");
					if (left == right && bs.indexOf("(") >= 0) {
						// this is an extra left bracket, should not be counted
						right += bs.replaceAll("[^\\)]", "").trim().length();
					} else {
						left += bs.replaceAll("[^\\(]", "").trim().length();
						right += bs.replaceAll("[^\\)]", "").trim().length();
					}

					if (left < right) {
						this.markAdd2Last(pid, "y-(");
					} else if (left > right) {
						// do nothing: there must be some extra left brackets
					} else if (left == right) {
						left = 0;
						right = 0;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			paras = null;
			types = null;
		}
	}
	
	public void markAdd2Last(int paraID, String mark) {
		String set = "add2last='" + mark + "'";
		String condition = "paraID=" + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isNonWordLine(String para) {
		para = para.trim();
		if (para.matches("[^\\w]+")) {
			return true;
		}
		return false;
	}
	
	/**
	 * para is an interrupting point if: 1. para starts with a lower case
	 * letter, or 2. para starts with a number but is not part of a numbered
	 * list
	 * 
	 * 
	 * 3. para starts with a *, e.g.: *A. (A.) page363
	 * 
	 * 4. if last end with ",", next content should be an interrupting point
	 * e.g. end of page 364 5. if last end with ";", next content should be an
	 * interrupting point e.g. end of page 371 combine 4 and 5, basically, if
	 * last end is not a period, next content should be an interrupting point,
	 * e.g. end of page 374
	 * 
	 * 
	 * 
	 * @param para
	 * @return
	 */
	public boolean isInterruptingPoint(int paraID, String para,
			int status_last_para, String type, String last_type) {
		para = para.trim();
		if (type.contains("taxonname") || type.contains("title")) {
			if (status_last_para == 3) { //last para ends with ","
				return true;
			} else 
				return false;
		} else if (last_type.contains("taxonname") || last_type.contains("title")
				|| last_type.contains("taxonAuthor")) {
			return false;
		} else if (status_last_para == 0) {
			return true; // last paragraph not ended
		} else if (status_last_para == 1) { // is last paragraph end with ] and
											// next paragraph is a taxon name,
											// then is not interruping point
			if (type.contains("taxonname"))
				return false;
			else
				return true;
		} else if (status_last_para == 3) {
			return true;
		}

		Pattern pattern = Pattern
				.compile("^([_ (\\[a-z0-9)\\]].*?)($|[\\.,]\\s+[A-Z].*)");
		// start with a lower case letter or a number
		Matcher m = pattern.matcher(para);
		if (m.matches()) {
			String start = m.group(1);
			start = start.replaceAll("[(\\[\\])]", "");
			start = start.replaceAll("[Il]", "1").replaceAll("[\\.\\s]", "");
			if (start.matches("\\d+") || start.matches("[a-zA-Z]")
					|| start.matches("\\d+[a-zA-Z]")) {// matches 2, a, 2a, 1920
				if (para.matches("^\\d+,.*")) {
					return true; // p42: 49,
				}
				return false; // bullets
			}
			if (/* start.matches("^[a-z].*") && */start.length() > 1) {
				return true; // else
			}

		}
		return false;
	}
	
	/**
	 * basically combine taxon name and content and produce paragraph, ignore
	 * non-content part
	 * 
	 * originated from ContentFixer.java
	 * 
	 * @throws Exception
	 */
	public void getCleanParagraph() throws Exception {
		// get records with type "content%"
		ResultSet rs = DatabaseAccessor.getParagraphsByCondition(prefix,
				"type like 'content%'", "paraID desc", "*", conn);
		String combinedPara = "";
		String note = "";
		boolean containsTaxon = false;
		boolean previousContainsTaxon = false;
		Pattern p_taxon = Pattern.compile(p_taxon_title
				+ ".*\\d\\d\\d\\d.*\\[.*\\].*");
		ArrayList<ParagraphBean> cleanParas = new ArrayList<ParagraphBean>();
		while (rs.next()) {
			String type = rs.getString("type");
			boolean add2last = false;
			if (rs.getString("add2last") != null) {
				if (rs.getString("add2last").startsWith("y")) {
					add2last = true;
				}
			}
			String currentPara = rs.getString("paragraph");
			combinedPara = combineParas(currentPara, combinedPara);

			if (!add2last/* || type.contains("taxonname") */) {
				if (containsTaxon) {
					if (currentPara.endsWith("pro")) {
						containsTaxon = false;
					}
				}
				ParagraphBean pb = new ParagraphBean(combinedPara,
						rs.getInt("paraID"));
				pb.normalize();
				// if containts taxonname, set a sign for later output
				// check how to set taxonname, there must have at least '[' in
				// the pattern
				// use note field to list problems: 1. unmatched brackets, 2.
				// containing taxon name

				// check []
				String bs = combinedPara.replaceAll("[^\\[\\]]", "").trim()
						.replaceAll("\\[\\]", "").replaceAll("\\[\\]", "")
						.replaceAll("\\[\\]", "");
				int left = bs.replaceAll("[^\\[]", "").trim().length();
				int right = bs.replaceAll("[^\\]]", "").trim().length();

				if (left > right) {
					note += "unmatched [ ; ";
				} else if (left < right) {
					note += "unmatched ] ; ";
				}

				// check ()
				bs = combinedPara.replaceAll("[^\\(\\)]", "").trim()
						.replaceAll("\\(\\)", "").replaceAll("\\(\\)", "")
						.replaceAll("\\(\\)", "");
				left = bs.replaceAll("[^\\(]", "").trim().length();
				right = bs.replaceAll("[^\\)]", "").trim().length();

				if (left > right) {
					note += "unmatched ( ; ";
				} else if (left < right) {
					note += "unmatched ) ; ";
				}

				if (containsTaxon || previousContainsTaxon) {
					note += "Contains taxon;";
				}
				pb.setNote(note);
				pb.setSource(rs.getString("source"));
				cleanParas.add(pb);
				combinedPara = "";
				note = "";
				containsTaxon = false;
				previousContainsTaxon = false;
			} else {
				if (containsTaxon) {
					if (currentPara.endsWith("pro")) {
						containsTaxon = false;
					} else {
						previousContainsTaxon = true;
					}
				}
				if (type.contains("taxonname")) {
					Matcher m = p_taxon.matcher(combinedPara);
					if (m.matches()) {
						// is in [], so there is ] before [
						int index_right = combinedPara.indexOf("]");
						int index_left = combinedPara.indexOf("[");
						if (index_right < 0 /* there is no ] */
								|| (index_left > 1 && index_left < index_right)) {
							containsTaxon = true;
						}
					}
				}
			}
		}

		try {
			// insert backwards
			for (int i = cleanParas.size() - 1; i >= 0; i--) {
				DatabaseAccessor.insertCleanParagraph(this.prefix
						+ this.cleanTableSurfix, cleanParas.get(i), conn);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("clean paragraphs inserted");
		rs = null;
	}

	public void outputCleanContent() {
		try {
			boolean hasLog = false;
			StringBuffer log = new StringBuffer();

			int count = 0;
			String filename = this.fileName + "_cleaned.txt";
			ResultSet rs = DatabaseAccessor.getParagraphsByCondition(
					this.prefix + "_clean", "", "", "*", conn);
			StringBuffer sb = new StringBuffer();
			log.append(System.getProperty("line.separator") + filename
					+ System.getProperty("line.separator"));
			while (rs.next()) {
				count++;
				String p = /* count + ": " + */rs.getString("paragraph");
				String note = rs.getString("note");
				if (!note.equals("")) {
					hasLog = true;
					log.append(note + System.getProperty("line.separator")
							+ p + System.getProperty("line.separator")
							+ System.getProperty("line.separator"));
				}
				sb.append(p + System.getProperty("line.separator")
						+ System.getProperty("line.separator"));
			}
			write2File(sb.toString(), filename);

			if (hasLog) {
				write2File(log.toString(), "log.txt");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void write2File(String text, String filename) throws IOException {
		OutputStreamWriter osw = null;
		try {
			FileOutputStream fos = new FileOutputStream(outputPath + filename);
			osw = new OutputStreamWriter(fos, "UTF-8");
			osw.write(text);
			osw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (osw != null) 
				osw.close(); 
		}
	}
	
	public String combineParas(String firstPara, String laterPara) {
		String rValue = firstPara;
		if (!laterPara.equals("")) {
			if (firstPara.endsWith("-")) {
				rValue = firstPara + laterPara;
			} else {
				rValue = firstPara + " " + laterPara;
			}
		}
		return rValue;
	}
	
	/**
	 * set add2last to those paragraph that are separated by column or page
	 * 1. last is key statement start but not ended -> add to last
	 * 2. end with , ; ] - [\w] -> add to last
	 * 3. end with .  -> do not add
	 * 4. last is figure, and bottom distance is over threshold -> do not add
	 */
	public boolean isInterruptingPoint(int id, String para, String type, int bottom, 
			String last_para, String last_type, int last_bottom) {
		if (last_type.contains("title") || last_type.contains("taxonname")
				|| type.contains("key_statement")) {
			return false;
		}		
		
		if (isFigTblCaption(last_para)) {
			if (bottom - last_bottom > 50) {
				return false; //figure caption ended
			} else {
				markAdd2Last(id, last_type);
				return true;//figure caption not ended				
			}			
		}
		
		if (last_para.matches(p_key_statement_tail)) {
			return false;//last key statement ended
		} else if (last_type.contains("key_statement")) {
			return true;
		}
		
		if (last_para.endsWith(".") || last_para.endsWith("]")) 
			return false;//last end with . ]
		
		return true;//default
	}
	
	public void setContent() {
		ResultSet rs_para = null;
		try {
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					"type = 'unassigned'", "paraID", "*", conn);
			if (rs_para != null) {
				while (rs_para.next()) {
					markAsType(rs_para.getInt("paraID"), "content");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void combinePageColumnSeparation() {
		ResultSet rs_para = null;
		try {
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					"", "paraID", "*", conn);
			String last_para = "", last_type = "";//last content
			String para = "", type = "";
			int id = 0, bottom = 0, last_bottom = 0;
			if (rs_para != null) {
				while (rs_para.next()) {
					para = rs_para.getString("paragraph");
					type = rs_para.getString("type");
					id = rs_para.getInt("paraID");
					bottom = rs_para.getInt("y1");
					
					if (last_para.equals("")) {//first paragraph
						last_para = para;
						last_type = type;
						last_bottom = bottom;
						continue;
					}
					
					if (type.contains("noncontent")) {
						continue;
					}
					
					if (isInterruptingPoint(id, para, type, bottom, last_para, last_type, last_bottom)) {
						markAdd2Last(id, "yes"); // set add2last
					}									
					
					last_para = para;
					last_type = type;
					last_bottom = bottom;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * select all pages with figure/table, and trace fig/tbl content page by
	 * page todo: what if there are two figures in one page?
	 */
	public void traceBackTblContent() {
		ResultSet rs_para = null;
		try {
			String last_pageNum = "";
			int last_figID = 0;
			String condition = "type like '%tbl_figtbl%'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID desc", "paraID, pageNum, source, y1, type",
					conn);
			if (rs_para != null) {
				while (rs_para.next()) {
					int figID = rs_para.getInt("paraID");
					String pageNum = rs_para.getString("pageNum");
					this.traceBackTblContent(rs_para.getString("source"),
							pageNum, rs_para.getInt("y1"), figID, last_pageNum,
							last_figID);
					last_figID = figID;
					last_pageNum = pageNum;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			rs_para = null;
		}
	}	

	/**
	 * trace figure/table content by page
	 * 
	 * @param source
	 * @param pageNum
	 * @param y1
	 *            : bottom of the fig line
	 */
	public void traceBackTblContent(String source, String pageNum, int y1,
			int figID, String last_pageNum, int last_figID) {
		try {
			// use regex to mark all possible figtbl_txt
			ResultSet rs_para = null;
			String condition1 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "'and type = 'unassigned'" + " and y1 > " + y1
					+ " and paraID > " + figID;
			if (!pageNum.equals(last_pageNum)) {
				rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
						condition1, "", "paraID, paragraph, type", conn);
				String last_type = "";
				while (rs_para.next()) {
					String para = rs_para.getString("paragraph");
					if (last_type.contains("taxonname")) { // exclude
															// "new family"
															// below a Family
						if (para.matches("^[n|N]ew\\s[family|Family|subfamily|Subfamily]|Order|order|suborder|Suborder")) {
							continue;
						}
					}
					last_type = rs_para.getString("type");
					if (isfigTblTxt(para)) {
						markAsType(rs_para.getInt("paraID"),
								"noncontent_figtbl_txt");
					}
				}
			}

			// mark all the content between 1st figtxt and fig line
			String condition2 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "' and y1 > " + y1 + " and paraID > " + figID;
			if (pageNum.equals(last_pageNum)) {
				condition2 += " and paraID < " + last_figID;
			}
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition2, "", "*", conn);
			boolean found_1st_figtxt = false;
			while (rs_para.next()) {
				if (!found_1st_figtxt
						&& rs_para.getString("type").equals(
								"noncontent_figtbl_txt")) {
					found_1st_figtxt = true;
					continue;
				}

				if (found_1st_figtxt
						&& rs_para.getString("type").equals("unassigned")) {
					String text = rs_para.getString("paragraph").trim();
					if (text.length() < 50) {
						markAsType(rs_para.getInt("paraID"),
								"noncontent_figtbl_txt");
					} else if (text.matches("^([A-Z][a-z-]+)+")) { // for
																	// vertical
																	// text in
																	// table
						markAsType(rs_para.getInt("paraID"),
								"noncontent_figtbl_txt");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*create assisting tables*/
	public void createTables() {
		try {
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
			DatabaseAccessor.createParagraphTable(this.prefix, conn);
			DatabaseAccessor.createCleanParagraphTable(this.prefix
					+ cleanTableSurfix, conn);			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void traceBackFigContent() {
		ResultSet rs_para = null;
		try {
			String last_pageNum = "";
			int last_figID = 0;
			String condition = "type like '%_figtbl%'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "paraID, pageNum, source, y1, type",
					conn);
			if (rs_para != null) {
				while (rs_para.next()) {
					int figID = rs_para.getInt("paraID");
					String pageNum = rs_para.getString("pageNum");
					this.traceBackFigContent(rs_para.getString("source"),
							pageNum, rs_para.getInt("y1"), figID, last_pageNum,
							last_figID);
					last_figID = figID;
					last_pageNum = pageNum;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			rs_para = null;
		}
	}
	
	/**
	 * trace figure/table content by page
	 * 
	 * @param source
	 * @param pageNum
	 * @param y1
	 *            : bottom of the fig line
	 */
	public void traceBackFigContent(String source, String pageNum, int y1,
			int figID, String last_pageNum, int last_figID) {
		try {
			// use regex to mark all possible figtbl_txt
			ResultSet rs_para = null;
			String condition1 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "'and type = 'unassigned'" + " and y1 < " + y1
					+ " and paraID < " + figID;
			if (pageNum.equals(last_pageNum)) {
				condition1 += " and paraID > " + last_figID;
			}
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition1, "", "paraID, paragraph, type", conn);
			String last_type = "";
			
			while (rs_para.next()) {
				if (isfigTblTxt(rs_para.getString("paragraph"))) {
					markAsType(rs_para.getInt("paraID"),
							"noncontent_figtbl_txt");
				}
			}

			// mark all the content between 1st figtxt and fig line
			String condition2 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "' and y1 < " + y1 + " and paraID < " + figID;
			if (pageNum.equals(last_pageNum)) {
				condition2 += " and paraID > " + last_figID;
			}
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition2, "", "*", conn);
			boolean found_1st_figtxt = false;
			while (rs_para.next()) {
				if (!found_1st_figtxt
						&& rs_para.getString("type").equals(
								"noncontent_figtbl_txt")) {
					found_1st_figtxt = true;
					continue;
				}

				if (found_1st_figtxt && 
						(rs_para.getString("type").equals("unassigned") ||
						 rs_para.getString("type").equals("noncontent_innersection"))) {
					String text = rs_para.getString("paragraph").trim();
					if (text.length() < 50) {
						markAsType(rs_para.getInt("paraID"),
								"noncontent_figtbl_txt");
					} else if (text.matches("^([A-Z][a-z-]+)+")) { // for
																	// vertical
																	// text in
																	// table
						markAsType(rs_para.getInt("paraID"),
								"noncontent_figtbl_txt");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void markAsType(int paraID, String type) {
		String set = "type = '" + type + "'";
		String condition = "paraID = " + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public  void getCopiedText(File txtFile) {
		try {
			FileInputStream fstream = new FileInputStream(txtFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line = "";
			while ((line = br.readLine()) != null) {
				String l = line.trim();
				if (!l.equals("")) {
					// original line
					copiedText.put(l.replaceAll("\\s", ""), l);

					// line without the last word
					int lastSpaceIndex = l.lastIndexOf(" ");
					if (lastSpaceIndex > 0) {
						String prefix_l = l.substring(0, lastSpaceIndex); // drop
																			// the
																			// last
																			// word
						String key = prefix_l.replaceAll("\\s", "");
						copiedText.put(key, prefix_l);
					}

					// line without the first word
					int firstSpaceIndex = l.indexOf(" ");
					if (firstSpaceIndex > 0) {
						String surfix_l = l.substring(firstSpaceIndex,
								l.length()); // drop the first word
						String key = surfix_l.replaceAll("\\s", "");
						copiedText.put(key, surfix_l);
					}

					// line without the first and the last word
					if (firstSpaceIndex > 0 && lastSpaceIndex > firstSpaceIndex) {
						String middle_l = l.substring(firstSpaceIndex,
								lastSpaceIndex);
						String key = middle_l.replaceAll("\\s", "");
						copiedText.put(key, middle_l);
					}
				}
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*read pages into tables*/
	public void readPages(File xmlfile) {
		SAXBuilder sb = new SAXBuilder();
		try {
			Document doc = (Document) sb.build(xmlfile);
			Element root = doc.getRootElement();
			Element body = root.getChild("BODY");
			List pages = body.getChildren("OBJECT");
			String pageNum = "";
			for (int j = 0; j < pages.size(); j++) {
				Element e_page = (Element) pages.get(j);
				Element page_content = null;
				if (e_page != null) {
					pageNum = e_page.getAttributeValue("usemap");
					Element pageColumn = e_page.getChild("HIDDENTEXT")
							.getChild("PAGECOLUMN");
					if (pageColumn != null) {
						page_content = pageColumn.getChild("REGION")
								.getChild("PARAGRAPH");
					}
				}

				if (page_content != null) {
					processPage(page_content, this.prefix, pageNum);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//p0052.djvu
	public int getPageNum(String pageNumStr) {
		pageNumStr = pageNumStr.substring(1, pageNumStr.indexOf(".djvu"));
		return Integer.parseInt(pageNumStr);
	}
	
	public void processPage(Element page, String source, String pageNum) {
		//check if content started or ended
		int pageN = getPageNum(pageNum);
		if (pageN < starting_page || pageN > ending_page) {
			return;
		}
		
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();
		ArrayList<Integer> bottoms = new ArrayList<Integer>();
		Line last_line = null;
		// first get all content
		
		ArrayList<Line> lines = getAllLinesInOrder(page, source, pageNum);
		
		identifyPageTitles(lines);

		// process content: readLine
		for (Line line : lines) {
			//todo: modify paragraph separation
			//processLine(paras, types, bottoms, line, bottom_of_last_line);
			last_line = processLine(paras, types, bottoms, line, last_line);
		}

		addParagraphs(paras, source, types, bottoms, pageNum);

		System.out.println("--" + pageNum + " fetched");
	}
	
	/**
	 * 1. could be last line, with no page title
	 * 2. first two lines, one is number, one is title
	 * @param lines
	 */
	public void identifyPageTitles(ArrayList<Line> lines) {
		if (lines.size() == 0) {
			return;
		} 
		if (lines.size() == 1) {
			if (isPageNumber(lines.get(0).get_text())) {
				lines.remove(0);
			}
			return;
		}
		
		//check first two lines
		Line line1 = lines.get(0);
		Line line2 = lines.get(1);
				
		if (isPageNumber(line1.get_text())) {
			if (line2.get_top_coord() < line1.get_bottom_coord()) {
				lines.remove(1);
				lines.remove(0);
			} else {
				lines.remove(0);
			}
			return;
		}
		//check last 
		Line last_line = lines.get(lines.size() - 1);
			
		if (isPageNumber(last_line.get_text())) {
			lines.remove(lines.size() - 1);
			return;
		}
	}
	
	/**
	 * check if this line is possibly end of a paragraph
	 * @param line
	 * @param column_end
	 * @return  0 - definitely not end
	 * 			3 - definitely end: has space at end and end with period
	 * 			1 - possibly end: end with period but with only little space
	 * 				will use the indentation of next line to decide if it is end or not
	 * 			2 - a title, but could have next line as a combined title
	 *  
	 */
	public int get_end_status(Line line, String type, Line last_line) {
		String text = line.get_text();		
		if (text.endsWith(".")) {//end with period
			//if end with more than one period, it is the middle of a key statement. return 0
			if (text.endsWith("..")) 
				return 0;
			
			if (type.contains("taxonname"))
				return 3;
			
			if (withInden_end(line.get_right_coord(), last_line))
				return 3;
			else 
				return 1;
		} else if (text.matches(".*?[,;-]$")){//end with ,;-
			return 0;
		} else if (text.endsWith("]")) {//special cases: para ends with ]
			return 1; 
		} else {//not .,;]-  could be taxon, could be key statement, could be key title
			if (type.contains("taxon") || type.contains("key_title")) {
				return 2;
			} else if (type.contains("key_statement")) {
				//if matches end statement pattern return 3
				if (text.matches(p_key_statement_tail)) {
					return 3;
				} else {
					return 0;
				}
				//else return 0
			}
		}
		return 0;
	}
	
	/**
	 *is a taxon title
	 *is a key title
	 *is a figure/table title
	 *
	 * @param line
	 * @param column_begin
	 * @return
	 */
	public boolean is_start_of_para(Line line) {
		boolean is_start = false;//default not start
		String text = line.get_text();
		if (isTaxonTitle(text) || isFigTblCaption(text) 
				|| isKeyStatement(text)) {
			return true;
		}
		
		return is_start;
	}
	
	public boolean is_new_para(Line line, Line last_line, int status_of_previous_line, 
			String last_type) {
		boolean isNew = false;
		/**
		 * this line starts a new paragraph:
		 * L1: distance from last line is over XX
		 * L2: last line is end of key statement
		 * L3: last para ends with period && last para is XX shorter than end-point
		 * L4: last para ends with period && this line start with XX larger than start-point
		 * L5: is start of a taxon title
		 * L6: is start of a key title
		 * L7: is start of figure/table title
		 */
		
		if (last_line != null) {
			if (status_of_previous_line == 3) {//last line is end of a paragraph
				return true;				 
			} else if (status_of_previous_line == 2) {//could be combined title
				//todo
			} else if (status_of_previous_line == 1) {
				//last line either end with . or ], could be end
				if (withInden_start(line.get_left_coord(), last_line)) {
					return true;
				} else {
					return false;
				}
			} else {//last line is not end 
				return false;
			}
		} else {//always start a new para for each page
			return true;
		}		
		
		return isNew;
	}
	
	String getLastFromArray(ArrayList<String> arrs) {
		String last = "";
		if (arrs.size() > 0) {
			last = arrs.get(arrs.size() - 1);
		}
		return last;
	}

	/**
	 * this line starts a new paragraph:
	 * L1: distance from last line is over XX
	 * L2: last line is end of key statement
	 * L3: last para ends with period && last para is XX shorter than end-point
	 * L4: last para ends with period && this line start with XX larger than start-point
	 * L5: is start of a taxon title
	 * L6: is start of a key title
	 * L7: is start of figure/table title
	 * 
	 * paragraph add up logic: last paragraph not ended
	 * L1: last para end with -,
	 * L2: last para ends at very close to end-point of columm && this para start at very close to start-point of column
	 * L3: unclosed brackets
	 * 
	 * special case: add up to last paragraph
	 * S1: is the 2nd line of title (key title, taxon title)
	 * S2: paragraph separated by page
	 * 
	 * S3: title in 2 lines, 1st line cross entire page, 2nd line lies in left column
	 */
	public Line processLine(ArrayList<String> paras, ArrayList<String> types,
			ArrayList<Integer> bottoms, Line line, Line last_line) {
		String original_text = line.get_text();
		String text = original_text.trim();
		if (!text.equals("")) {
			if (text.matches("^\\d+|\\w$")) {
				return last_line; //if just number or just one character, must be figtxt. 
				//no need to process
			}
			
			//set default values
			text = fixSpace(text);
			text = fixMissedSpace(text);
			
			String lastPara = getLastFromArray(paras);
			String lastType = getLastFromArray(types);
			boolean newPara = is_start_of_para(line);
			if (last_line != null) {
				newPara = newPara || withParaSpace(last_line.get_bottom_coord(), line.get_top_coord());
			}
			String type = "unassigned";
			
			//decide whether this line is a new paragraph
			if (!newPara && last_line != null) {
				int status_last_line = last_line.getStatus();
				
				if (status_last_line == 3) {//last is end
					newPara = true;
				} else if (status_last_line == 2) {//last is title
					//check if combined title
					if (!isCombinedTitle(last_line.get_text().trim() + " " + text.trim())) {
						newPara = true; //not combined title
					}
				} else if (status_last_line == 1) {//check indentation of this line
					if (isDistributionPara(text)) {
						newPara = true;
					} else if (withInden_start(line.get_left_coord(), last_line)) {
						newPara = true;
					}
				}
			}			
			
			//decide type
			if (newPara || last_line == null) {
				//taxon? fig/tbl? 
				type = getType(text);
			} else {
				type = lastType;
			}
			
			// update para
			if (newPara || paras.size() == 0) {
				paras.add(text);// insert para 
				types.add(type);// insert type
				bottoms.add(line.get_bottom_coord());
			} else {
				// update para
				if (!lastPara.endsWith("-")) {
					lastPara += " ";
				}
				paras.set(paras.size() - 1, lastPara + text);

				// update type
				if (types.get(types.size() - 1).equals("unassigned")) {
					type = getType(lastPara + text);
					types.set(types.size() - 1, type);
				}
				bottoms.set(bottoms.size() - 1, line.get_bottom_coord());
			}
			
			line.setStatus(get_end_status(line, type, last_line));
		}		
		return line;
	}
	
	public boolean isFigTblCaption(String text) {
		text = text.trim();
		if (text.matches(this.p_fig_tbl_cap)) {
			return true;
		}
		return false;
	}
	
	public boolean isfigTblTxt(String para) {
		Pattern p = Pattern.compile(p_fig_tbl_txt);
		Matcher mh = p.matcher(para);
		if (mh.matches()) {
			return true;
		}
		if (para.length() < 25) {
			// t.os
			// word1 word2 / word1-word2/word1
			if (para.matches("^[a-z\\s\\-]*") || para.matches("t\\.os")
					|| para.matches("^(ray,)(\\s(first)\\s(order))?")) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * sort all content lines in order, exclude the page number and page title
	 * goal: to solve the column switch problem.
	 * 
	 * Logic of exclude lines of page number and page title: 
	 * 3 cases: 
	 * 	a. line 0 is page number, line 1 is page title 
	 *  b. line 0 is page title, line 1 is page number 
	 *  c. line 0 is page number, last line of page is page title (in
	 * volume L_4) 
	 * 
	 * The checking logic is : 
	 * 	1. if line 0 is page number, try to
	 * find the page title in the rest of lines 2. if line 0 is not page number,
	 * check if line 1 is. if so, found title. else both line 0 and line 1 is
	 * content, insert line 0.
	 * 
	 * @param page
	 * @param source
	 * @param pageNum
	 */
	public ArrayList<Line> getAllLinesInOrder(Element page, String source,
			String pageNum) {
		ArrayList<Line> allLinesInOrder = new ArrayList<Line>();
		ArrayList<Line> left_side = new ArrayList<Line>();
		ArrayList<Line> cross_middle = new ArrayList<Line>();
		ArrayList<Line> right_side = new ArrayList<Line>();
		int left_coord, right_coord, top_coord, bottom_coord;

		// read all lines into leftside, crossingmiddle, rightside, remember
		// their top
		List lines = page.getChildren("LINE");
		
		for (int i = 0; i < lines.size(); i++) {
			Element e_line = (Element) lines.get(i);
			if (e_line != null) {
				String text = e_line.getValue().trim().replaceAll("\n", "");

				if (text.matches("[^\\w]+")) {// ignore the line if non-word
					continue;
				}

				// get coordinates of the line
				String coordinate = e_line.getAttributeValue("coords");
				String[] coords = coordinate.split(",");
				if (coords.length == 4) {
					left_coord = Integer.parseInt(coords[0]);
					bottom_coord = Integer.parseInt(coords[1]);
					right_coord = Integer.parseInt(coords[2]);
					top_coord = Integer.parseInt(coords[3]);

					Line new_line = new Line(text, left_coord, bottom_coord,
							right_coord, top_coord);
					insertLine(new_line, left_side, right_side, cross_middle);
				}
			}
		}

		// sort them by top
		if (cross_middle.size() > 1) {
			Collections.sort(cross_middle, new LineComparator());
		}
		if (left_side.size() > 1) {
			Collections.sort(left_side, new LineComparator());
		}
		if (right_side.size() > 1) {
			Collections.sort(right_side, new LineComparator());
		}

		// read from leftside, crossingmiddle, rightside
		/**
		 * get middle one by one, get its top, then insert text in order of 1.
		 * all left before top 2. all right before top 3. the middle one
		 * 
		 * After processing all middle text, get all left, then get all right
		 */
		String test = "";
		for (int i = 0; i < cross_middle.size(); i++) {
			String middle_text = cross_middle.get(i).get_text();
			int separator = cross_middle.get(i).get_bottom_coord();

			// get all lines before separator from left_side
			while (left_side.size() > 0) {
				if (left_side.get(0).get_top_coord() < separator) {
					test += (left_side.get(0).get_text())
							+ System.getProperty("line.separator");
					allLinesInOrder.add(left_side.remove(0));
				} else
					break;
			}

			// get all lines before separator from right_side
			while (right_side.size() > 0) {
				if (right_side.get(0).get_top_coord() < separator) {
					test += (right_side.get(0).get_text())
							+ System.getProperty("line.separator");
					allLinesInOrder.add(right_side.remove(0));
				} else
					break;
			}

			// insert the middle line after getting all text above it
			allLinesInOrder.add(cross_middle.get(i));
			test += (cross_middle.get(i).get_text())
					+ System.getProperty("line.separator");
		}

		// get the rest of lines from left_side
		while (left_side.size() > 0) {
			test += (left_side.get(0).get_text())
					+ System.getProperty("line.separator");
			allLinesInOrder.add(left_side.remove(0));
		}

		// get the rest of lines from right_side
		while (right_side.size() > 0) {
			test += (right_side.get(0).get_text())
					+ System.getProperty("line.separator");
			allLinesInOrder.add(right_side.remove(0));
		}

		return allLinesInOrder;
	}

	/**
	 * insert line into the correct arraylist by comparing the coordinates of
	 * line and deside which array list it belongs to
	 * 
	 * @param line
	 * @param left_side
	 * @param right_side
	 * @param cross_middle
	 */
	public void insertLine(Line line, ArrayList<Line> left_side,
			ArrayList<Line> right_side, ArrayList<Line> cross_middle) {
		/*special case: KEY TO FAMILIES should be in the middle*/
		if (line.get_text().trim().matches("^KEY\\sTO\\s[A-Z\\s]+$")) {
			cross_middle.add(line);
			return;
		}
		
		int right_coord = line.get_right_coord();
		int left_coord = line.get_left_coord();
		// check if the new line belongs to left side or right side or it
		// crosses the middle point of page
		if (right_coord < this.page_middle_point) {
			left_side.add(line);
		} else if (left_coord > this.page_middle_point) {
			right_side.add(line);
		} else {
			cross_middle.add(line);
		}
	}
	
	public void addParagraphs(ArrayList<String> paras, String source,
			ArrayList<String> types, ArrayList<Integer> bottoms, String pageNum) {

		try {
			DatabaseAccessor.insertParagraphs(this.prefix, paras, source, conn,
					types, pageNum, bottoms);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//sometimes space is missing between two words through copied text
	public String fixMissedSpace(String text) {
		String rv = text;
		Pattern p = Pattern.compile("^.+\\w{2,}([a-z][A-Z]).+$");
		Matcher mt = p.matcher(text);
		if (mt.matches()) {
			String toFix = mt.group(1);
			String theFix = toFix.substring(0, 1) + " " + toFix.substring(1);
			rv = text.replaceAll(toFix, theFix);
		}
		return rv;
	}
	
	/**
	 * use four hash tables to fix incorrect space in xml
	 * 
	 * @param line
	 * @return
	 */
	public String fixSpace(String line) {
		String lastword = ""; // the last word
		String firstword = "";
		String line_forCompare = line;

		// original compare
		String key = line_forCompare.replaceAll("\\s", "");
		String fixed_txt = copiedText.get(key);
		if (fixed_txt != null) {
			if (fixed_txt.length() < line_forCompare.length()) {
				return fixed_txt;
			} else {
				return line;
			}
		}

		// drop last word and compare
		int lastSpaceIndex = line.lastIndexOf(" ");
		if (lastSpaceIndex > 0) {
			line_forCompare = line.substring(0, lastSpaceIndex);
			lastword = line.substring(lastSpaceIndex, line.length());
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedText.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					return fixed_txt + lastword;
				} else {
					return line;
				}
			}
		}

		// drop the first word and compare
		int firstSpaceIndex = line.indexOf(" ");
		if (firstSpaceIndex > 0) {
			line_forCompare = line.substring(firstSpaceIndex, line.length());
			firstword = line.substring(0, firstSpaceIndex);
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedText.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					// may match the txt without leading space
					fixed_txt = firstword + " " + fixed_txt;
					return fixed_txt.replaceAll("\\s\\s", " ");
				} else {
					return line;
				}
			}
		}

		// drop the first and the last word and compare
		if (firstSpaceIndex > 0 && lastSpaceIndex > 0
				&& lastSpaceIndex > firstSpaceIndex) {
			line_forCompare = line.substring(firstSpaceIndex, lastSpaceIndex);
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedText.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					fixed_txt = firstword + " " + fixed_txt + " " + lastword;
					return fixed_txt.replaceAll("\\s\\s", " ");
				} else {
					return line;
				}
			}
		}

		return line;
	}
	
	/**
	 * decide whether this line could be page number, if yes, must be in a
	 * separate paragraph
	 * 
	 * @param para
	 * @return
	 */
	public boolean isPageNumber(String para) {
		para = para.trim();
		if (para.matches("^\\d+\\s{2,}(.*)") || para.matches("\\d+")
				|| isRomePageNumber(para) || para.matches("(.*?)\\s{2,}\\d+$")) {
			return true;
		} else
			return false;
	}
	
	public boolean isRomePageNumber(String line) {
		line = line.trim();
		if (line.matches("^(?i)M*(D?C{0,3}|C[DM])(L?X{0,3}|X[LC])(V?I{0,3}|I[VX])$")) {
			return true;
		}
		return false;
	}
	
	public boolean isCombinedTitle(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_taxon_title);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
			
			p = Pattern.compile(this.p_key_combined_title);
			mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isTaxonTitle(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_taxon_title);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public String getType(String text) {
		if (isTaxonTitle(text)) {
			return "content_taxonname";
		} else if (text.matches(p_normal_title)) {//start with index
			return "content_title";
		} else if (isKeyTitle(text)) {
			return "content_key_title";
		} else if (isKeyStatement(text)) {
			return "content_key_statement";
		} else if (isFigTblCaption(text)) {
			if (text.matches(p_fig_cap))
				return "noncontent_fig_figtbl";
			else 
				return "noncontent_tbl_figtbl";
		}
		
		return "unassigned";
	}
	
	public boolean isKeyTitle(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_title);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isDistributionPara(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_distribution);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isKeyCombinedTitle (String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_combined_title);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isTypeSpeciesPara(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_type_species_para);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isKeyStatement(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_statement_start);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isCompletedKeyStatement(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_statement);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isLeftColomn(int cood) {
		if (cood > page_middle_point) 
			return false;
		else 
			return true;
	}
	
	public boolean withInden_start(int cood, Line last_line) {		
		int begin = left_column_begin;
		if (!isLeftColomn(cood)) {
			begin = right_column_begin;
		}
		
		if (last_line != null) {
			if (isFigTblCaption(last_line.get_text())) {
				begin = last_line.get_left_coord();
			}
		}
		
		if (isTypeSpeciesPara(last_line.get_text()) 
				&& last_line.get_left_coord() - cood > 35 ) {
			return true;
		}
		
		return cood - begin > new_para_indent;
	}
	
	public boolean withInden_end(int cood, Line last_line) {
		int end = left_column_end;
		if (!isLeftColomn(cood)) 
			end = right_column_end;
		
		if (last_line != null && isFigTblCaption(last_line.get_text()))
				end = last_line.get_right_coord();
		
		return end - cood > space_between_para;
	}
	
	public boolean withParaSpace(int last_bottom, int this_top) {
		if (this_top > last_bottom) {
			return this_top - last_bottom > space_between_para;
		}
		return false;
	}
	
	/*output xml files*/
	public String taxonFolder = "D:\\Work\\Data\\bees\\Taxons\\";
	public String keyFilesFolder = "D:\\Work\\Data\\bees\\KeyFiles\\";
	private File[] sourceFiles = null;
	
	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	private ArrayList<String> ranksList = new ArrayList<>();
	private Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	private String volume = "";
	private int keyFileNumber = 0; 
	private int taxonFileNumber = 0;
	
	public void generateTaxonXmls() {
		// get txt files
		File sourceFolder = new File(this.cleanTxtPath);
		this.source = sourceFolder.getName();
		this.sourceFiles = sourceFolder.listFiles(); 

		// construct ranks
		ranks.put("Phylum", 1);
		ranksList.add("Phylum");
		
		ranks.put("Subphylum", 2);
		ranksList.add("Subphylum");
		
		ranks.put("Class", 3);
		ranksList.add("Class");
		
		ranks.put("Subclass", 4);
		ranksList.add("Subclass");
		
		ranks.put("Order", 5);
		ranksList.add("Order");
		
		ranks.put("Suborder", 6);
		ranksList.add("Suborder");
		
		ranks.put("Superfamily", 7);
		ranksList.add("Superfamily");
		
		ranks.put("Family", 8);
		ranksList.add("Family");
		
		ranks.put("Subfamily", 9);
		ranksList.add("Subfamily");
		
		ranks.put("Tribe", 10);
		ranksList.add("Tribe");
		
		ranks.put("Genus", 11);
		ranksList.add("Genus");
		
		ranks.put("Subgenera", 12);
		ranksList.add("Subgenera");
		
		ranks.put("Subgenus", 13);
		ranksList.add("Subgenus");
		
		// get conn
		try {
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		ExtractTaxon();
	}
	
	public String removeIndex(String title) {
		String rv = title;
		Pattern p = Pattern.compile("^" + p_index + ".+$");
		Matcher mt = p.matcher(title);
		if (mt.matches()) {
			return title.substring(title.indexOf(".") + 1, title.length());
		}
		return rv.trim();
	}
	
	/**
	 * process taxon by file
	 */
	protected void ExtractTaxon() {
		for (File eachTxtFile : sourceFiles) {
			if (eachTxtFile.getName().startsWith(".")) {
				continue;
			}
			
			this.source = eachTxtFile.getName();
			this.volume = eachTxtFile.getName().substring(0,
					eachTxtFile.getName().lastIndexOf(".")).replaceAll("[^\\w]", "_");
			this.keyFileNumber = 0;
			this.taxonFileNumber = 0;
			
			try {
				FileInputStream fstream = new FileInputStream(eachTxtFile);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8"); 
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				ArrayList<Taxon_bees> taxonList = new ArrayList<Taxon_bees>();
				ArrayList<KeyFile> keyFiles = new ArrayList<KeyFile>();
				
				Taxon_bees taxon = null;
				KeyFile keyfile = null;
				KeyStatement ks = null;
				boolean inTaxon = true; //either in taxon or in key
				
				while ((line = br.readLine()) != null) {
					line.trim();
					if (line.equals("")) {
						continue;
					}
					
					//if is taxon || is key title, add taxon and create new taxon
					boolean isTaxon = isTaxonTitle(line);
					boolean isKeyTitle = isKeyTitle(line);		
					if (isTaxon || isKeyTitle) {
						if (keyfile != null) {
							keyFiles.add(keyfile);
							keyfile = null;
						}
						
						//if taxon is not null, update the key file to the taxon
						if (isKeyTitle) {
							inTaxon = false;
							String keyHeading = removeIndex(line);
							keyFileNumber++;
							//create key file
							keyfile = new KeyFile(keyHeading, keyFileNumber);
							if (taxon != null) {
								taxon.setKeyFile(keyfile.getFileName());	
							}
						}
						
						if (taxon != null) {
							//add taxon to list
							taxonList.add(taxon);
							taxon = null;
						} 
						
						if (isTaxon) {
							inTaxon = true;
							taxonFileNumber++;
							//create taxon
							String taxonname = removeIndex(line);
							taxon = new Taxon_bees(taxonname, taxonFileNumber);
							String myRank = getRank(taxonname);
							taxon.setRank(myRank);
							if (!myRank.equals("undertermined")) {
								priorRanks.put(myRank, taxonname);	
							}
							taxon.setHierarchy(getHierarchy(myRank));
						}
						
						continue;
					}
					
					if (inTaxon) {
						if (isTypeSpeciesPara(line)) {							
							taxon.setType_species(addLineToArr(taxon.getType_species(), line));
						} else if (isDistributionPara(line)) {
							taxon.setDistribution(appendLine(taxon.getDistribution(), line));
						} else {
							if (isCompletedKeyStatement(line)) {
								System.out.println("****Wrong section");
							}
							taxon.setText(addLineToArr(taxon.getText(), line));
						}
					} else { // in key file
						if (isCompletedKeyStatement(line)) {
							if (line.matches("^\\d.+")) {//is start of a statement
								//add a key statement
								keyfile.setStatements(
										addKeyStatemenToArr(keyfile.getStatements(), createKeyStatement(line)));
								
							}
							updateLastKeyStatement(keyfile, line);
						} else {//add to discussion
							keyfile.setDiscussion(addLineToArr(keyfile.getDiscussion(), line));
						}
					}
				}
				
				//insert last key or taxon
				if (taxon != null) {
					taxonList.add(taxon);
				}
				if (keyfile != null) {
					keyFiles.add(keyfile);
				}

				outputTaxons(taxonList);
				outputKeyFiles(keyFiles);

				System.out.println("taxons and keys generated");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public KeyStatement createKeyStatement(String line) {
		KeyStatement ks = new KeyStatement();
		if (line.matches("^\\d+\\(.+")) {//has from id
			ks.setId(line.substring(0, line.indexOf("(")));
			ks.setFrom_id(line.substring(line.indexOf("(") + 1, line.indexOf(")")));
		} else {
			ks.setId(line.substring(0, line.indexOf(".")));
			ks.setFrom_id("");
		}
		return ks;
	}
	
	public void updateLastKeyStatement(KeyFile kf, String line) {
		ArrayList<KeyStatement> kss = kf.getStatements();
		KeyStatement last_ks = kss.get(kss.size() - 1);
		
		KeyChoice kc = new KeyChoice();
		kc.setStatement(line);
		Pattern p = Pattern.compile("^.+\\.{2,}\\s(\\d+)$");
		Matcher mt = p.matcher(line);
		if (mt.matches()) {
			kc.setNext_id(mt.group(1));
		} else {
			kc.setDetermination(line.substring(line.lastIndexOf("..") + 2, line.length()).trim());
		}
		
		ArrayList<KeyChoice> kcs = last_ks.getChoices();
		kcs.add(kc);
		last_ks.setChoices(kcs);
		
		kss.set(kss.size() - 1, last_ks);
		kf.setStatements(kss);
	}
	
	public String getHierarchy(String myRank) {
		String hierarchy = "";
		
		for (String thisRank : ranksList) {	
			if (ranks.get(thisRank) <= ranks.get(myRank)) {
				if (priorRanks.get(thisRank) != null) {
					if (hierarchy.equals("")) {
						hierarchy += priorRanks.get(thisRank);
					} else {
						hierarchy += "; " + priorRanks.get(thisRank);
					}
				}
			} else break;
		}
		return hierarchy;
	}
	
	public String getRank(String name) {
		String rank = "undetermined";
		Enumeration<String> em = ranks.keys();
		while (em.hasMoreElements()) {
			String temp = em.nextElement();
			if (name.contains(temp)) {
				return temp;
			}
		}
		return rank;
	}
	
	public ArrayList<String> addLineToArr(ArrayList<String> originalList, String line) {
		ArrayList<String> rv = originalList;
		rv.add(line);
		return rv;
	}
	
	public ArrayList<KeyStatement> addKeyStatemenToArr(ArrayList<KeyStatement> originalList, KeyStatement ks) {
		ArrayList<KeyStatement> rv = originalList;
		rv.add(ks);
		return rv;
	}
	
	public String appendLine(String originalLine, String line) {
		return originalLine + " " + line;
	}

	/**
	 * Pattern:
	 * part 1: Abc | A. (Abc)
	 * part 2: (A+all kinds of characters)
	 * part 3: NAME'SGEW &|and NAME2'SE &|and NAME3
	 * part 4: in PUBLICATION
	 * part 5: year: 1885 1885b
	 * part 6: page number: p. [0-9]+
	 * 
	 * @param text: the text before [
	 * @return
	 */
	protected String getName(String text) {
		Pattern p = Pattern.compile(Patterns.nameSplitPattern);
		Matcher m = p.matcher(text);
		String name = "";
		if (m.matches()) {
			for (int i = 1; i <= 3; i++) {
				if (m.group(i) != null) {
					name += m.group(i);
				}
			}
		} else {
			int commaIndex = text.indexOf(",");
			if (commaIndex > 0) {
				int leftBracketIndex = text.lastIndexOf("(");
				int rightBracketIndex = text.lastIndexOf(")");
				name = text.substring(0, commaIndex).trim();
				if (commaIndex > leftBracketIndex && commaIndex < rightBracketIndex) {
					String rest = text.substring(rightBracketIndex + 1, text.length()).trim();
					name = text.substring(0, rightBracketIndex + 1) + " " + rest.substring(0, rest.indexOf(","));
				}
			} else {
				name = text;
			}
		}
		return name;
	}

	protected void outputTaxons(ArrayList<Taxon_bees> taxons) throws Exception {
		for (Taxon_bees taxon : taxons) {
			// write xml file
			outputXMLFile(taxon);
			// add record to database: params: filename, name, hierarchy
			DatabaseAccessor.insertTaxonFileRelation(volume, taxon.getName(),
					taxon.getHierarchy(), taxon.getFilename(), conn);	
		}
	}
	
	public void outputKeyFiles(ArrayList<KeyFile> kfs) {
		for (KeyFile kf : kfs) {
			outputKeyFile(kf);
		}
	}

	private void outputXMLFile(Taxon_bees taxon) {
		try {
			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);
			
			// populate meta
			addElement(meta, this.source, "source");
			addElement(meta, this.volume, "volume");
			
			// nomenclature
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);

			addElement(nomen, taxon.getName(), "name");
			addElement(nomen, taxon.getRank(), "rank");
			addElement(nomen, taxon.getHierarchy(), "taxon_hierarchy");
			
			//output text
			addElements(root, taxon.getText(), "text");
			
			addElements(root, taxon.getType_species(), "type_species");
			addElement(root, taxon.getDistribution(), "distribution");
			
			//output key file name
			addElement(root, taxon.getKeyFile(), "key_file");
			
			// output xml file
			File f = new File(taxonFolder, taxon.getFilename() + ".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void outputKeyFile(KeyFile keyfile) {
		try {
			// create doc
			Element root = new Element("key");
			Document doc = new Document(root);

			addElement(root, keyfile.getHeading(), "key_heading");
			addElements(root, keyfile.getDiscussion(), "key_discussion");
			
			// nomenclature
			ArrayList<KeyStatement> kss = keyfile.getStatements();
			for (KeyStatement ks : kss) {
				Element e_ks = new Element("key_statement");
				root.addContent(e_ks);
				
				addElement(e_ks, ks.getId(), "statement_id");
				addElement(e_ks, ks.getFrom_id(), "statement_from_id");
				
				ArrayList<KeyChoice> kcs = ks.getChoices();
				for (KeyChoice kc : kcs) {
					Element e_kc = new Element("key_choice");
					e_ks.addContent(e_kc);
					
					addElement(e_kc, kc.getStatement(), "statement");
					addElement(e_kc, kc.getDetermination(), "determination");
					addElement(e_kc, kc.getNext_id(), "next_statement_id");
				}
			}
						
			// output xml file
			File f = new File(keyFilesFolder, keyfile.getFileName() + ".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void addElements(Element parent, ArrayList<String> arrs,
			String tagName) {
		if (arrs != null && arrs.size() > 0) {
			for (String str : arrs) {
				if (!str.equals("")) {
					Element e = new Element(tagName);
					e.setText(str);
					parent.addContent(e);
				}
			}
		}
	}
	
	public void addElement(Element parent, String str, String tagName) {
		if (!(str == null) && !str.equals("")) {
			Element e = new Element(tagName);
			e.setText(str);
			parent.addContent(e);
		}
	}
	
	/**
	 * type 1: taxon xml
	 * <treatment>
	 * 	<meta><source/></meta>
	 * 	<nomenclature>	
	 * 		<name/>
	 * 		<rank/>
	 * 		<taxon_hierarchy/>
	 * 	</nomenclature>
	 * 	<text/>
	 * 	<type_species/>
	 * 	<distribution/>
	 * 	<key/>
	 * </treatment>
	 * 
	 * type 2: key xml 
	 * <key>
	 * 	<key_head/>
	 * 	<key_discussion/>
	 * 	<key_statement>
	 * 		<statement_id/>
	 * 		<statement/>
	 * 		<determination/>
	 * 		<next_statement_id/>
	 * 	</key_statement>
	 * </key>
	 * @param taxon
	 */
	
}

