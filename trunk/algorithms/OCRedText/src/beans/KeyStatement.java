package beans;

import java.util.ArrayList;

public class KeyStatement { 
	private String id;
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
}
