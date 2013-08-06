package informationContent;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import beans.KeyChoice;
import beans.KeyFile;
import beans.KeyStatement;
import beans.Taxon_foc;

/**
 * this class is to process foc (word documents) into xml files (taxons and
 * keys)
 * 
 * @author Fengqiong Huang
 * 
 */

// general facts about foc:
// 1. has correct heading, each heading related to one taxon
// 2. can get correct rank from heading
// 3. description is the first para which doesn't start with Italian font

public class word2xml_foc {
	public static void main(String[] args) {
		word2xml_foc t = new word2xml_foc();
		t.getStarted();
	}

	public String p_native = "^(NATURALIZED|NATIVE|UNABRIDGED|WAIF|JFP-4)$";
	public String p_key_line = "^(\\d+'?)\\.?\\s(.+)$";// 1. Lvs
	// public String p_main_key_line = "^(\\d+)\\.\\s.+$";
	public String p_key_tail = "^.+\\.{3,}->(.+)$";
	public String p_marked_para = "^([A-Z0-9\\+\\s]+):(\\s.*)?$";
	public String p_descrip_tags = "|PISTILLATE FLOWER|TOXICITY|LEAF|CHROMOSOMES|FLOWER|"
			+ "FRUIT|INFLORESCENCE|SEED|STEM|STAMINATE FLOWER|";

	// ARUNCUS dioicus (Walter) Fernald var. acuminatus
	// Ruiz & Pav.
	public String p_author_part = "(\\s\\(.+\\))?(\\s[A-Z][a-z]*\\.?)(\\s&\\s[A-Z][a-z]*\\.?)?";
	public String p_taxon_name = "[A-Z]+(\\s[a-z-]+(" + p_author_part
			+ ")?(\\s(var|subsp)\\.\\s[a-z]+)?)?";

	/* output xml files */
	private String inputFolderPath = "D:\\Work\\Data\\foc\\FilesToProcess";
	private String outputFolderPath = "D:\\Work\\Data\\foc\\output";
	private String outputMainFolderName = "";
	private String outputKeyFolderName = "keys";
	private String outputTaxonFolderName = "taxons";

	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	private ArrayList<String> ranksList = new ArrayList<>();
	private Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	private int keyFileNumber = 0;
	private int taxonFileNumber = 0;

	/* file process helpers */
	public String prefix = "";
	public String source = "";
	public String fileName = "";

	public int last_ks_index = 0;
	public int last_key_id = 0;

	/**
	 * populate all ranks with correct order
	 */
	private void populateRanks() {
		// construct ranks

		// the number in ranks means the levels of that rank
		ranks.put("Family", 1);
		ranksList.add("Family");

		ranks.put("Subfamily", 2);
		ranksList.add("Subfamily");

		ranks.put("Tribe", 3);
		ranksList.add("Tribe");

		ranks.put("Genus", 4);
		ranksList.add("Genus");

		ranks.put("Section", 5);
		ranksList.add("Section");

		ranks.put("Species", 6);
		ranksList.add("Species");

		ranks.put("Subspecies", 7);
		ranksList.add("Subspecies");

		ranks.put("Variety", 7);
		ranksList.add("Variety");
	}

	public void getStarted() {
		populateRanks();

		// get input files
		File inputFolder = new File(inputFolderPath);
		if (inputFolder.isDirectory()) {
			File inputFiles[] = inputFolder.listFiles();
			for (File inputFile : inputFiles) {
				processFile(inputFile);
			}
		} else if (inputFolder.isFile()) {
			processFile(inputFolder);
		}
	}

	/**
	 * delete a folder and all its comtent
	 * 
	 * @param folder
	 */
	public void deleteFolder(File folder) {
		File[] files = folder.listFiles();
		if (files != null) { // some JVMs return null for empty dirs
			for (File f : files) {
				if (f.isDirectory()) {
					deleteFolder(f);
				} else {
					f.delete();
				}
			}
		}
		folder.delete();
	}

	/**
	 * create output folders
	 * 
	 * @param folderName
	 */
	private void createOutputFolders() {
		String path = this.outputFolderPath + "\\" + this.outputMainFolderName;
		File folder = new File(path);
		// delete old content
		if (folder.exists()) {
			deleteFolder(folder);
		}

		File mainfolder = new File(path);
		mainfolder.mkdir();

		File taxonFolder = new File(path + "\\" + this.outputTaxonFolderName);
		taxonFolder.mkdir();

		File keyFolder = new File(path + "\\" + this.outputKeyFolderName);
		keyFolder.mkdir();
	}

	/**
	 * get the first valid run: the first set of text
	 * 
	 * @param para
	 * @return
	 */
	private XWPFRun getFirstValidRun(XWPFParagraph para) {
		List<XWPFRun> runs = para.getRuns();
		XWPFRun first_valid_run = runs.get(0);
		for (int i = 0; i < runs.size(); i++) {
			XWPFRun run = runs.get(i);
			if (run == null) {
				continue;
			}

			if (run.getText(0) == null || run.getText(0).trim().equals("")
					|| run.getText(0).equals("•") || run.getText(0).equals("●")
					|| run.getText(0).equals("●")) {
				continue;
			}

			first_valid_run = run;
			break;
		}
		return first_valid_run;
	}

