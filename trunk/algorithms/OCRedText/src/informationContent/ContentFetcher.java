//$Id$
/**
 * 
 */
package informationContent;

import java.util.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.regex.*;

import db.DatabaseAccessor;

//import fna.charactermarkup.ChunkedSentence; 
/**
 * $Id
 * 
 * The class applies the following simple heuristics:
 * 1. a blank line terminates a paragraph
 * 2. page numbers are either at the beginning of a paragraph or at the end, with or without accompanying strings
 * 3. a lower case letter starting a paragraph is the sign that the information content is interrupted by page numbers, footnotes, or figures/tables.
 * 4. anything before the first long text paragraph is considered non-content. they may be table of content.
 * 5. anything after the last long text paragraph is considered non-content. They may be end-of-book index.
 * 6. the user may or may not be able to provide the text strings accompanying page numbers and to provide the tokens starting footnotes  
 *
 *
 * Input includes pageNumberText as an ArrayList, footNoteTokens as an ArrayList, and the source file path pointing to the folder where the OCRed text may be found
 * Output is a set of tables whose names are prefixed with "prefix" in a database named "sourceDatasets".
 *
 * The user will need to create the database "sourceDatasets" and the user "termuser" before running this class
 */
public class ContentFetcher {
	protected ArrayList pageNumberText = null;
	protected ArrayList footNoteTokens = null;
	protected File sourceFile = null;
	protected String prefix = null;
	public int lineLength = 75;
	protected boolean hasToCHeading = false;
	protected boolean hasToCDots = false;
	protected boolean hasIndex = false;
	protected Connection conn = null;
	protected static String url = ApplicationUtilities
			.getProperty("database.url");

