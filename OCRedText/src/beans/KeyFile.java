package beans;

import java.util.ArrayList;

public class KeyFile {
	private String fileName;
	private int id = 0; 
	private String heading;
	private ArrayList<String> discussion = new ArrayList<>();
	private ArrayList<KeyStatement> statements = new ArrayList<>();
	
	public KeyFile() {
	
	}
	public KeyFile(String heading, int id) {
		this.heading = heading;
		this.id = id;
		this.fileName = heading.replaceAll("[^\\w\\s,\\.\\(\\)]", "_").replaceAll("_+", "_");
		this.fileName = Integer.toString(id) + ". " + this.fileName;
	}
	
	public ArrayList<String> getDiscussion() {
		return discussion;
	}
	public void setDiscussion(ArrayList<String> discussion) {
		this.discussion = discussion;
	}
	public String getHeading() {
		return heading;
	}
	public void setHeading(String heading) {
		this.heading = heading;
	}
	public ArrayList<KeyStatement> getStatements() {
		return statements;
	}
	public void setStatements(ArrayList<KeyStatement> statements) {
		this.statements = statements;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
}