	/**
	 * get the next run of the given run
	 * 
	 * @param para
	 * @param run
	 * @return
	 */
	private XWPFRun getNextRun(XWPFParagraph para, XWPFRun run) {
		XWPFRun nextRun = null;
		List<XWPFRun> runs = para.getRuns();
		boolean gotTheOne = false;
		for (XWPFRun eachRun : runs) {
			if (gotTheOne) {
				return eachRun;
			}
			if (eachRun.equals(run)) {
				gotTheOne = true;
			}
		}

		return nextRun;
	}

	/**
	 * check key title pattern
	 * 
	 * @param text
	 * @return
	 */
	private boolean isKeyTitle(String text) {
		boolean isKeyTitie = false;
		// volume 15
		if (text.startsWith("Key ")) {
			return true;
		}

		// volume 16
		Pattern p = Pattern.compile("^[IVX]+\\.\\s+Sect\\.\\s+[A-Z][a-z]+$");
		Matcher m = p.matcher(text.trim());
		if (m.matches()) {
			return true;
		}
		return isKeyTitie;
	}

	/**
	 * in volume 16, key part for sections
	 * 
	 * @param para
	 * @return
	 */
	private boolean isKeyTitle(XWPFParagraph para) {
		boolean isKeyTitie = false;
		// volume 15
		if (para.getText().trim().startsWith("Key ")) {
			return true;
		}

		// volume 16
		if (para.getCTP() != null && para.getCTP().getPPr() != null
				&& para.getCTP().getPPr().getPStyle() != null) {
			String p_style = para.getCTP().getPPr().getPStyle().getVal()
					.toString().toLowerCase();
			if (p_style.startsWith("sect-1")) {
				// format check
				Pattern p = Pattern
						.compile("^[IVX]+\\.\\s+Sect\\.\\s+[A-Z][a-z]+$");
				Matcher m = p.matcher(para.getText().trim());
				if (m.matches()) {
					return true;
				}
			}
		}
		return isKeyTitie;
	}

	/**
	 * taxon title is bold and not start with "Key"
	 * 
	 * @param para
	 * @return
	 */
	private boolean isTaxonTitle(XWPFParagraph para) {
		boolean isTaxon = false;
		XWPFRun first_valid_run = getFirstValidRun(para);
		if (first_valid_run.getFontSize() == 8) {
			return false;
		}

		// volume 15, p_style has specific value: FAM-1, Fam-10, GEN-1, Gen-01,
		// Gen-10, SPEC-01, SECT-1
		if (para.getCTP() != null && para.getCTP().getPPr() != null
				&& para.getCTP().getPPr().getPStyle() != null) {
			String p_style = para.getCTP().getPPr().getPStyle().getVal()
					.toString().toLowerCase();
			if (p_style.startsWith("fam") || p_style.startsWith("gen")
					|| p_style.startsWith("spec-")
					|| p_style.startsWith("sect")) {
				return true;
			}
		}

		// volume 15, isbold
		if (first_valid_run.isBold()
				&& para.getText().toLowerCase().indexOf("key") < 0) {
			return true;
		}

		if (first_valid_run.getCTR() != null) {
			if (first_valid_run.getCTR().getRPr() != null) {
				boolean is_rStyle_bold = false;

				if (first_valid_run.getCTR().getRPr().getRStyle() != null) {
					String rStyle = first_valid_run.getCTR().getRPr()
							.getRStyle().getVal();
					if (rStyle.equals("bold")) {
						is_rStyle_bold = true;
					}
				}

				if (is_rStyle_bold) {
					if (first_valid_run.getCTR().getRPr().getB() != null) {
						if (first_valid_run.getCTR().getRPr().getB().getVal()
								.intValue() == 0) {
							return false;
						}
					} else {
						return true;
					}
				} else {
					return false;
				}
			}
		}

		// no need to use heading since we can use rStyle to find out bold
		// String style = para.getStyle();
		// if (style != null && style.toLowerCase().contains("heading")) {
		// return true;
		// }

		return isTaxon;
	}

	/**
	 * start with Chinese characters, font info: run.getFontFamily(); either 宋体
	 * or Arial Unicode MS
	 * 
	 * @param para
	 * @return
	 */
	private boolean isCommonName(XWPFParagraph para) {
		boolean isCommonName = false;
		XWPFRun first_valid_run = getFirstValidRun(para);

		if (first_valid_run.getFontFamily() != null
				&& (first_valid_run.getFontFamily().equals("宋体") || first_valid_run
						.getFontFamily().equals("Arial Unicode MS"))) {
			isCommonName = true;
		}
		return isCommonName;
	}

	/**
	 * description: alignment: both; font-size: 9; first run: not bold, not
	 * italic
	 * 
	 * @param para
	 * @return
	 */
	private boolean isDescription(XWPFParagraph para, Taxon_foc taxon) {
		boolean isDescription = false;
		// volume 16: p_style = "BODY-1" or "Body-10"
		if (para.getCTP() != null && para.getCTP().getPPr() != null
				&& para.getCTP().getPPr().getPStyle() != null) {
			String p_style = para.getCTP().getPPr().getPStyle().getVal()
					.toString();
			if (p_style.toLowerCase().contains("body-1")) {
				return true;
			}
		}

		// volume 15: font size = 9 and alignment = both
		// if (taxon.getDescriptions() != null
		// && taxon.getDescriptions().size() > 0) {
		// return false;
		// }

		XWPFRun first_valid_run = getFirstValidRun(para);
		if (!first_valid_run.isBold() && !first_valid_run.isItalic()
				&& first_valid_run.getFontSize() == 9
				&& para.getAlignment().toString().equals("BOTH")) {
			isDescription = true;
		}

		return isDescription;
	}

