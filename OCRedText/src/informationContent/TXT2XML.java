package informationContent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import db.DatabaseAccessor;
import beans.Taxon;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

public class TXT2XML {

	/**
	 * @param args
	 */

	//private String folderpath = "E:\\work_data\\TREATISE_ON_INVERTEBRATE_PALEONTOLOGY";
	private String folderpath = "E:\\work_data\\TREATISE\\Treatises_txt_files";
	private String processing = "0"; // file index or "all"
	private File[] sourceFiles = null;
	private String outputPath = "E:\\work_data\\TREATISE\\";
	private String xmlFolerName = "Taxons\\";
	private String txtFolderName = "Descriptions\\";
	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	java.sql.Connection conn = null;
	protected static String url = ApplicationUtilities
			.getProperty("database.url");
	private String source = "";
	private String volume = "";
	private int fileCount = 0;
	private int taxonCount = 0;

	public static void main(String[] args) {
		TXT2XML tx = new TXT2XML();
		tx.ExtractTaxon();
		System.out.println("Convert finished");
	}

	/**
	 * constructing function: get txt files needs to process
	 */
	public TXT2XML() {
		// get txt files
		File sourceFolder = new File(folderpath);
		this.source = sourceFolder.getName();
		this.sourceFiles = sourceFolder.listFiles();

		// construct ranks
		ranks.put("Phylum", 1);
		ranks.put("Class", 2);
		ranks.put("Subclass", 3);
		ranks.put("Order", 4);
		ranks.put("Suborder", 5);
		ranks.put("Superfamily", 6);
		ranks.put("Family", 7);
		ranks.put("Subfamily", 8);
		ranks.put("Genus", 9);
		ranks.put("Subgenera", 10);
		ranks.put("Subgenus", 11);

		// get conn
		try {
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * process taxon by file
	 */
	protected void ExtractTaxon() {
		for (File eachTxtFile : sourceFiles) {
			this.fileCount = 0;
			this.taxonCount = 0;
			this.volume = eachTxtFile.getName().substring(0,
					eachTxtFile.getName().lastIndexOf("."));
			try {
				createOutputFolders(volume);
				FileInputStream fstream = new FileInputStream(eachTxtFile);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8"); 
				//DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				ArrayList<Taxon> taxonList = new ArrayList<Taxon>();
				while ((line = br.readLine()) != null) {
					line.trim();
					if (line.equals("")) {
						continue;
					}

					Pattern pattern = Pattern
							.compile(Patterns.taxonNamePattern);
					Matcher m = pattern.matcher(line);
					int firstBracket = line.indexOf("[");
					if (firstBracket > 0 && firstBracket < 300 && m.matches()) {
						//is a taxon 
						
						this.taxonCount++;
						String name = "", name_info = "", rest = "", description = "", other = "", rank = "", discussion = "";
						String tx_hierarchy = "";
						Integer rankNo = 0;
						boolean reachedEnd = true;

						// get rank first
						rank = line.substring(0, line.indexOf(" "));
						rankNo = ranks.get(rank);
						if (rankNo == null) {
							Pattern subGeneraPtn = Pattern
									.compile(Patterns.underGeneraPattern);
							Matcher m_subGenera = subGeneraPtn.matcher(line);
							if (m_subGenera.matches()) {
								if (line.matches(Patterns.subGenusPattern)) {
									rank = "Subgenus";
									rankNo = ranks.get(rank);
								} else {
									rank = "Subgenera";
									rankNo = ranks.get(rank);
								}
							} else {
								rank = "Genus";
								rankNo = ranks.get(rank);
							}
						}

						// get name, subgenera name or before first , name_info
						// before last ], rest: after name_info
						if (rankNo == ranks.get("Subgenera")) {
							Pattern p_s = Pattern
									.compile(Patterns.subGeneraPattern);
							Matcher m_s = p_s.matcher(line);
							if ((m_s).matches()) {
								name = m_s.group(1).trim();
								rest = m_s.group(2).trim();
								if (rest.indexOf("]") > 0) {
									int des_index = getDescriptionIndex(line);
									name_info = line.substring(0,
											des_index + 1).trim();
									rest = line.substring(
											des_index + 1,
											line.length()).trim();
								} else {
									name_info = name;
								}
							}
						} else {
							String beforeBracket = line.substring(0, firstBracket);
							name = getName(beforeBracket);	
							
							int des_index = getDescriptionIndex(line);
							name_info = line.substring(0,
									des_index + 1).trim();
							rest = line.substring(des_index + 1,
									line.length()).trim();
						}

						// get discription, rest after name_info; other_info:
						// from --Fig
						if (rest.length() > 0) {
							Pattern p_descrip = Pattern
									.compile("(.*?)(-\\s?-\\s?F\\s?[i|I|l]\\s?[g|G])(.*?)");
							Matcher m_descrip = p_descrip.matcher(rest);
							if (m_descrip.matches()) {
								description = m_descrip.group(1);
								other = m_descrip.group(2) + m_descrip.group(3);
							} else {
								description = rest;
								other = "";
							}
						} else {
							reachedEnd = false;
						}

						// get reached_end: if have rank, check -Xxxxx
						// (Geological time)
						if (reachedEnd) {
							reachedEnd = descriptionEnded(rankNo, line);
						}

						if (description.startsWith(".")) {
							if (description.length() > 1) {
								description = description.substring(1,
										description.length()).trim();
							} else {
								description = "";
							}
						}

						while (taxonList.size() > 0) {
							int lastRankNo = taxonList
									.get(taxonList.size() - 1).getRankNumber();
							if (lastRankNo >= rankNo) {
								outputTaxon(taxonList
										.get(taxonList.size() - 1));
								taxonList.remove(taxonList.get(taxonList
										.size() - 1));
							} else {
								break;
							}
						}

						// get taxon_hierarchy
						if (taxonList.size() > 0) {
							Taxon last_taxon = taxonList
									.get(taxonList.size() - 1);
							if (last_taxon.getTx_hierarchy().equals("")) {
								tx_hierarchy = last_taxon.getName();
							} else {
								tx_hierarchy = last_taxon.getTx_hierarchy()
										+ "; " + last_taxon.getName();
							}
						}

						Taxon taxon = new Taxon(name, name_info, rank, rankNo,
								description, other, reachedEnd, tx_hierarchy);
						taxon.setIndex(this.taxonCount);
						taxonList.add(taxon);
					} else { //not a taxon paragraph
						// add this line to previous
						if (taxonList.size() > 0) {
							updateTaxon(taxonList.get(taxonList.size() - 1),
									line);
						}
					}
				}

				for (Taxon taxon : taxonList) {
					outputTaxon(taxon);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
	
	protected int getDescriptionIndex(String line) {
		int index = 0;
		String rest = line ;
		while (true) {
			int i = rest.indexOf("]");
			index = index + 1 + i;
			if (index < 0) {
				break;
			}
			rest = rest.substring(i + 1, rest.length()).trim();
			if (!rest.startsWith("[")) {
				break;
			} 
		}
		return index;
	}
	
	protected void updateTaxon(Taxon taxon, String line) {
		// if reached end, add to discussion
/*		if (taxon.isReachedEnd()) {
			taxon.setDiscussion(taxon.getDiscussion() + " " + line);
		} else {
			// else, append to description
			taxon.setDescription(taxon.getDescription() + " " + line);
		}

		// check reached_end
		taxon.setReachedEnd(descriptionEnded(taxon.getRankNumber(), line));
*/		
		taxon.setDiscussion(taxon.getDiscussion() + " " + line);
	}

	protected boolean descriptionEnded(int rankNo, String line) {
		if (rankNo < ranks.get("Genus")) {
			Pattern p_time = Pattern.compile(Patterns.timePatterm);
			Matcher m_time = p_time.matcher(line);
			if (m_time.matches()) {
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	protected void outputTaxon(Taxon taxon) throws Exception {
		this.fileCount++;

		System.out.println(fileCount + ".xml: " + taxon.getName() + " ["
				+ taxon.getIndex() + "]");
		
		// write xml file
		outputXMLFile(taxon, this.fileCount);

		// write txt file
		outputTxtFile(taxon.getDescription(), fileCount);

		// add record to database: params: filename, name, hierarchy
		DatabaseAccessor.insertTaxonFileRelation(volume, taxon.getName(),
				taxon.getTx_hierarchy(), fileCount, conn);
	}

	private void outputTxtFile(String description, int fileCount) {
		try {
			FileOutputStream fos = new FileOutputStream(outputPath
					+ txtFolderName + volume + "\\" + fileCount + ".txt");
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			osw.write(description);
			osw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void outputXMLFile(Taxon taxon, int filecount) {
		try {
			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);
			// populate meta
			Element source = new Element("source");
			source.setText(this.source);
			Element e_volume = new Element("volume");
			e_volume.setText(this.volume);
			meta.addContent(source);
			meta.addContent(e_volume);

			// nomenclature
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);

			Element name = new Element("name");
			name.setText(taxon.getName());
			nomen.addContent(name);
			
			Element rank = new Element("rank");
			nomen.addContent(rank);
			rank.addContent(taxon.getRank());

			Element th = new Element("taxon_hierarchy");
			th.setText(taxon.getTx_hierarchy());
			nomen.addContent(th);

			Element name_info = new Element("name_info");
			nomen.addContent(name_info);
			name_info.setText(taxon.getName_info());


			// description
			Element description = new Element("description");
			root.addContent(description);
			description.setText(taxon.getDescription());

			// other_info
			Element other = new Element("other_info");
			root.addContent(other);
			other.setText(taxon.getOther());
			
			//discussion
			Element discusson = new Element("discussion");
			root.addContent(discusson);
			discusson.setText(taxon.getDiscussion());

			// output xml file
			File f = new File(outputPath + xmlFolerName + this.volume + "\\", filecount + ".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * create two folers: one for xml files, the other for txt files
	 * @param volume
	 * @return
	 */
	public boolean createOutputFolders(String volume){
		boolean rv = false;
		rv = new File(outputPath + xmlFolerName + volume).mkdir() && new File(outputPath + txtFolderName + volume).mkdir();
		return rv;
	}

}
