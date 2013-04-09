package beans;

import java.util.ArrayList;

public class KeyStatement { 
	private String id;
	private String statement;
	private String next_id;
	private String determination;
	
	
	private String from_id;
	private ArrayList<KeyChoice> choices = new ArrayList<KeyChoice>();
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}

	public String getFrom_id() {
		return from_id;
	}
	public void setFrom_id(String from_id) {
		this.from_id = from_id;
	}
	public ArrayList<KeyChoice> getChoices() {
		return choices;
	}
	public void setChoices(ArrayList<KeyChoice> choices) {
		this.choices = choices;
	}
	public String getStatement() {
		return statement;
	}
	public void setStatement(String statement) {
		this.statement = statement;
	}
	public String getNext_id() {
		return next_id;
	}
	public void setNext_id(String next_id) {
		this.next_id = next_id;
	}
	public String getDetermination() {
		return determination;
	}
	public void setDetermination(String determination) {
		this.determination = determination;
	}
}
