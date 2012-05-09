package informationContent;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import beans.ColumnBean;
import beans.ParagraphBean;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import db.DatabaseAccessor;

/**
 * 1. convert pdf to djvu 2. convert djvu to xml 3. remove the DOCTYPE line of
 * the xml file
 * 
 * 1. define first line and last line 2. page number line: may be two lines:
 * check the first two lines' y1, if no greater difference than 5, consider both
 * page number 3. taxonName format: Abcdef ABEASWT wetqwett, 1994 [xxxx] [] 4.
 * inner section format: all capital 5. fig (description of figure) 6. figText
 * (text inside fiture): short text. usually before fig, sigle word, no space
 * after trim
 * 
 * @author Fengqiong
 * 
 */
public class ContentExtract {

	protected ArrayList pageNumberText = null;
	protected ArrayList footNoteTokens = null;
	protected File sourceFile = null;
	protected String prefix = null;
	public long lineLength = 75;
	public int lastLength = 0;
	public long totalLength = 0; // for lineLength calculation
	public long linecount = 0; // for lineLength calculation
	protected boolean epilogBegings = false; // once this is true, all text will
												// be marked as
												// noncontent_epilog
	protected boolean hasToCHeading = false;
	protected boolean hasToCDots = false;
	protected boolean hasIndex = false;
	protected Connection conn = null;
	protected static String url = ApplicationUtilities
			.getProperty("database.url");
	protected boolean newNextPara = false; // to specify the next line should be
											// a
											// new paragraph
	protected String figurePattern = "(FlG|F\\s?i\\s?G|F\\s?I\\s?G|F\\s?i\\s?g)\\s?\\.\\s+\\d+\\s?\\.\\s+.*?";
	protected String figException = "";
	protected String tablePattern = "(TABLE|Table)\\s+\\d+\\s*\\..*?";
	protected String figtblTxtPattern;
	protected String innerSectionPattern = "^([A-Z]+\\s?)+[A-Z]+";
	protected String taxonNamePattern = "";

	protected String startText = "SYSTEMATIC DESCRIPTIONS";// define the first
															// line
	protected String endText = "REFERENCES";// define the last line

	protected boolean contentStarted = false;
	protected String startPage = "331";
	protected boolean contentEnded = false;
	protected String cleanTableSurfix = "_clean_paragraphs";
	protected String outputPath = "E:\\work_data\\clean_o\\";
	protected String txtPath = "E:\\work_data\\txt\\";
	protected String txtFileSuffix = ".txt";
	protected int lengthOfFootnoteKey = 50;
	protected int spacefixed = 0;
	
	Hashtable<String, String> copiedTxtHash = new Hashtable<String, String>();
	protected Hashtable<String, String> footnotes = new Hashtable<String, String>();


