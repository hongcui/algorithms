package informationContent;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import org.jdom.*;
import org.jdom.input.SAXBuilder;

import com.sun.org.apache.xpath.internal.FoundIndex;

import beans.*;

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
public class ContentExtract_2 {

	private ArrayList pageNumberText = null;
	private ArrayList footNoteTokens = null;
	private File sourceFile = null;
	private String prefix = null;
	private int left_column_end = 1009; // different volumes have different
										// values
	private int right_column_start = 1034; // different volumes have different
											// values
	private int page_middle_point = 1021;
	private long lineLength = 75;
	private int lastLength = 0;
	private long totalLength = 0; // for lineLength calculation
	private long linecount = 0; // for lineLength calculation
	private boolean epilogBegings = false; // once this is true, all text will
											// be marked as
											// noncontent_epilog
	private boolean hasToCHeading = false;
	private boolean hasToCDots = false;
	private boolean hasIndex = false;
	private Connection conn = null;
	private static String url = ApplicationUtilities
			.getProperty("database.url");
	private boolean newNextPara = false; // to specify the next line should be
											// a
											// new paragraph
	private String figurePattern = "(FlG|F\\s?i\\s?G|F\\s?I\\s?G|F\\s?i\\s?g)\\s?\\.\\s+\\d+\\s?\\.\\s+.*?";
	private String figException = "";
	private String tablePattern = "(TABLE|Table)\\s+\\d+\\s*\\..*?";
	private String figtblTxtPattern;
	private String innerSectionPattern = "^([A-Z]+\\s?)+[A-Z]+";
	private String taxonNamePattern = Patterns.taxonNamePattern;

	private String startText = "SYSTEMATIC DESCRIPTIONS";// define the first
															// line
	private String endText = "REFERENCES";// define the last line

	private boolean contentStarted = false;
	private String startPage = "331";
	private boolean contentEnded = false;
	private String cleanTableSurfix = "_clean_paragraphs";
	private String outputPath = "E:\\work_data\\clean_o\\";
	private String txtPath = "E:\\work_data\\txt\\";
	private String txtFileSuffix = ".txt";
	private int lengthOfFootnoteKey = 50;
	private int spacefixed = 0;

	private Hashtable<String, String> copiedTxtHash = new Hashtable<String, String>();
	private Hashtable<String, String> footnotes = new Hashtable<String, String>();

