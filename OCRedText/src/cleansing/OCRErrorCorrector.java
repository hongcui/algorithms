
/**
 *$Id$ 
 */
package cleansing;

import java.util.*;
/**
 * @author hongcui
 * This class attempts to identify errors in OCRed text by checking a compiled lexicon for the domain and WordNet
 * it also attempts to correct the errors by finding correct spellings from the OCRed text
 * 
 * Input should be the domain lexicon as an ArrayList and the file path to the source folder where the OCRed documents are saved
 * Output should be the corrected documents saved in a folder named source-spellingCorrected, a database showing the correction steps and intermediate results,
 * the database should have the name spellingCorrection_source
 */
public class OCRErrorCorrector {
	
	OCRErrorCorrector(ArrayList domainLexicon, String sourceFilePath){
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
