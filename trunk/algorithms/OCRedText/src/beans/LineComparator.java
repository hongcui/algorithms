package beans;

import java.util.Comparator;

public class LineComparator implements Comparator<Line> {
	public int compare(Line line1, Line line2) {
		int top_diff = line1.get_top_coord() - line2.get_top_coord();
		int bottom_diff = line1.get_top_coord() - line2.get_bottom_coord(); 
		if (top_diff * bottom_diff > 0) {
			return top_diff;
		} else {//same line
			return line1.get_left_coord() - line2.get_left_coord();
		} 
	}
}
