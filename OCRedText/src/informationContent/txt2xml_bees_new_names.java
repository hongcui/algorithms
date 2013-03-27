package informationContent;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import db.DatabaseAccessor;

public class txt2xml_bees_new_names {

	
	public String p_index = "^(\\d+)\\.\\s(.+)$";
	public String p_name = "^(\\??([A-Z][a-z]+\\s[A-Z][a-z]+).+)\\.(.*?)$";
	public String p_type_genus = "^(.*)(Type\\sge-?\\s?nus:.*)$";
	public String p_combining_stem = "^(.*)(Com-?\\s?bin-?\\s?ing\\sstem:.*)$";
	public String p_note = "^(.*)(Notes?:(.+))$";
	
	public String p_taxon_title = "^([A-Z]+|[A-Z][a-z]+)\\s[A-Z][a-z]+(\\sand\\s[A-Z][a-z]+)?,\\snew\\s([a-z]+)$";//MACROGALEINA Engel, new subtribe
	public String p_named_para = "^([A-Z\\s]+):\\s(.*)$";
	
	public String last_mark = ""; 
	
	public String input_file = "D:\\Work\\Data\\bees_2nd\\6.family-group_names\\txt\\newNames.txt";
	public String output_foler = "D:\\Work\\Data\\bees_2nd\\6.family-group_names\\output\\SYSTEMATICS\\";
	public java.sql.Connection conn = null;
	public static String url = ApplicationUtilities
			.getProperty("database.url");
	public String source = "";
	
	int note_count = 0;
	int key_count = 1;
	int taxon_count = 1;
	
	private ArrayList<String> ranksList = new ArrayList<>();
	private Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	private Hashtable<String, Integer> ranks = new Hashtable<String, Integer>();
	
	
	public boolean isTaxonTitle(String line) {
		boolean rv = false;
		Pattern p = Pattern.compile(p_taxon_title);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			rv = true;
		}
		return rv;
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
	