	// regular figurepattern:
	// (Fig\\.|Figure|Table|FIG\\s?\\.|TABLE|FIGURE|FlG\\.|FlGURE)\\s+\\d+\\s*\\.\\s+.*?
	/**
	 * 
	 */
	private ContentExtract_2() {
		this.taxonNamePattern = Patterns.taxonNamePattern;
		String sourceFilePath = "";
		this.outputPath = "E:\\work_data\\clean\\";
		this.figtblTxtPattern = "^\\d+(\\.\\d+)?\\s?\\??mm?"
				+ "|(\\d\\d?[a-z]*\\d?)(\\s?\\d\\d?[a-z])*\\'*" + "|[a-z0-9]*"
				+ "|[a-z]+" + "|[a-z][0-9](\\s[a-z][0-9])+" + "|[A-Z]+\\'*"
				+ "|([A-Z][a-z]*(\\s[A-Z][a-z]*)*)"
				+ "|([A-Z][a-z]*\\s)?\\([A-Z][a-z]*\\)"
				+ "|\\([a-z]+(\\s[a-z]+)*\\)";
		// (transmedian,
		// anterior lateral)

		// volume o
		// sourceFilePath = "E:\\work_data\\xml\\xml_o";
		// this.startText = "SYSTEMATIC DESCRIPTIONS";
		// this.endText = "REFERENCES";
		// this.startPage = "331";
		// this.left_column_end = 1009;
		// this.right_column_start = 1034;
		//
//		 // volume b
//		 sourceFilePath = "E:\\work_data\\xml\\xml_b";
//		 this.startText = "SYSTEMATIC DESCRIPTIONS";
//		 this.startPage = "108";
//		 this.endText = "NOMINA DUBIA AND GENERIC NAMES WRONGLY";
//		 this.left_column_end = 1009;
//		 this.right_column_start = 1034;

		//
		// // volume h --nothing in this volume
		// sourceFilePath = "E:\\work_data\\xml\\xml_h";
		// this.startText = "ANATOMY";
		// this.startPage = "27";
		// this.endText = "REFERENCES";

		//
		// // volume e_2 --nothing in this volume
		// sourceFilePath = "E:\\work_data\\xml\\xml_e_2";
		// this.startText = "GENERAL FEATURES OF THE PORIFERA";
		// this.startPage = "29";
		// this.endText = "GLOSSARY OF MORPHOLOGICAL TERMS";
		//
		// volume e_3
		sourceFilePath = "E:\\work_data\\xml\\xml_e_3";
		this.startText = "PALEOZOIC DEMOSPONGES";
		this.startPage = "9";
		this.endText = "RANGES OF TAXA";
		this.figException = "FIG. 33a";
		this.left_column_end = 1250;
		this.right_column_start = 1300;
		// exception: FIG. 33a-d.Grow, p47
		// this.figurePattern = "(FlG|FIG)\\s?\\.\\s+\\d+(\\s?\\.\\s+)?.*?";
		// exception: p52(2b2c, 2b 2c) p57(500 ?m; 50?m; 20 mm; 29mm; 0.125 mm;
		// Erylus (Erylus))

		// //volume h_2 - problem of table content
		// sourceFilePath = "E:\\work_data\\xml\\h_2";
		// this.startText = "BRACHIOPODA";
		// this.startPage = "60";
		// this.endText = ""; //no end, till the last word
		// this.left_column_end = 1009;
		// this.right_column_start = 1034;
		//
//		 //volume h_3 - problem of missing columns; long figure text
//		 sourceFilePath = "E:\\work_data\\xml\\h_3";
//		 this.startText = "PRODUCTIDINA";
//		 this.startPage = "4";
//		 this.endText = "REFERENCES";
//		 this.left_column_end = 955;
//		 this.right_column_start = 995;

//		 //volume h_4 - problem of missing text; other pattern of figure text
//		 sourceFilePath = "E:\\work_data\\xml\\h_4";
//		 this.startText = "PENTAMERIDA";
//		 this.startPage = "41";
//		 this.endText = "NOMENCLATORIAL NOTE";
//		 this.left_column_end = 1009;
//		 this.right_column_start = 1034;

//		 //volume h_5 - problem of wrong text
//		 sourceFilePath = "E:\\work_data\\xml\\h_5";
//		 this.startText = "SPIRIFERIDA";
//		 this.startPage = "47";
//		 this.endText = "NOMENCLATORIAL NOTE";
//		 this.left_column_end = 1009;
//		 this.right_column_start = 1034;
		//
		// //volume h_6
		// //may not work for space fixing since copied text are not by line
		// sourceFilePath = "E:\\work_data\\xml\\h_6";
		// this.startText = "SYSTEMATIC DESCRIPTIONS:";
		// this.startPage = "262";
		// this.endText = "AFFINITIES OF BRACHIOPODS AND TRENDS IN"; //page2822
		// this.left_column_end = ?;
		// this.right_column_start = ?;
		//
//		 //volume l_4
//		 sourceFilePath = "E:\\work_data\\xml\\l_4";
//		 this.startText = "SYSTEMATIC DESCRIPTIONS";
//		 this.startPage = "21";
//		 this.endText = "EXPLANATION OF CORRELATION CHART";
//		 this.left_column_end = 960;
//		 this.right_column_start = 990;

		this.page_middle_point = (this.right_column_start + this.left_column_end) / 2;
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

					// check if line 0 is a number (page number)
					if (i == 0) {
						botom_of_1st_line = bottom_coord;
						if (maybePageNumber(text)) {
							line0IsNumber = true;
							actgualPageNum = text;
						} else {
							first_line = new Line(text, left_coord,
									bottom_coord, right_coord, top_coord);
						}
					}

					// in case of line 0 is page title and line 1 is page number
					if (i == 1 && top_coord < botom_of_1st_line) {
						if (line0IsNumber) {// line 1 is page title and page 0
											// is page number
							titleFound = true;
							continue;
						} else if (maybePageNumber(text)) {
							actgualPageNum = text;
							titleFound = true;
							continue;
						}
					}

					// check the content start
					if (!contentStarted) {
						// haven't found the first line
						if (!isFirstline(text, actgualPageNum, pageNum)) {
							continue;
						} else {
							contentStarted = true;
						}
					}
					// check the content end
					if (contentStarted && !contentEnded) {
						// started but not ended
						if (isLastline(text)) {
							contentEnded = true;
							break;
						}
					}

					if (i == 0)
						continue;// always skip the first line, will be dealt
									// with in i == 1
					if (i == 1) {// insert first line
						if (first_line != null) {
							insertLine(first_line, left_side, right_side,
									cross_middle);
						}
					}

					// when hasPageNum but title not found yet
					if (i > 1 && line0IsNumber && !titleFound) {// try to find
																// the page
																// title (in
																// volume L_4,
																// the page
																// title in the
																// last line)
						if (top_coord < botom_of_1st_line) {
							titleFound = true;
							continue;// skip the page title line
						}
					}

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
			int separator = cross_middle.get(i).get_top_coord();

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

	private void processPage(Element page, String source, String pageNum) {
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();
		ArrayList<Integer> bottoms = new ArrayList<Integer>();
		int bottom_of_last_line = 0;
		// first get all content
		ArrayList<Line> lines = getAllLinesInOrder(page, source, pageNum);

		// process content: readLine
		for (Line line : lines) {
			processLine(paras, types, bottoms, line, bottom_of_last_line);
			bottom_of_last_line = line.get_bottom_coord();
		}

		if (contentStarted) {
			addParagraphs(paras, source, types, bottoms, pageNum);
		}

		System.out.println("--" + pageNum + " fetched");
	}

	private void processLine(ArrayList<String> paras, ArrayList<String> types,
			ArrayList<Integer> y1s, Line line, int bottom_lastline) {
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
			} else if (hasUncertain(text)) {
				if (isUncertainTaxon(text)) {
					type = "content_taxonname";
					newNextPara = true;		
					newPara = true;
				} else if (isCombinedUnCertain(text, lastPara)) {
					newPara = false;
					type = "content_taxonname";
					newNextPara = true;
				} else if (isInnerSection(text)) {
					newPara = true;
					type = "noncontent_innersection";
				}
			} else if (isInnerSection(text)) {
				if (isCombinedTaxonName(text, lastPara)) {
					// add up with new type
					type = "content_taxonname";
					newPara = false; /*
									 * set newPara to be false if combined taxon
									 * name
									 */
				} else {
					newPara = true;
					type = "noncontent_innersection";
				}
			} else if (isTaxonName(text)) {
				newPara = true;
				type = "content_taxonname";
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

			} else if (text
					.matches("^((TABLE OF )?CONTENTS|(Table [Oo]f )?Contents)$")) {
				this.hasToCHeading = true;

				newPara = true; // "CONTENTS" should be a separate paragraph
				newNextPara = true;
				type = "noncontent_prolog";
			} else if (text
					.matches(".*?[a-zA-Z].*?\\s*[\\. ]{3,}\\s*[A-D]?[ivx\\d]+$")) {
				this.hasToCDots = true; // dots used in table of content

				newPara = true;// "dots should be a separate paragraph"
				newNextPara = true;
				type = "noncontent_prolog";
			} else if (text.matches("INDEX$")) {
				this.hasIndex = true;
				this.epilogBegings = true;

				newPara = true;// "INDEX" should be a separate paragraph
				newNextPara = true;
				type = "noncontent_epilog";
			} else if (isFootNote(text)) {
				newPara = true;
				type = "noncontent_footnote";
			} else if (text.matches("Suborder|Subfamily|Family|Order")) {
				// v_e_3 page49
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
				y1s.add(bottom);
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
	 * 
	 * @param fileName
	 */
	private void getCopiedText(String fileName) {
		File txtFile = new File(txtPath + fileName + txtFileSuffix);
		try {
			FileInputStream fstream = new FileInputStream(txtFile);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line = "";
			while ((line = br.readLine()) != null) {
				String l = line.trim();
				if (!l.equals("")) {
					// original line
					copiedTxtHash.put(l.replaceAll("\\s", ""), l);

					// line without the last word
					int lastSpaceIndex = l.lastIndexOf(" ");
					if (lastSpaceIndex > 0) {
						String prefix_l = l.substring(0, lastSpaceIndex); // drop
																			// the
																			// last
																			// word
						String key = prefix_l.replaceAll("\\s", "");
						copiedTxtHash.put(key, prefix_l);

						// footnote
						if (prefix_l.matches("^\\d[A-Z][a-z]+,?\\s.*?")) {
							key = key.substring(1);
							if (key.length() > lengthOfFootnoteKey) {
								key = key.substring(0, lengthOfFootnoteKey);
							}
							footnotes.put(key, prefix_l.substring(1));
						}
					}

					// line without the first word
					int firstSpaceIndex = l.indexOf(" ");
					if (firstSpaceIndex > 0) {
						String surfix_l = l.substring(firstSpaceIndex,
								l.length()); // drop the first word
						String key = surfix_l.replaceAll("\\s", "");
						copiedTxtHash.put(key, surfix_l);
					}

					// line without the first and the last word
					if (firstSpaceIndex > 0 && lastSpaceIndex > firstSpaceIndex) {
						String middle_l = l.substring(firstSpaceIndex,
								lastSpaceIndex);
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
	private void readPages() throws FileNotFoundException {
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
						processPage(page_content, source, pageNum);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
		String fixed_txt = copiedTxtHash.get(key);
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

		// drop the first word and compare
		int firstSpaceIndex = line.indexOf(" ");
		if (firstSpaceIndex > 0) {
			line_forCompare = line.substring(firstSpaceIndex, line.length());
			firstword = line.substring(0, firstSpaceIndex);
			key = line_forCompare.replaceAll("\\s", "");
			fixed_txt = copiedTxtHash.get(key);
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

	/*
	 * @param line
	 * 
	 * @return
	 */
	private String replaceIllegalCharacter(String line) {
		line = line.replaceAll("[“|”]", "\"");
		line = line.replaceAll("’", "'");
		line = line.replaceAll("[–|-|–|—]", "-");
		line = line.replaceAll(" ", " ");
		// line = line.replaceAll("ö", "o");
		return line;
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

	/**
	 * mark inner section
	 * 
	 * @param rs_para
	 * @throws Exception
	 */
	private void markInnerSectionAndFakeTaxon() throws Exception {
		ResultSet rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
				"", "paraID", "*", conn);
		boolean started = false;
		boolean endwithFig = false, unfinishedFig = false;
		boolean isFakeTaxon = false, isTrueTaxon = false;
		while (rs_para.next()) {
			isFakeTaxon = false;
			isTrueTaxon = false;
			String currentType = rs_para.getString("type");
			String para = rs_para.getString("paragraph");

			// determind if the line is end with unfinished fig
			unfinishedFig = endwithFig; // unfinishedFig is for the last
										// paragraph
			if (para.matches("^.*?-\\s?-\\s?(FIG).{2,15}$")) {
				endwithFig = true;
			} else
				endwithFig = false;

			// determine if it is a fake taxon name
			if (currentType.contains("taxonname")) {
				if (hasUncertain(para)) {
					isTrueTaxon = true;
					isFakeTaxon = false;
				} else if (para.length() > lineLength * 1.5) {
					Pattern p = Pattern.compile("(" + taxonNamePattern + ")"
							+ ".*\\d\\d\\d\\d.*\\[.*\\].*");
					Matcher m = p.matcher(para);
					if (!m.matches()) {
						isFakeTaxon = true;
					} else {
						isTrueTaxon = true;
					}
				} else {
					// the taxon name that will break an innersection must be a
					// title, therefore, it can only match the bigger part
					Pattern p = Pattern.compile(taxonNamePattern);
					Pattern p2 = Pattern.compile(taxonNamePattern
							+ "\\s[A-ZÖÉÄ][a-z]+,\\s\\d\\d\\d\\d");

					// ^\\??[A-Z]([a-z]+)\\s([A-ZÖÉÄ]\\.?\\s?([A-ZÖÉÄ']+))
					Matcher m = p.matcher(para);
					Matcher m2 = p2.matcher(para);
					if (m.matches() || m2.matches()) {
						isTrueTaxon = true;
					} else {
						isFakeTaxon = true;
					}
				}
			}

			if (started) {
				if (currentType.contains("taxonname")) {
					if (isFakeTaxon) {
						markAsType(rs_para.getInt("paraID"),
								"noncontent_faketaxon");
					} else {
						started = false; // end the innersection
					}
					continue;
				} else if (!currentType.equals("noncontent_pagenum")
						&& !currentType.equals("noncontent_tbl_figtbl")
						&& !currentType.equals("noncontent_fig_figtbl")) {
					markAsType(rs_para.getInt("paraID"),
							"noncontent_innersection");
				}
			} else if (currentType.contains("innersection")) {
				started = true;
				continue;
			} else if (!isTrueTaxon && unfinishedFig) {
				// markAsType(rs_para.getInt("paraID"), "content");
				markAdd2Last(rs_para.getInt("paraID"), "yes"); // set add2last
			}
		}
	}

	private void identifyContent() {
		ResultSet rs_para = null;
		try {
			// mark prolog
			DatabaseAccessor.updateProlog(prefix, conn);

			// mark fig content (texts between last pagenum and figure)
			traceBackFigContent();
			System.out.println("trace figure content finished "
					+ new Date(System.currentTimeMillis()));
			
			//mark table content
			traceBackTblContent();
			System.out.println("trace table content finished " + new Date(System.currentTimeMillis()));
			
			// mark inner section
			markInnerSectionAndFakeTaxon();
			System.out.println("markInnerSection finished "
					+ new Date(System.currentTimeMillis()));

			String condition = "(type='unassigned' or type='content_taxonname')";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "*", conn);
			if (rs_para != null) {
				int status_of_last_para = 2; // 0 - para_not_ended; 1- maybe
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
								status_of_last_para, rs_para.getString("type"), last_type)) { // H3
							markAdd2Last(paraID, "yes"); // set add2last
						}

						if (type.equals("unassigned")) {
							markAsType(paraID, "content");
						}

						if (para.endsWith(".")) {
							status_of_last_para = 2; // paragraph ended
						} else if (para.endsWith("]")) {
							status_of_last_para = 1; // if next paragraph is
														// taxon_name, then
														// ended, else not ended
						} else if (type.contains("taxonname") && hasUncertain(para)) {
							status_of_last_para = 4; //uncertain taxon
						} else {
							status_of_last_para = 0;
						}
						
						last_type = type;
					}
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
	private boolean isInterruptingPoint(int paraID, String para,
			int status_last_para, String type, String last_type) {
		para = para.trim();
		
		
		if (last_type.contains("taxonname")) {//last is simple taxon, this one is uncertain
			if (hasUncertain(para) && type.contains("taxonname")) {
				return false;
			}			
		} 
		if (status_last_para == 4) {//last is taxon uncertain, this one is taxon
			if (type.contains("taxonname")) {
				return false;
			}
		} else if (status_last_para == 0) { // if last paragraph not ended, is
										// interrunpting point
			return true;
		} else if (status_last_para == 1) { // is last paragraph end with ] and
											// next paragraph is a taxon name,
											// then is not interruping point
			if (type.contains("taxonname"))
				return false;
			else
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
		return false;
	}

	/**
	 * need to traceBack if a figure is found/table is found
	 * 
	 * @param para
	 * @return 0: not fig/table, 1: table, 2: figure
	 */
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

	private boolean isNonWordLine(String para) {
		para = para.trim();
		if (para.matches("[^\\w]+")) {
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
		} else {
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
			Pattern p = Pattern
					.compile("^[A-Z][A-Z][A-Z,:]+(\\s[A-Z][A-Z,:]+)*"); // over
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
	 * Order UNCERTAIN Order and Suborder UNCERTAIN Class, Order, and Family \n
	 * UNCERTAIN --not handled - can be handled with combined uncertain
	 * 
	 * @param line
	 * @return
	 */
	private boolean hasUncertain(String line) {
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
	
	private boolean isUncertainTaxon(String line) {
		line = line.trim();
		try {
			Pattern p1 = Pattern
					.compile("^([A-Z][a-z]+(,\\s[A-Z][a-z]+,?)*(\\s(and)\\s[A-Z][a-z]+)?\\s)?(UNCERTAIN|Uncertain)");

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
	 * Class, Order, and Family UNCERTAIN
	 * 
	 * @param line
	 * @param lastline
	 * @return
	 */
	private boolean isCombinedUnCertain(String line, String lastline) {
		if (line.matches("(UNCERTAIN|Uncertain)")) {
			Pattern p = Pattern
					.compile("^([A-Z][a-z]+(,\\s[A-Z][a-z]+,?)*(\\s(and)\\s[A-Z][a-z]+)?\\s)?(UNCERTAIN|Uncertain)");
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
			Pattern p = Pattern.compile(this.taxonNamePattern + ".*?");
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
				if (isInterruptingPoint(paraID, para, 2, "", "")) {
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
	private boolean startWithAToken(String para, String source) {
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

	private void markAsType(int paraID, String type) {
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
		ArrayList<String> sources = new ArrayList<String>();
		try {
			DatabaseAccessor.selectDistinctSources(this.prefix, sources,
					this.conn);
			Iterator<String> it = sources.iterator();
			boolean hasLog = false;
			StringBuffer log = new StringBuffer();
			while (it.hasNext()) {
				int count = 0;
				String filename = (String) it.next();
				String condition = "source=\"" + filename + "\"";
				ResultSet rs = DatabaseAccessor.getParagraphsByCondition(
						this.prefix + "_clean", condition, "", "*", conn);
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
				filename = filename.replaceFirst("\\.[a-z]+$", "_cleaned.txt");
				write2File(sb.toString(), filename);
			}
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

		ContentExtract_2 contentExtract = new ContentExtract_2();
		System.out.println("started: " + new Date(System.currentTimeMillis()));

		contentExtract.readPages();
		System.out.println("readPages completed "
				+ new Date(System.currentTimeMillis()));

		contentExtract.identifyContent();

		contentExtract.getCleanParagraph();
		// output file
		contentExtract.outputCleanContent();
		System.out.println("content extract finished: ("
				+ contentExtract.spacefixed + "space fixed) "
				+ new Date(System.currentTimeMillis()));
	}
}
