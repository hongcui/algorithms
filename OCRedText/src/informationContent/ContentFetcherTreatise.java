/**
 * 
 */
package informationContent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import db.DatabaseAccessor;
import fna.charactermarkup.ChunkedSentence;
/**
 * @author hongcui
 * this class removes the assumption from ContentFetcher that a paragraph is terminated by a blank line
 */
public class ContentFetcherTreatise extends ContentFetcher {
	private int currentpagenumber = 0;
	public String nonendings = "p\\.|pl\\.|fig\\.|Fig\\.|,|;|>|<|/|`|~|@|#|$|%|^|&|\\*|\\(|\\[|\\|:|\\+|=|-|_";
	private String abbreviation = "\\b[a-zA-Z]\\.|\\d+\\.\\d+|cf\\.|sp\\.|subg\\.|pl\\.|fig\\.|Fig\\.";
	/**
	 * @param pageNumberText
	 * @param footNoteTokens
	 * @param sourceFilePath
	 */
	public ContentFetcherTreatise(ArrayList pageNumberText,
			ArrayList footNoteTokens, String sourceFilePath) {
		super(pageNumberText, footNoteTokens, sourceFilePath);
		// TODO Auto-generated constructor stub
	}

	/*
	 *simply add paragraphs to the paragraphs table
	 *
	 */
	protected void readParagraphs(){
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> sources = new ArrayList<String>();
		int count = 0;
		int paraID = 0;
		File[] allFiles = sourceFile.listFiles();
		
		for(int i =0; i<allFiles.length; i++){
			String source = allFiles[i].getName();
			
			//read this file line by line, concat the lines that make a paragraph. 
			//A paragraph ends at a end-of-line period that does not reach the full length of a line
			try{
			    FileInputStream fstream = new FileInputStream(allFiles[i]);
			    DataInputStream in = new DataInputStream(fstream);
			    BufferedReader br = new BufferedReader(new InputStreamReader(in));
			    String line = "";
			    
			    //read text into buffer,collect info on linelength
			    Pattern p1 = Pattern.compile("(.*?[a-z])([A-Z][a-z]+$)");
			    Pattern p2 = Pattern.compile("(^FIG\\. \\d+\\. .*|^TABLE \\d+\\. .*)");
			    ArrayList<String> lines = new ArrayList<String>();
			    ArrayList<Integer> lengths = new ArrayList<Integer>(); 
			    while ((line = br.readLine()) != null){
			    	String l = line.trim();
			    	if(l.matches("^((TABLE OF )?CONTENTS|(Table [Oo]f )?Contents)$")){ 
			    		this.hasToCHeading = true;
			    	}
			    	if(l.matches(".*?[a-zA-Z]\\s*[\\.]+\\s*[A-D]?[ivx\\d]+$")){
			    		this.hasToCDots = true;
			    	}
			    	if(l.matches("INDEX")){
			    		this.hasIndex = true;
			    	}
			    	l = l.replaceAll("–", "-");
			    	lengths.add(new Integer(l.length()));	
			    	//fix some page numbers. The word is attached to the end of the last paragraph
			    	Matcher m = p1.matcher(l);
			    	if(m.matches()){ //CenoReferences\n161
			    		line = br.readLine();
			    		String l1 = m.group(1);
			    		String l2 = m.group(2);
			    		if(line!=null && line.trim().matches("\\d+")){
			    			lines.add(l1);
			    			lines.add(l2+" "+line);
			    		}
			    	}else{
				    	//fix Fig captions
				    	m = p2.matcher(l);
				    	if(m.matches()){
				    		StringBuffer sb = new StringBuffer();
				    		while(!l.endsWith(".")){
				    			sb.append(" "+l);
				    			line = br.readLine();
				    			if(line !=null) l = line.trim();
				    		}
				    		String fig = sb.append(" "+l).toString();
				    		//System.out.println("Fig: "+fig);
				    		lines.add(fig);
				    	}else{
				    		lines.add(l);//other lines
				    	}
			    	}
			    }
				Collections.sort(lengths);
				this.lineLength = lengths.get(lengths.size()/2).intValue();
				System.out.println("median line length is "+this.lineLength+" characters");
				
				//collect paragraphs
				Iterator<String> it = lines.iterator();
			    StringBuffer para = new StringBuffer();
			    while (it.hasNext()){
			    	line = it.next();
			    	String l = line.trim();
			    	//page numbers
			    	boolean ispn = isPageNumber(l) || isFigureTable(l);
			    	if(ispn){//a new paragraph on its own
			    		if(para.toString().trim().length()>0){//add last paragraph
				    		/*paraIDs.add(paraID+"");
		    				paraID++;
			    			paras.add(para.toString());
			    			sources.add(source);
			    			count++;
			    			*/
			    			int[] paraID_count= holdParagraph(para, paraIDs, paras, sources, paraID, count, source);
			    			paraID = paraID_count[0];
			    			count = paraID_count[1];

				    		para = new StringBuffer();
			    		}
			    		
	    				paraIDs.add(paraID+""); //add page number paragraph
	    				paraID++;
		    			paras.add(l);
		    			sources.add(source);
		    			count++;
		    			continue;
	    			}
			    	//headings
			    	boolean ish = isHeading(l) && !hasUnclosedLeftBracket(l);
			    	if(ish && (para.toString().trim().length()==0 || para.toString().trim().endsWith("."))){//a new paragraph on its own
			    		if(para.toString().trim().length()>0){//add last paragraph
				    		/*paraIDs.add(paraID+"");
		    				paraID++;
			    			paras.add(para.toString());//add last paragraph
			    			sources.add(source);
			    			count++;*/
			    			int[] paraID_count= holdParagraph(para, paraIDs, paras, sources, paraID, count, source);//
			    			paraID = paraID_count[0];
			    			count = paraID_count[1];
			    			
				    		para = new StringBuffer();
			    		}
			    		
	    				paraIDs.add(paraID+""); //add heading
	    				paraID++;
		    			paras.add(l);//use para instead of content to preserve leading spaces
		    			sources.add(source);
		    			count++;
		    			continue;
	    			}
			    	//if this line is the end of a paragraph
				   	if(l.compareTo("") == 0 || !hasUnclosedLeftBracket(l) && (
				   			(l.matches(".*?[^A-Z]\\.$") /*&& l.length() < this.lineLength*/) ||
				   			 (l.length()<this.lineLength*2/3) && !l.matches(".*?\\W$"))){
				   		para.append(line).append(" ");
			    		String content = para.toString().trim();
			    		if(content.length()>0){
			    			int[] paraID_count= holdParagraph(para, paraIDs, paras, sources, paraID, count, source);
			    			paraID = paraID_count[0];
			    			count = paraID_count[1];
			    					    			
			    			para= new StringBuffer();
			    			if(count >=100){ //in each batch, add 100 paragraphs.
			    				addParagraphs(paraIDs, paras, sources);
			    				paraIDs = new ArrayList();
			    				paras = new ArrayList<String>();
			    				sources = new ArrayList<String>();
			    				count = 0;
			    			}
			    			
			    		}
			    	}else{
			    		line = line.replaceAll(System.getProperty("line.separator"), "");//use line instead of l to preserve leading spaces
			    		para.append(line).append(" ");
			    	}
			     
			    }
			    in.close();
			    }catch (Exception e){
			    	e.printStackTrace();
			    }
		}
		
	this.currentpagenumber = 0; //reset to 0 for idContent	
	}
	
