package informationContent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.*;

import javax.xml.crypto.Data;

import db.DatabaseAccessor;

public class ContentFixer_O {
	protected String paraTableName = null;
	protected Connection conn = null;
	protected static String url = ApplicationUtilities
			.getProperty("database.url");
	protected String prefix = null;
	protected String cleanTableName = null;
	public int lineLength = 78;

	// collection specific arguments, values obtained from a to-be-create
	// config-GUI
	private boolean hasGlossary = true;
	private String glossHeading = "GLOSSARY";
	private String headingStyle = "ALLCAP";
	private boolean hasReferences = true;
	private String refHeading = "REFERENCES";
	private String Style = "ALLCAP";

	// private File source =new File(Registry.SourceDirectory); //a folder of
	// text documents to be annotated
	// private File source = new
	// File("X:\\DATA\\Plazi\\1stFetchFromPlazi\\antssubset_cleaned"); //where
	// output files will be saved
	private File source = new File("E:\\work_data");

	/**
     * 
     */
	public ContentFixer_O(String paraTableName) {
		this.paraTableName = paraTableName; // test_paragraphs
		this.prefix = paraTableName.replaceAll("_paragraphs", ""); // test_clean_paragraphs
		this.cleanTableName = this.prefix + "_clean_paragraphs";
		try {
			// paraID not null primary key longint, source varchar(50),
			// paragraph text(5000), type varchar(10), add2last varchar(5),
			// remark varchar(50)
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);

		} catch (Exception e) {
			e.printStackTrace();
			// LOGGER.error("Failed to create a conn in ContentFixer::constructor",
			// e);
		}
	}

	/**
	 * remove noncontent_pagenum, figtbl, prolog, figtbl_txt, epilog concate
	 * add2last paragraphs category glossaries, abbreviations, and references
	 * some noncontent_shorttext need to be saved
	 * 
	 * @throws SQLException
	 */
	protected void makeCleanContent() throws SQLException {
		try {
			DatabaseAccessor.createCleanParagraphTable(this.cleanTableName,
					this.conn);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// reset the add2last paragraphs that should be added to
		// the caption of a preceding fig or table. Work on the
		// original table, not the clean_paragraphs
		fixFigtbl();
		System.out.println("fixFigtbl complete: "
				+ new Date(System.currentTimeMillis()));

		// populate clean_paragraphs
		fixAdd2Last();
		System.out.println("fixAdd2Last complete: "
				+ new Date(System.currentTimeMillis()));
		
		// post processing: collection specific features
		// ALLCAP headings
		DatabaseAccessor.fixHeading(prefix, this.headingStyle, conn);
		System.out.println("fixHeading complete: "
				+ new Date(System.currentTimeMillis()));
		
		// Glossary
		if (this.hasGlossary) {
			DatabaseAccessor.fixGlossary(prefix, this.glossHeading, conn);
			System.out.println("fixGlossary complete: "
					+ new Date(System.currentTimeMillis()));
		}

		if (this.hasReferences) {
			DatabaseAccessor.fixReferences(prefix, this.refHeading, conn);
			System.out.println("fixReferences complete: "
					+ new Date(System.currentTimeMillis()));
		}
	}

	/**
	 * piece text segments back to text files
	 * 
	 */
	protected void outputCleanContent() {
		ArrayList<String> sources = new ArrayList<String>();
		try {
			DatabaseAccessor.selectDistinctSources(this.prefix, sources,
					this.conn);
			Iterator<String> it = sources.iterator();
			while (it.hasNext()) {
				String filename = (String) it.next();
				String condition = "source=\"" + filename + "\"";
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(this.prefix + "_clean",
						condition, "", paraIDs, paras, conn);
				Iterator<String> ps = paras.iterator();
				StringBuffer sb = new StringBuffer();
				while (ps.hasNext()) {
					String p = (String) ps.next();
					sb.append(p + System.getProperty("line.separator")
							+ System.getProperty("line.separator"));
				}
				filename = filename.replaceFirst("\\.[a-z]+$", "_cleaned.txt");
				File out = new File(this.source, filename);
				write2File(sb.toString(), out);
			}
		} catch (Exception e) {
			e.printStackTrace();
			// LOGGER.error("Failed to output text file in ContentFixer::outputCleanContent",
			// e);
			// throw new ParsingException("Failed to output text file.", e);
		}
	}

	private void write2File(String text, File f) {
		try {

			BufferedWriter out = new BufferedWriter(new FileWriter(f));
			out.write(text);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			// LOGGER.error("Failed to output text file in ContentFixer::write2File",
			// e);
			// throw new ParsingException("Failed to output text file.", e);
		}
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

	private void fixAdd2Last() throws SQLException {
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> sources = new ArrayList<String>();

		ResultSet rs_para_add2 = null;
		String condition = "type ='content' and add2last like 'y%'";
		try {
			// get all "add2last" paragraphs
			rs_para_add2 = DatabaseAccessor.getParagraphsByCondition(prefix,
					condition, "paraID desc", "paraID", conn);
			condition = "type ='content'"; // get all content paragraphs
			DatabaseAccessor.selectParagraphsSources(
					prefix.replaceAll("_clean", ""), condition, "", paraIDs,
					paras, sources, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// use paraIDs_add2 to manipulate the content of paras
		// TODO: should an add2last seg be added to the fig immediately before
		// it, or the paragraph immediately before the fig? Example
		// paraID459-460 in treatise part d
		if (rs_para_add2 != null) {
			while (rs_para_add2.next()) {
				String add2ID = rs_para_add2.getString(1);
				// fetch and attach in paraIDs
				int index = paraIDs.indexOf(add2ID);
				String add2p = paras.get(index).trim();
				System.out.println("add2ID = " + add2ID + "; index = " + index);
				if (index > 0) {
					String p = paras.get(index - 1).replaceFirst("-\\s*$", "-");

					String add2s = sources.get(index);
					String s = sources.get(index - 1);
					if (add2s.compareTo(s) == 0) {
						String t = "";
						if (p.endsWith("-")) {
							t = p + add2p;
						} else {
							t = p + " " + add2p;
						}

						paras.set(index - 1, t);
						paras.set(index, "");
					}
				}
			}
		}

		ArrayList<String> cparaIDs = new ArrayList<String>();

		// read out paras to clean_paragraphs table
		int ccount = 0;
		for (int i = 0; i < paras.size();) {
			if (paras.get(i).compareTo("") != 0) {
				cparaIDs.add(ccount + "");
				ccount++;
				i++;
			} else {
				paras.remove(i);
				paraIDs.remove(i);
				sources.remove(i);
			}
		}

		System.out.println("removed empty paras: new size is: "
				+ cparaIDs.size());

		if (cparaIDs.size() != paras.size() || paras.size() != paraIDs.size()
				|| paras.size() != sources.size()) {
			System.out.print("wrong!");
			System.exit(1);
		}

		normalize(paras);
		System.out.println("begin to insert clean paragraphs");
		try {
			DatabaseAccessor.insertCleanParagraphs(cleanTableName, cparaIDs,
					paraIDs, paras, sources, conn);
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("clean paragraphs inserted");

		paraIDs = null;
		paras = null;
		sources = null;
		rs_para_add2 = null;
	}

	private void normalize(ArrayList<String> paras) {
		for (int i = 0; i < paras.size(); i++) {
			String t = paras.get(i);
			t = t.replaceAll("(?<=[a-z])-\\s+(?=[a-z])", "-")
					.replaceAll("\\s+", " ").replaceAll("\\^", "");
			paras.set(i, t);
		}

	}

	/**
	 * @param args
	 * @throws SQLException
	 */
	public static void main(String[] args) throws SQLException {
		// TODO Auto-generated method stub
		ContentFixer_O cf = new ContentFixer_O("xml_o_paragraphs");
		// ContentFixer cf = new ContentFixer("antssubset_paragraphs");
		cf.makeCleanContent();
		System.out.println("makeCleanContent complete: "
				+ new Date(System.currentTimeMillis()));
		cf.outputCleanContent();
		System.out.println("outputCleanContent complete: "
				+ new Date(System.currentTimeMillis()));
	}
}