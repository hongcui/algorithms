package informationContent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import beans.Line;
import beans.LineComparator;
import beans.ParagraphBean;

import db.DatabaseAccessor;

public class ContentExtract_sponges {
	/*Paths*/
	//D:\Work\Data\systemaporifera
	public String filesPath = "D:\\Work\\Data\\systemaporifera\\";
	public String sourceXmlsPath = "";
	public String sourceTxtsFolderName = "txts";
	public String outputPath = "D:\\Work\\Data\\systemaporifera\\output\\";
	
	/*Patterns*/
	public String figurePattern = "(FlG|F\\s?i\\s?G|F\\s?I\\s?G|F\\s?i\\s?g)\\s?\\.\\s+\\d+\\s?\\.\\s+.*?";
	public String tablePattern = "(TABLE|Table)\\s+\\d+\\s*\\..*?";
	public String figtblTxtPattern = "^\\d+(\\.\\d+)?\\s?\\??mm?"
			+ "|(\\d\\d?[a-z]*\\d?)(\\s?\\d\\d?[a-z])*\\'*" + "|[a-z0-9]*"
			+ "|[a-z]+" + "|[a-z][0-9](\\s[a-z][0-9])+" + "|[A-Z]+\\'*"
			+ "|([A-Z][a-z]*(\\s[A-Z][a-z]*)*)"
			+ "|([A-Z][a-z]*\\s)?\\([A-Z][a-z]*\\)"
			+ "|\\([a-z]+(\\s[a-z]+)*\\)";
	
	//FAMILY BAERIIDAE BOROJEVIC ET AL., 2000
	public String taxonNamePattern = "^(Ü|\\?)?(([A-Z…÷¿‹È]('[A-Z…÷¿‹È])?[a-zÈ?-]+(\\s|,|))|([A-Z…÷¿‹È-]+(\\s|,))|(&|\\s|de))+(ET\\sAL\\.,)?((\\s[0-9]{4}[a-z]?(:\\s[0-9]+)?\\.?)|([A-Za-z]{3,6}\\.\\s?[A-Za-z]{3}\\.)|(\\sIN\\sPRESS))(\\s\\(.*\\))?(:(\\s[A-Z][a-zÈ]+)+)?$";
	
	public String titleStandOutPattern = "^((\\d{1,2}\\.)+\\s)?(([A-Z]+,\\s)+[A-Z]+)|([A-Z][a-z]+(\\s([a-z]+\\s){0,3}[a-z]+)?)|(([A-Z]+\\s)*[A-Z]+)$";
	public String taxonAuthorPattern = "^((([A-Z…][a-zÎ]+\\s)|(([A-Z…]\\.)+\\s))*([A-Z…][a-zÎ]+)(-[A-Z…][a-zÎ]+)?[0-9]?((\\s&\\s)|(,\\s))?)+$";
	public String desTypeSpeciesParagraphPattern = "^(Synonymy|Material\\sexamined|Description(\\sof\\s.+|\\s\\(.+\\))?|Remarks|Reproduction|" +
			"Distribution\\sand\\secology|Restricted\\ssynonymy|Synonymy\\s\\(restricted\\)|Geographic\\sdistribution|Habitat\\sand\\sdistribution)" +
			"(\\s?\\(.+\\)\\s?)?(\\.|:)(.+?)";//possible brackets not ended
	
	public String titleOfTypespeciesNoEndPattern = "^((\\d{1,2}\\.)+\\s)?(Synonymy|Material\\sexamined|Description|Remarks|Keywords|Distribution\\sand\\secology)\\s\\([^\\)]+";	
	public String keywordsPattern = "^((\\d{1,2}\\.)+\\s)?(Keywords|Key\\swords)" +
			"(\\s?\\(.+\\)\\s?)?(\\.|:).+?";
	/*for pdf with only one columns - different format*/
	
	
	/*hash tables*/
	public Hashtable<String, String> copiedText = new Hashtable<String, String>();
	public Hashtable<String, Integer> middlePoints = new Hashtable<String, Integer>();
	
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
	public int spacefixed = 0;
	public int page_middle_point = 10000;
	public static int DEFAULT_MIDDLE_POINT = 1274;
	public long lineLength = 75;
	public int lastLength = 0;
	public long totalLength = 0; // for lineLength calculation
	public long linecount = 0; // for lineLength calculation
	public boolean epilogBegings = false; // once this is true, all text will
											// be marked as
											// noncontent_epilog
	public String figException = "";

