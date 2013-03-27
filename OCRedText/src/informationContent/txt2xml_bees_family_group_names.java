package informationContent;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import db.DatabaseAccessor;

public class txt2xml_bees_family_group_names {
	
	public String p_index = "^(\\d+)\\.\\s(.+)$";
	public String p_name = "^(\\??([A-Z][a-z]+\\s[A-Z][a-z]+).+)\\.(.*?)$";
	public String p_type_genus = "^(.*)(Type\\sge-?\\s?nus:.*)$";
	public String p_combining_stem = "^(.*)(Com-?\\s?bin-?\\s?ing\\sstem:.*)$";
	public String p_note = "^(.*)(Notes?:(.+))$";
	
	public String input_file = "D:\\Work\\Data\\bees_2nd\\6.family-group_names\\txt\\family_group_names.txt";
	public String output_foler = "D:\\Work\\Data\\bees_2nd\\6.family-group_names\\output\\FAMILY-GROUP_NAMES\\";
	public java.sql.Connection conn = null;
	public static String url = ApplicationUtilities
			.getProperty("database.url");
	public String source = "";
	
	int note_count = 0;
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		txt2xml_bees_family_group_names t = new txt2xml_bees_family_group_names();
		t.ExtractTaxon();
	}
	
	protected void ExtractTaxon() throws Exception {
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
					
					if (hasIndex(line)) {
						if (ht_taxon != null) {
							taxon_count++;
							ht_taxon.put("count", Integer.toString(taxon_count));
							taxonList.add(ht_taxon);
						}
						ht_taxon = new Hashtable<>();
						note_count = 0;
					} else {
						if (ht_taxon != null) {
							String note_key = getNoteKey();
							ht_taxon.put(note_key, line);
							System.out.println("  " + note_key + " is: " + ht_taxon.get(note_key));
						}
						continue;
					}
					
					//extract parts of the taxa
					//get index
					rest = getIndex(line, ht_taxon); 
					
					//get taxa name
					rest = getTaxaName(rest, ht_taxon);
					
					//get type genus
					rest = getTypeGenus(rest, ht_taxon);
					
					//get combing 
					rest = getCombiningStem(rest, ht_taxon);
					
					//get note
					getNote(rest, ht_taxon);
				}
				
				if (ht_taxon != null) {
					taxon_count++;
					ht_taxon.put("count", Integer.toString(taxon_count));
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
				Element name = new Element("name_info");
				name.setText(taxon.get("name_info"));
				nomen.addContent(name);	
			}
			
			
/*			Element rank = new Element("rank");
			nomen.addContent(rank);
			rank.addContent(taxon.getRank());

			Element th = new Element("taxon_hierarchy");
			th.setText(taxon.getHierarchy());
			nomen.addContent(th);
*/

			if (taxon.get("type_genus") != null) {
				Element name = new Element("type_genus");
				name.setText(taxon.get("type_genus"));
				root.addContent(name);	
			}
			
			if (taxon.get("combining_stem") != null) {
				Element name = new Element("combining_stem");
				name.setText(taxon.get("combining_stem"));
				root.addContent(name);	
			}
			
			if (taxon.get("note") != null) {
				Element name = new Element("note");
				name.setText(taxon.get("note"));
				root.addContent(name);	
			}
			
			//other notes
			int i = 1;
			while (i > 0) {
				String key = "note" + Integer.toString(i);
				if (taxon.get(key) != null) {
					Element name = new Element("note");
					name.setText(taxon.get(key));
					root.addContent(name);	
					i++;
				} else {
					break;
				}
			}
			
			String filename = taxon.get("count") + ". " + taxon.get("name").replaceAll("[^\\w]", "_").replaceAll("_+", "_")
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
