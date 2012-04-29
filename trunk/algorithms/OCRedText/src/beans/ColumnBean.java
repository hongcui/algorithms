package beans;

import java.util.ArrayList;

public class ColumnBean {
	private ArrayList<String> paras;
	private ArrayList<String> types;
	private ArrayList<Integer> y1s;// coordinate of y1, will be saved in
									// database

	// the following 4 factors will be used to sort columns
	private int minX;
	private int maxX;
	private int maxY;
	private int minY;

	public ColumnBean(ArrayList<String> paras, ArrayList<String> types) {
		this.paras = paras;
		this.types = types;
	}

	public ColumnBean(ArrayList<String> paras, ArrayList<String> types,
			ArrayList<Integer> y1s) {
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

	public int getMinX() {
		return minX;
	}

	public void setMinX(int minX) {
		this.minX = minX;
	}

	public int getMaxX() {
		return maxX;
	}

	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	public int getMaxY() {
		return maxY;
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}
	
	public void setCoordinates(int minX, int minY, int maxX, int maxY) {
		this.maxX = maxX;
		this.minX = minX;
		this.maxY = maxY;
		this.minY = minY;
	}

	public int getMinY() {
		return minY;
	}

	public void setMinY(int minY) {
		this.minY = minY;
	}
}