	/**
	 * main function
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		ContentExtract_sponges contentExtract = new ContentExtract_sponges();
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public ContentExtract_sponges() throws Exception {
		this.sourceXmlsPath = this.filesPath + "xmls";
		
		//set the middle points of each xml file
		//(end_of_left + start_of_right)/2
		middlePoints.put("chapter-89_Agelasida", 1273);//(1248 + 1299)/2
		middlePoints.put("Chapter-90_Agelasiidae", 1274); //1249 1300
		middlePoints.put("Chapter-91_Astroscleridae", 1274);//1249 1300
		
		middlePoints.put("borojevic_et_al_leucosoleniida_text", 2000);
		middlePoints.put("reiswig_farreidae", 5000);
		middlePoints.put("bergquist_family_ianthellidae", 5000);
		middlePoints.put("boury_esnault_Chondrosida_text", 5000);
		middlePoints.put("vacelet_family_verticillitidae_recent", 5000);
		
		middlePoints.put("Chapter-0123_Order_Clathrinida", 1390); //(1365 + 1416)/2
		middlePoints.put("Chapter-25_Placospongiidae", 1390);
		middlePoints.put("Chapter-24_Hemiasterellidae", 1390);
		middlePoints.put("Chapter-23_Clionaidae", 1390);
		middlePoints.put("Chapter-22_Hadromerida", 1390);
		middlePoints.put("Chapter-0101_Suborder_Spongillidae", 1390);
		middlePoints.put("Chapter-10_Homosclerophorida", 1390);
		middlePoints.put("Chapter-21_Astrophorida_incertae", 1390);
		middlePoints.put("Chapter-20_Thrombidae", 1390);
		middlePoints.put("Chapter-19_Pachastrellidae", 1390);
		middlePoints.put("Chapter-18_Geodiidae", 1390);		
		middlePoints.put("Chapter-17_Calthropellidae", 1390);
		middlePoints.put("Chapter-16_Ancorinidae", 1390);
		middlePoints.put("Chapter-15_Astrophorida", 1390);
		middlePoints.put("Chapter-14_Spirasigmidae", 1390);
		middlePoints.put("Chapter-13_Samidae", 1390);
		middlePoints.put("Chapter-12_Tetillidae", 1390);
		middlePoints.put("Chapter-11_Spirophorida", 1390);
		
		middlePoints.put("Chapter-66_Hymedesmiidae", 1236); // 1212, 1260
		middlePoints.put("Chapter-57_Raspailiidae", 1236);
		//1366 1416
		middlePoints.put("Tabachnick_2002Rossellidae", 1390);
		
		
		getFiles(this.sourceXmlsPath);		
	}
	
	/*get each xml file and process it*/
	public void getFiles(String path) throws Exception {
		File directory = new File(path);
		File[] allFiles = directory.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			if (allFiles[i].isFile()) {
				processFile(allFiles[i]);
			} else if (allFiles[i].isDirectory()) {
				getFiles(allFiles[i].getAbsolutePath());
			}			
		}
	}
	
	/**
	 * @throws Exception 
	 * 
	 */
	public void processFile(File xmlFile) throws Exception {
		/*get prefix*/
		this.source = xmlFile.getName();
		this.fileName = source.replace(".xml", "");
		System.out.println("start on file: " + xmlFile.getAbsolutePath());
		this.prefix = fileName.replaceAll("-", "_");
		if (middlePoints.get(fileName) != null) 
			this.page_middle_point = middlePoints.get(fileName); 
		else 
			this.page_middle_point = DEFAULT_MIDDLE_POINT;
		
		/*create tables to assist file processing*/
		createTables();
		
		/*get the matching txt if existed*/
		File txtFile = new File(xmlFile.getAbsolutePath().replaceAll("\\.xml", ".txt").replaceAll("xmls", "txts"));
		if (txtFile.exists()) {
			getCopiedText(txtFile);
		}
		
		//read pages for this file
		readPages(xmlFile);		
		
		//identify content: mark out figure and related text
		traceBackFigContent();
		traceBackTblContent();
		
		//combine separated paragraphs
		fixSeparatedParagraphs();
		
		//combine paragraphs separated by brackets
		fixBrackets();
		fixSquareBrackets();

		//output content
		getCleanParagraph();
		outputCleanContent();
		
	}
	
	public void fixSeparatedParagraphs() {
		ResultSet rs_para = null;
		try {
			String condition = "";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "*", conn);
			if (rs_para != null) {
				int status_of_last_para = 2; // 0 - generally para_not_ended; 1- maybe
												// ended, end with ]; 2-
												// para_ended, end with period
				String last_type = "";
				while (rs_para.next()) {
					int paraID = rs_para.getInt("paraID");
					String para = rs_para.getString("paragraph");
					String type = rs_para.getString("type");
					
					if (isNonWordLine(para)) {
						markAsType(paraID, "noncontent_illegal");
					} else {
						if (isInterruptingPoint(paraID, para,
								status_of_last_para, type, last_type)) {
							markAdd2Last(paraID, "yes"); // set add2last
						}

						if (type.equals("unassigned")) {
							markAsType(paraID, "content");
						}

						if (para.endsWith(".")) {// paragraph ended
							status_of_last_para = 2; 
						} else if (para.endsWith("]")) {//maybe ended
							status_of_last_para = 1;
						} else if (para.endsWith(",") || para.endsWith("&")) { //definately not ended
							status_of_last_para = 3;
						} else {
							status_of_last_para = 0; //possibly not ended
						}
						
						last_type = type;
					}
				}
			}
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
	
	private void processPage(Element page, String source, String pageNum) {
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();
		ArrayList<Integer> bottoms = new ArrayList<Integer>();
		int bottom_of_last_line = 0;
		// first get all content
		ArrayList<Line> lines = getAllLinesInOrder(page, source, pageNum);
		
		//remove page number and page title: either the first two or the last two
		identifyPageTitles(lines);

		// process content: readLine
		for (Line line : lines) {
			processLine(paras, types, bottoms, line, bottom_of_last_line);
			bottom_of_last_line = line.get_bottom_coord();
		}

		addParagraphs(paras, source, types, bottoms, pageNum);

		System.out.println("--" + pageNum + " fetched");
	}

	private void processLine(ArrayList<String> paras, ArrayList<String> types,
			ArrayList<Integer> bottoms, Line line, int bottom_lastline) {
		String original_text = replaceIllegalCharacter(line.get_text());
		int bottom = line.get_bottom_coord();
		String text = original_text.trim();
		if (!text.equals("")) {
			text = fixSpace(text);

			this.totalLength += text.length();
			this.linecount++;
			this.lineLength = this.totalLength / this.linecount;
			boolean newPara = newNextPara;
			String type = "unassigned";
			newNextPara = false; // set to be false after getting the last value

			String lastPara = "";
			if (paras.size() > 0) {
				lastPara = paras.get(paras.size() - 1);
			}

			if (line.get_left_coord() == 0 && line.get_top_coord() == 0) {
				newPara = true;// pageNum should be a separate paragraph
				newNextPara = true;
				type = "noncontent_pagenum";
			} else if (isTaxonName(text)) {
				newPara = true;
				type = "content_taxonname";
			} else if (isTaxonName(lastPara) && isTaxonAuthor(text)) {
				newPara = true;
				type = "content_taxonAuthor";
			} else if (isTitle(text)) {
				newPara = true;
				type = "content_title";
			} else if (isKeywords(text)) {
				newPara = true;
				type = "content_keywords";
			} else if (isInParagraphTitle(text)) {
				newPara = true;
				type = "content_typespecies";
			} else if (isFigureTable(text, lastPara) > 0) {
				// start of a figure should start a new paragraph, but may be
				// connected to later lines
				newPara = true;
				if (isFigureTable(text, lastPara) == 1) {
					type = "noncontent_tbl_figtbl";
				} else {
					type = "noncontent_fig_figtbl";
				}
			} else if (isfigTblTxt(text)) {
				newPara = true;
			}else if (text.matches("Suborder|Subfamily|Family|Order")) {
				newPara = true;
			}

			// use y-coordinate to separate paragraph
			int diff = bottom - bottom_lastline;
			if (diff < 0)
				diff = -diff; // to be tested
			if (diff > 65) {
				newPara = true;
			}

			// use period and length of line to separate paragraph
			if (text.length() <= this.lineLength * 2 / 3
					|| (text.endsWith(".") && text.length() < lastLength - 10)) {
				newNextPara = true;
			}

			lastLength = text.length();

			// update para
			if (newPara || paras.size() == 0) {
				paras.add(text);// insert para
				types.add(type);// insert type
				bottoms.add(bottom);
			} else {
				// update para
				if (!lastPara.endsWith("-")) {
					lastPara += " ";
				}
				paras.set(paras.size() - 1, lastPara + text);

				// update type
				if (types.get(types.size() - 1).equals("unassigned")) {
					types.set(types.size() - 1, type);
				}
			}
		}
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
	private ArrayList<Line> getAllLinesInOrder(Element page, String source,
			String pageNum) {
		ArrayList<Line> allLinesInOrder = new ArrayList<Line>();
		ArrayList<Line> left_side = new ArrayList<Line>();
		ArrayList<Line> cross_middle = new ArrayList<Line>();
		ArrayList<Line> right_side = new ArrayList<Line>();
		int left_coord, right_coord, top_coord, bottom_coord;
		int botom_of_1st_line = 0;
		String actgualPageNum = "";
		String line_of_pageNum = "";
		boolean line0IsNumber = false;
		boolean titleFound = false;
		Line first_line = null;

		// read all lines into leftside, crossingmiddle, rightside, remember
		// their top
		List lines = page.getChildren("LINE");
		
		//get specific middle point for "Tabachnick_2002Rossellidae"
		if (fileName.startsWith("Tabachnick_2002Rossellidae")) {
			int count_1 = 0, count_2 = 0;
			for (int i = 0; i < lines.size(); i++) {
				Element e_line = (Element) lines.get(i);
				if (e_line != null) {
					String coordinate = e_line.getAttributeValue("coords");
					String[] coords = coordinate.split(",");
					if (coords.length == 4) {
						int c = Integer.parseInt(coords[0]);
						if ((c > 240 && c < 260) || (c > 1290 && c < 1310)) {
							count_1++;
						} else if ((c > 240 && c < 260) || (c > 1290 && c < 1310)) {
							count_2++;
						}
						
						//count to 6
						if (count_1 > count_2 && count_1 > 5) {
							this.page_middle_point = 1274;
							break;
						} else if (count_2 > count_1 && count_2 > 5) {
							this.page_middle_point = 1390;
							break;
						}
					}
				}
				this.page_middle_point = middlePoints.get(fileName);
			}
		}
		//end of getting the page_middle_point
		
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
	private void insertLine(Line line, ArrayList<Line> left_side,
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
	
	private void addParagraphs(ArrayList<String> paras, String source,
			ArrayList<String> types, ArrayList<Integer> bottoms, String pageNum) {

		try {
			DatabaseAccessor.insertParagraphs(this.prefix, paras, source, conn,
					types, pageNum, bottoms);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String replaceIllegalCharacter(String line) {
		line = line.replaceAll("[ì|î]", "\"");
		line = line.replaceAll("í", "'");
		line = line.replaceAll("[ñ|-|ñ|ó]", "-");
		line = line.replaceAll(" ", " ");
		// line = line.replaceAll("ˆ", "o");
		return line;
	}

	/**
	 * use four hash tables to fix incorrect space in xml
	 * 
	 * @param line
	 * @return
	 */
	private String fixSpace(String line) {
		String lastword = ""; // the last word
		String firstword = "";
		String line_forCompare = line;

		// original compare
		String key = line_forCompare.replaceAll("\\s", "");
		String fixed_txt = copiedText.get(key);
		if (fixed_txt != null) {
			if (fixed_txt.length() < line_forCompare.length()) {
				spacefixed++;
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
					spacefixed++;
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
					spacefixed++;
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
	 * decide whether this line could be page number, if yes, must be in a
	 * separate paragraph
	 * 
	 * @param para
	 * @return
	 */
	private boolean maybePageNumber(String para) {
		para = para.trim();
		if (para.matches("^\\d+\\s{2,}(.*)") || para.matches("\\d+")
				|| isRomePageNumber(para) || para.matches("(.*?)\\s{2,}\\d+$")) {
			return true;
		} else
			return false;

	}
	
	private boolean isRomePageNumber(String line) {
		line = line.trim();
		if (line.matches("^(?i)M*(D?C{0,3}|C[DM])(L?X{0,3}|X[LC])(V?I{0,3}|I[VX])$")) {
			return true;
		}
		return false;
	}
	
	private boolean isTaxonName(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.taxonNamePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/*for titles of typespecies*/
	private boolean isInParagraphTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			//normal pattern
			Pattern p = Pattern.compile(this.desTypeSpeciesParagraphPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
			
			//special case: brackets not ended
			p = Pattern.compile(this.titleOfTypespeciesNoEndPattern);
			mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean isKeywords(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.keywordsPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean isTaxonAuthor(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.taxonAuthorPattern + ".*?");
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean isTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleStandOutPattern + ".*?");
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private int isFigureTable(String para, String lastPara) {
		if (lastPara.trim().endsWith("--")) {
			return 0;
		}

		para = para.trim();

		if (para.matches(this.tablePattern)) {
			return 1;
		}
		if (para.matches(this.figurePattern)) {
			return 2;
		}

		if (!figException.equals("")) {
			if (para.startsWith(figException)) {
				return 2;
			}
		}
		return 0;
	}
	
	private boolean isfigTblTxt(String para) {
		Pattern p = Pattern.compile(figtblTxtPattern);
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
	 * remove page title and page number from lines
	 * @param lines
	 */
	private void identifyPageTitles(ArrayList<Line> lines) {
		if (lines.size() == 0) {
			return;
		} 
		if (lines.size() == 1) {
			if (maybePageNumber(lines.get(0).get_text())) {
				lines.remove(0);
			}
			return;
		}
		
		//check first two lines
		Line line1 = lines.get(0);
		Line line2 = lines.get(1);
				
		if (maybePageNumber(line1.get_text())) {
			if (lines.size() > 2) {
				Line line3 = lines.get(2);
				if (line3.get_top_coord() < line1.get_bottom_coord()) {
					lines.remove(2);
				}
			}
			
			if (line2.get_top_coord() < line1.get_bottom_coord()) {
				lines.remove(1);
				lines.remove(0);
			} else {
				lines.remove(0);
			}
			return;
		}
		
		if (maybePageNumber(line2.get_text())) {
			if (lines.size() > 2) {
				Line line3 = lines.get(2);
				if (line3.get_top_coord() < line2.get_bottom_coord()) {
					lines.remove(2);
				}
			}
			
			if (line1.get_top_coord() < line2.get_bottom_coord()) {
				lines.remove(1);
				lines.remove(0);
			}
			return;
		}
		
		if (lines.size() > 2) {
			Line line3 = lines.get(2);
			if (maybePageNumber(line3.get_text())) {
				lines.remove(2);
				if (line2.get_top_coord() < line3.get_bottom_coord()) {
					lines.remove(1);
				}
				if (line1.get_top_coord() < line3.get_bottom_coord()) {
					lines.remove(0);
				}
			}
			return;
		}
		
		//check last two lines
		line2 = lines.get(lines.size() - 1);
		line1 = lines.get(lines.size() - 2);
			
		if (maybePageNumber(line1.get_text())) {
			if (line2.get_top_coord() < line1.get_bottom_coord()) {
				lines.remove(lines.size() - 2);
				lines.remove(lines.size() - 1);
			}
			return;
		}
		
		if (maybePageNumber(line2.get_text())) {
			if (line2.get_top_coord() < line1.get_bottom_coord()) {
				lines.remove(lines.size() - 2);
				lines.remove(lines.size() - 1);
			} else {
				lines.remove(lines.size() - 1);
			}
			return;
		}
	}
	
	/**
	 * select all pages with figure/table, and trace fig/tbl content page by
	 * page todo: what if there are two figures in one page?
	 */
	private void traceBackTblContent() {
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
	private void traceBackTblContent(String source, String pageNum, int y1,
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
	
	
	/**
	 * select all pages with figure/table, and trace fig/tbl content page by
	 * page todo: what if there are two figures in one page?
	 */
	private void traceBackFigContent() {
		ResultSet rs_para = null;
		try {
			String last_pageNum = "";
			int last_figID = 0;
			String condition = "type like '%fig_figtbl%'";
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
	private void traceBackFigContent(String source, String pageNum, int y1,
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
	
	private void markAsType(int paraID, String type) {
		String set = "type = '" + type + "'";
		String condition = "paraID = " + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
	
	private void markAdd2Last(int paraID, String mark) {
		String set = "add2last='" + mark + "'";
		String condition = "paraID=" + paraID;
		try {
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean isNonWordLine(String para) {
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
	private boolean isInterruptingPoint(int paraID, String para,
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
	private void getCleanParagraph() throws Exception {
		// get records with type "content%"
		ResultSet rs = DatabaseAccessor.getParagraphsByCondition(prefix,
				"type like 'content%'", "paraID desc", "*", conn);
		String combinedPara = "";
		String note = "";
		boolean containsTaxon = false;
		boolean previousContainsTaxon = false;
		Pattern p_taxon = Pattern.compile(taxonNamePattern
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
								|| (index_left > 1 && index_left < index_right) /*
																				 * there
																				 * is
																				 * ]
																				 * but
																				 * there
																				 * is
																				 * also
																				 * [
																				 * before
																				 * ]
																				 */) {
							containsTaxon = true;
						}
					}
				}
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

	private void outputCleanContent() {
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
	
}
