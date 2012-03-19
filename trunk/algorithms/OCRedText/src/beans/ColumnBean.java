package beans;

import java.util.ArrayList;

public class ColumnBean {
	private ArrayList<String> paras;
	private ArrayList<String> types;

	public ColumnBean(ArrayList<String> paras, ArrayList<String> types) {
		this.paras = paras;
		this.types = types;
	}

	public ArrayList<String> getParas() {
		return paras;
	}

	public void setParas(ArrayList<String> paras) {
		this.paras = paras;
	}

	public ArrayList<String> getTypes() {
		return types;
	}

	public void setTypes(ArrayList<String> types) {
		this.types = types;
	}
}
