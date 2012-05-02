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
public class ContentFetcher_O {

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
	protected String figurePattern = "(FlG|FIG)\\s?\\.\\s+\\d+\\s?\\.\\s+.*?";
	protected String tablePattern = "(TABLE)\\s+\\d+\\s*\\..*?";
	protected String innerSectionPattern = "^([A-Z]+\\s?)+[A-Z]+";

	protected String startText = "SYSTEMATIC DESCRIPTIONS";// define the first
															// line
	protected String endText = "REFERENCES";// define the last line

	protected boolean contentStarted = false;
	protected String startPage = "331";
	protected boolean contentEnded = false;
	protected String cleanTableSurfix = "_clean_paragraphs";
	protected String outputPath = "E:\\work_data\\clean_o\\";

	// regular figurepattern:
	// (Fig\\.|Figure|Table|FIG\\s?\\.|TABLE|FIGURE|FlG\\.|FlGURE)\\s+\\d+\\s*\\.\\s+.*?

	/**
	 * 
	 */
	public ContentFetcher_O(ArrayList pageNumberText, ArrayList footNoteTokens,
			String sourceFilePath) {
		this.pageNumberText = pageNumberText;
		this.footNoteTokens = footNoteTokens;

		// volumn o
		sourceFilePath = "E:\\work_data\\xml\\xml_o";
		this.outputPath = "E:\\work_data\\clean\\clean_o\\";

		// volumn b
		sourceFilePath = "E:\\work_data\\xml\\xml_b";
		this.outputPath = "E:\\work_data\\clean\\";
		this.startText = "SYSTEMATIC DESCRIPTIONS";
		this.startPage = "108";
		this.endText = "NOMINA DUBIA AND GENERIC NAMES WRONGLY";

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

	protected void readPage_old(Element page, String source, String pageNum) {
		boolean isRegularPage = false;
		boolean secondColIdentified = false;
		boolean isContentTablePage = false;
		String actgualPageNum = "";

		List lines = page.getChildren("LINE");
		int minX_col_1 = 1000, maxY_col_1 = 0;
		int x1 = 0, y1 = 0;
		int pageNum_y1 = 0;
		int last_y1 = 0; // need this to separate obvious paragraph

		ArrayList<ColumnBean> columns = new ArrayList<ColumnBean>();

		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();

		// page number lines, may be 1 or 2 lines
		ArrayList<String> pageNum_para = new ArrayList<String>();
		// column 1 and paragraph
		ArrayList<String> col1_para = new ArrayList<String>();
		ArrayList<String> col1_type = new ArrayList<String>();
		// column2 id and paragraph
		ArrayList<String> col2_para = new ArrayList<String>();
		ArrayList<String> col2_type = new ArrayList<String>();
		// the rest id and paragraph
		ArrayList<String> rest_para = new ArrayList<String>();
		ArrayList<String> rest_type = new ArrayList<String>();

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

				// if the first line is page number, it should be excluded from
				// this part
				if (i == 0) {
					if (maybePageNumber(line.trim())) {
						pageNum_para.add(line);
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

				// check if the second line is description of page number
				int diff = (y1 > pageNum_y1 ? y1 - pageNum_y1 : pageNum_y1 - y1);
				if (diff < 15) {
					pageNum_para.add(line);
					continue;
				}

				if (isRegularPage) {
					// this is a regular page
					readLine(col1_para, col1_type, line, y1, last_y1);
				} else {
					if (!secondColIdentified) {
						// try to identify the 2nd column
						if (y1 < maxY_col_1) {
							// this is the start line of the 2nd column
							secondColIdentified = true;
							if (x1 < minX_col_1) {
								// switch col1 and col2 (page num may be
								// switched
								// too, but this is acceptable)
								col2_para = col1_para;
								col2_type = col1_type;
								col1_para = new ArrayList<String>();
								col1_type = new ArrayList<String>();
								readLine(col1_para, col1_type, line, y1,
										last_y1);
							} else {
								// no need t switch column, this is a regular
								// page
								isRegularPage = true;
								readLine(col1_para, col1_type, line, y1,
										last_y1);
							}
						} else {
							// this is still the 1st column
							readLine(col1_para, col1_type, line, y1, last_y1);
							// update minX_col_1 and maxY_col_1
							if (x1 < minX_col_1) {
								minX_col_1 = x1;
							}
							if (y1 > maxY_col_1) {
								maxY_col_1 = y1;
							}
						}
					} else {
						// try to identify the rest text
						if (y1 > maxY_col_1 + 15) { // give 15 padding
							// add rest txt into rest_para
							readLine(rest_para, rest_type, line, y1, last_y1);
						} else {
							// keep reading lines into col1_para
							readLine(col1_para, col1_type, line, y1, last_y1);
						}
					}
				}
			}
		}

		if (contentStarted) {
			addParagraphs(pageNum_para, col1_para, col2_para, rest_para,
					source, col1_type, col2_type, rest_type, pageNum);
		}

		System.out.println("--" + pageNum + " fetched");
		col1_para = null;
		col1_type = null;
		col2_para = null;
		col2_type = null;
		rest_para = null;
		rest_type = null;
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
		int last_y1 = 0; // need this to separate obvious paragraph

		ArrayList<ColumnBean> columns = new ArrayList<ColumnBean>();

		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();

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

				// if the first line is page number, it should be excluded from
				// this part
				if (i == 0) {
					if (maybePageNumber(line.trim())) {
						pageNum_para.add(line);
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

				// check if the second line is description of page number
				int diff = (y1 > pageNum_y1 ? y1 - pageNum_y1 : pageNum_y1 - y1);
				if (diff < 15) {
					pageNum_para.add(line);
					continue;
				}

				if (y1 < maxY) {
					// anothe column
					ColumnBean cb = new ColumnBean(paras, types);

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

					if (x1 < minX) {
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
				readLine(paras, types, line, y1, last_y1);
			}
		}

		// add the last column
		ColumnBean cb = new ColumnBean(paras, types);
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
	 * read xml page by page
	 * 
	 * @throws FileNotFoundException
	 * 
	 */
	protected void readPages() throws FileNotFoundException {
		File[] allFiles = sourceFile.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			if (contentEnded) {
				System.out.println("reached last line in last file");
				return;
			}
			File xmlfile = allFiles[i];
			SAXBuilder sb = new SAXBuilder();
			String source = allFiles[i].getName();
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
	 * decide add line as a separate paragraph or append line to last record
	 * 
	 * @param paras
	 * @param types
	 * @param line
	 */
	protected void readLine(ArrayList<String> paras, ArrayList<String> types,
			String line, int y1, int last_y1) {
		line = replaceIllegalCharacter(line);
		String l = line.trim();
		if (!l.equals("")) {
			this.totalLength += l.length();
			this.linecount++;
			this.lineLength = this.totalLength / this.linecount;
			boolean newPara = newNextPara;
			newNextPara = false; // set to be false after getting the last value

			String lastPara = "";
			if (paras.size() > 0) {
				lastPara = paras.get(paras.size() - 1);
			}

			String type = "unassigned";

			if (maybePageNumber(l)) {
				newPara = true;// pageNum should be a separate paragraph
				newNextPara = true;
				type = "noncontent_pagenum";
			} else if (isUncertain(l)) {
				type = "noncontent_uncertain";
			} else if (isInnerSection(l)) {
				if (isCombinedTaxonName(l, lastPara)) {
					// add up with new type
					type = "content_taxonname";
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
			}

			// use y-coordinate to separate paragraph
			if (y1 - last_y1 > 80) {
				newPara = true;
			}

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
						source, conn, pageNumTypes, pageNum);
			}

			for (ColumnBean cb : cbs) {
				if (cb.getParas().size() > 0) {
					DatabaseAccessor
							.insertParagraphs(this.prefix, cb.getParas(),
									source, conn, cb.getTypes(), pageNum);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Overload the original addParagraphs add pageNum; add col1, col2, rest one
	 * by one
	 * 
	 * @param col1
	 * @param col2
	 * @param rest
	 * @param source
	 * @param col1_types
	 * @param col2_types
	 * @param rest_types
	 * @param pageNum
	 */
	protected void addParagraphs(ArrayList<String> pageNumLines,
			ArrayList<String> col1, ArrayList<String> col2,
			ArrayList<String> rest, String source,
			ArrayList<String> col1_types, ArrayList<String> col2_types,
			ArrayList<String> rest_types, String pageNum) {

		try {
			if (pageNumLines.size() > 0) {
				ArrayList<String> pageNumTypes = new ArrayList<String>();
				for (int i = 0; i < pageNumLines.size(); i++) {
					pageNumTypes.add("noncontent_pagenum");
				}
				DatabaseAccessor.insertParagraphs(this.prefix, pageNumLines,
						source, conn, pageNumTypes, pageNum);
			}

			if (col1.size() > 0 && col1_types.size() > 0
					&& (col1.size() == col1_types.size())) {
				DatabaseAccessor.insertParagraphs(this.prefix, col1, source,
						conn, col1_types, pageNum);
			}
			if (col2.size() > 0 && col2_types.size() > 0
					&& (col2.size() == col2_types.size())) {
				DatabaseAccessor.insertParagraphs(this.prefix, col2, source,
						conn, col2_types, pageNum);
			}
			if (rest.size() > 0 && rest_types.size() > 0
					&& (rest.size() == rest_types.size())) {
				DatabaseAccessor.insertParagraphs(this.prefix, rest, source,
						conn, rest_types, pageNum);
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
	public void markInnerSection(String prefix, String source) throws Exception {
		ResultSet rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
				"source='" + source + "'", "paraID", "*", conn);
		boolean started = false;
		boolean ended = false;
		while (rs_para.next()) {
			String currentType = rs_para.getString("type");
			if (currentType.contains("taxonname")) {
				ended = true;
				started = false;
				continue;
			}
			if (currentType.contains("innersection")) {
				started = true;
				ended = false;
				continue;
			}

			if (!rs_para.getString("type").equals("noncontent_pagenum")
					&& started && !ended) {
				markAsType(rs_para.getInt("paraID"), "noncontent_innersection");
			}
		}
	}

	/*
	 * Replaced by identifyContent()
	 * 
	 * columns in paragraphs: paraID, source, paragraph, type, add2last, remark
	 * use heuristics 2-4 type: one of name, description, description-mixed,
	 * other-content, content, non-content, and unassigned add2last: yes,no,
	 * uncertain
	 * 
	 * this function uses only content, non-content, and unassigned and
	 * yes/no/uncertain for add2last
	 */
	public void idContent() {
		File[] allFiles = sourceFile.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			String source = allFiles[i].getName();
			if (source.substring(0, 1).equals(".")) {
				continue;
			}
			ResultSet rs_para = null;
			try {
				// prolog
				DatabaseAccessor.updateProlog(prefix, conn);
				rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
						"source='" + source + "' and type='unassigned'",
						"paraID", "", conn);
				if (rs_para != null) {
					while (rs_para.next()) {
						int paraID = rs_para.getInt("paraID");
						System.out.println("papaID: " + paraID + ":"
								+ new Date(System.currentTimeMillis()));
						String para = rs_para.getString("paragraph");
						/*
						 * if (isFigureTable(para)) { // also take care of
						 * labels for sub-figures H2 H6 markAsType(paraID,
						 * "noncontent_figtbl"); continue; }
						 */

						if (isPageNumber(paraID, para, source)) { // H2 H6
							markAsType(paraID, "noncontent_pagenum");
							continue;
						}
						if (isFootNote(paraID, para, source)) { // H2 H6
							markAsType(paraID, "noncontent_footnote");
							continue;
						}
						if (isHeading(para)) { //
							markAsType(paraID, "content_heading");
							continue;
						}
						if (isShortTexts(paraID, para, source)) { //
							markAsType(paraID, "noncontent_shorttext");
							continue;
						}

						if (isInterruptingPoint(paraID, para, source, false)) { // H3
							markAdd2Last(paraID, "yes"); // set add2last
						}

						markAsType(paraID, "content");
					}
				}
				rs_para = null;
				System.out.println("idContent finished "
						+ new Date(System.currentTimeMillis()));

				traceBackFigTblContent(source);
				System.out.println("traceBackFigTblContent finished "
						+ new Date(System.currentTimeMillis()));

				fixSquareBrackets(source);
				System.out.println("fixBrackets finished "
						+ new Date(System.currentTimeMillis()));

				fixAdd2LastHeadings(source);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Fengqiong: use this one to replace idContent() 1. mark inner section:
	 * from innersection to next taxon name 2.
	 */
	public void identifyContent() {
		File[] allFiles = sourceFile.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			String source = allFiles[i].getName();
			if (source.substring(0, 1).equals(".")) {
				continue;
			}
			ResultSet rs_para = null;
			try {
				// mark prolog
				DatabaseAccessor.updateProlog(prefix, conn);

				// mark inner section
				markInnerSection(prefix, source);

				// mark table content (texts between last pagenum and figure)
				traceBackFigTblContent(source);
				System.out.println("traceBackFigTblContent finished "
						+ new Date(System.currentTimeMillis()));

				// mark foot note, interrupting point
				rs_para = DatabaseAccessor
						.getParagraphsByCondition(
								prefix,
								"source='"
										+ source
										+ "' and (type='unassigned' or type='content_taxonname')",
								"paraID", "*", conn);
				if (rs_para != null) {
					boolean noEndPara = false;
					while (rs_para.next()) {
						boolean checkEndofPara = true;
						int paraID = rs_para.getInt("paraID");
						String para = rs_para.getString("paragraph");

						if (isPureQuestionMark(para)) {
							markAsType(paraID, "noncontent_illegal");
						} else {
							if (isInterruptingPoint(paraID, para, source,
									noEndPara)) { // H3
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
						 * markAsType(paraID, "noncontent_footnote"); continue;
						 * }
						 */
						/*
						 * if (isHeading(para)) { // markAsType(paraID,
						 * "content_heading"); continue; }
						 */

						/*
						 * if (isShortTexts(paraID, para, source)) { //
						 * markAsType(paraID, "noncontent_shorttext"); continue;
						 * }
						 */
					}
				}
				rs_para = null;
				System.out.println("idContent finished "
						+ new Date(System.currentTimeMillis()));

				// fix brackets, produce add2last y-[
				fixBrackets(source);
				fixSquareBrackets(source);

				System.out.println("fixBrackets finished "
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
	private void fixSquareBrackets(String source) {
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
			String source, boolean statusOfLastPara) {
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
	 * capitalized short text before paraID should be set to non-content
	 * 
	 * @param paraID
	 * @param source
	 * 
	 * 
	 */

	protected void traceBackFigTblContent(String source) {
		ResultSet rs_para = null;
		try {// find all figtbl

			// find the paraID for the last pagenum
			String condition = "source ='" + source
					+ "' and type like '%figtbl%'";
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID", "paraID", conn);
			if (rs_para != null) {
				while (rs_para.next()) {
					int paraID = rs_para.getInt("paraID");
					this.traceBackFigTblContent(paraID, source);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			rs_para = null;
		}
	}

	protected void traceBackFigTblContent(int paraID, String source) {
		// String condition =
		// "paraID < "+paraID+" and paraID > (select max(paraID) from "+prefix+"_paragraphs where type like '%pagenum%' and paraID < "+paraID+") and length(paragraph) <=50 and paragraph COLLATE utf8_bin rlike '^[[:space:]]*[[:upper:]]'";
		// String condition =
		// "source='"+source+"'and paraID < "+paraID+" and paraID > (select max(paraID) from "+prefix+"_paragraphs where type like '%pagenum%' and paraID < "+paraID+") and length(paragraph) <=50";

		try {

			ResultSet rs_para = null;
			// find the paraID for the last pagenum
			int last = 0;
			String condition = "type like '%pagenum%' and paraID < " + paraID;
			rs_para = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID desc", "paraID", conn);
			// DatabaseAccessor.selectParagraphs(prefix, condition,
			// "paraID desc",
			// paraIDs, paras, conn);
			if (rs_para != null && rs_para.next()) {
				last = rs_para.getInt("paraID");
			}
			rs_para = null;
			condition = "source='" + source + "'and paraID < " + paraID
					+ " and paraID > " + last + " and length(paragraph) <="
					+ this.lineLength * 1 / 2;

			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs,
					paras, conn);
			int offset = 1;
			for (int i = paraIDs.size() - 1; i >= 0; i--) {
				int pID = Integer.parseInt((String) paraIDs.get(i));
				if (pID == paraID - offset++) {
					markAsType(pID, "noncontent_figtbl_txt");
				} else {
					return;
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
		return startWith(line, this.endText);
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
			Pattern p = Pattern.compile("^[A-Z]+(\\s[A-Z]+)*");
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
	 * 
	 * @param line
	 * @return
	 */
	private boolean isUncertain(String line) {
		line = line.trim();
		try {
			Pattern p1 = Pattern
					.compile("^[A-Z]([a-z]+)\\s(UNCERTAIN|Uncertain)");
			Matcher mt1 = p1.matcher(line);
			if (mt1.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
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
					.compile("^\\??[A-Z]([a-z]+)\\s([A-ZÖÉ]\\.?\\s?([A-ZÖÉ]+)).*?");
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
				if (isInterruptingPoint(paraID, para, source, false)) {
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

		ContentFetcher_O cf = new ContentFetcher_O(pageNumberText,
				footNoteTokens, sourceFilePath);
		System.out.println("started: " + new Date(System.currentTimeMillis()));

		cf.readPages();
		System.out.println("readPages completed "
				+ new Date(System.currentTimeMillis()));

		cf.identifyContent();
		System.out.println("content identified"
				+ new Date(System.currentTimeMillis()));

		cf.getCleanParagraph();
		// output file
		cf.outputCleanContent();
	}
}
