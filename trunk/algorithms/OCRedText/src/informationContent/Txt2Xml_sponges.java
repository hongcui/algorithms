package informationContent;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Verifier;
import org.jdom.output.XMLOutputter;
import beans.Taxon_spnges;
import db.DatabaseAccessor;

public class Txt2Xml_sponges {

	/**
	 * @param args
	 */
	
	/*paths*/
	public String folderpath = "E:\\work_data\\systemaporifera\\cleanText";//for windows
	public String outputPath = "E:\\work_data\\systemaporifera\\FinalOutput\\";//for windows
	public String xmlFolerPath = "E:\\work_data\\systemaporifera\\Taxons\\";//for windows
	public String descriptionFolerPath = "E:\\work_data\\systemaporifera\\Descriptions\\";
	private String directorySeparator = "\\";//for windows
	
	/*Pattern*/
	//SYCYSSA HAECKEL, 1872 (INCERTAE SEDIS)
	public String taxonNamePattern = "^(Ü|\\?)?(([A-Z…÷¿‹È]('[A-Z…÷¿‹È])?[a-zÈ?-]+(\\s|,|))|([A-Z…÷¿‹È-]+(\\s|,))|(&|\\s|de))+(ET\\sAL\\.,)?((\\s[0-9]{4}[a-z]?(:\\s[0-9]+)?\\.?)|([A-Za-z]{3,6}\\.\\s?[A-Za-z]{3}\\.)|(\\sIN\\sPRESS))(\\s\\(.*\\))?(:(\\s[A-Z][a-zÈ]+)+)?$";
	public String taxonNamePattern2 = "^ë[A-Z][a-z]+'\\s\\w+$"; //ëLithistid' Demospongiae
	public String titleStandOutPattern = "^(([A-Z]+,\\s)+[A-Z]+)|([A-Z][a-z-]+(\\s([ë']?[a-z-]+[ë']?\\s){0,3}[a-z-]+)?)|([A-Z]+\\s\\(.+\\))|(([A-Z]+\\s)*[A-Z]+)\\.?$";
	//Description of ërepresentative' species 
	public String titleEmbededPattern = "^(Definition|Synonymy|Restricted\\ssynonymy|Synonymy\\s\\(restricted\\)|" +
			"Diagnosis|Age|Distribution|Type\\sspecies|Type-species|Scope|Other\\snames|Remarks)\\.\\s.+";
	public String taxonAuthorPattern = "^((([A-Z…‘][a-zÎ]+\\s)|(([A-Z…‘]\\.)+\\s))*([A-Z][a-zÎ]+)(-[A-Z][a-zÎ]+)?[0-9]?((\\s&\\s)|(,\\s))?)+$";
	public String desTypeSpeciesParagraphPattern = "^(Synonymy|Material\\sexamined|Description(\\sof\\s.+)?|Remarks|Reproduction|" +
			"Distribution(\\sand\\secology)?|Ecology|Restricted\\ssynonymy|Synonymy\\s\\(restricted\\)|Geographic\\sdistribution|Habitat\\sand\\sdistribution" +
			"|Contents|Desmoids|Previous\\sreview)" +
			"(\\s?\\(.+\\)\\s?)?(\\.|:)(.+?)";//possible brackets not ended
	public String keywordsPattern = "^((\\d{1,2}\\.)+\\s)?(Keywords|Key\\swords)" +
			"(\\s?\\(.+\\)\\s?)?(\\.|:).+?";
	
	public String descriptionTitlesPattern = "^((Definition(\\sand\\sdiagnosis)?)|(Diagnosis(\\s\\(.+\\))?(\\sof\\s.+)?))(\\s\\(.+\\))?$";
	public String firstLevelTitlePattern = "^((Age)|(Distribution)|(Scope(\\s\\(.+\\))?)|(Synonymy)|(Other\\snames)|(Type\\sspecies)|(Type-species)|(Restricted\\ssynonymy)|(Synonymy\\s\\(restricted\\)))$";
	
	//Description of second species - chapter 35
	//Description of type species of Axociella.  chapter 56
	
