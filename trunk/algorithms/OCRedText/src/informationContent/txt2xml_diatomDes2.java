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

public class txt2xml_diatomDes2 {

	public static void main(String[] args) {
		txt2xml_diatomDes2 t = new txt2xml_diatomDes2();
		t.generateTaxonXmls();
	}
	
	public String p_marked_para = "^([\\w\\s/]+):\\s?(.*)$";
	public String p_taxon_name = "^taxon\\snames:(.+)$";
		
	/*output xml files*/
	public String txtFile = "D:\\Work\\Data\\diatom\\DiatomDes2_centric_diatom_atlas.txt";
	public String taxonFilesFolder = "D:\\Work\\Data\\diatom\\output_DiatomDes2\\taxons\\";			
	private File sourceFile = null;
	
	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	private ArrayList<String> ranksList = new ArrayList<>();
	private Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	private String volume = "";
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
	
	public String getTaxonName(String text) {
		text = text.trim();
		try {
			text = text.trim();
			Pattern p = Pattern.compile(this.p_taxon_name);
			Matcher mt = p.matcher(text);
			if (mt.matches()) {
				return mt.group(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	
	
	public boolean isNewTaxonStart(String text) {
		return isTaxonTitle(text);
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
	
	public ArrayList<String> getTagAndContent(String text) {
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
	
	
	/**
	 * process taxon by file
	 */
	protected void ExtractTaxon() {
		if (sourceFile != null) {
			this.source = sourceFile.getName();
			this.volume = sourceFile.getName().substring(0,
					sourceFile.getName().lastIndexOf(".")).replaceAll("[^\\w]", "_");
			this.taxonFileNumber = 0;
			
			try {
				FileInputStream fstream = new FileInputStream(sourceFile);
				InputStreamReader is = new InputStreamReader(fstream, "ISO-8859-1"); //ISO 8859-1
				@SuppressWarnings("resource")
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				ArrayList<Taxon_bees> taxonList = new ArrayList<Taxon_bees>();
				Taxon_bees taxon = null;
				
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.equals("") || line.startsWith("###")) {
						continue;
					}
					
					//System.out.println("Line: " + line);
					
					if (isNewTaxonStart(line)) {
						if (taxon != null) {
							//add taxon to list
							taxonList.add(taxon);
							taxon = null;
						}
						//create a new taxon
						taxonFileNumber++;
						String taxonname = getTaxonName(line);
						taxon = new Taxon_bees(taxonname, taxonFileNumber);
						//process name to detail
						taxon = process_name(taxon);
						
						continue;
					}
					
					if (isMarkedPara(line)) {
						ArrayList<String> para = getTagAndContent(line);
						String tag = para.get(0);
						String content = para.get(1);
						
						//update tags and paras
						if (taxon != null) {
							if (tag.equals("source file name")) {
								taxon.setSource_file_info(content);
							} else {
								taxon.setTags(addLineToArr(taxon.getTags(), tag));
								taxon.setArr_paras(addLineToArr(taxon.getArr_paras(), content));	
							}	
						} else {
							System.out.println("ERROR");
						}
					} else { // no paragraph mark, description
						if (taxon != null) {
							taxon.setTags(addLineToArr(taxon.getTags(), "description"));
							taxon.setArr_paras(addLineToArr(taxon.getArr_paras(), line));	
						} else {
							System.out.println("ERROR");
						}						
					}
				}
				
				
				//insert last key or taxon
				if (taxon != null) {
					taxonList.add(taxon);
				}

				outputTaxons(taxonList);

				System.out.println("taxons and keys generated");
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	protected Taxon_bees process_name(Taxon_bees taxon) {
		String name = taxon.getName();
		//check if any synonym info
		if (name.indexOf("#") > 0) { //has synonym info
			taxon.setName(name.substring(0, name.indexOf("#")));
			taxon.setSynonym(name.substring(name.indexOf("#") + 1, name.length()));
		}
		
		//get rank
		taxon.setRank(getRank(taxon.getName()));
		
		return taxon;
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
		String rank = "species";
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

	private void outputXMLFile(Taxon_bees taxon) {
		try {
			System.out.println("--" + taxon.getFilename());
			
			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);
			
			// populate meta
			addElement(meta, this.source, "source");
			addElement(meta, taxon.getSource_file_info(), "source_file_name");
			//addElement(meta, this.volume, "volume");
			
			// nomenclature
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);
			
			//Element descrip = new Element("description");
			//root.addContent(descrip);

			//add name 
			addElement(nomen, taxon.getName(), "name");
			addElement(nomen, taxon.getName_info(), "name_info");
			addElement(nomen, taxon.getSynonym(), "synonym");
			
			ArrayList<String> tags = taxon.getTags();
			ArrayList<String> paras = taxon.getArr_paras();
			
			for (int i = 0; i < tags.size(); i++) {
				addElement(root, paras.get(i), tags.get(i));
			}
			
			// output xml file
			File f = new File(taxonFilesFolder, taxon.getFilename() + ".xml");
			org.jdom.output.Format format = org.jdom.output.Format.getPrettyFormat();
			format.setEncoding("ISO-8859-1");
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

}