	// regular figurepattern:
	// (Fig\\.|Figure|Table|FIG\\s?\\.|TABLE|FIGURE|FlG\\.|FlGURE)\\s+\\d+\\s*\\.\\s+.*?
	/**
	 * 
	 */
	public ContentExtract() {
		this.taxonNamePattern = Patterns.taxonNamePattern;
		String sourceFilePath = "";
		this.outputPath = "E:\\work_data\\clean\\";
		this.figtblTxtPattern = "^\\d+(\\.\\d+)?\\s?\\??mm?" +
				"|(\\d\\d?[a-z]*\\d?)(\\s?\\d\\d?[a-z])*\\'*" +
				"|[a-z0-9]*" +
				"|[a-z]+" +
				"|[a-z][0-9](\\s[a-z][0-9])+" +
				"|[A-Z]+\\'*" +
				"|([A-Z][a-z]*(\\s[A-Z][a-z]*)*)" +
				"|([A-Z][a-z]*\\s)?\\([A-Z][a-z]*\\)" + 
				"|\\([a-z]+(\\s[a-z]+)*\\)";
//(transmedian,
//		anterior lateral)
			
//		// volume o
//		sourceFilePath = "E:\\work_data\\xml\\xml_o";
//		this.startText = "SYSTEMATIC DESCRIPTIONS";
//		this.endText = "REFERENCES";
//		this.startPage = "331";
		
		// volume b
		sourceFilePath = "E:\\work_data\\xml\\xml_b";
		this.startText = "SYSTEMATIC DESCRIPTIONS";
		this.startPage = "108";
		this.endText = "NOMINA DUBIA AND GENERIC NAMES WRONGLY";
//				
//		// volume h --nothing in this volume
//		sourceFilePath = "E:\\work_data\\xml\\xml_h";
//		this.startText = "ANATOMY";
//		this.startPage = "27";
//		this.endText = "REFERENCES";
//
//		// volume e_2 --nothing in this volume
//		sourceFilePath = "E:\\work_data\\xml\\xml_e_2";
//		this.startText = "GENERAL FEATURES OF THE PORIFERA";
//		this.startPage = "29";
//		this.endText = "GLOSSARY OF MORPHOLOGICAL TERMS";
//
//		// volume e_3 
//		sourceFilePath = "E:\\work_data\\xml\\xml_e_3";
//		this.startText = "PALEOZOIC DEMOSPONGES";
//		this.startPage = "9";
//		this.endText = "RANGES OF TAXA";
//		this.figException = "FIG. 33a";
//		// exception: FIG. 33a-d.Grow, p47
//		//this.figurePattern = "(FlG|FIG)\\s?\\.\\s+\\d+(\\s?\\.\\s+)?.*?";
//		// exception: p52(2b2c, 2b 2c) p57(500 ?m; 50?m; 20 mm; 29mm; 0.125 mm; Erylus (Erylus))
		
//		//volume h_2 - problem of table content
//		sourceFilePath = "E:\\work_data\\xml\\h_2";
//		this.startText = "BRACHIOPODA";
//		this.startPage = "60";
//		this.endText = ""; //no end, till the last word
		
//		//volume h_3 - problem of missing columns; long figure text
//		sourceFilePath = "E:\\work_data\\xml\\h_3";
//		this.startText = "PRODUCTIDINA";
//		this.startPage = "4";
//		this.endText = "REFERENCES";
		
//		//volume h_4 - problem of missing text; other pattern of figure text
//		sourceFilePath = "E:\\work_data\\xml\\h_4";
//		this.startText = "PENTAMERIDA";
//		this.startPage = "41";
//		this.endText = "NOMENCLATORIAL NOTE";
		
//		//volume h_5 - problem of wrong text
//		sourceFilePath = "E:\\work_data\\xml\\h_5";
//		this.startText = "SPIRIFERIDA";
//		this.startPage = "47";
//		this.endText = "NOMENCLATORIAL NOTE";
//		
//		//volume h_6 
//		//may not work for space fixing since copied text are not by line
//		sourceFilePath = "E:\\work_data\\xml\\h_6";
//		this.startText = "SYSTEMATIC DESCRIPTIONS:";
//		this.startPage = "262";
//		this.endText = "AFFINITIES OF BRACHIOPODS AND TRENDS IN"; //page2822
//		
//		//volume l_4
//		sourceFilePath = "E:\\work_data\\xml\\l_4";
//		this.startText = "SYSTEMATIC DESCRIPTIONS";
//		this.startPage = "21";
//		this.endText = "EXPLANATION OF CORRELATION CHART";
		
		
		this.sourceFile = new File(sourceFilePath);
		this.prefix = sourceFile.getName();
		try {
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);

			// set characterset to be utf8 incase of not
			// DatabaseAccessor.setCharacterSet(conn);

			DatabaseAccessor.createParagraphTable(this.prefix, conn);
			DatabaseAccessor.createCleanParagraphTable(this.prefix
					+ cleanTableSurfix, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * read this page, insert into prefix_paragraphs main function is to switch
	 * columns if the line sequences are not correct
	 */
	protected void readPage(Element page, String source, String pageNum) {
		String actgualPageNum = "";
		boolean needSwitch = false;

		List lines = page.getChildren("LINE");
		int minX = 1000, maxY = 0;
		int x1 = 0, y1 = 0;
		int pageNum_y1 = 0;
		int firstline_y1 = 0; 
		String firstline = "";
		int last_y1 = 0; // need this to separate obvious paragraph

		ArrayList<ColumnBean> columns = new ArrayList<ColumnBean>();
		
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();
		ArrayList<Integer> y1s = new ArrayList<Integer>();

		// page number lines, may be 1 or 2 lines
		ArrayList<String> pageNum_para = new ArrayList<String>();

		for (int i = 0; i < lines.size(); i++) {
			Element e_line = (Element) lines.get(i);
			if (e_line != null) {
				// get line by line
				String line = e_line.getValue().trim().replaceAll("\n", "");

				String coordinate = e_line.getAttributeValue("coords");
				String[] coords = coordinate.split(",");
				if (coords.length == 4) {
					x1 = Integer.parseInt(coords[0]);
					last_y1 = y1;
					y1 = Integer.parseInt(coords[1]);
				}

				//find page number: may be the first or second line
				if (i <= 1) {
					if (i == 0) {
						firstline_y1 = y1;
						firstline = line;
					}
					
					if (maybePageNumber(line.trim())) {
						if (i == 0) {//page number is first line
							pageNum_para.add(line);
						} else {//page number is second line
							int diff = (y1 > firstline_y1 ? y1 - firstline_y1 : firstline_y1 - y1);
							if (diff < 16) {
								pageNum_para.add(firstline + " " + line);
								//reset paras and types to clear page title
								paras = new ArrayList<String>();
								types = new ArrayList<String>();
								y1s = new ArrayList<Integer>();
							}
						}
						actgualPageNum = line;
						pageNum_y1 = y1;
						continue;	
					}
				}

				if (!contentStarted) {
					// haven't found the first line
					if (!isFirstline(line, actgualPageNum, pageNum)) {
						continue;
					} else {
						contentStarted = true;
					}
				}

				if (contentStarted && !contentEnded) {
					// started but not ended
					if (isLastline(line)) {
						contentEnded = true;
						break;
					}
				}

				// check later page title
				int diff = (y1 > pageNum_y1 ? y1 - pageNum_y1 : pageNum_y1 - y1);
				if (diff < 16) {
					pageNum_para.add(line);
					continue;
				}

				if (y1 < maxY) {
					// anothe column
					ColumnBean cb = new ColumnBean(paras, types, y1s);

					if (needSwitch) { // switch this column with last column
						assert (columns.size() > 0);
						ColumnBean temp = columns.get(columns.size() - 1);
						columns.remove(columns.get(columns.size() - 1));
						columns.add(cb);
						columns.add(temp);
						needSwitch = false;
					} else {
						columns.add(cb);
					}
					paras = new ArrayList<String>();
					types = new ArrayList<String>();
					y1s = new ArrayList<Integer>();

					if (x1 < minX - 20) {//add a threshold
						// this new column need to switch with the one just
						// added into columns
						needSwitch = true;
					}

					minX = x1;
					maxY = y1;
				} else {
					// the same column
					// update minX and maxY for each column
					minX = (x1 < minX ? x1 : minX);
					maxY = (y1 > maxY ? y1 : maxY);
				}
				readLine(paras, types, y1s, line, y1, last_y1);
			}
		}

		// add the last column
		ColumnBean cb = new ColumnBean(paras, types, y1s);
		if (needSwitch) {
			assert (columns.size() > 0);
			ColumnBean temp = columns.get(columns.size() - 1);
			columns.remove(columns.get(columns.size() - 1));
			columns.add(cb);
			columns.add(temp);
		} else {
			columns.add(cb);
		}

		if (contentStarted) {
			addParagraphs(pageNum_para, source, columns, pageNum);
		}

		System.out.println("--" + pageNum + " fetched");
	}
	
	/**
	 * 
	 * @param fileName
	 */
	protected void getCopiedText(String fileName) {
		File txtFile = new File(txtPath + fileName + txtFileSuffix);
		try {
			FileInputStream fstream = new FileInputStream(txtFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(
					new InputStreamReader(in));
			String line = "";
			while ((line = br.readLine()) != null) {
				String l = line.trim();
				if (!l.equals("")) {
					//original line
					copiedTxtHash.put(l.replaceAll("\\s", ""), l);
					
					//line without the last word
					int lastSpaceIndex = l.lastIndexOf(" ");
					if (lastSpaceIndex > 0) {
						String prefix_l = l.substring(0, lastSpaceIndex); //drop the last word
						String key = prefix_l.replaceAll("\\s", "");
						copiedTxtHash.put(key, prefix_l);
						
						//footnote
						if (prefix_l.matches("^\\d[A-Z][a-z]+,?\\s.*?")) {
							key = key.substring(1);
							if (key.length() > lengthOfFootnoteKey) {
								key = key.substring(0, lengthOfFootnoteKey);
							}
							footnotes.put(key, prefix_l.substring(1));
						}
					}
					
					//line without the first word
					int firstSpaceIndex = l.indexOf(" ");
					if (firstSpaceIndex > 0) {
						String surfix_l = l.substring(firstSpaceIndex, l.length()); // drop the first word
						String key = surfix_l.replaceAll("\\s", "");
						copiedTxtHash.put(key, surfix_l);
					}
					
					//line without the first and the last word
					if (firstSpaceIndex > 0 && lastSpaceIndex > firstSpaceIndex) {
						String middle_l = l.substring(firstSpaceIndex, lastSpaceIndex);
						String key = middle_l.replaceAll("\\s", "");
						copiedTxtHash.put(key, middle_l);
					}
				}					
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * read xml page by page
	 * 
	 * @throws FileNotFoundException
	 * 
	 */
	protected void readPages() throws FileNotFoundException {
		File[] allFiles = sourceFile.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			if (contentEnded) {
				System.out.println("reached last line in previous file");
				return;
			}

			File xmlfile = allFiles[i];
			SAXBuilder sb = new SAXBuilder();
			String source = allFiles[i].getName();

			// read txt into a hashtable
			String fileName = source.substring(0, source.indexOf("."));
			copiedTxtHash = new Hashtable<String, String>();
			getCopiedText(fileName);

			// read xml file
			try {
				Document doc = (Document) sb.build(xmlfile);

				Element root = doc.getRootElement();
				Element body = root.getChild("BODY");
				List pages = body.getChildren("OBJECT");
				String pageNum = "";
				for (int j = 0; j < pages.size(); j++) {
					if (contentEnded) {
						System.out.println("reached last line");
						return;
					}

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
						readPage(page_content, source, pageNum);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * use four hash tables to fix incorrect space in xml 
	 * @param line
	 * @return
	 */
	protected String fixSpace(String line) {
		String lastword = ""; //the last word
		String firstword = "";
		String line_forCompare = line;
		
		//original compare
		String key = line_forCompare.replaceAll("\\s", "");
		String fixed_txt = copiedTxtHash.get(key);
		if (fixed_txt != null) {
			if (fixed_txt.length() < line_forCompare.length()) {
				spacefixed++;
				return fixed_txt;
			} else {
				return line;
			}		
		}
		
		//drop last word and compare
		int lastSpaceIndex = line.lastIndexOf(" ");
		if (lastSpaceIndex > 0) {
			line_forCompare = line.substring(0, lastSpaceIndex);
			lastword = line.substring(lastSpaceIndex, line.length());
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedTxtHash.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					spacefixed++;
					return fixed_txt + lastword; 
				} else {
					return line;
				}
			}
		}	
		
		//drop the first word and compare
		int firstSpaceIndex = line.indexOf(" ");
		if (firstSpaceIndex > 0) {
			line_forCompare = line.substring(firstSpaceIndex, line.length());
			firstword = line.substring(0, firstSpaceIndex);
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedTxtHash.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					spacefixed++;
					//may match the txt without leading space
					fixed_txt = firstword + " " + fixed_txt;					
					return fixed_txt.replaceAll("\\s\\s", " ");
				} else {
					return line;
				}
			}
		}
		
		//drop the first and the last word and compare
		if (firstSpaceIndex > 0 && lastSpaceIndex > 0 && lastSpaceIndex > firstSpaceIndex) {
			line_forCompare = line.substring(firstSpaceIndex, lastSpaceIndex);
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedTxtHash.get(key);
			if (fixed_txt != null) {
				if (fixed_txt.length() < line_forCompare.length()) {
					spacefixed++;
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
	 * decide add line as a separate paragraph or append line to last record
	 * 
	 * @param paras
	 * @param types
	 * @param line
	 */
	protected void readLine(ArrayList<String> paras, ArrayList<String> types, ArrayList<Integer> y1s, 
			String line, int y1, int last_y1) {
		line = replaceIllegalCharacter(line);
		String l = line.trim();
		if (!l.equals("")) {
			l = fixSpace(l);

			this.totalLength += l.length();
			this.linecount++;
			this.lineLength = this.totalLength / this.linecount;
			boolean newPara = newNextPara;
			newNextPara = false; // set to be false after getting the last value

			String lastPara = "";
			String lastType = "";
			if (paras.size() > 0) {
				lastPara = paras.get(paras.size() - 1);
				lastType = types.get(types.size() - 1);
			}

			String type = "unassigned";

			if (maybePageNumber(l)) {
				newPara = true;// pageNum should be a separate paragraph
				newNextPara = true;
				type = "noncontent_pagenum";
			} else if (isUncertain(l)) {
				type = "noncontent_innersection";
				if (isCombinedUnCertain(l, lastPara)) {
					newPara = false;
				}
				newNextPara = true; 
			} else if (isInnerSection(l)) {
				if (isCombinedTaxonName(l, lastPara)) {
					// add up with new type
					type = "content_taxonname";
					newPara = false; /*set newPara to be false if combined taxon name*/
				} else {
					newPara = true;
					type = "noncontent_innersection";
				}
			} else if (isTaxonName(l)) {
				newPara = true;
				type = "content_taxonname";
			} else if (isFigureTable(l)) {
				// start of a figure should start a new paragraph, but may be
				// connected to later lines
				newPara = true;
				type = "noncontent_figtbl";
			} else

			if (l.matches("^((TABLE OF )?CONTENTS|(Table [Oo]f )?Contents)$")) {
				this.hasToCHeading = true;

				newPara = true; // "CONTENTS" should be a separate paragraph
				newNextPara = true;
				type = "noncontent_prolog";
			} else if (l
					.matches(".*?[a-zA-Z].*?\\s*[\\. ]{3,}\\s*[A-D]?[ivx\\d]+$")) {
				this.hasToCDots = true; // dots used in table of content

				newPara = true;// "dots should be a separate paragraph"
				newNextPara = true;
				type = "noncontent_prolog";
			} else if (l.matches("INDEX$")) {
				this.hasIndex = true;
				this.epilogBegings = true;

				newPara = true;// "INDEX" should be a separate paragraph
				newNextPara = true;
				type = "noncontent_epilog";
			} else if (isFootNote(l)) {
				newPara = true;
				type = "noncontent_footnote";
			} else if (l.matches("Suborder|Subfamily|Family|Order")) {
				//v_e_3 page49
				newPara = true; 
			}
			
			//int diff = (y1 - last_y1 > 0) ? (y1 - last_y1) : (y1 - last_y1) * (-1);
			int diff = y1 - last_y1;
			// use y-coordinate to separate paragraph
			if (diff > 65) {
				newPara = true;
			}
			
//			if (lastPara.endsWith(".") && y1 > last_y1 + 70) {
//				newPara = true;
//			}
			
		

			// use period and length of line to separate paragraph
			if (l.length() <= this.lineLength * 2 / 3 
					|| (l.endsWith(".") && l.length() < lastLength - 10)) {
				newNextPara = true;
			}			
			
			lastLength = l.length();

			// update para
			if (newPara || paras.size() == 0) {
				paras.add(l);// insert para
				types.add(type);// insert type
				y1s.add(y1);
			} else {
				// update para
				if (!lastPara.endsWith("-")) {
					lastPara += " ";
				}
				paras.set(paras.size() - 1, lastPara + l);

				// update type
				if (types.get(types.size() - 1).equals("unassigned")) {
					types.set(types.size() - 1, type);
				}
			}
		}
	}

	/*
	 * @param line
	 * 
	 * @return
	 */
	protected String replaceIllegalCharacter(String line) {
		line = line.replaceAll("[“|”]", "\"");
		line = line.replaceAll("’", "'");
		line = line.replaceAll("[–|-|–|—]", "-");
		line = line.replaceAll(" ", " ");
		// line = line.replaceAll("ö", "o");
		return line;
	}

	/**
	 * overload addParagraphs with columns
	 * 
	 * @param pageNumLines
	 * @param source
	 * @param cbs
	 * @param pageNum
	 */
	protected void addParagraphs(ArrayList<String> pageNumLines, String source,
			ArrayList<ColumnBean> cbs, String pageNum) {

		try {
			if (pageNumLines.size() > 0) {
				ArrayList<String> pageNumTypes = new ArrayList<String>();
				for (int i = 0; i < pageNumLines.size(); i++) {
					pageNumTypes.add("noncontent_pagenum");
				}
				DatabaseAccessor.insertParagraphs(this.prefix, pageNumLines,
						source, conn, pageNumTypes, pageNum, null);
			}

			for (ColumnBean cb : cbs) {
				if (cb.getParas().size() > 0) {
					DatabaseAccessor
							.insertParagraphs(this.prefix, cb.getParas(),
									source, conn, cb.getTypes(), pageNum, cb.getY1s());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * mark inner section
	 * 
	 * @param rs_para
	 * @throws Exception
	 */
	public void markInnerSection() throws Exception {
		ResultSet rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
				"", "paraID", "*", conn);
		boolean started = false;
		while (rs_para.next()) {
			String currentType = rs_para.getString("type");
			if (started) {
				if (currentType.contains("taxonname")) {
					String para = rs_para.getString("paragraph");
					
					if (para.length() > lineLength * 1.5) {
						Pattern p = Pattern.compile(taxonNamePattern + ".*\\d\\d\\d\\d.*\\[.*\\].*");
						Matcher m = p.matcher(para);
						if (!m.matches()) {
							markAsType(rs_para.getInt("paraID"), "noncontent_faketaxon");
						} else {
							//end this innersection
							started = false;
						}
					} else {
						//the taxon name that will break an innersection must be a title, therefore, it can only match the bigger part
						Pattern p = Pattern.compile(taxonNamePattern);
						Pattern p2 = Pattern.compile(taxonNamePattern + "\\s[A-ZÖÉÄ][a-z]+,\\s\\d\\d\\d\\d");
						
						//^\\??[A-Z]([a-z]+)\\s([A-ZÖÉÄ]\\.?\\s?([A-ZÖÉÄ']+))
						Matcher m = p.matcher(para);
						Matcher m2 = p2.matcher(para);
						if (m.matches() || m2.matches()) {
							//end the innersection
							started = false;
						} else {
							markAsType(rs_para.getInt("paraID"), "noncontent_faketaxon");
						}
					}
					continue;
				} else if (!currentType.equals("noncontent_pagenum") && !currentType.equals("noncontent_figtbl")){
					markAsType(rs_para.getInt("paraID"), "noncontent_innersection");
				}
			} else if (currentType.contains("innersection")) {
				started = true;
				continue;
			}
		}
	}

	public void identifyContent() {
		ResultSet rs_para = null;
		try {
			// mark prolog
			DatabaseAccessor.updateProlog(prefix, conn);

			// mark inner section
			markInnerSection();
			System.out.println("markInnerSection finished "
					+ new Date(System.currentTimeMillis()));

			// mark table content (texts between last pagenum and figure)
			traceBackFigTblContent();
			System.out.println("traceBackFigTblContent finished "
					+ new Date(System.currentTimeMillis()));

			String condition = "(type='unassigned' or type='content_taxonname')";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "*", conn);
			if (rs_para != null) {
				boolean noEndPara = false;
				while (rs_para.next()) {
					int paraID = rs_para.getInt("paraID");
					String para = rs_para.getString("paragraph");

					if (isPureQuestionMark(para)) {
						markAsType(paraID, "noncontent_illegal");
					} else {
						if (isInterruptingPoint(paraID, para, noEndPara)) { // H3
							markAdd2Last(paraID, "yes"); // set add2last
						}

						if (rs_para.getString("type").equals("unassigned")) {
							markAsType(paraID, "content");
						}

						if (!para.endsWith(".")) {
							noEndPara = true;
						} else {
							noEndPara = false;
						}
					}

					/*
					 * if (isFootNote(paraID, para, source)) { // H2 H6
					 * markAsType(paraID, "noncontent_footnote"); continue; }
					 */
					/*
					 * if (isHeading(para)) { // markAsType(paraID,
					 * "content_heading"); continue; }
					 */

					/*
					 * if (isShortTexts(paraID, para, source)) { //
					 * markAsType(paraID, "noncontent_shorttext"); continue; }
					 */
				}
			}
			rs_para = null;
			System.out.println("mark content finished "
					+ new Date(System.currentTimeMillis()));

			// fix brackets, produce add2last y-[
			fixBrackets();
			System.out.println("fixBrackets finished "
					+ new Date(System.currentTimeMillis()));

			fixSquareBrackets();

			System.out.println("fixSquareBrackets finished "
					+ new Date(System.currentTimeMillis()));

			// fix those add2last but should be add to fig description
			/*
			 * fixFigtbl(); System.out.println("fixFigtbl finished " + new
			 * Date(System.currentTimeMillis()));
			 */

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void fixAdd2LastHeadings(String source) {
		// do nothing here, let subclass to extend.
	}

	/**
	 * Overload isHeading (seems like no need to use the other params) Can mark
	 * this type when insert paragraphs
	 * 
	 * @param para
	 * @return
	 */
	@SuppressWarnings("unused")
	private boolean isHeading(String para) {
		String l = para.trim();
		if (l.matches("^[a-z].*")
				|| l.matches(".*? ([a-z]+|.*,|.*;)$")
				|| l.toLowerCase().matches(
						".*?\\b(" + ChunkedSentence.stop + "|"
								+ ChunkedSentence.prepositions + "|.*?\\W)$")) {
			// , or ; at the end or a lower case word at the end
			return false;
		}
		if (l.matches(".*?\\d+ \\d+.*")) {
			return false;
		}
		String[] words = l
				.replaceAll("\\d+", "")
				.replaceAll("(?<!\\w)\\W(?!\\w)", " ")
				.replaceAll(
						"\\b(" + ChunkedSentence.stop + "|"
								+ ChunkedSentence.prepositions + ")\\b", "")
				.trim().split("\\s+");
		int capitals = 0;
		for (int i = 0; i < words.length; i++) {
			if (words[i].compareTo(words[i].toLowerCase()) != 0) {
				capitals++;
			}
		}
		if (capitals == words.length) {
			return true;
		}
		return false;
	}

	private boolean isHeading(int paraID, String para, String source) {
		String l = para.trim();
		if (l.matches("^[a-z].*")
				|| l.matches(".*? ([a-z]+|.*,|.*;)$")
				|| l.toLowerCase().matches(
						".*?\\b(" + ChunkedSentence.stop + "|"
								+ ChunkedSentence.prepositions + "|.*?\\W)$")) {
			// , or ; at the end or a lower case word at the end
			return false;
		}
		if (l.matches(".*?\\d+ \\d+.*")) {
			return false;
		}
		String[] words = l
				.replaceAll("\\d+", "")
				.replaceAll("(?<!\\w)\\W(?!\\w)", " ")
				.replaceAll(
						"\\b(" + ChunkedSentence.stop + "|"
								+ ChunkedSentence.prepositions + ")\\b", "")
				.trim().split("\\s+");
		int capitals = 0;
		for (int i = 0; i < words.length; i++) {
			if (words[i].compareTo(words[i].toLowerCase()) != 0) {
				capitals++;
			}
		}
		if (capitals == words.length) {
			return true;
		}
		return false;
	}

	/**
	 * after idContent, fix broken brackets. From the last content para track
	 * back to the beginning record right and left brackets if reading a record
	 * makes right != left, set its add2last to "yes"
	 * 
	 * todo: fix (), not just [] e.g. page369
	 */
	private void fixSquareBrackets() {
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
				if (type.contains("taxonname")) {
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

	private void fixBrackets() {
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
				if (type.contains("taxonname")) {
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

	private void fixBrackets(String source) {
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();

		ResultSet rs = null;
		try {
			String condition = "source='" + source + "'";
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
				if (type.contains("taxonname")) {
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

	/**
	 * text and its preceding or following n text are also very short
	 * 
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	private boolean isShortTexts(int paraID, String para, String source) {

		int limit = 5;
		if (para.trim().length() < this.lineLength * 2 / 3) {
			try {
				// preceding
				int preLimit = paraID - limit;
				String condition = "source='" + source
						+ "' and type like '%shorttext%' and paraID>="
						+ preLimit + " and paraID <" + paraID
						+ " and length(paragraph) < " + this.lineLength * 2 / 3;

				int num = DatabaseAccessor.numberOfRecordsInParagraph(prefix,
						condition, conn);
				if (num == limit) {
					return true;
				}
				// following
				int folLimit = paraID + limit;
				condition = "source='" + source
						+ "'and type like '%unassigned%' and paraID<="
						+ folLimit + " and paraID >" + paraID
						+ " and length(paragraph) < " + this.lineLength * 2 / 3;
				num = DatabaseAccessor.numberOfRecordsInParagraph(prefix,
						condition, conn);
				if (num == limit) {
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * find
	 * 
	 * @param paraID
	 * @param source
	 */
	private void markAdd2Last(int paraID, String mark) {
		String set = "add2last='" + mark + "'";
		String condition = "paraID=" + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
	protected boolean isInterruptingPoint(int paraID, String para,
			boolean statusOfLastPara) {
		para = para.trim();

		// if last para is not ended with period, this para is an interrunpting
		// point
		if (statusOfLastPara) {
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
			// OCR errors, mistaken 1 as I or l.
			// Make 2 Ia. =>21a. Make 2.1 =>21
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

		/*
		 * String first = para.substring(0, 1); Pattern pattern =
		 * Pattern.compile("[a-z]"); Matcher m = pattern.matcher(first);
		 * if(m.matches()){ return true; //para starts with a lower case letter
		 * }
		 */
		// if start with a number, then number+1 is not the starting token of
		// the next content paragraph
		/*
		 * pattern = Pattern.compile("^(\\d+)(.*?) .*"); //TODO here assume list
		 * are numbered without letters, so no 1a., 1.b. m =
		 * pattern.matcher(para); if(m.matches()){ String num = m.group(1);
		 * String text = m.group(2); text = text.replaceAll("\\W", "");
		 * if(text.length()==1){ //1a. , 1.b., 1a-, yes, this is a list, and not
		 * an interrupting point return false; } int next =
		 * Integer.parseInt(num)+1; ArrayList<String> paraIDs = new
		 * ArrayList<String> (); ArrayList<String> paras = new ArrayList<String>
		 * (); try{ DatabaseAccessor.selectParagraphsFromSource(prefix, source,
		 * "paraID>="
		 * +paraID+" and (type not like 'non-content%' or type='unassigned')",
		 * "", paraIDs, paras, conn);
		 * 
		 * text = (String)paras.get(0); if(text.startsWith(next+"")){ return
		 * false; //para starts with a number but is a part of a list }else{
		 * return true; //para starts with a number and is NOT a part of a list
		 * } }catch (Exception e){ e.printStackTrace(); } }
		 */
		return false;
	}

	/**
	 * need to traceBack if a figure is found/table is found
	 * 
	 * @param para
	 * @return
	 */
	protected boolean isFigureTable(String para) {
		para = para.trim();

		if (para.matches(this.tablePattern) || para.matches(figurePattern)) {
			return true;
		}
		
		if (!figException.equals("")) {
			if (para.startsWith(figException)) {
				return true;
			}
		}

		/*
		 * Pattern pattern = Pattern.compile(this.figurePattern); Matcher m =
		 * pattern.matcher(para); if (m.matches()) { return true; }
		 * 
		 * pattern = Pattern.compile(this.tablePattern); m =
		 * pattern.matcher(para); if (m.matches()) { return true; }
		 */
		return false;
	}

	protected boolean isPureQuestionMark(String para) {
		para = para.trim();
		if (para.matches("(\\?\\s*)+")) {
			return true;
		}
		return false;
	}

	/**
	 * This function need more consideration for now, only for figures, not
	 * table content
	 * 
	 * @param para
	 * @return
	 */
	protected boolean isfigTblTxt(String para) {
		Pattern p = Pattern.compile(figtblTxtPattern);
		Matcher mh = p.matcher(para);
		if (mh.matches()) {
			return true;
		}
		if (para.length() < 25) {
			//t.os
			//word1 word2 / word1-word2/word1
			if (para.matches("^[a-z\\s\\-]*") || para.matches("t\\.os") || para.matches("^(ray,)(\\s(first)\\s(order))?")) {
				return true;
			}
		}
		return false;
	}
	
	protected boolean reCheckfigTblTxt(String para) {
		Pattern p = Pattern.compile(figtblTxtPattern);
		Matcher mh = p.matcher(para);
		if (mh.matches()) {
			return true;
		}
		if (para.length() < this.lineLength * 0.8) {
			//t.os
			//word1 word2 / word1-word2/word1
			if (para.matches("^[a-z\\s\\-]*") || para.matches("t\\.os") || para.matches("^(ray,)(\\s(first)\\s(order))?")) {
				return true;
			}
		}
		return false;
	}
	
	

	protected void traceBackFigTblContent(String source, String pageNum, int y1) {
		try {
			ResultSet rs_para = null;
			String condition1 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "'and type = 'unassigned'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition1, "", "", conn);
			while (rs_para.next()) {
				String para = rs_para.getString("paragraph");
				if (isfigTblTxt(para)) {
					markAsType(rs_para.getInt("paraID"),
							"noncontent_figtbl_txt");
				}
			}
			
			String condition2 = "pageNum = '" + pageNum + "' and source = '"
					+ source + "'and type = 'unassigned' and y1 > " + y1;
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition2, "", "", conn);
			while (rs_para.next()) {
				String para = rs_para.getString("paragraph");
				if (reCheckfigTblTxt(para)) {
					markAsType(rs_para.getInt("paraID"),
							"noncontent_figtbl_txt");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void traceBackFigTblContent(String source, String pageNum) {
		try {
			ResultSet rs_para = null;
			String condition = "pageNum = '" + pageNum + "' and source = '"
					+ source + "'and type = 'unassigned'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "", "", conn);
			while (rs_para.next()) {
				String para = rs_para.getString("paragraph");
				if (isfigTblTxt(para)) {
					markAsType(rs_para.getInt("paraID"),
							"noncontent_figtbl_txt");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * capitalized short text before paraID should be set to non-content
	 * 
	 * @param paraID
	 * @param source
	 * 
	 * 
	 */

	protected void traceBackFigTblContent() {
		ResultSet rs_para = null;
		try {// find all figtbl

			// find the paraID for the last pagenum
			String condition = "type like '%figtbl%'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "paraID, pageNum, source, y1", conn);
			if (rs_para != null) {
				while (rs_para.next()) {
					this.traceBackFigTblContent(rs_para.getString("source"),
							rs_para.getString("pageNum"), rs_para.getInt("y1"));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			rs_para = null;
		}
	}

	/**
	 * old one, should be discarded
	 * @param paraID
	 * @param source
	 */
	protected void traceBackFigTblContent(int paraID, String source) {
		try {
			ResultSet rs_para = null;
			boolean figContentFound = false;

			// backward in that page
			// find the start of this page
			int last = 0;
			String condition = "type like '%pagenum%' and paraID < " + paraID;
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID desc", "paraID", conn);
			if (rs_para != null && rs_para.next()) {
				last = rs_para.getInt("paraID");
			}
			rs_para = null;
			condition = "source='" + source + "'and paraID < " + paraID
					+ " and paraID > " + last + " and length(paragraph) <="
					+ this.lineLength * 1 / 2 + " and type = 'unassigned'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "", "", conn);
			int count = 0;
			while (rs_para.next()) {
				int pID = rs_para.getInt("paraID");
				if (pID == pID - count) {/* make sure figtbl_txt are continuous */
					markAsType(pID, "noncontent_figtbl_txt");
					count++;
					figContentFound = true;
				} else {
					break;
				}
			}

			// forward in that page
			if (figContentFound) {
				return;
			}

			// find the paraID for the last pagenum
			last = 0;
			condition = "type like '%pagenum%' and paraID > " + paraID;
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "", "paraID", conn);
			if (rs_para != null && rs_para.next()) {
				last = rs_para.getInt("paraID");
			}
			rs_para = null;
			condition = "source='" + source + "'and paraID < " + paraID
					+ " and paraID > " + last + " and length(paragraph) <="
					+ this.lineLength * 1 / 2;
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "", "", conn);
			count = 0;
			while (rs_para.next()) {
				int pID = rs_para.getInt("paraID");
				if (pID == pID + count) {/* make sure figtbl_txt are continuous */
					markAsType(pID, "noncontent_figtbl_txt");
					count++;
				} else {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * decide whether this line could be page number, if yes, must be in a
	 * separate paragraph
	 * 
	 * @param para
	 * @return
	 */
	protected boolean maybePageNumber(String para) {
		para = para.trim();
		if (para.matches("^\\d+\\s{2,}(.*)") || para.matches("\\d+")
				|| isRomePageNumber(para) || para.matches("(.*?)\\s{2,}\\d+$")) {
			return true;
		} else
			return false;

	}

	protected boolean isRomePageNumber(String line) {
		line = line.trim();
		if (line.matches("^(?i)M*(D?C{0,3}|C[DM])(L?X{0,3}|X[LC])(V?I{0,3}|I[VX])$")) {
			return true;
		}
		return false;
	}

	/**
	 * [ BURGER: FLORA COSTARICENSIS 5 ]
	 * 
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	protected boolean isPageNumber(int paraID, String para, String source) {
		para = para.trim();
		if (para.matches("\\d+") || isRomePageNumber(para)) {
			return true;
		}
		Pattern lpattern = Pattern.compile("^\\d+\\s+(.*)");
		// changed \\s+ to \\s* as Treatises page numbers are like D7.
		// Test the effects.
		Pattern rpattern = Pattern.compile("(.*?)\\s*\\d+$");

		Matcher lm = lpattern.matcher(para);
		Matcher rm = rpattern.matcher(para);
		String header = "";
		if (lm.matches()) {
			header = lm.group(1).toLowerCase();
		} else if (rm.matches()) {
			header = rm.group(1).toLowerCase();
		} else {
			return false;
		}

		if (header.trim().compareTo("") == 0) {
			return true; // stand alone page number
		}

		// if user provided pageNumberText
		if (this.pageNumberText.contains(header)) {
			return true;
		}
		// if not
		// check to see if header may be used in regexp
		try {
			Pattern p = Pattern.compile(header);
		} catch (Exception e) {
			return false;
		}

		if (header.length() < this.lineLength * 2 / 3) {
			header = header.replaceAll("\\.", "[[.period.]]")
					.replaceAll("'", "[[.apostrophe.]]")
					.replaceAll("\\^", "[[.circumflex.]]");
			String condition = "source='"
					+ source
					+ "' and paragraph rlike '^[[:space:]]*[[:digit:]]+[[:space:]]+"
					+ header + "' or paragraph rlike '" + header
					+ "[[:space:]]+[[:digit:]]+[[:space:]]*$'";
			try {
				int num = DatabaseAccessor.numberOfRecordsInParagraph(prefix,
						condition, conn);
				if (num >= 3) {
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * check if current line is the first line
	 * 
	 * @param line
	 * @return
	 */
	private boolean isFirstline(String line, String actualPageNum,
			String djvuPageNum) {
		if (startWith(line, this.startText)
				&& (actualPageNum.contains(startPage) || djvuPageNum
						.contains(startPage))) {
			return true;
		} else {
			return false;
		}

	}

	/**
	 * check if current line is the last line
	 * 
	 * @param line
	 * @return
	 */
	private boolean isLastline(String line) {
		if (endText.equals("")) {
			return false;
		}
		else {
			return startWith(line, this.endText);
		}
	}

	private boolean startWith(String line, String prefix) {
		line = line.trim();
		if (line.startsWith(prefix)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Check if taxon name are splited to two lines
	 * 
	 * @param line
	 * @param lastLine
	 * @return
	 */
	private boolean isCombinedTaxonName(String line, String lastLine) {
		if (lastLine.matches("^[A-Z][a-z]+")) {
			line = lastLine + " " + line;
			if (isTaxonName(line)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * if the line is all capital, this might be a inner section. expected
	 * result: mark it and try to find the next taxon name. contents between
	 * this line and next taxon will be considered to be inner section
	 * 
	 * @param line
	 * @return
	 */
	private boolean isInnerSection(String line) {
		line = line.trim();
		try {
			Pattern p = Pattern.compile("^[A-Z][A-Z][A-Z,:]+(\\s[A-Z][A-Z,:]+)*"); // over
																				// 3
																				// letters
																				// per
																				// word
			Matcher mt = p.matcher(line);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
		return false;
	}

	/**
	 * Order UNCERTAIN
	 * Order and Suborder UNCERTAIN
	 * Class, Order, and Family \n UNCERTAIN --not handled - can be handled with combined uncertain
	 * @param line
	 * @return
	 */
	private boolean isUncertain(String line) {
		line = line.trim();
		try {
			Pattern p1 = Pattern
					.compile("^([A-Z][a-z]+(\\s(and)\\s[A-Z][a-z]+)?\\s)?(UNCERTAIN|Uncertain)");
			
			Matcher mt1 = p1.matcher(line);
			if (mt1.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Class, Order, and Family 
	 * 		UNCERTAIN
	 * @param line
	 * @param lastline
	 * @return
	 */
	private boolean isCombinedUnCertain(String line, String lastline) {
		if (line.matches("(UNCERTAIN|Uncertain)")) {
			Pattern p = Pattern.compile("^([A-Z][a-z]+,?\\s)+(and)\\s[A-Z][a-z]+");
			Matcher m = p.matcher(lastline);
			if (m.matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * if is category title like "Subfamily HOLMIINAE Hupé, 1953" marked it out
	 * This is to tolerate bracket errors to limit the damage inside one
	 * category
	 * 
	 * @param para
	 * @return
	 */
	private boolean isTaxonName(String para) {
		para = para.trim();
		try {
			para = para.trim();
			// not just like this,
			/*
			 * Pseudocobboldia HUPÉ in BOUDDA Sinodiscus W. C HANG Plurinodus Ö
			 * PIK Alaskadiscus S. ZHANG
			 * 
			 * what about like this? p442/418 Superfamily FALLOTASPIDOIDEA Hupé,
			 * 1953
			 */
			// Pattern p =
			// Pattern.compile("^\\??[A-Z]([a-z]+)\\s([A-ZÖ]([A-ZÖ]+)).*?");
			Pattern p = Pattern
					.compile(this.taxonNamePattern + ".*?");
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
		return false;
	}

	private boolean isFootNote(String line) {
		String l = line.replaceAll("\\s", "");

		if (l.matches("^\\d[A-Z][a-z]+,?\\s.*?")) { /* 1Abc ... */
			return true;
		}

		if (l.length() > lengthOfFootnoteKey) {
			l = l.substring(0, lengthOfFootnoteKey);
		}
		if (footnotes.get(l) != null) { /* in footnotes table */
			return true;
		}
		return false;
	}

	/**
	 * true if 1. para start with a (footnote) token AND 2. the next paragraph
	 * started with a lower case letter or a non-listing number. That is, it is
	 * an interrupting point. If it is not an interrupting point, then it is
	 * safer to treat the para as content.
	 * 
	 * @param para
	 * @return
	 */
	private boolean isFootNote(int paraID, String para, String source) {
		boolean cond1 = false;
		boolean cond2 = false;
		if (startWithAToken(para, source)) {
			cond1 = true;
		}
		if (cond1 == false) {
			return false;
		}
		try {
			paraID++;
			String condition = "paraID=" + paraID;
			para = DatabaseAccessor.getParaByCondition(prefix, condition, conn);
			if (!para.equals("")) {
				if (isInterruptingPoint(paraID, para, false)) {
					cond2 = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return cond1 && cond2;
	}

	/**
	 * footnote should appear at least countThreshold times in the document. If
	 * it is not in the footNoteTokens, it should appear at least twice as often
	 * to be considered footnote
	 * 
	 * @param para
	 * @return
	 */
	protected boolean startWithAToken(String para, String source) {
		para = para.trim();
		if (para.matches("^[(\\[{}\\])].*")) {
			return false;
		}
		int countThreshold = 2;
		boolean cond1 = false; // in footNoteToken
		boolean cond2 = false; // appear > 1
		if (para.length() == 0) {
			return false;
		}

		String first = para.substring(0, 1);
		Pattern pattern = Pattern.compile("[a-zA-Z0-9_()\\[\\]&%!*]");
		Matcher m = pattern.matcher(first);
		if (this.footNoteTokens.contains(first) && !m.matches()) { // any
																	// non-word
																	// token
			cond1 = true;
		}
		if (this.footNoteTokens.contains(first)) { // if the token is a word
													// token, then there must be
													// a non-word token
													// following it
			String second = para.substring(1, 2);
			m = pattern.matcher(second);
			if (!m.matches()) {
				cond1 = true;
			}
		}

		if (first.matches("\\W")) {
			first = first.replaceAll("\\*", "[[.asterisk.]]")
					.replaceAll("\\+", "[[.plus-sign.]]")
					.replaceAll("\\?", "[[.question-mark.]]")
					.replaceAll("\\|", "[[.vertical-line.]]")
					.replaceAll("\\.", "[[.period.]]")
					.replaceAll("'", "[[.apostrophe.]]")
					.replaceAll("\\^", "[[.circumflex.]]");
			String condition = "source='" + source
					+ "' and paragraph rlike '^[[:space:]]*" + first + "'";
			try {
				int num = DatabaseAccessor.numberOfRecordsInParagraph(prefix,
						condition, conn);
				if (num >= countThreshold) {
					cond2 = true;
				}
				if (num >= countThreshold * 2) {
					cond2 = true;
					cond1 = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return cond1 && cond2;
	}

	protected void markAsType(int paraID, String type) {
		String set = "type = '" + type + "'";
		String condition = "paraID = " + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String combineParas(String firstPara, String laterPara) {
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
	 * basically combine taxon name and content and produce paragraph, ignore
	 * non-content part
	 * 
	 * originated from ContentFixer.java
	 * 
	 * @throws Exception
	 */
	protected void getCleanParagraph() throws Exception {
		// get records with type "content%"
		ResultSet rs = DatabaseAccessor.getParagraphsByCondition(prefix,
				"type like 'content%'", "paraID desc", "*", conn);
		String combinedPara = "";
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
				ParagraphBean pb = new ParagraphBean(combinedPara,
						rs.getInt("paraID"));
				pb.normalize();
				pb.setSource(rs.getString("source"));
				cleanParas.add(pb);
				combinedPara = "";
			}
		}

		System.out.println("begin to insert clean paragraphs");
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

	/**
	 * reset the add2last paragraphs that should be added to the caption of a
	 * preceding fig or table. scan through add2last paragraphs: for each
	 * add2last paragraph if the preceding paragraph is a figtbl determine if
	 * the add2last should be added to figtbl paragraph, if so change its type
	 * to figtbl and reset add2last flag for it
	 * 
	 * @throws SQLException
	 * 
	 */
	private void fixFigtbl() throws SQLException {
		boolean goon = false;
		do {
			goon = false;
			ResultSet rs_para_add2 = null;

			String condition = "type ='content' and add2last like 'y%'";
			try {
				// get all "add2last" paragraphs
				rs_para_add2 = DatabaseAccessor.getParagraphsByCondition(
						prefix, condition, "paraID desc", "paraID", conn);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (rs_para_add2 == null) {
				return;
			}
			while (rs_para_add2.next()) {
				int pid = rs_para_add2.getInt("paraID");
				int figtblid = pid - 1;
				ResultSet rs_para = null;
				condition = "type like '%figtbl' and paraID = " + figtblid;
				try {
					// get all figtbl paragraphs precedes an add2last
					rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
							condition, "paraID desc",
							"paraID, paragraph, source", conn);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (rs_para == null) {
					break;
				}
				if (rs_para.next()) {
					String figtblp = rs_para.getString("paragraph");
					if (isIncomplete(figtblp, figtblid)) {
						// reset add2last for pid, change type to figtbl for pid
						try {
							String set = "type ='content-figtbl', add2last=''";
							String cond = "paraID = " + pid;
							DatabaseAccessor.updateParagraph(prefix, set, cond,
									conn);
							goon = true;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
			rs_para_add2 = null;
		} while (goon);

	}

	/**
	 * test to see if figtbl paragraph is not a complete sentence must meet the
	 * following conditions: 1. not end with a period or a ) 2. the last content
	 * end with a period or a )
	 * 
	 * @param figtblp
	 * @param figtblid
	 * @return
	 */
	private boolean isIncomplete(String figtblp, int figtblid) {
		// get the last content paragraph
		ResultSet rs_para = null;
		String condition = "type like 'content%' and paraID<" + figtblid;
		try {
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID desc", "", conn);
			if (rs_para == null) {
				return true;
			}

			if (rs_para.next()) {
				String p = rs_para.getString("paragraph");
				figtblp = figtblp.trim();
				if (isComplete(p) && !isComplete(figtblp)) {
					rs_para = null;
					return true;
				} else if (!isComplete(p) && isComplete(figtblp)) {
					rs_para = null;
					return false;
				} else if (!isComplete(p) && !isComplete(figtblp)) { // both are
					rs_para = null; // incomplete
					return true;
				} else { // both are complete
					if (figtblp.length() > this.lineLength * 4 / 5) {
						rs_para = null;
						return false;
					} else {
						rs_para = null;
						return true;
					}
				}
			} else {
				return true; // no content paragraph.
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			rs_para = null;
		}
		return false;
	}

	private boolean isComplete(String p) {
		if (p.matches("[?!]$")) {
			return true;
		}

		if (p.matches("[a-z]\\.$")) { // find.
			return true;
		}

		if (p.matches("(\\.\\)|\\).)$")) { // .) or ).
			return true;
		}

		return false;
	}

	private void normalize(ArrayList<String> paras) {
		for (int i = 0; i < paras.size(); i++) {
			String t = paras.get(i);
			t = t.replaceAll("(?<=[a-z])-\\s+(?=[a-z])", "-")
					.replaceAll("\\s+", " ").replaceAll("\\^", "");
			paras.set(i, t);
		}

	}

	protected void outputCleanContent() {
		ArrayList<String> sources = new ArrayList<String>();
		try {
			DatabaseAccessor.selectDistinctSources(this.prefix, sources,
					this.conn);
			Iterator<String> it = sources.iterator();
			while (it.hasNext()) {
				String filename = (String) it.next();
				String condition = "source=\"" + filename + "\"";
				ResultSet rs = DatabaseAccessor.getParagraphsByCondition(
						this.prefix + "_clean", condition, "", "*", conn);
				StringBuffer sb = new StringBuffer();
				while (rs.next()) {
					String p = rs.getString("paragraph");
					sb.append(p + System.getProperty("line.separator")
							+ System.getProperty("line.separator"));
				}
				filename = filename.replaceFirst("\\.[a-z]+$", "_cleaned.txt");
				write2File(sb.toString(), filename);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void write2File(String text, String filename) {
		try {
			FileOutputStream fos = new FileOutputStream(outputPath + filename);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			osw.write(text);
			osw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set isCategory is true. if isCategory, will stop here for detecting
	 * brackets
	 * 
	 * @param paraID
	 * @param bool
	 */
	/*
	 * protected void markCategory(int paraID, boolean isCategory) { if
	 * (isCategory) { String set = "isCategory = true"; String condition =
	 * "paraID = " + paraID; try { DatabaseAccessor.updateParagraph(prefix, set,
	 * condition, conn); } catch (Exception e) { e.printStackTrace(); } } }
	 */

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String sourceFilePath = "X:\\DATA\\Treatise\\recent\\text\\test";
		sourceFilePath = "E:\\work_data\\xml_o"; // run on Fengqiong's windows

		ArrayList<String> pageNumberText = new ArrayList<String>();
		String pnt1 = "FIELDIANA: BOTANY, VOLUME 40".toLowerCase();
		String pnt2 = "BURGER: FLORA COSTARICENSIS".toLowerCase();
		pageNumberText.add(pnt1);
		pageNumberText.add(pnt2);

		ArrayList<String> footNoteTokens = new ArrayList<String>();
		String fnt1 = "'";
		footNoteTokens.add(fnt1);

		ContentExtract cf = new ContentExtract();
		System.out.println("started: " + new Date(System.currentTimeMillis()));

		cf.readPages();
		System.out.println("readPages completed "
				+ new Date(System.currentTimeMillis()));

		cf.identifyContent();

		cf.getCleanParagraph();
		// output file
		cf.outputCleanContent();
		System.out.println("content extract finished: (" + cf.spacefixed +
				"space fixed) "
				+ new Date(System.currentTimeMillis()));
	}
}
