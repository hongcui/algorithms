package beans;

public class ParagraphBean {
	private int originalID;
	private String para;
	private String source;
	private String note = "";

	public void normalize() {
		para.replaceAll("(?<=[a-z])-\\s+(?=[a-z])", "-")
				.replaceAll("\\s+", " ").replaceAll("\\^", "").replaceAll("\\“", "\"").replaceAll("\\”", "\"");
	}

	public ParagraphBean(String para, int originalID) {
		this.para = para;
		this.originalID = originalID;
	}

	public String getPara() {
		return para;
	}

	public void setPara(String para) {
		this.para = para;
	}

	public int getOriginalID() {
		return originalID;
	}

	public void setOriginalID(int originalID) {
		this.originalID = originalID;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note; 
	}
}