	//Description of type-species 
	public String descriptionOfTypeSpeciesTitlePattern = "^(Description\\sof\\s(the\\s)?((type[\\s-])|(second\\s)|([ë'][a-z]+[ë']\\s))?species(\\sof\\s\\w+)?)|Taxonomic\\sremarks|Taxonomic\\shistory$"; //type-species
	//Description of the type species
	public String typeSpeciesTitlePattern = "^(Type\\sspecies)|(Type-species)$";
	public String synonymTitlePattern = "^(Synonymy|Restricted\\ssynonymy|Synonymy\\s\\(restricted\\)|(Other\\snames))$";
	public String ageTitlePattern = "^Age$";
	public String distributionTitlePattern = "^Distribution$";
	public String scopeTitlePattern = "^Scope$";
	public String explicitRankPattern = "";/*will be set dynamically*/
	public String speciesNamePattern = "^[^\\w]?\\s?\\w+\\s\\w+\\s\\w.+";
	public String genusNamePattern = "^[^\\w]?\\s?\\w+\\s(DE\\s|de\\s)?(\\w.+)?";//NEOPHRISSOSPONGIA GEN. NOV.
	
	public java.sql.Connection conn = null;
	public static String url = ApplicationUtilities
			.getProperty("database.url");
	public String source = "";
	public String volume = "";
	public int fileCount = 0;
	public int taxonCount = 0;
	public File[] sourceFiles = null;
	
	public Hashtable<String, String> priorRanks = new Hashtable<String, String>();
	public ArrayList<String> allRanks = new ArrayList<String>();
	
	
	public static void main(String[] args) throws Exception {
		Txt2Xml_sponges t2x = new Txt2Xml_sponges();
		t2x.ExtractTaxon();
		System.out.println("Convert finished");
	}
	
