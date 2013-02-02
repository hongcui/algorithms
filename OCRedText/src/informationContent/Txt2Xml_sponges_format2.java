
/**special cases:
 * 
 * chapter 07 Subclass Homoscleromorpha Bergquist, 1978
		titles are like description of type species style
 */
package informationContent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import beans.Taxon_spnges;
import db.DatabaseAccessor;

public class Txt2Xml_sponges_format2 extends Txt2Xml_sponges{
	public static void main(String[] args) throws Exception {
		Txt2Xml_sponges_format2 t2x = new Txt2Xml_sponges_format2();
		
		t2x.folderpath = "E:\\work_data\\systemaporifera\\Format2\\txt";//for windows
		t2x.outputPath = "E:\\work_data\\systemaporifera\\Format2\\FinalOutput\\";//for windows
		
		
		
		/*Pattern*/
		//may start with index and may not
		//FAMILY SOLENEISCIDAE nomen novum 
		//SOLENEISCUS  nomen novum
		//32. POLEJAEVIA BOROJEVIC, BOURY-ESNAULT & VACELET, 2000 32.1.
		//35. SYCONESSA BOROJEVIC, BOURY-ESNAULT & VACELET, 2000 35.1. 
		//4. ACANTHOTRIAENA VACELET, VASSEUR & LéVI, 1976
		//16. VULCANELLA (ANNULASTRELLA) SUBGEN.NOV.
		//17. VULCANELLA (VULCANELLA) SUBGEN.NOV.
		//4. ASPIDOSCOPULIA N. GEN.  -*****reiswig_fa file, a lot of new title pattern
		t2x.taxonNamePattern = "^((\\d+\\.)+\\s?)?" + //leading index
				"(†|\\?)?" + //possible non-word
				"(([A-ZÉÖØÀÜé\\?]('[A-ZÉÖØÀÜé])?[a-zé?-]+(\\s|,|))|([A-ZÉÖØÀÜ\\(\\)é\\?-]+(\\s|,))|(&|\\s|de))+" + //name part
				"(ET\\sAL\\.,)?" + //et al
				"((\\s[0-9]{4}[a-z]?(:\\s[0-9]+)?(\\s\\d+\\.\\d+\\.)?\\.?)|([A-Za-z]{3,6}\\.\\s?[A-Za-z]{3}\\.)|(\\sIN\\sPRESS)|(nomen\\snovum)|(N.\\sGEN\\.))" + //year and publication
				"(\\s\\(.*\\))?(:(\\s[A-Z][a-zé]+)+)?$"; //possible tails
		t2x.taxonNamePattern2 = "^‘[A-Z][a-z]+'\\s\\w+$"; //‘Lithistid' Demospongiae
		
		
		t2x.titleStandOutPattern = "^((\\d+\\.)+\\s?)?((([A-Z]+,\\s)+[A-Z]+)|([A-Z][a-z-]+(\\s([‘']?[a-z-]+[‘']?\\s){0,3}[a-z-]+)?)|(([A-Z]+\\s)*[A-Z]+))\\.?$";
		
		//start with index
		t2x.titleEmbededPattern ="((\\d+\\.)+\\s?)?" + //index
			"(Definition(\\sof\\sgenus)?|Synonymy|Restricted\\s[sS]ynonymy|Synonymy\\s\\(restricted\\)" +
				"|Diagnosis(\\sof\\sgenus)?|Age|Type\\sspecies|Type-species|Scope|Other\\snames|(Taxonomic\\s)?Remarks|History\\sand\\sBiology" +
				"|Previous\\sreviews?|Description\\sof\\s(the\\s)?((type[\\s-])|(second\\s)|([‘'][a-z]+[‘']\\s))?species" +				
				"|Material\\sexamined|Description(\\sof\\s.+|\\s\\(.+\\))?|Reproduction|Distribution(\\sof\\sgenus)?" +
				"|Distribution(\\sand\\secology)?|Ecology|Geographic\\sdistribution|Habitat\\sand\\sdistribution" +
				"|Contents|Desmoids|(Restricted\\s)?[Ss]ynonymy\\sand\\stype\\sspecies)" + //title part
			"\\.?(.*)";
		
		
		t2x.taxonAuthorPattern = "^((([A-ZÉÔ][a-zë]+\\s)|(([A-ZÉÔ]\\.)+\\s))*([A-Z][a-zë]+)(-[A-Z][a-zë]+)?[0-9]?((\\s&\\s)|(,\\s))?)+$";
		//same pattern with embeded title, but contains the previous index
		t2x.desTypeSpeciesParagraphPattern = "^((\\d+\\.)+\\s?)?(Synonymy|Material\\sexamined|Description(\\sof\\s.+|\\s\\(.+\\))?|Remarks|Reproduction|" +
				"Distribution(\\sand\\secology)?|Ecology|Restricted\\s[sS]ynonymy|Synonymy\\s\\(restricted\\)|Geographic\\sdistribution|Habitat\\sand\\sdistribution" +
				"|Contents|Desmoids)" +
				"(\\s?\\(.+\\)\\s?)?(\\.|:)(.+?)";//possible brackets not ended
		t2x.keywordsPattern = "^((\\d{1,2}\\.)+\\s)?(Keywords|Key\\swords)" +
				"(\\s?\\(.+\\)\\s?)?(\\.|:).+?";
		
		t2x.descriptionTitlesPattern = "^((Definition)|(Diagnosis(\\s\\(.+\\))?))$";
		t2x.firstLevelTitlePattern = "^((Age)|(Distribution)|(Scope(\\s\\(.+\\))?)|(Synonymy)|(Other\\snames)|(Type\\sspecies)|(Type-species)|(Restricted\\ssynonymy)|(Synonymy\\s\\(restricted\\)))$";
		
		//Description of type-species 
		t2x.descriptionOfTypeSpeciesTitlePattern = "^(Description\\sof\\s(the\\s)?((type[\\s-])|(second\\s)|([‘'][a-z]+[‘']\\s))?species(\\sof\\s\\w+)?)|Taxonomic\\sremarks|Taxonomic\\shistory$"; //type-species
		//Description of the type species
		t2x.typeSpeciesTitlePattern = "^(Type\\sspecies)|(Type-species)$";
		t2x.synonymTitlePattern = "^(.*([Ss]ynonymy).*|(Other\\snames))$";
		t2x.ageTitlePattern = "^Age$";
		t2x.distributionTitlePattern = "^.*[dD]istribution.*";
		t2x.scopeTitlePattern = "^Scope$";
		t2x.explicitRankPattern = "";/*will be set dynamically*/
		t2x.speciesNamePattern = "^\\w+\\s\\w+\\s\\w.+";
		t2x.genusNamePattern = "^\\w+\\s.+";
		
		t2x.ExtractTaxon();
		System.out.println("Convert finished");
	}
	
