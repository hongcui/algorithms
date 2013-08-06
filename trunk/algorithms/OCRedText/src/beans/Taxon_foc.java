package beans;

import java.util.ArrayList;

public class Taxon_foc {
	private int output_file_count;
	private String taxon_index;
	private String taxon_name;
	private String rank;
	private String hierarchical_name;
	private String output_file_name;
	private String name_info; // the complete name line
	private String common_name;
	private String synonym;
	private ArrayList<String> descriptions;
	private ArrayList<String> discussions;
	private String hab_ele_dis;// Habitat, elevation and distribution
	private String key_file_name;
	private String author; // only for families

	public Taxon_foc(int file_count) {
		this.output_file_count = file_count;
	}

	public String getTaxon_index() {
		return taxon_index;
	}

	public void setTaxon_index(String taxon_index) {
		this.taxon_index = taxon_index;
	}

	public String getTaxon_name() {
		return taxon_name;
	}

	public void setTaxon_name(String taxon_name) {
		this.taxon_name = taxon_name;
		this.setOutput_file_name(Integer.toString(this.output_file_count)
				+ ". "
				+ taxon_name.trim().replaceAll("É", "E")
						.replaceAll("[ÖØ]", "O").replaceAll("À", "A")
						.replaceAll("Ü", "U").replaceAll("[טיךכ]", "e")
						.replaceAll("[נעפץצ]", "o").replaceAll("[אבגדה]", "a")
						.replaceAll("שתûü", "u").replaceAll("ÿ", "y")
						.replaceAll("[^\\w\\s,\\.\\(\\)]", "_")
						.replaceAll("_+", "_"));
	}

	public String getName_info() {
		return name_info;
	}

	public void setName_info(String name_info) {
		this.name_info = name_info;
	}

	public String getOutput_file_name() {
		return output_file_name;
	}

	public void setOutput_file_name(String output_file_name) {
		this.output_file_name = output_file_name;
	}

	public String getCommon_name() {
		return common_name;
	}

	public void setCommon_name(String common_name) {
		this.common_name = common_name;
	}

	public ArrayList<String> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(ArrayList<String> descriptions) {
		this.descriptions = descriptions;
	}

	public String getSynonym() {
		return synonym;
	}

	public void setSynonym(String synonym) {
		this.synonym = synonym;
	}

	public ArrayList<String> getDiscussions() {
		return discussions;
	}

	public void setDiscussions(ArrayList<String> discussions) {
		this.discussions = discussions;
	}

	public String getKey_file_name() {
		return key_file_name;
	}

	public void setKey_file_name(String key_file_name) {
		this.key_file_name = key_file_name;
	}

	public int getOutput_file_count() {
		return output_file_count;
	}

	public void setOutput_file_count(int output_file_count) {
		this.output_file_count = output_file_count;
	}

	public String getRank() {
		return rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public String getHierarchical_name() {
		return hierarchical_name;
	}

	public void setHierarchical_name(String hierarchical_name) {
		this.hierarchical_name = hierarchical_name;
	}

	public String getHab_ele_dis() {
		return hab_ele_dis;
	}

	public void setHab_ele_dis(String hab_ele_dis) {
		this.hab_ele_dis = hab_ele_dis;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

}