	public Txt2Xml_sponges() {		
		//pre set hierarchical ranks
		allRanks.add("phylum");
		allRanks.add("subphylum");
		allRanks.add("class");
		allRanks.add("subclass");
		allRanks.add("order");
		allRanks.add("suborder");
		allRanks.add("superfamily");
		allRanks.add("family");
		allRanks.add("subfamily");
		allRanks.add("genus");
		allRanks.add("subgenera");
		allRanks.add("subgenus");
		allRanks.add("species");
		
		//set explicitRankPattern
		for (String rank : allRanks) {
			if (explicitRankPattern.equals(""))
				explicitRankPattern += "^[^\\w]?\\s?((" + rank + ")";
			else 
				explicitRankPattern += "|(" + rank + ")";
		}
		explicitRankPattern += ").*";
		System.out.println("explicitRankPattern is : " + explicitRankPattern);
		
		// get conn
		try {
			Class.forName(ApplicationUtilities
					.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	protected void ExtractTaxon() throws Exception {
		File sourceFolder = new File(folderpath);
		this.source = sourceFolder.getName();
		this.sourceFiles = sourceFolder.listFiles();
		
		//sort files based on their chapter number
		Hashtable<Integer, File> files_with_number = new Hashtable<Integer, File>();
		Hashtable<String, File> files_without_number = new Hashtable<String, File>();
		for (File file : sourceFiles) {
			String filename = file.getName();
			Pattern p = Pattern.compile("^[Cc]hapter[-_]((\\d)+).*");
			Matcher m = p.matcher(filename);
			if (m.matches()) {
				int chapterNumber = Integer.parseInt(m.group(1));
				files_with_number.put(chapterNumber, file);
			} else {
				files_without_number.put(filename, file);
			}
		}
		
		//process files with chapter numbers
		List<Integer> keys_int = Collections.list(files_with_number.keys());
		Collections.sort(keys_int);
		Iterator<Integer> it_int = keys_int.iterator();
		while (it_int.hasNext()) {
			int key = it_int.next();
			File file = files_with_number.get(key);
			System.out.println("");
			System.out.println("chapter " + key + ": " + file.getName());
						
			//process file
			processFile(file);
		}
		
		//process files with out chapter numbers
		List<String> keys_string = Collections.list(files_without_number.keys());
		Collections.sort(keys_string);
		Iterator<String> it_string = keys_string.iterator();
		while (it_string.hasNext()) {
			String key = it_string.next();
			File file = files_without_number.get(key);
			System.out.println("");
			System.out.println("no chapter id: " + file.getName());
			
			//process file
			processFile(file);
		}
	}
	
	
	public void processFile(File file) throws Exception {
			if (file.getName().startsWith(".")) {
				return;
			}
			this.fileCount = 0;
			this.taxonCount = 0;
			this.source = file.getName().substring(0,
					file.getName().lastIndexOf("."));
			getVolume(this.source);
			createOutputFolders();
			ArrayList<Taxon_spnges> taxonList = new ArrayList<Taxon_spnges>();
			
			try {				
				FileInputStream fstream = new FileInputStream(file);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8");
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				Taxon_spnges taxon = null;
				boolean contentStarted = false;/*set true when hit first taxon name*/
				boolean isLastParaTitle = false;
				boolean isSpecialTitle = false;
				boolean betweenTaxonAndFirstTitle = false;
				boolean desOfTypeSpeciesStarted = false;
				String titleContainsSubtitles = "";
				String lastTitle = "";
				String lastTypeSpeciesTitle = "";
				
				while ((line = br.readLine()) != null) {					
					if (line.equals("")) {
						continue;
					}

					line = line.replaceAll("ñ", "-");
					line = cleanIllegalChars(line.trim());
					//start with the first taxon
					if (!contentStarted) {
						if (!isTaxonName(line)) {
							continue;
						} else {
							contentStarted = true;
						}
					}
					
					/*line is taxon name line*/
					if (isTaxonName(line) && !(isLastParaTitle && isSpecialTitle)) {
						//store the last working taxon
						if (taxon != null) {
							System.out.println("  taxon name: " + taxon.getName());
							System.out.println("      name info: " + taxon.getName_info());
							System.out.println("      file name: " + taxon.getFile_name());
							System.out.println("      rank: " + taxon.getRank());
							System.out.println("      hierarchy: " + taxon.getHierarchy());
							
							taxonList.add(taxon);
							lastTypeSpeciesTitle = "";
							titleContainsSubtitles = "";
							lastTitle = "";
							desOfTypeSpeciesStarted = false;
						}
						
						//create a new taxon
						this.taxonCount++;
						String name = getTaxonName(line);
						taxon = new Taxon_spnges(name);
						taxon.setName_info(line);
						String myRank = getRank(name);
						taxon.setRank(myRank);
						taxon.setHierarchy(getHierarchy(name, myRank, priorRanks));
						//update priorRanks: remove all lower ranks 
						updatePriorRanks(taxon.getRank(), taxon.getName());
						
						taxon.setSource(this.source);
						betweenTaxonAndFirstTitle = true;
						
						continue;						
					}
					
					/* line is title. May have two titles together:
					 * one joint title followed by separate title.*/
					if (isStandAloneTitle(line)) {
						isLastParaTitle = true;
						lastTitle = line;
						betweenTaxonAndFirstTitle = false;
						if (isDescriptionOfTypeTitleSpecies(line)) {
							desOfTypeSpeciesStarted = true;	
							titleContainsSubtitles = line;
						} else {
							desOfTypeSpeciesStarted = false;
						}
						
						if (isSynonymTitle(line) || line.toLowerCase().contains("review") 
								|| line.toLowerCase().contains("type")) {
							isSpecialTitle = true;
						} else {
							isSpecialTitle = false;
						}
						
						continue; /*notice: joint title will be ignored*/
					}
					
					/*line is not taxon and not title -> line is real content*/
					
					/*real text: either add to last title, or add to last title of description of type species*/
					if (desOfTypeSpeciesStarted) {
						/*add this para into hashtable description_of_type_species*/
						lastTypeSpeciesTitle = addParaIntoTypeSpeciesDes(taxon, line, lastTypeSpeciesTitle, titleContainsSubtitles);
					} else {
						/*deal with possible embeded titles*/
						if (isEmbededTitle(line)) {
							lastTitle = getEmbededTitle(line);
							betweenTaxonAndFirstTitle = false;
						} else {
							if (betweenTaxonAndFirstTitle) {
								/*put into other_info*/
									taxon.setOther_info(taxon.getOther_info() 
											+ "\n"
											+ line);
									isLastParaTitle = false;
									continue;
							}
						}
						
						/*add into specific position*/
						addPara(taxon, line, lastTitle);
					}
					isLastParaTitle = false;
				}
				
				/*add the last taxon into taxonlist*/
				if (taxon != null) {
					taxonList.add(taxon);
					System.out.println("  taxon name: " + taxon.getName());
					System.out.println("      name info: " + taxon.getName_info());
					System.out.println("      file name: " + taxon.getFile_name());
					System.out.println("      rank: " + taxon.getRank());
					System.out.println("      hierarchy: " + taxon.getHierarchy());
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			
			for (Taxon_spnges t : taxonList) {
				outputTaxon(t);
			}
	}
	
	
	public void updatePriorRanks(String myRank, String myName) {
		//add into priorRank
		priorRanks.put(myRank, myName);
		
		//remove all previous lower ranks
		boolean isLower = false;
		for (String rank : allRanks) {
			if (isLower) {
				if (priorRanks.get(rank) != null)
					priorRanks.remove(rank);
			} else if (rank == myRank){
				isLower = true;
			}
		}
	}
	
	/*get the tag name and content and add it into hashtable*/
	public String addParaIntoTypeSpeciesDes(Taxon_spnges taxon, String line, String last_des_sub_title, 
			String descriptionTitle) {		
		String returnValue = "";
		Pattern p = Pattern.compile(this.desTypeSpeciesParagraphPattern);
		Matcher m = p.matcher(line);
		Hashtable<String, String> ht = taxon.getDescription_of_type_species();
		
		if (ht.get("tagName") == null) {
			ht.put("tagName", descriptionTitle);
		}
		
		if (m.matches()) {
			String tag = m.group(1);
			String content = line;
			if (tag == null || tag.equals("")) {
				System.out.println("--alert: description of type species");
			} else {
				ht.put(tag, content);
				returnValue = tag;
			}
			
		} else {
			String tag = last_des_sub_title;
			returnValue = last_des_sub_title;
			if (tag.equals("")) {
				/*place the text between first titled paragraph and title into "info"*/
				addToHashTable(taxon.getDescription_of_type_species(), "info", line);
			} else {
				String content = appendPara(ht.get(tag), line);
				ht.remove(tag);
				ht.put(tag, content);
			}
		}
		taxon.setDescription_of_type_species(ht);
		return returnValue;
	}
	
	public void addPara(Taxon_spnges taxon, String line, String last_title) {
		if (isFirstLevelTitle(last_title)) {
			if (isSynonymTitle(last_title)) {
				taxon.setSynonym(appendPara(taxon.getSynonym(), line));
			} else if (isAgeTitle(last_title)) {
				taxon.setAge(appendPara(taxon.getAge(), line));
			} else if (isDistributionTitle(last_title)) {
				taxon.setDistribution(appendPara(taxon.getDistribution(), line));
			} else if (isScopeTitle(last_title)) {
				taxon.setScope(appendPara(taxon.getScope(), line));
			} else if (isTypeSpeciesTitle(last_title)) {
				taxon.setType_species(appendPara(taxon.getType_species(), line));
			}
		} else if (belongToDescription(last_title)) {
			/*put into description*/
			Hashtable<String, String> ht = addToHashTable(taxon.getDescription(), last_title, line);
			taxon.setDescription(ht);
		} else {
			/*put into discussion*/
			Hashtable<String, String> ht = addToHashTable(taxon.getDiscussion(), last_title, line);
			taxon.setDiscussion(ht);
		}
	}
	
	/**
	 * create two folers: one for xml files, the other for txt files
	 * @param volume
	 * @return
	 */
	public boolean createOutputFolders(){
		boolean rv = false;
		rv = new File(xmlFolerPath + this.volume).mkdir();
		rv = new File(descriptionFolerPath + this.volume).mkdir();
		return rv;
	}
	
	
	/**
	 * exceptions: FAMILY NUCHIDAE FAM. NOV. --chapter 122
	 * FAMILY SOLENEISCIDAE NOM. NOV. --chapter 123
	 * @param para
	 * @return
	 */
	public boolean isTaxonName(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.taxonNamePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
			
			if (para.matches(this.taxonNamePattern2)) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * could be stand alone title
	 * could be enbeded as the first word of the paragraph
	 * @param para
	 * @return
	 */
	public boolean isStandAloneTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleStandOutPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isEmbededTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleEmbededPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public String getEmbededTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleEmbededPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return mt.group(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	
	public boolean isDescriptionOfTypeSpeciesWithLeadingTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.desTypeSpeciesParagraphPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**/
	public String getRank(String name) {
		String rank = "";
		Pattern p = Pattern.compile(this.explicitRankPattern.toLowerCase());
		Matcher m = p.matcher(name.toLowerCase());
		if (m.matches()) {
			rank = m.group(1);
		} else {
			//if two words - species
			if (name.matches(this.speciesNamePattern) && !name.matches(this.genusNamePattern)) {
				rank = "species";
			} else if (name.matches(this.genusNamePattern)) {
				//else if one word - genus
				rank = "genus";
			} else {
				rank = "to be determined";
			}
		}
		return rank;
	}
		
	public String getHierarchy(String name, String myRank, 
			Hashtable<String, String> priorRanks) {
		String hierarchy = "";
		for (String rank : this.allRanks) {
			if (myRank.equals(rank)) {
				break;
			} else if (priorRanks.get(rank) != null) {
				hierarchy += priorRanks.get(rank) + "; ";
			}
		}

		hierarchy += name;
		return hierarchy;
	}
	
	public boolean belongToDescription(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.descriptionTitlesPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isTypeSpeciesTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.typeSpeciesTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public String appendPara(String existPara, String paraToAppend) {
		if (existPara == null || existPara.equals("")) {
			return paraToAppend;
		} else {
			return existPara + "\n" + paraToAppend;
		}
	}
	
	public Hashtable<String, String> addToHashTable(Hashtable<String, String> tb, String key, String para) {
		assert(key == null || key.equals(""));
		if (key == null || key.equals("")) {
			System.out.println("--alert: key is empty.");
		}
		if (tb.get(key) != null) {
			tb.put(key, para);
			return tb;
		} else {
			String oldText = tb.get(key);
			tb.remove(key);
			tb.put(key, appendPara(oldText, para));
			return tb;
		}
		
	}
	
	public boolean isSynonymTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.synonymTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isAgeTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.ageTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isScopeTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.scopeTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isDistributionTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.distributionTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean isDescriptionOfTypeTitleSpecies(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.descriptionOfTypeSpeciesTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public void getVolume(String text) {
		text = text.replaceAll("\\s", "_").replaceAll("-", "_");
		Pattern p = Pattern.compile("^(.*[0-9]+).*?");
		Matcher m = p.matcher(text);
		if (m.matches()) {
			this.volume = m.group(1);
		} else {
			this.volume = text;
		}
	}
	
	public boolean isFirstLevelTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.firstLevelTitlePattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public String getTaxonName(String line) {
		line = line.replaceAll("[A-Z]{3}\\.\\s[A-Z]{3}\\.", "");
		if (line.indexOf(",") > 0) {
			return line.substring(0, line.indexOf(","));
		} else 
			return line;
	}
	
	public String getTagName(String tag) {
		return tag.toLowerCase().replaceAll("[^\\w]", "_").replaceAll("_+", "_");
	}
	
	
	protected void outputTaxon(Taxon_spnges taxon) throws Exception {
		// write xml file
		outputXMLFile(taxon);
		
		outputTxtFile(taxon);

		// add record to database: params: filename, name, hierarchy
		DatabaseAccessor.insertTaxonFileRelation(volume, taxon.getName(),
				taxon.getHierarchy(), taxon.getFile_name(), conn);
	}
	
	private void outputTxtFile(Taxon_spnges taxon) {
		try {
			String description = "";
			if (taxon.getDescription() != null) {
				description += getTextFromHT(taxon.getDescription(), false, "");
			}
			
			if (taxon.getDescription_of_type_species() != null) {
				description += getTextFromHT(taxon.getDescription_of_type_species(), 
						true, "description");
			}
			
			
			FileOutputStream fos = new FileOutputStream(descriptionFolerPath + volume + directorySeparator 
					+ taxon.getFile_name() + ".txt");
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			osw.write(description);
			osw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void outputXMLFile(Taxon_spnges taxon) {
		try {			
			// create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);

			// meta
			Element meta = new Element("meta");
			root.addContent(meta);
			// populate meta
			Element source = new Element("source");
			source.setText(taxon.getSource());
			meta.addContent(source);

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
			th.setText(taxon.getHierarchy());
			nomen.addContent(th);

			Element name_info = new Element("name_info");
			nomen.addContent(name_info);
			name_info.setText(taxon.getName_info());

			/*first level tags: type_species, synonym, age, distribution, scope, other_info*/
			//synonym
			if (!taxon.getSynonym().equals("")) {
				Element e = new Element("synonym");
				root.addContent(e);
				e.setText(taxon.getSynonym());	
			}
			//ageinfo
			if (!taxon.getAge().equals("")) {
				Element e = new Element("age_info");
				root.addContent(e);
				e.setText(taxon.getAge());	
			}
			//distribution
			if (!taxon.getDistribution().equals("")) {
				Element e = new Element("distribution");
				root.addContent(e);
				e.setText(taxon.getDistribution());	
			}
			//type_species
			if (!taxon.getType_species().equals("")) {
				Element e = new Element("type_species");
				root.addContent(e);
				e.setText(taxon.getType_species());	
			}
			//scope
			if (!taxon.getScope().equals("")) {
				Element e = new Element("scope");
				root.addContent(e);
				e.setText(taxon.getScope());	
			}
			//other_info
			if (!taxon.getOther_info().equals("")) {
				Element e = new Element("other_info");
				root.addContent(e);
				e.setText(taxon.getOther_info());	
			}
			
			/*get description*/
			addDescriptions(root, taxon.getDescription());
			
			/*get description of type species*/
			if (taxon.getDescription_of_type_species() != null) {
				Hashtable<String, String> ht = taxon.getDescription_of_type_species();
				if (ht.get("tagName") != null) {
					Element tsDescription = new Element(getTagName(ht.get("tagName")));
					ht.remove("tagName");
					root.addContent(tsDescription);
					addElements(tsDescription, ht, root);		
				} else {
					Element tsDescription = new Element("description_of_type_species");
					root.addContent(tsDescription);
					addElements(tsDescription, ht, root);
				}
			}
			
			
			/*get discussion*/
			Element discussion = new Element("discussion");
			root.addContent(discussion);
			addElements(discussion, taxon.getDiscussion(), root);
			

			// output xml file
			File f = new File(xmlFolerPath + volume + directorySeparator, taxon.getFile_name() + ".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc,
					new DataOutputStream(new FileOutputStream(f)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public String getTextFromHT(Hashtable<String, String> ht, 
			boolean filter, String filterStr) {
		String txt = "";
		Enumeration<String> enu = ht.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextElement();
			if (filter) {
				if (key.toLowerCase().contains(filterStr)) {
					txt += "\nDescription of Typespecies \n";
					txt += ht.get(key) + "\n\n";
				}
			} else {
				txt += key + "\n";
				txt += ht.get(key) + "\n\n";
			}
		}
		return txt;
	}
	
	
	public void addElements(Element parent, Hashtable<String, String> ht, Element root) {
		Enumeration<String> enu = ht.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextElement();
			if (key == null || key.equals("")) {
				System.out.println("--alert: key is empty");
			} else {
				Element e = new Element(getTagName(key));
				if (ht.get(key) != null) {
					e.setText(ht.get(key));
					parent.addContent(e);
										
					if (key.toLowerCase().contains("description")) {
						addDescription("type_species", ht.get(key), root);
					}
				} else {
					System.out.println("--alert: value for " + key + "is empty");
				}								
			} 
		}
	}

	public void addDescriptions(Element parent, Hashtable<String, String> ht) {
		Enumeration<String> enu = ht.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextElement();
			if (key == null || key.equals("")) {
				System.out.println("--alert: key is empty");
			} else if (ht.get(key) != null) {
				addDescription(key, ht.get(key), parent);					
			} else {
				System.out.println("--alert: value for " + key + "is empty");
			}
		}
	}
	
	
	public void addDescription(String type, String content, Element root) {
		if (content != null && !content.equals("")) {
			Element e = new Element("description");
			//set type to be key
			e.setAttribute("type", type);
			
			e.setText(content);
			root.addContent(e);
		}	
	}
	
	public String cleanIllegalChars(String line) {
		line = line.replaceAll("…", "E").replaceAll("[÷ÿ]", "O").replaceAll("¿", "A")
				.replaceAll("‹", "U").replaceAll("È", "e");
		String legalContent = "";
		for (int i = 0; i < line.length(); i++) {
			final char ch = line.charAt(i);
			if (Verifier.isXMLCharacter(ch)) {
				legalContent += ch;
			}
		}
		return legalContent;
	}
}
