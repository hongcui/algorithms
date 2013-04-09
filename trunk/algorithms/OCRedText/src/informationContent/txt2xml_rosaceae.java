package informationContent;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import beans.KeyChoice;
import beans.KeyFile;
import beans.KeyStatement;
import beans.Taxon_bees;
import db.DatabaseAccessor;

public class txt2xml_rosaceae {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		txt2xml_rosaceae t = new txt2xml_rosaceae();
		t.generateTaxonXmls();
	}
	
	public String p_native = "^(NATURALIZED|NATIVE|UNABRIDGED|WAIF|JFP-4)$";
	public String p_key_line = "^(\\d+'?)\\.?\\s(.+)$";//1. Lvs
	//public String p_main_key_line = "^(\\d+)\\.\\s.+$";
	public String p_key_tail = "^.+\\.{3,}->(.+)$";
	public String p_marked_para = "^([A-Z0-9\\+\\s]+):(\\s.*)?$";
	public String p_descrip_tags = "|PISTILLATE FLOWER|TOXICITY|LEAF|CHROMOSOMES|FLOWER|" +
			"FRUIT|INFLORESCENCE|SEED|STEM|STAMINATE FLOWER|";
	
	//ARUNCUS dioicus (Walter) Fernald var. acuminatus
	// Ruiz & Pav.
	public String p_author_part = "(\\s\\(.+\\))?(\\s[A-Z][a-z]*\\.?)(\\s&\\s[A-Z][a-z]*\\.?)?";
	public String p_taxon_name = "[A-Z]+(\\s[a-z-]+(" + p_author_part +
			")?(\\s(var|subsp)\\.\\s[a-z]+)?)?";
		
	/*output xml files*/
	public String txtFile = "D:\\Work\\Data\\rosaceae\\rosaceae_for_Macklin.txt";
	public String keyFilesFolder = "D:\\Work\\Data\\rosaceae\\output\\keys\\";
	public String taxonFilesFolder = "D:\\Work\\Data\\rosaceae\\output\\taxons\\";			
	private File sourceFile = null;
	
	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	private ArrayList<String> ranksList = new ArrayList<>();
	private Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	private String volume = "";
	private int keyFileNumber = 0; 
	private int taxonFileNumber = 0;
	
	
	/*databases*/
	public Connection conn = null;
	public static String url = ApplicationUtilities
			.getProperty("database.url");
	public String cleanTableSurfix = "_clean_paragraphs";
	
	/*file process helpers*/
	public String prefix = "";
	public String source = "";
	public String fileName = "";
	
	public int last_ks_index = 0;
	public int last_key_id = 0;
	
	public void generateTaxonXmls() {
		// get txt files
		sourceFile = new File(this.txtFile);
		this.source = sourceFile.getName();

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
		Pattern p = Pattern.compile("^" + p_key_line + ".+$");
		Matcher mt = p.matcher(title);
		if (mt.matches()) {
			return mt.group(2);
		}
		return rv.trim();
	}
	
	public boolean isTaxonTitle(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_taxon_name);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public KeyFile processKeyStatement(String text, KeyFile kf) {
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
			
			//update the next_id of last ks
			if (!id.equals("")) {
				if (kss.size() > 0) {
					KeyStatement last_ks = kss.get(kss.size() - 1);
					if (last_ks.getDetermination() == null || last_ks.getDetermination().equals("")) {
						last_ks.setNext_id(id);
						kss.set(kss.size() - 1, last_ks);	
					}
				}	
			} else {
				System.out.println("~~Error~~");
			}
			
			//update kf
			kss.add(ks);
			kf.setStatements(kss);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return kf;
	}
	
	/**
	 * last_ks_index
	 * last_ks_id
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
				//ks.setId(mt.group(1));
				myID = mt.group(1);
			}
			
			KeyChoice kc = new KeyChoice();
			kc.setStatement(text);
			p = Pattern.compile(this.p_key_tail);
			mt = p.matcher(text);
			if (mt.matches()) {
				kc.setDetermination(mt.group(1));
			}
			
			//find existing key statement
			KeyStatement ks = null;
			int ks_index = -1;
			ArrayList<KeyStatement> kss = kf.getStatements();
			
			//update the next_id of last ks
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
			
			//set from id
			if (ks == null) {//new ks, new choice
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
	
	
	public boolean isNewTaxonStart(String text) {
		return isNative(text) || isTaxonTitle(text);
	}
	
	public boolean belongToDescrip(String tag) {
		return p_descrip_tags.indexOf("|" + tag + "|") > 0;
	}
	
	public boolean isNative(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_native);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isMarkedPara(String text) {
		text = text.trim();
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_marked_para);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public ArrayList<String> getParaTag(String text) {
		text = text.trim();
		ArrayList<String> rv = new ArrayList<>();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_marked_para);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				rv.add(mt.group(1));
				rv.add(mt.group(2) == null ? "" : mt.group(2));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return rv;
	}
	
	public boolean isKeyLine(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_key_line);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * process taxon by file
	 */
	protected void ExtractTaxon() {
		if (sourceFile != null) {
			this.source = sourceFile.getName();
			this.volume = sourceFile.getName().substring(0,
					sourceFile.getName().lastIndexOf(".")).replaceAll("[^\\w]", "_");
			this.keyFileNumber = 0;
			this.taxonFileNumber = 0;
			
			try {
				FileInputStream fstream = new FileInputStream(sourceFile);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8"); 
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				ArrayList<Taxon_bees> taxonList = new ArrayList<Taxon_bees>();
				ArrayList<KeyFile> keyFiles = new ArrayList<KeyFile>();
				
				Taxon_bees taxon = null;
				KeyFile keyfile = null;
				String nativeAttr = "";
				
				while ((line = br.readLine()) != null) {
					
					line = line.trim();
					if (line.equals("")) {
						continue;
					}
					
					System.out.println("Line: " + line);
					
					if (isNewTaxonStart(line)) {
						if (keyfile != null) {
							keyFiles.add(keyfile);
							keyfile = null;
						}
						
						if (taxon != null) {
							//add taxon to list
							taxonList.add(taxon);
							taxon = null;
						}
						
						if (isNative(line)) {
							if (nativeAttr.equals("")) {
								nativeAttr = line;
							} else {
								nativeAttr += ", " + line;	
							}
						} else {
							taxonFileNumber++;
							String taxonname = line;
							taxon = new Taxon_bees(taxonname, taxonFileNumber);
							String myRank = getRank(taxonname);
							taxon.setRank(myRank);	
							taxon.setNativeAttr(nativeAttr);
							nativeAttr = "";
							last_key_id = 0;
							last_ks_index = 0;
							//no hierarchy since rank is not accurate
							/*if (!myRank.equals("undertermined")) {
								priorRanks.put(myRank, taxonname);	
							}
							taxon.setHierarchy(getHierarchy(myRank));
						*/
						}
					
						continue;
					}
					
					if (isMarkedPara(line)) {
						ArrayList<String> para = getParaTag(line);
						String tag = para.get(0);
						String content = para.get(1);
						//update tags and paras
						if (taxon != null) {
							taxon.setTags(addLineToArr(taxon.getTags(), tag));
							taxon.setParas(addToHashTable(tag, content, taxon.getParas()));	
						} else {
							System.out.println("ERROR");
						}
						
					} else if (isKeyLine(line)){ // in key file
						if (keyfile == null) {
							keyFileNumber++;
							keyfile = new KeyFile("Key to " + taxon.getName(), keyFileNumber);
							//update taxon's key file
							taxon.setKeyFiles(addLineToArr(taxon.getKeyFiles(), keyfile.getFileName()));
						}
						
						//process key statement
						keyfile = processKeyStatement(line, keyfile);
					} else {
						System.out.println("!!! unexpected line !!!");
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
	
	protected void getTagsList() {
		if (sourceFile != null) {
			this.source = sourceFile.getName();
			this.volume = sourceFile.getName().substring(0,
					sourceFile.getName().lastIndexOf(".")).replaceAll("[^\\w]", "_");
			this.keyFileNumber = 0;
			this.taxonFileNumber = 0;
			
			try {
				FileInputStream fstream = new FileInputStream(sourceFile);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8"); 
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				ArrayList<Taxon_bees> taxonList = new ArrayList<Taxon_bees>();
				ArrayList<KeyFile> keyFiles = new ArrayList<KeyFile>();
				Hashtable<String, String> tagList = new Hashtable<>(); 
				
				Taxon_bees taxon = null;
				KeyFile keyfile = null;
				boolean inTaxon = true; //either in taxon or in key
				
				while ((line = br.readLine()) != null) {
					
					line = line.trim();
					if (line.equals("")) {
						continue;
					}
					
					
					if (isMarkedPara(line)) {
						ArrayList<String> para = getParaTag(line);
						if (!para.get(0).equals("")) {
							tagList.put(para.get(0), para.get(1));
						}
					}
				}
					
				Enumeration<String> em = tagList.keys();
				while (em.hasMoreElements()) {
					String key = em.nextElement();
					System.out.println(key + ": " + tagList.get(key));
				}
				
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
		/*Enumeration<String> em = ranks.keys();
		while (em.hasMoreElements()) {
			String temp = em.nextElement();
			if (name.contains(temp)) {
				return temp;
			}
		}*/
		if (name.indexOf("var.") > 0) {
			rank = "Variety";
		} else if (name.indexOf("subsp.") > 0) {
			rank = "Subspecies";
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
	
	public ArrayList<KeyChoice> addKeyChoiceToArr(ArrayList<KeyChoice> originalList, KeyChoice kc) {
		ArrayList<KeyChoice> rv = originalList;
		rv.add(kc);
		return rv;
	}
	
	public Hashtable<String, String> addToHashTable(String key, String value, 
			Hashtable<String, String> ht) {
		if (! key.equals("") && ht.get(key) == null) {
			ht.put(key, value);
		}
		return ht;
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
			System.out.println("--" + taxon.getFilename());
			System.out.println("  rank - " + taxon.getRank());
			System.out.println("  keys = " + taxon.getKeyFiles().toString());
			
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
			
			//Element descrip = new Element("description");
			//root.addContent(descrip);

			//add name 
			Element name = new Element("name");
			name.setText(taxon.getName());
			name.setAttribute("attr", taxon.getNativeAttr());
			nomen.addContent(name);
			
			addElement(nomen, taxon.getRank(), "rank");
			//addElement(nomen, taxon.getHierarchy(), "taxon_hierarchy");
			
			ArrayList<String> tags = taxon.getTags();
			Hashtable<String, String> paras = taxon.getParas();
			
			for (String tag : tags) {
				if (belongToDescrip(tag)) {
					addElement(root, tag + ": " + paras.get(tag), "description");
				} else {
					addElement(root, paras.get(tag), tag);	
				}
			}
			
			//output key file name
			addElements(root, taxon.getKeyFiles(), "key_file");
			
			// output xml file
			File f = new File(taxonFilesFolder, taxon.getFilename() + ".xml");
			XMLOutputter serializer = new XMLOutputter();
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
			//addElements(root, keyfile.getDiscussion(), "key_discussion");
			
			ArrayList<KeyStatement> kss = keyfile.getStatements();
			for (KeyStatement ks : kss) {
				Element e_ks = new Element("key_statement");
				root.addContent(e_ks);
				
				addElement(e_ks, ks.getId(), "statement_id");
				//addElement(e_ks, ks.getFrom_id(), "statement_from_id");
				addElement(e_ks, ks.getStatement(), "statement");
				addElement(e_ks, ks.getDetermination(), "determination");
				addElement(e_ks, ks.getNext_id(), "next_statement_id");
				
				/*
				ArrayList<KeyChoice> kcs = ks.getChoices();
				for (KeyChoice kc : kcs) {
					Element e_kc = new Element("key_choice");
					e_ks.addContent(e_kc);
					addElement(e_kc, kc.getStatement(), "statement");
					addElement(e_kc, kc.getNext_id(), "next_id");
					addElement(e_kc, kc.getDetermination(), "determination");	
				}*/
			}
						
			// output xml file
			String filename = keyfile.getFileName();
			//filename = filename.replaceAll("[\\.\\s]", "");
			File f = new File(keyFilesFolder, filename + ".xml");
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
