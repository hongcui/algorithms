package beans;

public class Line {
	private int left_coord;
	private int bottom_coord;
	private int right_coord;
	private int top_coord;
	private String text;
	
	public Line(String t) {
		this.set_text(t);
	}
	
	public Line(String t, int left, int bottom, int right, int top) {
		this.text = t;
		this.left_coord = left;
		this.bottom_coord = bottom;
		this.right_coord = right;
		this.top_coord = top;
	}

	public int get_left_coord() {
		return left_coord;
	}

	public void set_left_coord(int left_coord) {
		this.left_coord = left_coord;
	}

	public int get_bottom_coord() {
		return bottom_coord;
	}

	public void set_bottom_coord(int bottom_coord) {
		this.bottom_coord = bottom_coord;
	}

	public int get_right_coord() {
		return right_coord;
	}

	public void set_right_coord(int right_coord) {
		this.right_coord = right_coord;
	}

	public int get_top_coord() {
		return top_coord;
	}

	public void set_top_coord(int top_coord) {
		this.top_coord = top_coord;
	}

	public String get_text() {
		return text;
	}

	public void set_text(String text) {
		this.text = text;
	}
}