	/**
	 * family name info is separated from family name title
	 * 
	 * @param para
	 * @param taxon
	 * @return
	 */
	private boolean ifFamilyAuthor(XWPFParagraph para, Taxon_foc taxon) {
		boolean isFamilyNameInfo = false;
		if (taxon.getRank().equals("Family")) {
			if (para.getAlignment() != null) {
				if (para.getAlignment().toString().equals("CENTER")) {
					XWPFRun first_valid_run = getFirstValidRun(para);
					if (first_valid_run != null
							&& first_valid_run.getFontSize() == 9) {
						return true;
					}
				} else {
					return false;
				}
			}
		} else {
			return false;
		}
		return isFamilyNameInfo;
	}

	/**
	 * first valid run or second valid run: italic
	 * 
	 * @param para
	 * @return
	 */
	private boolean isSynonym(XWPFParagraph para) {
		// any part is italic will do
		boolean isSynonym = false;
		XWPFRun first_valid_run = getFirstValidRun(para);

		// volume 16: p_stype = BODY-1 or Body-10
		boolean isBody_1 = false;
		if (para.getCTP() != null && para.getCTP().getPPr() != null
				&& para.getCTP().getPPr().getPStyle() != null) {
			String p_style = para.getCTP().getPPr().getPStyle().getVal()
					.toString();
			if (p_style.toLowerCase().contains("body-1")) {
				isBody_1 = true;
			}
		}

		// volume 15: font-size = 9
		if (isBody_1 || first_valid_run.getFontSize() == 9) {
			if (first_valid_run.isItalic()) {
				return true;
			}

			if (first_valid_run.getCTR() != null) {
				if (first_valid_run.getCTR().getRPr() != null) {
					if (first_valid_run.getCTR().getRPr().getRStyle() != null) {
						String rStyle = first_valid_run.getCTR().getRPr()
								.getRStyle().getVal();
						if (rStyle.equals("italic")) {
							return true;
						}
					}
				}
			}
		}

		return isSynonym;
	}

	/**
	 * the para is footnote
	 * 
	 * @param para
	 * @return
	 */
	private boolean isFootnote(XWPFParagraph para) {
		boolean isFootnote = false;
		if (para.getCTP() != null) {
			if (para.getCTP().getPPr() != null) {
				if (para.getCTP().getPPr().getPStyle() != null) {
					if (para.getCTP().getPPr().getPStyle().getVal()
							.contains("Footnote")) {
						return true;
					}
				}
			}
		}

		// volume 16: first run: size = 16 and vertAlign = superscript
		// second run: size = 16
		List<XWPFRun> runs = para.getRuns();
		if (runs.size() >= 2) {
			XWPFRun first_run = runs.get(0);
			if (first_run.getCTR() != null
					&& first_run.getCTR().getRPr() != null
					&& first_run.getCTR().getRPr().getVertAlign() != null
					&& first_run.getCTR().getRPr().getVertAlign().getVal()
							.toString().equals("superscript")) {
				XWPFRun second_run = runs.get(1);
				if (second_run.getCTR() != null
						&& second_run.getCTR().getRPr() != null
						&& second_run.getCTR().getRPr().getSz() != null
						&& second_run.getCTR().getRPr().getSz().getVal()
								.toString().equals("16")) {
					return true;
				}
			}
		}

		return isFootnote;
	}

	/**
	 * the first para of font-size = 8
	 * 
	 * @param para
	 * @param taxon
	 * @return
	 */
	private boolean isDistribution(XWPFParagraph para, Taxon_foc taxon) {
		// if (taxon.getRank().equals("Family") ||
		// taxon.getRank().equals("Genus")) {
		// return false;
		// }
		boolean isDistribution = false;
		if (taxon.getHab_ele_dis() != null
				&& !taxon.getHab_ele_dis().equals("")) {
			return false;
		}

		// volume 16: p_style = "BODY-2" or Body-20
		if (para.getCTP() != null && para.getCTP().getPPr() != null
				&& para.getCTP().getPPr().getPStyle() != null) {
			String p_style = para.getCTP().getPPr().getPStyle().getVal()
					.toString();
			if (p_style.toLowerCase().contains("body-2")) {
				return true;
			}
		}

		// volume 15: size = 8
		XWPFRun first_valid_run = getFirstValidRun(para);
		if (first_valid_run.getFontSize() == 8) {
			isDistribution = true;
		}

		return isDistribution;
	}