	public void processTaxonTitle(String line, Hashtable<String, String> ht_taxon) {
		last_mark = "";
		key_count = 1;
		Pattern p = Pattern.compile(p_taxon_title);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			String count = Integer.toString(taxon_count);
			ht_taxon.put("index", count);
			System.out.println("# " + count);
			taxon_count++;
			
			String name = line.substring(0, line.indexOf(","));
			ht_taxon.put("name", name);
			System.out.println("  name is: " + name);
			
			String rank = m.group(3);
			ht_taxon.put("rank", rank);
			priorRanks.put(rank, name);
			System.out.println("  rank is: " + rank);
			
			String hierarchy = getHierarchy(rank.toLowerCase());
			ht_taxon.put("hierarchy", hierarchy);
			
			System.out.println("  hierarchy is: " + hierarchy);			
		}
	}
	
	public void processPara(String line, Hashtable<String, String> ht_taxon) {
		if (ht_taxon == null) return;
		
		if (hasParaName(line)) {
			key_count = 1;
			String key = line.substring(0, line.indexOf(":")).trim();
			String value = line.substring(line.indexOf(":") + 1, line.length()).trim();
			ht_taxon.put(key, value);
			System.out.println("  " + key + 
					" is: " + ht_taxon.get(key));
			last_mark = key;
		} else if (last_mark.equals("")) {
			String key = getNextKey("note");
			ht_taxon.put(key, line);
			System.out.println("  " + key + 
					" is: " + ht_taxon.get(key));
			last_mark = "note";
		} else {
			String key = getNextKey(last_mark);
			ht_taxon.put(key, line);
			System.out.println("  " + key + 
					" is: " + ht_taxon.get(key));
		}
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		txt2xml_bees_new_names t = new txt2xml_bees_new_names();
		t.ExtractTaxon();
	}
	
	protected void ExtractTaxon() throws Exception {
		
		// construct ranks
				ranks.put("phylum", 1);
				ranksList.add("phylum");
				
				ranks.put("subphylum", 2);
				ranksList.add("subphylum");
				
				ranks.put("class", 3);
				ranksList.add("class");
				
				ranks.put("subclass", 4);
				ranksList.add("subclass");
				
				ranks.put("order", 5);
				ranksList.add("order");
				
				ranks.put("suborder", 6);
				ranksList.add("suborder");
				
				ranks.put("superfamily", 7);
				ranksList.add("superfamily");
				
				ranks.put("family", 8);
				ranksList.add("family");
				
				ranks.put("subfamily", 9);
				ranksList.add("subfamily");
				
				ranks.put("tribe", 10);
				ranksList.add("tribe");
				
				ranks.put("subtribe", 11);
				ranksList.add("subtribe");
				
				ranks.put("genus", 12);
				ranksList.add("genus");
				
				ranks.put("subgenera", 13);
				ranksList.add("subgenera");
				
				ranks.put("subgenus", 14);
				ranksList.add("subgenus");
				
				
		File file = new File(input_file);
		if (file != null) {
			try {
				Class.forName(ApplicationUtilities
						.getProperty("database.driverPath"));
				conn = DriverManager.getConnection(url);
			} catch (Exception e) {
				e.printStackTrace();
			}	
			
			this.source = file.getName();
			processFile(file);
		}
	}
	
	
	@SuppressWarnings("unused")
	public void processFile(File file) throws Exception {
			if (file.getName().startsWith(".")) {
				return;
			}
			
			ArrayList<Hashtable<String, String>> taxonList = new ArrayList<>();
			
			try {				
				FileInputStream fstream = new FileInputStream(file);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8");
				@SuppressWarnings("resource")
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable("bees_family_group_names", conn);

				String line = "";
				String rest = "";
				int taxon_count = 0;
				Hashtable<String, String> ht_taxon = null;
				while ((line = br.readLine()) != null) {					
					if (line.equals("")) {
						continue;
					}
					
					line = line.trim();
					
					if (isTaxonTitle(line)) {
						if (ht_taxon != null) {
							taxonList.add(ht_taxon);
						}
						ht_taxon = new Hashtable<>();
						processTaxonTitle(line, ht_taxon);
					} else {
						processPara(line, ht_taxon);
					}
				}
				
				if (ht_taxon != null) {
					taxonList.add(ht_taxon);	
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			for (Hashtable<String, String> taxon : taxonList) {
				outputTaxon(taxon);
			}
	}
	
	private void outputTaxon(Hashtable<String, String> taxon) {
		try {			
			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);
			
			Element index = new Element("index");
			index.setText(taxon.get("index"));
			meta.addContent(index);
			
			// populate meta
			Element source = new Element("source");
			source.setText(this.source);
			meta.addContent(source);

			// nomenclature
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);

			//get name 
			if (taxon.get("name") != null) {
				Element name = new Element("name");
				name.setText(taxon.get("name"));
				nomen.addContent(name);	
			}
			
			//get name 
			if (taxon.get("name_info") != null) {
				Element name_info = new Element("name_info");
				name_info.setText(taxon.get("name_info"));
				nomen.addContent(name_info);	
			}
			
			if (taxon.get("rank") != null) {
				Element rank = new Element("rank");
				rank.setText(taxon.get("rank"));
				nomen.addContent(rank);	
			}
			
			if (taxon.get("hierarchy") != null) {
				Element hierarchy = new Element("hierarchy");
				hierarchy.setText(taxon.get("hierarchy"));
				nomen.addContent(hierarchy);	
			}
			
			Enumeration<String> eu = taxon.keys();
			while (eu.hasMoreElements()) {
				String key = eu.nextElement();
				if (!isReservedTag(key)) {
					addElement(key, root, taxon);
				}
			}
		
			String filename = taxon.get("index") + ". " + taxon.get("name").replaceAll("[^\\w]", "_").replaceAll("_+", "_")
					.replaceAll("Щ", "E").replaceAll("[жи]", "O").replaceAll("Р", "A")
					.replaceAll("м", "U").replaceAll("щ", "e");
			
			System.out.println("OUTPUT: index " + taxon.get("index") + " to " + filename);

			// output xml file
			File f = new File(output_foler, filename + ".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getIndex(String line, Hashtable<String, String> ht_taxon) {
		String index = line.substring(0, line.indexOf(".")).trim();
		
		ht_taxon.put("index", index);
		System.out.println("index is: " + ht_taxon.get("index"));
		
		String rest = line.substring(line.indexOf(".") + 1, line.length());
		
		return rest.trim();
	}
	
	public boolean hasIndex(String line) {
		boolean rv = false;
		Pattern p = Pattern.compile(p_index);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			rv = true;
		}
		return rv;
	}
	
	public boolean hasParaName(String line) {
		boolean rv = false;
		Pattern p = Pattern.compile(p_named_para);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			rv = true;
		}
		return rv;
	}
	
	public String getTaxaName(String line, Hashtable<String, String> ht_taxon) {
		String rest = line;
		Pattern p = Pattern.compile(p_type_genus);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			String name_info = m.group(1).trim();
			ht_taxon.put("name_info", m.group(1));
			System.out.println("  name_info is: " + ht_taxon.get("name_info"));
			rest = line.substring(line.indexOf(name_info), line.length());
			
			String name = name_info.substring(0, name_info.indexOf(",")).trim();
			ht_taxon.put("name", name);
			System.out.println("  name is: " + ht_taxon.get("name"));	
			
			rest = m.group(2);
		}
		return rest.trim();
	}
	
	public String getNoteKey() {
		String note_key = "";
		if (note_count == 0) {
			note_key = "note";
		} else {
			note_key = "note" + Integer.toString(note_count);
		}
		note_count++;
		return note_key;
	}
	
	public String getNextKey(String key) {
		String rv = key + "__" + Integer.toString(key_count);
		key_count++;
		return rv;
	}
	
	public boolean isReservedTag(String key) {
		return (key.equals("name") || key.equals("name_info")||
				key.equals("rank") || key.equals("hierarchy")||
				key.equals("index"));
	}
	
	public void addElement(String name, Element root, Hashtable<String, String> taxon) {
		String key = name;
		if (name.indexOf("__") > 0) {
			key = name.substring(0, name.indexOf("__"));
		}
		key = key.replaceAll("[^\\w]", "_");
		
		Element e = new Element(key);
		e.setText(taxon.get(name));
		root.addContent(e);
	}
	
	public String getTypeGenus(String line, Hashtable<String, String> ht_taxon) {
		String rest = line;
		Pattern p = Pattern.compile(p_combining_stem);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			
			String type_genus = m.group(1); 
			type_genus = type_genus.substring(type_genus.indexOf(":") + 1, type_genus.length()).trim();
			ht_taxon.put("type_genus", type_genus);
			System.out.println("  type_genus is: " + ht_taxon.get("type_genus"));
			
			rest = m.group(2);
		}
		return rest.trim();
	}
	
	public String getCombiningStem(String line, Hashtable<String, String> ht_taxon) {
		String rest = line;
		Pattern p = Pattern.compile(p_note);
		Matcher m = p.matcher(line);
		if (m.matches()) {
			String cs = m.group(1);
			cs = cs.substring(cs.indexOf(":") + 1, cs.length()).trim();
			ht_taxon.put("combining_stem", cs);
			System.out.println("  combining_stem is: " + ht_taxon.get("combining_stem"));
			
			rest = m.group(2);
		} else {
			p = Pattern.compile(p_combining_stem);
			m = p.matcher(line);
			if (m.matches()) { 
				String cs = line.substring(line.indexOf(":") + 1, line.length()).trim();
				ht_taxon.put("combining_stem", cs);
				System.out.println("  combining_stem is: " + ht_taxon.get("combining_stem"));
				rest = "";
			}
		}
		return rest.trim();
	}
	
	public void getNote(String line, Hashtable<String, String> ht_taxon) {
		if (line.equals(""))
			return;
			
		Pattern p = Pattern.compile("^Notes?:(.*)");
		String note_key = getNoteKey();		
		Matcher m = p.matcher(line);
		if (m.matches()) {
			ht_taxon.put(note_key, m.group(1));
		} else {
			ht_taxon.put(note_key, line);
		}
		System.out.println("  " + note_key + " is: " + ht_taxon.get(note_key));
	}

}
