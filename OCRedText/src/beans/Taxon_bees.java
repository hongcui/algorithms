package beans;

import java.util.ArrayList;

public class Taxon_bees {
	private String name = "";
	private String filename = "";
	private String rank = "";
	private ArrayList<String> text = new ArrayList<String>();
	private String hierarchy = "";
	private String distribution = "";
	private ArrayList<String> type_species = new ArrayList<String>();
	private ArrayList<String> keyFiles = new ArrayList<String>();
	private int fileNumber = 0;
	
	public Taxon_bees() {
		
	}
	
	public Taxon_bees(String name, int fileNumber) {
		this.name = name;
		this.setFileNumber(fileNumber);
		this.setFilename(Integer.toString(fileNumber) + ". " + name.trim()
				.replaceAll("É", "E").replaceAll("[ÖØ]", "O").replaceAll("À", "A")
				.replaceAll("Ü", "U").replaceAll("[טיךכ]", "e").replaceAll("[נעפץצ]", "o")
				.replaceAll("[אבגדה]", "a").replaceAll("שתûü", "u").replaceAll("ÿ", "y")
				.replaceAll("[^\\w\\s,\\.\\(\\)]", "_").replaceAll("_+", "_"));	
	}
	
	public String getRank() {
		return rank;
	}
	public void setRank(String rank) {
		this.rank = rank;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public ArrayList<String> getText() {
		return text;
	}
	public void setText(ArrayList<String> text) {
		this.text = text;
	}
	public String getDistribution() {
		return distribution;
	}
	public void setDistribution(String distribution) {
		this.distribution = distribution;
	}
	public ArrayList<String> getType_species() {
		return type_species;
	}
	public void setType_species(ArrayList<String> type_species) {
		this.type_species = type_species;
	}
	public String getHierarchy() {
		return hierarchy;
	}
	public void setHierarchy(String hierarchy) {
		this.hierarchy = hierarchy;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public int getFileNumber() {
		return fileNumber;
	}

	public void setFileNumber(int fileNumber) {
		this.fileNumber = fileNumber;
	}

	public ArrayList<String> getKeyFiles() {
		return keyFiles;
	}

	public void setKeyFiles(ArrayList<String> keyFiles) {
		this.keyFiles = keyFiles;
	}
}