	private int[] holdParagraph(StringBuffer para, ArrayList<String> paraIDs,
			ArrayList<String> paras, ArrayList<String> sources, int paraID, int count, String source) {
		if(para.toString().matches("^\\s*\\[.*")){
			String[] twoparts = para.toString().split("(?<=\\]) (?=[A-Z])");
			paraIDs.add(paraID+"");
			paraID++;
			paras.add(twoparts[0]);//use para instead of content to preserve leading spaces
			sources.add(source);
			count++;
			if(twoparts.length>1){
				paraIDs.add(paraID+"");
				paraID++;
				paras.add(twoparts[1]);//use para instead of content to preserve leading spaces
				sources.add(source);
				count++;
			}
		}else{
			paraIDs.add(paraID+"");
			paraID++;
			paras.add(para.toString());//use para instead of content to preserve leading spaces
			sources.add(source);
			count++;
		}
		return new int[]{paraID, count};
	}
	
	//have unclosed left bracket
	private boolean hasUnclosedLeftBracket(String l) {
		l = l.replaceAll("[(\\[]", " ( ").replaceAll("[)\\]]", " ) ");
		String[] words = l.split("\\s+");
		int left = 0;
		int right = 0;
		for(int i = 0; i < words.length; i++){
			if(words[i].compareTo("(") == 0){
				left++;
			}
			if(words[i].compareTo(")") == 0){
				right++;
			}
		}
		if(left > right){
			return true;
		}
		return false;
	}
	
	private boolean isPageNumber(String l){
		if(l.matches("[ivx]+") && this.currentpagenumber==0){//page number
			System.out.println("page number:"+l);
			return true;
		}
		if(isHeading(l)&&(l.matches("^\\d+ [^\\d]+") ||l.matches("[^\\d]+[a-z] \\d+$"))){
			int pn = Integer.parseInt(l.replaceAll("[^\\d]", "").trim());
			if(pn > this.currentpagenumber && pn <= this.currentpagenumber+10){
				System.out.println("page number:"+l);
				this.currentpagenumber = pn;
				return true;
			}
		}
		/*if(l.matches("\\d+")){
			System.out.println("page number:"+l);
			return true;
		}*/
		return false;	
	}

