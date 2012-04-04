package beans;

import java.util.ArrayList;

public class ColumnBean {
	private ArrayList<String> paras;
	private ArrayList<String> types;
	private ArrayList<Integer> y1s;//coordinate of y1

	public ColumnBean(ArrayList<String> paras, ArrayList<String> types) {
		this.paras = paras;
		this.types = types;
	}
	public ColumnBean(ArrayList<String> paras, ArrayList<String> types, ArrayList<Integer> y1s) {
		this.paras = paras;
		this.types = types;
		this.y1s = y1s;
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

	public ArrayList<Integer> getY1s() {
		return y1s;
	}

	public void setY1s(ArrayList<Integer> y1s) {
		this.y1s = y1s;
	}
}