	/**
	 * the end of a key statement may look like a start, this function is to
	 * distinguish the start from an end
	 * 
	 * @param para
	 * @return
	 */
	private boolean isKeyStatementEndLine(XWPFParagraph para) {
		boolean isKeyEndLine = false;
		String text = para.getText();
		// has at least one leading tab/space
		// possible index
		// name part: 4 possibilities
		// possible brackets
		// possible ending space
		Pattern p = Pattern.compile("(\\\t|\\s)+(\\d+[a-z]?\\.\\s)?"
				+
				// name part
				"(" + "[A-Z](\\.\\s)?[a-z-]+" + "|(var|subsp)\\.\\s[a-z-]+"
				+ "|Sect\\.\\s[IVX]+\\.\\s[A-Z][a-z-]+" + ")" +
				// brackets part
				"(\\s\\([^\\(\\)]+\\))?\\s?$", Pattern.DOTALL);
		Matcher m = p.matcher(text);
		if (m.matches()) {
			return true;
		}

		return isKeyEndLine;
	}

	/**
	 * is a start of a key line, check the pattern of para start
	 * 
	 * @param para
	 * @return
	 */
	private boolean isKeyLine(XWPFParagraph para) {
		boolean isKeyLine = false;

		if (isKeyStatementEndLine(para)) {
			return false;
		}

		Pattern p = Pattern.compile("^\\d+[a-z]?\\.(\\\t|\\s).+$",
				Pattern.DOTALL);

		String text = para.getText().replaceAll("\\\n", "").trim();
		Matcher m = p.matcher(text);
		if (m.matches()) {
			isKeyLine = true;
		}

		return isKeyLine;
	}

	private void addToDescription(Taxon_foc taxon, String des) {
		taxon.setDescriptions(addToArrayList(taxon.getDescriptions(), des));
	}

	private void addToDiscussion(Taxon_foc taxon, String discussion) {
		taxon.setDiscussions(addToArrayList(taxon.getDiscussions(), discussion));
	}

	private ArrayList<String> addToArrayList(ArrayList<String> targetList,
			String text) {
		if (targetList == null) {
			targetList = new ArrayList<String>();
		}

		targetList.add(text);
		return targetList;
	}

	/**
	 * process the name line: get name_info, taxon_name, index, rank, heirarchy
	 * 
	 * @param para
	 * @param taxon
	 */
	private void processNameLine(XWPFParagraph para, Taxon_foc taxon) {
		// entire line is name_info
		String text = para.getText().trim();
		text = text.replaceAll("\n", " ");
		taxon.setName_info(text);

		String rank = "";

		// if couldnot get the bold part, use heading and alignment as backup
		// plan
		if (text.matches("^[A-Z-]+$")) { // family
			rank = "Family";
			taxon.setTaxon_name(text);
			taxon.setTaxon_index("");
		} else {
			Pattern p = Pattern.compile(
					"^(\\d+[a-z]?)\\.\\s*([A-Z-]+)\\s(.*)$", Pattern.DOTALL);
			Matcher m = p.matcher(text);
			if (m.matches()) { // genus
				rank = "Genus";
				taxon.setTaxon_index(m.group(1));
				taxon.setTaxon_name(m.group(2));
			} else {
				p = Pattern.compile(
						"^([IXV]+)\\.\\s+(Sect\\.\\s+[A-Za-z-]+)\\s(.*)$",
						Pattern.DOTALL);
				m = p.matcher(text);
				if (m.matches()) {
					rank = "Section";
					taxon.setTaxon_index(m.group(1));
					taxon.setTaxon_name(m.group(2));
				} else {
					p = Pattern
							.compile(
									"^(\\d+[a-z]?)\\.\\s*(.*(\\ssubsp\\.\\s|\\svar\\.\\s)\\s?[a-z-]+)(\\s.*)?$",
									Pattern.DOTALL);
					m = p.matcher(text);
					if (m.matches()) {// subspecies or variety
						if (text.indexOf("subsp.") > 0) {
							rank = "Subspecies";
						} else if (text.indexOf("var.") > 0) {
							rank = "Variety";
						}
						taxon.setTaxon_index(m.group(1));
						taxon.setTaxon_name(m.group(2));
					} else { // species
						p = Pattern
								.compile(
										"^(\\d+[a-z]?)\\.\\s*([A-Za-z-]+\\s{1,2}[a-z-×]+)\\s(.+)$",
										Pattern.DOTALL);
						m = p.matcher(text);
						if (m.matches()) {
							rank = "Species";
							taxon.setTaxon_index(m.group(1));
							taxon.setTaxon_name(m.group(2));
						} else { // undetermined
							rank = "undetermined";
							System.out.println("ERROR processing name line");
						}
					}
				}
			}
		}

		taxon.setRank(rank);
		if (!rank.equals("undetermined")) {
			updatePriorRanks(rank, taxon.getTaxon_name());
			taxon.setHierarchical_name(getHierarchy(rank));
		}
	}

	private void updatePriorRanks(String rank, String taxon_name) {
		// clear all equal and lower saved ranks to rank
		Enumeration<String> savedRanks = priorRanks.keys();
		while (savedRanks.hasMoreElements()) {
			String savedRank = savedRanks.nextElement();
			if (ranks.get(rank) != null && ranks.get(savedRank) != null) {
				if (ranks.get(savedRank) >= ranks.get(rank)) {
					priorRanks.remove(savedRank);
				}
			}
		}

		// put rank in
		priorRanks.put(rank, taxon_name);
	}

