package beans;

public class Taxon {
	private String name = "";
	private String name_info = "";
	private String rank = "";
	private Integer rankNumber;
	private String nomenclature = "";
	private String description = "";
	private String other = "";
	private String discussion = "";
	private boolean reachedEnd = true;
	private String tx_hierarchy = "";
	private int index;
	private String ageAndRest = "";
	
	
	public Taxon(String name, String rank, int rankNo) {
		this.name = name;
		this.rank = rank;
		this.rankNumber = rankNo;
	}
	
	public Taxon(String name, String name_info, String rank, int rankNo, String description, String other, boolean reachedEnd, String tx_hierarchy) {
		this.name = name;
		this.name_info = name_info;
		this.rank = rank;
		this.rankNumber = rankNo;
		this.description = description;
		this.other = other;
		this.reachedEnd = reachedEnd;
		this.tx_hierarchy = tx_hierarchy;
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
	public Integer getRankNumber() {
		return rankNumber;
	}
	public void setRankNumber(Integer rankNumber) {
		this.rankNumber = rankNumber;
	}
	public String getNomenclature() {
		return nomenclature;
	}
	public void setNomenclature(String nomenclature) {
		this.nomenclature = nomenclature;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getOther() {
		return other;
	}
	public void setOther(String other) {
		this.other = other;
	}
	public boolean isReachedEnd() {
		return reachedEnd;
	}
	public void setReachedEnd(boolean reachedEnd) {
		this.reachedEnd = reachedEnd;
	}

	public String getDiscussion() {
		return discussion;
	}

	public void setDiscussion(String discussion) {
		this.discussion = discussion;
	}

	public String getName_info() {
		return name_info;
	}

	public void setName_info(String name_info) {
		this.name_info = name_info;
	}

	public String getTx_hierarchy() {
		return tx_hierarchy;
	}

	public void setTx_hierarchy(String tx_hierarchy) {
		this.tx_hierarchy = tx_hierarchy;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getAgeAndRest() {
		return ageAndRest;
	}

	public void setAgeAndRest(String ageAndRest) {
		this.ageAndRest = ageAndRest;
	}

}