	public String getTagName(String tag) {
		tag = tag.replaceAll("^(\\d+\\.)+\\s?", "");
		return tag.toLowerCase().replaceAll("[^\\w]", "_").replaceAll("_+", "_");
	}
	
	
	public String getContent(String para) {
		String content = para;
		Pattern p = Pattern.compile(this.titleEmbededPattern);
		Matcher m = p.matcher(para);
		if (m.matches()) {
			content = m.group(3);
			if (!content.equals("")) {
				content = para.substring(para.indexOf(content) + content.length() + 1, para.length());	
			}			
		}
		return content;
	}
	
	public String getIndex(String para) {
		String index = "";
		Pattern p = Pattern.compile("^((\\d+\\.)+).+");
		Matcher m = p.matcher(para);
		if (m.matches()) {
			index = m.group(1);
		}
		return index;
	}
	
	public String getEmbededTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleEmbededPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return mt.group(3);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
	
	public String getTaxonName(String line) {
		line = line.replaceAll("^(\\d+\\.)+\\s?", "");
		line = line.replaceAll("[A-Z]{3}\\.\\s[A-Z]{3}\\.", "");
		if (line.indexOf(",") > 0) {
			return line.substring(0, line.indexOf(","));
		} else 
			return line;
	}
	
	public String getStandAloneTitle(String para) {
		para = para.trim();
		try {
			para = para.trim();
			Pattern p = Pattern.compile(this.titleStandOutPattern);
			Matcher mt = p.matcher(para);
			if (mt.matches()) {
				return mt.group(3);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return para;
	}	
	
	protected void ExtractTaxon() throws Exception {
		File sourceFolder = new File(folderpath);
		this.source = sourceFolder.getName();
		this.sourceFiles = sourceFolder.listFiles();
		for (File eachTxtFile : sourceFiles) {
			if (eachTxtFile.getName().startsWith(".")) {
				continue;
			}
			this.fileCount = 0;
			this.taxonCount = 0;
			this.source = eachTxtFile.getName().substring(0,
					eachTxtFile.getName().lastIndexOf("."));
			getVolume(this.source);
			System.out.println("processing file: " + this.volume);
			createOutputFolders();
			ArrayList<Taxon_spnges> taxonList = new ArrayList<Taxon_spnges>();
			priorRanks = new Hashtable<String, String>();
			try {				
				FileInputStream fstream = new FileInputStream(eachTxtFile);
				InputStreamReader is = new InputStreamReader(fstream,"UTF-8");
				BufferedReader br = new BufferedReader(is);

				// create table in database
				DatabaseAccessor.createXMLFileRelationsTable(this.volume, conn);

				String line = "";
				
				Taxon_spnges taxon = null;
				boolean contentStarted = false;/*set true when hit first taxon name*/
				boolean betweenTaxonAndFirstTitle = false;
				boolean desOfTypeSpeciesStarted = false;
				String titleContainsSubtitles = "";
				String lastTitle = "";
				String lastTypeSpeciesTitle = "";
				String desOfTypeSpeciesIndex = "";				
				
				while ((line = br.readLine()) != null) {
					if (line.equals("") || line.matches("\\d+")) {
						continue;
					}
					
					line = line.replaceAll("–", "-");
					line = cleanIllegalChars(line.trim());
					String content = line;
					String index = "";
					
					//end of file
					if (isEnd(line)) {
						break;
					}
					
					//start with the first taxon
					if (!contentStarted) {
						if (!isTaxonName(line)) {
							continue;
						} else {
							contentStarted = true;
						}
					}
					
					/*line is taxon name line*/
					if (isTaxonName(line)) {
						//store the last working taxon
						if (taxon != null) {
							taxonList.add(taxon);
							priorRanks.put(taxon.getRank(), taxon.getName());
							lastTypeSpeciesTitle = "";
							titleContainsSubtitles = "";
							lastTitle = "";
							desOfTypeSpeciesStarted = false;
							desOfTypeSpeciesIndex = "";
						}
						
						//create a new taxon
						this.taxonCount++;
						String name = getTaxonName(line);
						taxon = new Taxon_spnges(name);
						taxon.setName_info(line);
						String myRank = getRank(name);
						taxon.setRank(myRank);
						taxon.setHierarchy(getHierarchy(name, myRank, priorRanks));
						taxon.setSource(this.source);
						betweenTaxonAndFirstTitle = true;
						
						continue;						
					}
					
					/* line is title. May have two titles together:
					 * one joint title followed by separate title.*/
					if (isStandAloneTitle(line)) {
						lastTitle = getStandAloneTitle(line);
						betweenTaxonAndFirstTitle = false;
						if (isDescriptionOfTypeTitleSpecies(line)) {
							desOfTypeSpeciesStarted = true;	
							desOfTypeSpeciesIndex = getIndex(line);
							titleContainsSubtitles = lastTitle;
							lastTypeSpeciesTitle = lastTitle;
							
						} else {
							desOfTypeSpeciesStarted = false;
						}
						
						continue; /*notice: joint title will be ignored*/
					}					
					
					if (!isEmbededTitle(line)) {
						if (betweenTaxonAndFirstTitle) {
							/*put into other_info*/
							taxon.setOther_info(taxon.getOther_info() 
									+ "\n"
									+ line);
							continue;
						} else {
							/*add into specific position*/
							addPara(taxon, content, lastTitle);
						}
					} else {//is embeded title
						betweenTaxonAndFirstTitle = false;
						content = getContent(line);
						index = getIndex(line);
						if (desOfTypeSpeciesStarted && index.contains(desOfTypeSpeciesIndex)) {
							/*add this para into hashtable description_of_type_species*/
							lastTypeSpeciesTitle = addParaIntoTypeSpeciesDes(taxon, line, lastTypeSpeciesTitle, titleContainsSubtitles);	
						} else {
							desOfTypeSpeciesStarted = false;
							//another title with higher level of desoftypespecies
							lastTitle = getEmbededTitle(line);
							
							if (isDescriptionOfTypeTitleSpecies(lastTitle)) {
								desOfTypeSpeciesIndex = getIndex(line);
								lastTypeSpeciesTitle = addParaIntoTypeSpeciesDes(taxon, content, "type_species_des", lastTitle);
								desOfTypeSpeciesStarted = true;
								continue;
							}
							
							addPara(taxon, content, lastTitle);
						}
					}					
				}
				
				/*add the last taxon into taxonlist*/
				if (taxon != null) {
					taxonList.add(taxon);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			
			for (Taxon_spnges t : taxonList) {
				outputTaxon(t);
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
			String tag = m.group(3);
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
				/*place the text between first titled paragraph and title into "type_species_des"*/
				addToHashTable(taxon.getDescription_of_type_species(), "type_species_des", line);
			} else {
				String content = appendPara(ht.get(tag), line);
				ht.remove(tag);
				ht.put(tag, content);
			}
		}
		taxon.setDescription_of_type_species(ht);
		return returnValue;
	}
	
	public boolean isEnd(String line) {
		if (line.contains("LITERATURE CITED") || line.startsWith("INDEX OF")
				|| line.contains("ACKNOWLEDGEMENTS")) {
			return true;
		} else return false;
	}
}