	private void addToLastPara(Taxon_foc taxon, String lastParaStatus,
			String text) {
		switch (lastParaStatus) {
		case "common_name":
			taxon.setCommon_name(taxon.getCommon_name() + " " + text);
			break;
		case "author":
			taxon.setAuthor(taxon.getAuthor() + " " + text);
			break;
		case "synonym":
			taxon.setSynonym(taxon.getSynonym() + " " + text);
			break;
		case "description":
			ArrayList<String> dess = taxon.getDescriptions();
			if (dess.size() > 0) {
				dess.set(dess.size() - 1, dess.get(dess.size() - 1) + " "
						+ text);
			} else {
				System.out.println("ERROR: no description");
			}
			break;
		case "distribution":
			taxon.setHab_ele_dis(taxon.getHab_ele_dis() + " " + text);
			break;
		case "discussion":
			ArrayList<String> discussions = taxon.getDiscussions();
			if (discussions.size() > 0) {
				discussions.set(discussions.size() - 1,
						discussions.get(discussions.size() - 1) + " " + text);
			} else {
				System.out.println("ERROR: no discussion");
			}
			break;
		default:
			System.out.println("ERROR: no last status");
			break;
		}
	}

	/**
	 * process taxon by file
	 */
	protected void processFile(File inputFile) {
		if (inputFile != null) {
			// create output folder
			this.source = inputFile.getName();
			this.outputMainFolderName = inputFile.getName()
					.substring(0, inputFile.getName().lastIndexOf("."))
					.replaceAll("[^\\w]", "_");
			createOutputFolders();

			// start to extract taxon and key files
			ArrayList<Taxon_foc> taxonList = new ArrayList<Taxon_foc>();
			ArrayList<KeyFile> keyFiles = new ArrayList<KeyFile>();
			this.keyFileNumber = 0;
			this.taxonFileNumber = 0;
			try {
				FileInputStream fileIS = new FileInputStream(inputFile);

				XWPFDocument wordDoc = new XWPFDocument(fileIS);

				List<XWPFParagraph> paragraphs = wordDoc.getParagraphs();

				Taxon_foc taxon = null;
				ArrayList<String> keyParas = new ArrayList<>();
				boolean keyFileStarted = false;

				String lastParaStatus = "";
				String lastPara = "";
				for (XWPFParagraph para : paragraphs) {
					if (isFootnote(para)) {
						continue;
					}

					if (para.getRuns().size() < 1) {
						continue;
					}

					if (para.getText() != null
							&& (para.getText().trim().matches("^_+$") || para
									.getText().trim().equals(""))) {
						continue;
					}

					// key title for volume 16
					if (isKeyTitle(para)) {
						// add into keyParas
						if (keyFileStarted) {
							lastParaStatus = "";
							lastPara = "";
							addToKeyParas(keyParas, para);
							continue;
						}
					}

					// if is taxon name (bold, and not start with 'Key')
					if (isTaxonTitle(para)) {
						// process last taxon and key file
						if (keyParas.size() > 0) {
							processKeyFile(keyParas, taxon, keyFiles);
							// keyFiles.add(kf);
						}

						// taxon must be behind key file because generateKeyFile
						// will modify taxon
						if (taxon != null) {
							taxonList.add(taxon);
						}

						// reset key related variables
						keyParas = new ArrayList<>();
						keyFileStarted = false;
						lastParaStatus = "";
						lastPara = "";

						// create next taxon obj and key file obj
						this.taxonFileNumber++;
						taxon = new Taxon_foc(this.taxonFileNumber);

						// for name line, process rank, hierarchy , name_info
						processNameLine(para, taxon);

						continue;
					}

					// volume 16: key title

					if (keyFileStarted) {
						lastParaStatus = "";
						lastPara = "";
						addToKeyParas(keyParas, para);
					} else {
						if (isKeyLine(para)) {
							lastParaStatus = "";
							lastPara = "";
							keyFileStarted = true;
							addToKeyParas(keyParas, para);
							continue;
						}

						String text = para.getText();
						text = text.replaceAll("\n", " ").replaceAll("\t", " ")
								.trim();
						if (text.matches("^[a-z-].*$")) {
							addToLastPara(taxon, lastParaStatus, text);
							lastPara = text;
							continue;
						}

						if (lastPara.trim().matches("^.*[–,]$")) {
							addToLastPara(taxon, lastParaStatus, text);
							lastPara = text;
							continue;
						}

						if (lastPara.trim().matches("^.*[a-z]$")
								&& text.startsWith("(")) {
							addToLastPara(taxon, lastParaStatus, text);
							lastPara = text;
							continue;
						}
						lastPara = text;

						if (isCommonName(para)) {
							lastParaStatus = "common_name";
							taxon.setCommon_name(para.getText());
							continue;
						}

						if (ifFamilyAuthor(para, taxon)) {
							lastParaStatus = "author";
							taxon.setAuthor(para.getText());
							continue;
						}

						if (isSynonym(para)) {
							lastParaStatus = "synonym";
							taxon.setSynonym(para.getText());
							continue;
						}

						if (isDescription(para, taxon)) {
							lastParaStatus = "description";
							addToDescription(taxon, para.getText());
							continue;
						}

						if (isDistribution(para, taxon)) {
							lastParaStatus = "distribution";
							taxon.setHab_ele_dis(para.getText());
							continue;
						}

						addToDiscussion(taxon, para.getText());
						lastParaStatus = "discussion";
					}
				}

				if (keyParas.size() > 0) {
					processKeyFile(keyParas, taxon, keyFiles);
					// keyFiles.add(kf);
				}

				// taxon must be behind key file because generateKeyFile
				// will modify taxon
				if (taxon != null) {
					taxonList.add(taxon);
				}

				outputTaxons(taxonList);
				outputKeyFiles(keyFiles);

				System.out.println("taxons and keys generated");

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String removeIndex(String title) {
		String rv = title;
		Pattern p = Pattern.compile("^" + p_key_line + ".+$", Pattern.DOTALL);
		Matcher mt = p.matcher(title);
		if (mt.matches()) {
			return mt.group(2);
		}
		return rv.trim();
	}

	/**
	 * removing leading tabs for key part
	 * 
	 * @param text
	 * @return
	 */
	private String removeLeadingTab(XWPFParagraph para) {
		// if is key statement end line, do not remove all the leading tabs
		if (isKeyStatementEndLine(para)) {
			return para.getText();
		}

		String text = para.getText();
		// leading line change or space
		Pattern p = Pattern.compile("^(\\\n\\s?)+(.*)$", Pattern.DOTALL);
		Matcher m = p.matcher(text);
		if (m.matches()) {
			text = m.group(2);
		}

		p = Pattern.compile("^(\\\t)+(\\\t\\s.*)$", Pattern.DOTALL);
		m = p.matcher(text);
		if (m.matches()) {
			text = m.group(2);
		} else {
			p = Pattern.compile("^(\\\t)+(.*)$", Pattern.DOTALL);
			m = p.matcher(text);
			if (m.matches()) {
				text = m.group(2);
			}
		}
		return text;
	}

	private void addToKeyParas(ArrayList<String> keyParas, XWPFParagraph para) {
		if (isKeyTitle(para) || isKeyLine(para)) {
			keyParas.add(para.getText().trim());
		} else {
			String text = removeLeadingTab(para);
			keyParas.set(keyParas.size() - 1, keyParas.get(keyParas.size() - 1)
					+ " " + text);
		}
	}

	public void processKeyFile(ArrayList<String> keyParas, Taxon_foc taxon,
			ArrayList<KeyFile> keyFiles) {
		String key_heading = "Key of " + taxon.getTaxon_name();
		this.keyFileNumber++;
		KeyFile kf = new KeyFile(key_heading, this.keyFileNumber);
		taxon.setKey_file_name(kf.getFileName());

		// process statement by statement
		ArrayList<KeyStatement> kss = new ArrayList<>();
		for (int i = 0; i < keyParas.size(); i++) {
			String keyPara = keyParas.get(i);

			if (isKeyTitle(keyPara)) {
				// add in last key file
				keyFiles.add(kf);

				// new key file
				this.keyFileNumber++;
				key_heading = "Key of " + keyPara;
				kf = new KeyFile(key_heading, this.keyFileNumber);
				kss = new ArrayList<>();
			} else {
				KeyStatement ks = new KeyStatement();

				ks.setId(keyPara.substring(0, keyPara.indexOf(".")));

				String restPart = keyPara.substring(keyPara.indexOf(".") + 1,
						keyPara.length());
				ks.setStatement(restPart);
				// process determination
				Pattern p = Pattern
						.compile("^(.+)(\\\t)+(.+)$", Pattern.DOTALL);
				Matcher m = p.matcher(restPart);
				if (m.matches()) {
					ks.setStatement(m.group(1));
					ks.setDetermination(m.group(3));
				} else {
					if (keyParas.size() - 1 > i) {
						String nextStatement = keyParas.get(i + 1);
						ks.setNext_id(nextStatement.substring(0,
								nextStatement.indexOf(".")));
					}
				}

				kss.add(ks);
				kf.setStatements(kss);
			}
		}

		// add into keyFiles
		keyFiles.add(kf);
	}

	public KeyFile processKeyStatement(String text, KeyFile kf) {
		if (kf == null) {
			kf = new KeyFile();
		}

		text = text.trim();
		KeyStatement ks = new KeyStatement();
		ArrayList<KeyStatement> kss = kf.getStatements();

		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_line);
			Matcher mt = p.matcher(text);
			String id = "";
			if (mt.matches()) {
				id = mt.group(1);
				ks.setId(id);
				ks.setStatement(text);
			}

			p = Pattern.compile(this.p_key_tail);
			mt = p.matcher(text);
			if (mt.matches()) {
				ks.setDetermination(mt.group(1));
			}

			// update the next_id of last ks
			if (!id.equals("")) {
				if (kss.size() > 0) {
					KeyStatement last_ks = kss.get(kss.size() - 1);
					if (last_ks.getDetermination() == null
							|| last_ks.getDetermination().equals("")) {
						last_ks.setNext_id(id);
						kss.set(kss.size() - 1, last_ks);
					}
				}
			} else {
				System.out.println("~~Error~~");
			}

			// update kf
			kss.add(ks);
			kf.setStatements(kss);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return kf;
	}

	/**
	 * last_ks_index last_ks_id
	 * 
	 * @param text
	 * @return
	 */
	public KeyFile processKeyStatement_withFromID(String text, KeyFile kf) {
		text = text.trim();
		KeyFile KF = kf;
		try {
			String myID = "";
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_line);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				// ks.setId(mt.group(1));
				myID = mt.group(1);
			}

			KeyChoice kc = new KeyChoice();
			kc.setStatement(text);
			p = Pattern.compile(this.p_key_tail);
			mt = p.matcher(text);
			if (mt.matches()) {
				kc.setDetermination(mt.group(1));
			}

			// find existing key statement
			KeyStatement ks = null;
			int ks_index = -1;
			ArrayList<KeyStatement> kss = kf.getStatements();

			// update the next_id of last ks
			if (Integer.parseInt(myID) > 1) {
				KeyStatement ksToUpdate = kss.get(last_ks_index);
				ArrayList<KeyChoice> kcs = ksToUpdate.getChoices();
				for (int i = 0; i < kcs.size(); i++) {
					KeyChoice kcc = kcs.get(i);
					String next_id = kcc.getNext_id();
					if (kcc.getDetermination() != null) {
						continue;
					}
					if (next_id == null || next_id.equals("")) {
						kcc.setNext_id(myID);
						kcs.set(i, kcc);
						ksToUpdate.setChoices(kcs);
						kss.set(last_ks_index, ksToUpdate);
						KF.setStatements(kss);
						break;
					}
				}
			}

			kss = kf.getStatements();
			for (int i = 0; i < kss.size(); i++) {
				KeyStatement k = kss.get(i);
				if (k.getId().equals(myID)) {
					ks = k;
					ks_index = i;
					break;
				}
			}

			// set from id
			if (ks == null) {// new ks, new choice
				ks = new KeyStatement();
				ks.setId(myID);
				if (last_key_id > 0) {
					ks.setFrom_id(Integer.toString(last_key_id));
				}

			}
			ks.setChoices(addKeyChoiceToArr(ks.getChoices(), kc));

			if (ks_index > -1) {
				kss.set(ks_index, ks);
				last_ks_index = ks_index;
			} else {
				kss.add(ks);
				last_ks_index = kss.size() - 1;
			}
			KF.setStatements(kss);
			last_key_id = Integer.parseInt(myID);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return KF;
	}

	public KeyStatement createKeyStatement(String line) {
		KeyStatement ks = new KeyStatement();
		if (line.matches("^\\d+\\(.+")) {// has from id
			ks.setId(line.substring(0, line.indexOf("(")));
			ks.setFrom_id(line.substring(line.indexOf("(") + 1,
					line.indexOf(")")));
		} else {
			ks.setId(line.substring(0, line.indexOf(".")));
			ks.setFrom_id("");
		}
		return ks;
	}

	public void updateLastKeyStatement(KeyFile kf, String line) {
		ArrayList<KeyStatement> kss = kf.getStatements();
		if (kss == null || kss.size() < 1) {
			System.out.println("KSS is null");
		}
		KeyStatement last_ks = kss.get(kss.size() - 1);

		KeyChoice kc = new KeyChoice();
		kc.setStatement(line);
		Pattern p = Pattern.compile("^.+\\.{2,}\\s(\\d+)$");
		Matcher mt = p.matcher(line);
		if (mt.matches()) {
			kc.setNext_id(mt.group(1));
		} else {
			kc.setDetermination(line.substring(line.lastIndexOf("..") + 2,
					line.length()).trim());
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
			} else {
				break;
			}
		}
		return hierarchy;
	}

