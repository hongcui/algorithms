package beans;

import java.util.Hashtable;

public class Taxon_spnges {
	
	private String file_name = "";/*save each taxon in one file with taxon name*/

	/*static tags*/
	/*meta*/
	private String source = "";
	
	/*nomenclature*/
	private String name = "";
	private String rank = "";
	private String name_info = "";
	private String hierarchy = "";
	
	/*1st level tags*/
	private String type_species = "";
	private String synonym = "";
	private String age = "";
	private String distribution = "";
	private String scope = "";
	private String other_info = "";
	
	/*2nd level hash tables*/
	private Hashtable<String, String> description = null;
	private Hashtable<String, String> description_of_type_species = null;
	private Hashtable<String, String> discussion = null;
	
	
	public Taxon_spnges(String name) {
		this.name = name;
		
		//deal with special characters in taxon name
		this.file_name = name.replaceAll("[^\\w]", "_").replaceAll("_+", "_")
				.replaceAll("Щ", "E").replaceAll("[жи]", "O").replaceAll("Р", "A")
				.replaceAll("м", "U").replaceAll("щ", "e");
		description = new Hashtable<String, String>();
		description_of_type_species = new Hashtable<String, String>();
		discussion = new Hashtable<String, String>();
	}
	
	/*set and get functions*/
	public String getFile_name() {
		return file_name;
	}
	public void setFile_name(String file_name) {
		this.file_name = file_name;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getRank() {
		return rank;
	}
	public void setRank(String rank) {
		this.rank = rank;
	}
	public String getName_info() {
		return name_info;
	}
	public void setName_info(String name_info) {
		this.name_info = name_info;
	}
	public String getHierarchy() {
		return hierarchy;
	}
	public void setHierarchy(String hierarchy) {
		this.hierarchy = hierarchy;
	}
	public String getType_species() {
		return type_species;
	}
	public void setType_species(String type_species) {
		this.type_species = type_species;
	}
	public String getSynonym() {
		return synonym;
	}
	public void setSynonym(String synonym) {
		this.synonym = synonym;
	}
	public String getAge() {
		return age;
	}
	public void setAge(String age) {
		this.age = age;
	}
	public String getDistribution() {
		return distribution;
	}
	public void setDistribution(String distribution) {
		this.distribution = distribution;
	}
	public Hashtable<String, String> getDescription() {
		return description;
	}
	public void setDescription(Hashtable<String, String> description) {
		this.description = description;
	}
	public Hashtable<String, String> getDescription_of_type_species() {
		return description_of_type_species;
	}
	public void setDescription_of_type_species(
			Hashtable<String, String> description_of_type_species) {
		this.description_of_type_species = description_of_type_species;
	}
	public Hashtable<String, String> getDiscussion() {
		return discussion;
	}
	public void setDiscussion(Hashtable<String, String> discussion) {
		this.discussion = discussion;
	}
	public String getScope() {
		return scope;
	}
	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getOther_info() {
		return other_info;
	}

	public void setOther_info(String other_info) {
		this.other_info = other_info;
	}

}