	/**
	 * 
	 */
	public ContentFetcher(ArrayList pageNumberText, ArrayList footNoteTokens,
			String sourceFilePath) {
		this.pageNumberText = pageNumberText;
		this.footNoteTokens = footNoteTokens;
		this.sourceFile = new File(sourceFilePath);
		this.prefix = sourceFile.getName();
		// create paragraphs table
		try {
			// paraID not null primary key longint, source varchar(50),
			// paragraph text(5000), type varchar(10), add2last varchar(5),
			// remark varchar(50)
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
			DatabaseAccessor.createParagraphTable(this.prefix, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// readParagraphs(); //populate paragraph table
		// idContent(); //identify content paragraphs (footnotes, headnotes/page
		// numbers, figures, and tables are not content paragraphs
	}

	/*
	 * simply add paragraphs to the paragraphs table
	 */
	protected void readParagraphs() {
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> sources = new ArrayList<String>();
		ArrayList<String> types = new ArrayList<String>();
		int count = 0;
		int paraID = 0;
		File[] allFiles = sourceFile.listFiles();
		for (int i = 0; i < allFiles.length; i++) {
			String source = allFiles[i].getName();
			if (source.substring(0, 1).equals(".")) {
				continue;
			}

			// read this file line by line, concat the lines that make a
			// paragraph, use heuristics 1: H1
			try {
				FileInputStream fstream = new FileInputStream(allFiles[i]);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(
						new InputStreamReader(in));
				String line = "";
				StringBuffer para = new StringBuffer();
				while ((line = br.readLine()) != null) {
					String l = line.trim();
					boolean contentOrIndex = false;
					if (l.matches("^((TABLE OF )?CONTENTS|(Table [Oo]f )?Contents)$")) {
						this.hasToCHeading = true;
						contentOrIndex = true; 
					}
					if (l.matches(".*?[a-zA-Z].*?\\s*[\\. ]{2,}\\s*[A-D]?[ivx\\d]+$")) {
						this.hasToCDots = true; //dots used in table of content
					}
					if (l.matches("INDEX$")) {
						this.hasIndex = true;
						contentOrIndex = true;
					}
					if (l.compareTo("") == 0
							|| l.length() <= this.lineLength * 2 / 3) {
						//make an end of a paragraph
						if (contentOrIndex) {
							//add previous paraID and para
							paraIDs.add(paraID + "");
							paras.add(para.toString());
							sources.add(source);
							types.add(hasToCHeading || hasToCDots ? "noncontent_prolog" : (hasIndex ? "noncontent_epilog" : "unassigned"));
							count++;
							if (count >= 100) {
								addParagraphs(paraIDs, paras, sources);
								paraIDs = new ArrayList();
								paras = new ArrayList<String>();
								sources = new ArrayList<String>();
								count = 0;
							}
							//new paraID and para
							paraID++;
							para = new StringBuffer();
						}
						
						//append current line to previous para
						//new paraID and para
						para.append(line).append(" ");
						String content = para.toString().trim();
						if (content.length() > 0) {
							paraIDs.add(paraID + "");
							paras.add(para.toString());
							// use para instead of content to preserve leading spaces
							sources.add(source);
							types.add(hasToCHeading || hasToCDots ? "noncontent_prolog" : (hasIndex ? "noncontent_epilog" : "unassigned"));
							count++;
							if (count >= 100) {
								addParagraphs(paraIDs, paras, sources);
								paraIDs = new ArrayList();
								paras = new ArrayList<String>();
								sources = new ArrayList<String>();
								count = 0;
							}
							paraID++;
							para = new StringBuffer();
						}
					} else {
						// use line instead of l to preserve leading spaces
						line = line.replaceAll(
								System.getProperty("line.separator"), "");
						para.append(line).append(" ");
					}
				}
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * preserve original case and leading spaces
	 * 
	 * @param paraID
	 * @param para
	 * @param source
	 */
	protected void addParagraphs(ArrayList paraIDs, ArrayList paras,
			ArrayList sources) {

		try {
			DatabaseAccessor.insertParagraphs(this.prefix, paraIDs, paras,
					sources, conn, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
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

			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			try {
				// prolog
				DatabaseAccessor.selectParagraphs(prefix, "source='" + source
						+ "' and type='unassigned'", "paraID", paraIDs, paras,
						conn);

				if (this.hasToCHeading) {
					// find "table of content | content" and mark to be
					// noncontent_prolog
					for (int j = 0; j < paraIDs.size(); j++) {
						int paraID = Integer.parseInt((String) paraIDs.get(j));
						String para = (String) paras.get(j);
						// if(para.trim().length() < this.lineLength){ //H4
						if (!para
								.trim()
								.matches(
										"^((TABLE OF )?CONTENTS|(Table [Oo]f )?Contents)$")) { // H4
							markAsType(paraID, "noncontent_prolog");
						} else {
							break;
						}
					}
				} else if (this.hasToCDots) {
					for (int j = 0; j < paraIDs.size(); j++) {
						int paraID = Integer.parseInt((String) paraIDs.get(j));
						String para = (String) paras.get(j);
						if (!para.trim().matches(
								".*?[a-zA-Z]\\s*[\\.]+\\s*[A-D]?[ivx\\d]+$")) { // H4
							markAsType(paraID, "noncontent_prolog");
						} else {
							break;
						}
					}
				}
				// epilogue
				if (this.hasIndex) {
					for (int j = paraIDs.size() - 1; j > 0; j--) {
						int paraID = Integer.parseInt((String) paraIDs.get(j));
						String para = (String) paras.get(j);
						if (!para.trim().matches("INDEX")) { // H5
							markAsType(paraID, "noncontent_epilog");
						} else {
							break;
						}
					}
				}
				/*
				 * paraIDs = new ArrayList<String>(); paras = new
				 * ArrayList<String>();
				 * DatabaseAccessor.selectParagraphs(prefix,
				 * "source='"+source+"' and type='unassigned'", "paraID desc",
				 * paraIDs, paras, conn); //reversed order for( int j = 0;
				 * j<paraIDs.size(); j++){ int paraID =
				 * Integer.parseInt((String)paraIDs.get(j)); String para =
				 * (String)paras.get(j); if(para.trim().length() <
				 * this.lineLength){ //H5 markAsType(paraID,
				 * "noncontent_epilog"); }else{ break; } }
				 */
				// body
				paraIDs = new ArrayList<String>();
				paras = new ArrayList<String>();

				DatabaseAccessor.selectParagraphs(prefix, "source='" + source
						+ "' and type='unassigned'", "paraID", paraIDs, paras,
						conn);
				for (int j = 0; j < paraIDs.size(); j++) {
					int paraID = Integer.parseInt((String) paraIDs.get(j));
					String para = (String) paras.get(j);
					if (isFigureTable(paraID, para, source)) {
						// also take care of labels for sub-figures H2 H6
						markAsType(paraID, "noncontent_figtbl");
						continue;
					}
					if (isPageNumber(paraID, para, source)) { // H2 H6
						markAsType(paraID, "noncontent_pagenum");
						continue;
					}
					if (isFootNote(paraID, para, source)) { // H2 H6
						markAsType(paraID, "noncontent_footnote");
						continue;
					}
					if (isHeading(paraID, para, source)) { //
						markAsType(paraID, "content_heading");
						continue;
					}
					if (isShortTexts(paraID, para, source)) { //
						markAsType(paraID, "noncontent_shorttext");
						continue;
					}
					if (isInterruptingPoint(paraID, para, source)) { // H3
						markAdd2Last(paraID, "yes"); // set add2last
					}
					markAsType(paraID, "content");
				}
				System.out.println("idContent finished "
						+ new Date(System.currentTimeMillis()));
				traceBackFigTblContent(source);
				System.out.println("traceBackFigTblContent finished "
						+ new Date(System.currentTimeMillis()));
				fixBrackets(source);
				System.out.println("fixBrackets finished "
						+ new Date(System.currentTimeMillis()));
				fixAdd2LastHeadings(source);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	protected void fixAdd2LastHeadings(String source) {
		// do nothing here, let subclass to extend.
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
	 */
	private void fixBrackets(String source) {
		try {
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			ArrayList<String> types = new ArrayList<String>();
			String condition = "source='" + source + "'";
			DatabaseAccessor.selectParagraphsTypes(prefix, condition,
					"paraID desc", paraIDs, paras, types, conn);
			int left = 0;
			int right = 0;
			for (int i = 0; i < paraIDs.size(); i++) {
				int pid = Integer.parseInt(paraIDs.get(i));
				String para = paras.get(i);
				String type = types.get(i);
				if (type.startsWith("content")) {
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
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix,
						"source='" + source
								+ "' and type like '%shorttext%' and paraID>="
								+ preLimit + " and paraID <" + paraID
								+ " and length(paragraph) < " + this.lineLength
								* 2 / 3, "", paraIDs, paras, conn);
				if (paras.size() == limit) {
					return true;
				}
				// following
				int folLimit = paraID + limit;
				paraIDs = new ArrayList<String>();
				paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix,
						"source='" + source
								+ "'and type like '%unassigned%' and paraID<="
								+ folLimit + " and paraID >" + paraID
								+ " and length(paragraph) < " + this.lineLength
								* 2 / 3, "", paraIDs, paras, conn);
				if (paras.size() == limit) {
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
	 * @param para
	 * @return
	 */
	protected boolean isInterruptingPoint(int paraID, String para, String source) {
		para = para.trim();

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
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	protected boolean isFigureTable(int paraID, String para, String source) {
		para = para.trim();
		Pattern pattern = Pattern
				.compile("(Fig\\.|Figure|Table|FIG\\.|TABLE|FIGURE|FlG\\.|FlGURE)\\s+\\d+.*?");
		Matcher m = pattern.matcher(para);
		if (m.matches()) {
			// traceBack should be done after all pagenumbers are found
			// traceBackFigTblContent(paraID, source);
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

		try {// find all figtbl
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			// find the paraID for the last pagenum
			String condition = "source ='" + source
					+ "' and type like '%figtbl%'";
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID",
					paraIDs, paras, conn);
			Iterator<String> it = paraIDs.iterator();
			while (it.hasNext()) {
				int paraID = Integer.parseInt(it.next());
				this.traceBackFigTblContent(paraID, source);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void traceBackFigTblContent(int paraID, String source) {
		// String condition =
		// "paraID < "+paraID+" and paraID > (select max(paraID) from "+prefix+"_paragraphs where type like '%pagenum%' and paraID < "+paraID+") and length(paragraph) <=50 and paragraph COLLATE utf8_bin rlike '^[[:space:]]*[[:upper:]]'";
		// String condition =
		// "source='"+source+"'and paraID < "+paraID+" and paraID > (select max(paraID) from "+prefix+"_paragraphs where type like '%pagenum%' and paraID < "+paraID+") and length(paragraph) <=50";

		try {
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			// find the paraID for the last pagenum
			int last = 0;
			String condition = "type like '%pagenum%' and paraID < " + paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID desc",
					paraIDs, paras, conn);
			if (paraIDs.size() >= 1) {
				last = Integer.parseInt(paraIDs.get(0));
			}
			paraIDs = new ArrayList<String>();
			paras = new ArrayList<String>();
			condition = "source='" + source + "'and paraID < " + paraID
					+ " and paraID > " + last + " and length(paragraph) <="
					+ this.lineLength * 1 / 2;
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
	 * [ BURGER: FLORA COSTARICENSIS 5 ]
	 * 
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	protected boolean isPageNumber(int paraID, String para, String source) {
		para = para.trim();
		if (para.matches("\\d+")) {
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
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix, condition, "",
						paraIDs, paras, conn);
				if (paraIDs.size() >= 3) {
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
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
			ArrayList<String> paras = new ArrayList();
			ArrayList<String> paraIDs = new ArrayList();
			paraID++;
			String condition = "paraID=" + paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs,
					paras, conn);
			if (paras.size() >= 1) {
				para = paras.get(0);
				if (isInterruptingPoint(paraID, para, source)) {
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
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix, condition, "",
						paraIDs, paras, conn);
				if (paraIDs.size() >= countThreshold) {
					cond2 = true;
				}
				if (paraIDs.size() >= countThreshold * 2) {
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

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// String sourceFilePath="X:\\DATA\\BHL\\test";
		// String sourceFilePath="X:/DATA/Treatise/treatiseTest";
		// String
		// sourceFilePath="X:\\DATA\\Plazi\\1stFetchFromPlazi\\antssubset";
		// String
		// sourceFilePath="X:\\DATA\\Plazi\\1stFetchFromPlazi\\plazi_fish_plain_text_english";
		String sourceFilePath = "X:\\DATA\\Treatise\\recent\\text\\test";
		sourceFilePath = "/Users/Jone/Documents/RA/OCRedText/fqo"; // run on
																	// Fengqiong's
																	// mac

		ArrayList<String> pageNumberText = new ArrayList<String>();
		String pnt1 = "FIELDIANA: BOTANY, VOLUME 40".toLowerCase();
		String pnt2 = "BURGER: FLORA COSTARICENSIS".toLowerCase();
		// String pnt3 = "FIELDIANA: BOTANY, VOLUME 24".toLowerCase();
		// String pnt4 =
		// "STANDLEY AND STEYERMARK: FLORA OF GUATEMALA".toLowerCase();
		pageNumberText.add(pnt1);
		pageNumberText.add(pnt2);
		// pageNumberText.add(pnt3);
		// pageNumberText.add(pnt4);

		ArrayList<String> footNoteTokens = new ArrayList<String>();
		String fnt1 = "'";
		footNoteTokens.add(fnt1);

		ContentFetcher cf = new ContentFetcher(pageNumberText, footNoteTokens,
				sourceFilePath);
		System.out.println("started: " + new Date(System.currentTimeMillis()));
		cf.readParagraphs();
		System.out.println("readParagraphs completed "
				+ new Date(System.currentTimeMillis()));
		cf.idContent();
		System.out.println("Fetch finished "
				+ new Date(System.currentTimeMillis()));
	}
}