	public ArrayList<String> addLineToArr(ArrayList<String> originalList,
			String line) {
		ArrayList<String> rv = originalList;
		rv.add(line);
		return rv;
	}

	public ArrayList<KeyStatement> addKeyStatemenToArr(
			ArrayList<KeyStatement> originalList, KeyStatement ks) {
		ArrayList<KeyStatement> rv = originalList;
		rv.add(ks);
		return rv;
	}

	public ArrayList<KeyChoice> addKeyChoiceToArr(
			ArrayList<KeyChoice> originalList, KeyChoice kc) {
		ArrayList<KeyChoice> rv = originalList;
		rv.add(kc);
		return rv;
	}

	public Hashtable<String, String> addToHashTable(String key, String value,
			Hashtable<String, String> ht) {
		if (!key.equals("") && ht.get(key) == null) {
			ht.put(key, value);
		}
		return ht;
	}

	public String appendLine(String originalLine, String line) {
		return originalLine + " " + line;
	}

	/**
	 * Pattern: part 1: Abc | A. (Abc) part 2: (A+all kinds of characters) part
	 * 3: NAME'SGEW &|and NAME2'SE &|and NAME3 part 4: in PUBLICATION part 5:
	 * year: 1885 1885b part 6: page number: p. [0-9]+
	 * 
	 * @param text
	 *            : the text before [
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
				if (commaIndex > leftBracketIndex
						&& commaIndex < rightBracketIndex) {
					String rest = text.substring(rightBracketIndex + 1,
							text.length()).trim();
					name = text.substring(0, rightBracketIndex + 1) + " "
							+ rest.substring(0, rest.indexOf(","));
				}
			} else {
				name = text;
			}
		}
		return name;
	}

	protected void outputTaxons(ArrayList<Taxon_foc> taxons) throws Exception {
		for (Taxon_foc taxon : taxons) {
			// write xml file
			outputXMLFile(taxon);
			// add record to database: params: filename, name, hierarchy

		}
	}

	public void outputKeyFiles(ArrayList<KeyFile> kfs) {
		for (KeyFile kf : kfs) {
			outputKeyFile(kf);
		}
	}

	private void outputXMLFile(Taxon_foc taxon) {
		try {
			// print out information for review
			System.out.println("--" + taxon.getOutput_file_name());
			System.out.println("  name - " + taxon.getTaxon_name());
			System.out.println("  rank - " + taxon.getRank());
			System.out.println("  hierarchy - " + taxon.getHierarchical_name());
			System.out.println("  name_info - " + taxon.getName_info());
			System.out.println("  common name - " + taxon.getCommon_name());
			System.out.println("  distribution - " + taxon.getHab_ele_dis());
			ArrayList<String> dess = taxon.getDescriptions();
			if (dess != null && dess.size() > 0) {
				for (int i = 0; i < dess.size(); i++) {
					System.out.println("  des" + i + " = " + dess.get(i));
				}
			} else {
				System.out.println("	no description");
			}

			ArrayList<String> discussions = taxon.getDiscussions();
			if (discussions != null && discussions.size() > 0) {
				for (int i = 0; i < discussions.size(); i++) {
					System.out.println("  discussion" + i + " = "
							+ discussions.get(i));
				}
			} else {
				System.out.println("	no discussion");
			}

			System.out.println("  key = " + taxon.getKey_file_name());

			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);

			// populate meta
			addElement(meta, this.source, "source");

			// nomenclature
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);

			// add name
			Element name = new Element("name");
			name.setText(taxon.getTaxon_name());
			nomen.addContent(name);

			// add index
			addElement(nomen, taxon.getTaxon_index(), "index");

			// add author
			addElement(nomen, taxon.getAuthor(), "author");

			// add name_info
			addElement(nomen, taxon.getName_info(), "name_info");

			// add rank
			addElement(nomen, taxon.getRank(), "rank");

			// add common name
			addElement(nomen, taxon.getCommon_name(), "common_name");

			// add synonym
			addElement(nomen, taxon.getSynonym(), "synonym");

			// add hierarchy
			addElement(nomen, taxon.getHierarchical_name(), "taxon_hierarchy");

			// add description
			addElements(root, taxon.getDescriptions(), "description");

			// add habitat_elevation_distribution
			addElement(root, taxon.getHab_ele_dis(),
					"habitat_elevation_distribution");

			// add discussions
			addElements(root, taxon.getDiscussions(), "discussion");

			// add key file name
			addElement(root, taxon.getKey_file_name(), "key_file");

			// output xml file
			File f = new File(outputFolderPath + "\\" + outputMainFolderName
					+ "\\" + outputTaxonFolderName, taxon.getOutput_file_name()
					+ ".xml");
			org.jdom.output.Format format = org.jdom.output.Format
					.getPrettyFormat();
			format.setEncoding("UTF-8");
			XMLOutputter serializer = new XMLOutputter(format);
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void outputKeyFile(KeyFile keyfile) {
		try {
			System.out.println("--" + keyfile.getFileName());

			// create doc
			Element root = new Element("key");
			Document doc = new Document(root);

			addElement(root, keyfile.getHeading(), "key_heading");
			// addElements(root, keyfile.getDiscussion(), "key_discussion");

			ArrayList<KeyStatement> kss = keyfile.getStatements();
			for (KeyStatement ks : kss) {
				Element e_ks = new Element("key_statement");
				root.addContent(e_ks);

				addElement(e_ks, ks.getId(), "statement_id");
				// addElement(e_ks, ks.getFrom_id(), "statement_from_id");
				addElement(e_ks, ks.getStatement(), "statement");
				addElement(e_ks, ks.getDetermination(), "determination");
				addElement(e_ks, ks.getNext_id(), "next_statement_id");
			}

			// output xml file
			String filename = keyfile.getFileName();
			// filename = filename.replaceAll("[\\.\\s]", "");
			File f = new File(outputFolderPath + "\\" + outputMainFolderName
					+ "\\" + outputKeyFolderName, filename + ".xml");
			org.jdom.output.Format format = org.jdom.output.Format
					.getPrettyFormat();
			format.setEncoding("UTF-8");
			XMLOutputter serializer = new XMLOutputter(format);
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
					tagName = tagName.replaceAll("[^\\w]", "_");
					Element e = new Element(tagName);
					e.setText(str);
					parent.addContent(e);
				}
			}
		}
	}

	public void addElement(Element parent, String str, String tagName) {
		if (!(str == null) && !str.equals("")) {
			tagName = tagName.replaceAll("[^\\w]", "_");
			Element e = new Element(tagName);
			e.setText(str);
			parent.addContent(e);
		}
	}

	/**
	 * type 1: taxon xml <treatment> <meta><source/></meta> <nomenclature>
	 * <name/> <rank/> <taxon_hierarchy/> </nomenclature> <text/>
	 * <type_species/> <distribution/> <key/> </treatment>
	 * 
	 * type 2: key xml <key> <key_head/> <key_discussion/> <key_statement>
	 * <statement_id/> <statement/> <determination/> <next_statement_id/>
	 * </key_statement> </key>
	 * 
	 * @param taxon
	 */

}