	private boolean isFigureTable(String l) {
		if(l.matches("^FIG\\. \\d+\\. .*?.$")){
			System.out.println("FIG. number:"+l);
			return true;
		}
		if(l.matches("^TABLE \\d+\\. .*?.$")){
			System.out.println("TABLE number:"+l);
			return true;
		}
		return false;
	}

	private boolean isHeading(String l) {
		// TODO Auto-generated method stub
		if(l.indexOf("...")>0){ //table of content headings
			return true;
		}

		
		if(l.matches("^[a-z].*") || l.matches(".*? ([a-z]+|.*,|.*;)$") || l.toLowerCase().matches(".*?\\b("+ChunkedSentence.stop+"|"+ChunkedSentence.prepositions+"|.*?\\W)$")){ //, or ; at the end or a lower case word at the end
			return false;
		}
		String[] words = l.replaceAll("\\d+", "").replaceAll("(?<!\\w)\\W(?!\\w)", " ").replaceAll("\\b("+ChunkedSentence.stop+"|"+ChunkedSentence.prepositions+")\\b", "").trim().split("\\s+");
		int capitals = 0;
		for(int i = 0; i<words.length; i++){
			if(words[i].compareTo(words[i].toLowerCase())!=0){
				capitals++;
			}
		}
		if(capitals == words.length){
			return true;
		}
		return false;
	}

	
	/**
	 * need to traceBack if a figure is found/table is found
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	protected boolean isFigureTable(int paraID, String para, String source) {
		return isFigureTable(para.trim());
		/*para = para.trim();
		if(isFigureTable(para)){
			//traceBack
			traceBackFigTblContent(paraID, source);
			return true;
		}
		return false;
		*/
	}
	
	protected boolean isPageNumber(int paraID, String para, String source) {
		return isPageNumber(para);
	}
	
	
	/**
	 * para is an interrupting point if:
	 * 0. para starts with a non word symbol
	 * 1. para starts with a lower case letter, or
	 * 2. para starts with a number but is not part of a numbered list
	 * 3. the previous content para did not end with "."
	 * @param para
	 * @return
	 */
	protected boolean isInterruptingPoint(int paraID, String para, String source) {
		para = para.trim();
		if(para.matches("\\[.{5,}\\]")){//[nomenclature]
			return false;
		}
		if(para.matches("^\\W.*")){ //case 0
			return true;
		}
		//case 1 + 2
		Pattern pattern = Pattern.compile("^([_ (\\[a-z0-9)\\]].*?)($|[\\.,]\\s+[A-Z].*)");//start with a lower case letter or a number
		Matcher m = pattern.matcher(para);
		if(m.matches()){
			String start = m.group(1);
			start = start.replaceAll("[(\\[\\])]", "");
			start = start.replaceAll("[Il]", "1").replaceAll("[\\.\\s]", ""); //OCR errors, mistaken 1 as I or l. Make 2 Ia. =>21a. Make 2.1 =>21
			
			if(start.matches("\\d+") || start.matches("[a-zA-Z]") || start.matches("\\d+[a-zA-Z]")){//matches 2, a, 2a, 1920
				return false; //bullets
			}
			if(/*start.matches("^[a-z].*") &&*/ start.length()>1){
				return true; //else
			}
		}
		//case 3
		if(!para.startsWith("[") && !para.matches("^[A-Z][a-z].*")){
			try{
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix, "source='"+source+"' and paraID<"+paraID+" and type = 'content'", "paraID desc", paraIDs, paras, conn);
				if(paras.size()>=1){
					String lastcontent = (String)paras.get(0).trim();
					if(!lastcontent.matches(".*?\\b("+nonendings+")\\.$")){
						return false; //last content not ends with a nonending token.
					}else{
						return true; //last content ends with a nonending token.
					}
				}
			}catch (Exception e){
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * footnote should appear at least countThreshold times in the document.
	 * If it is not in the footNoteTokens, it should appear at least twice as often to be considered footnote
	 * @param para
	 * @return
	 */
	protected boolean startWithAToken(String para, String source) {
		return false; //we don't do footnotes for this volume, so return false.
		//what should be here is a list of tokens such as "*" or "1" that starts a footnote. 
	}
	
	/**
	 * searching for those in one page limit for figure and table content words. 
	 * @param paraID
	 * @param source
	 */
	protected void traceBackFigTblContent(int paraID, String source) {
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			//separate Figs from Tables
			String condition = "source='"+source+"'and paraID = "+paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID", paraIDs, paras, conn);
			if(paras.size()>=1){
				if(paras.get(0).matches("^(TABLE|Table) \\d+\\..*")){
					traceBackTableContent(paraID, source);
				}else{
					traceBackFigContent(paraID, source);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * search range is from paraID to the next heading, figtbl, or pagenum 
	 * there must be at least 1 tbl_txt after a TABLE
	 * @param paraID
	 * @param source
	 */
	private void traceBackTableContent(int paraID, String source) {
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			//find the paraID for the next heading
			int end = tableSearchRange(paraID, source);
						
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			String condition = "source='"+source+"'and paraID < "+end+" and paraID > "+paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs, paras, conn);
			int count = 0;
			for(int i = 0; i < paras.size(); i++){
				String p = paras.get(i);
				p = p.replaceAll("("+abbreviation +")", "");
				if(p.indexOf(".")<0){//table content contains no periods.
					int pID = Integer.parseInt((String)paraIDs.get(i));
					markAsType(pID, "noncontent_tbl_txt");
					count++;
				}else{
					if(count==0){//must have at least 1 tbl_txt
						int pID = Integer.parseInt((String)paraIDs.get(i));
						markAsType(pID, "noncontent_tbl_txt");
					}
					break;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
		
	/**
	 * table contents may be taken as headings. Find the reasonable search range for table content.
	 * @param paraID
	 * @param source
	 * @return
	 */
	private int tableSearchRange(int paraID, String source){
		int end = 0+0;
		int pagelim = 10000000;
		int figtbllim = 10000000;
		int headinglim = 10000000;
		int headingcounts = 0;
		int figtblcounts = 0;
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			String condition = "source='"+source+"'and type like '%pagenum%' and paraID > "+paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs, paras, conn);
			//find the paraID for the next pagenum
			if(paraIDs.size()>0){
				pagelim = Integer.parseInt(paraIDs.get(0));
				end = pagelim;
			}
			
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source='"+source+"'and type like '%figtbl%' and paraID > "+paraID+" and paraID < "+pagelim;
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs, paras, conn);
			//find the paraID for the next figtbl
			if(paraIDs.size()>0){
				figtbllim = Integer.parseInt(paraIDs.get(0));
				end = figtbllim;
			}
			
			int closerlim = (pagelim < figtbllim? pagelim : figtbllim);
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source='"+source+"'and type like '%heading%' and paraID > "+paraID +" and paraID < "+ closerlim;
			
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs, paras, conn);
			//find the paraID for the next heading
			if(paraIDs.size()>0){
				headinglim = Integer.parseInt(paraIDs.get(0));
				headingcounts = paraIDs.size();
				if(headingcounts < closerlim-paraID && headinglim - paraID > 3){
					end = headinglim;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return end;
	}
	

	private void traceBackFigContent(int paraID, String source){
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			//find the paraID for the last pagenum
			int lastpage = 0;
			String condition = "source='"+source+"'and type like '%pagenum%' and paraID < "+paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID desc", paraIDs, paras, conn);
			if(paraIDs.size()>=1){
				lastpage = Integer.parseInt(paraIDs.get(0));
			}
			//find the paraID for the current pagenum
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			int thispage = 0;
			condition = "source='"+source+"'and type like '%pagenum%' and paraID > "+paraID;
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID", paraIDs, paras, conn);
			if(paraIDs.size()>=1){
				thispage = Integer.parseInt(paraIDs.get(0));
			}
			
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source='"+source+"'and paraID < "+thispage+" and paraID > "+lastpage;
			DatabaseAccessor.selectParagraphs(prefix, condition, "", paraIDs, paras, conn);
			for(int i =paraIDs.size()-1; i>=0; i--){
				String p = paras.get(i);
				if(p.replaceAll("\\b([a-zA-Z]|[A-Z][a-z]+|[0-9][a-z]|[a-zA-Z][0-9]+|[0-9]+)\\b","").trim().length()==0 //match any of Abc Abc a 1a A E F12 e1
						 && p.length()<=this.lineLength*2/3){ //either taxon names, or 1a 3c
					int pID = Integer.parseInt((String)paraIDs.get(i));
					markAsType(pID, "noncontent_fig_txt");
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		String sourceFilePath="X:\\DATA\\Treatise\\recent\\text\\test";
		ArrayList<String> pageNumberText = new ArrayList<String>();
		ArrayList<String> footNoteTokens = new ArrayList<String>();
		ContentFetcherTreatise cf = new ContentFetcherTreatise(pageNumberText, footNoteTokens, sourceFilePath);
		cf.readParagraphs();
		cf.idContent();
		
	}

	

}
