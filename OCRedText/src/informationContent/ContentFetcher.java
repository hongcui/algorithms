/**
 * 
 */
package informationContent;

import java.util.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.regex.*;


import db.DatabaseAccessor;

/**
 * @author hongcui
 * This class fetch the information content from a (OCRed) text document, where the flow of information content may be interrupted
 * by page numbers, footnotes, figures, and tables, etc.
 * 
 * The class applies the following simple heuristics:
 * 1. a blank line terminates a paragraph
 * 2. page numbers are either at the beginning of a paragraph or at the end, with or without accompanying strings
 * 3. a lower case letter starting a paragraph is the sign that the information content is interrupted by page numbers, footnotes, or figures/tables.
 * 4. anything before the first long text paragraph is considered non-content. they may be table of content.
 * 5. anything after the last long text paragraph is considered non-content. They may be end-of-book index.
 * 6. the user may or may not be able to provide the text strings accompanying page numbers and to provide the tokens starting footnotes  
 *
 *
 * Input includes pageNumberText as an ArrayList, footNoteTokens as an ArrayList, and the source file path pointing to the folder where the OCRed text may be found
 * Output is a set of tables whoes names are prefixed with "prefix" in a database named "sourceDatasets".
 *
 * The user will need to create the database "sourceDatasets" and the user "termuser" before running this class
 */
public class ContentFetcher {
	private ArrayList pageNumberText = null;
	private ArrayList footNoteTokens = null;
	private File sourceFile = null;
	private String prefix = null;
	private int lineLength = 78;
	private Connection conn = null;
	private static String url = ApplicationUtilities.getProperty("database.url");
	/**
	 * 
	 */
	public ContentFetcher(ArrayList pageNumberText, ArrayList footNoteTokens, String sourceFilePath) {
		this.pageNumberText = pageNumberText;
		this.footNoteTokens = footNoteTokens;
		this.sourceFile = new File(sourceFilePath);
		this.prefix = sourceFile.getName();
		//create paragraphs table
		try{
			//paraID not null primary key longint, source varchar(50), paragraph text(5000), type varchar(10), add2last varchar(5), remark varchar(50)
			Class.forName(ApplicationUtilities.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
			DatabaseAccessor.createParagraphTable(this.prefix, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		//readParagraphs(); //populate paragraph table
		//idContent();      //identify content paragraphs (footnotes, headnotes/page numbers, figures, and tables are not content paragraphs
	}
	
	/*
	 *simply add paragraphs to the paragraphs table
	 *
	 */
	public void readParagraphs(){
		ArrayList<String> paraIDs = new ArrayList<String>();
		ArrayList<String> paras = new ArrayList<String>();
		ArrayList<String> sources = new ArrayList<String>();
		int count = 0;
		File[] allFiles = sourceFile.listFiles();
		for(int i =0; i<allFiles.length; i++){
			String source = allFiles[i].getName();
			int paraID = 0;
			//read this file line by line, concat the lines that make a paragraph, use heuristics 1: H1
			try{
			    FileInputStream fstream = new FileInputStream(allFiles[i]);
			    DataInputStream in = new DataInputStream(fstream);
			    BufferedReader br = new BufferedReader(new InputStreamReader(in));
			    String line = "";
			    StringBuffer para = new StringBuffer();
			    while ((line = br.readLine()) != null){
			    	String l = line.trim();
			    	if(l.compareTo("") == 0 || l.length() <= this.lineLength * 2/3){
			    		para.append(line).append(" ");
			    		String content = para.toString().trim();
			    		if(content.length()>0){
			    			paraIDs.add(paraID+"");
			    			paras.add(para.toString());//use para instead of content to preserve leading spaces
			    			sources.add(source);
			    			count++;
			    			if(count >=100){
			    				addParagraphs(paraIDs, paras, sources);
			    				paraIDs = new ArrayList();
			    				paras = new ArrayList<String>();
			    				sources = new ArrayList<String>();
			    				count = 0;
			    			}
			    			paraID++;
			    			para= new StringBuffer();			    			
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
		
		
	}
	/**
	 * preserve original case and leading spaces
	 * @param paraID
	 * @param para
	 * @param source
	 */
	private void addParagraphs(ArrayList paraIDs, ArrayList paras, ArrayList sources){
		
		try{
			DatabaseAccessor.insertParagraphs(this.prefix, paraIDs, paras, sources, conn);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	/* 
	 * columns in paragraphs: paraID, source, paragraph, type, add2last, remark 
	 * use heuristics 2-4
	 * type: one of name, description, description-mixed, other-content, content, non-content, and unassigned
	 * add2last: yes,no, uncertain
	 * 
	 * this function uses only content, non-content, and unassigned and yes/no/uncertain for add2last
	*/
	public void idContent(){
		File[] allFiles = sourceFile.listFiles();
		for(int i =0; i<allFiles.length; i++){
			String source = allFiles[i].getName();
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			try{
				//prolog
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "type='unassigned'", "paraID", paraIDs, paras, conn);

				for( int j = 0; j<paraIDs.size(); j++){
					int paraID = Integer.parseInt((String)paraIDs.get(j));
					String para = (String)paras.get(j);
					if(para.trim().length() < this.lineLength){ //H4
						markAsType(paraID, "noncontent_prolog");
					}else{
						break;
					}
				}
				//epilogue
				paraIDs = new ArrayList<String>();
				paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "type='unassigned'", "paraID desc", paraIDs, paras, conn);
				for( int j = 0; j<paraIDs.size(); j++){
					int paraID = Integer.parseInt((String)paraIDs.get(j));
					String para = (String)paras.get(j);
					if(para.trim().length() < 75 ){ //H5
						markAsType(paraID, "noncontent_epilog");
					}else{
						break;
					}
				}
				//body
				paraIDs = new ArrayList<String>();
				paras = new ArrayList<String>();
				
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "type='unassigned'", "paraID", paraIDs, paras, conn);
				for( int j = 0; j<paraIDs.size(); j++){
					int paraID = Integer.parseInt((String)paraIDs.get(j));
					String para = (String)paras.get(j);
					if(isFigureTable(paraID, para, source)){ //also take care of labels for sub-figures //H2 H6
						markAsType(paraID, "noncontent_figtbl");
						continue;
					}
					if(isPageNumber(paraID, para, source)){ //H2 H6
						markAsType(paraID, "noncontent_pagenum");
						continue;
					}
					if(isFootNote(paraID, para, source)){ //H2 H6
						markAsType(paraID, "noncontent_footnote");
						continue;
					}
					if(isShortTexts(paraID, para, source)){ //
						markAsType(paraID, "noncontent_shorttext");
						continue;
					}
					if(isInterruptingPoint(paraID, para, source)){	//H3
						markAdd2Last(paraID, "");				//set add2last
					}
					markAsType(paraID, "content");
				}
				
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * text and its preceding or followiing n text are also very short
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	private boolean isShortTexts(int paraID, String para, String source) {

		int limit = 5;
		if(para.trim().length() < this.lineLength*2/3){
			try{
				//preceding
				int preLimit = paraID - limit;
				ArrayList<String> paraIDs = new ArrayList<String>();
				ArrayList<String> paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "paraID>="+preLimit+" and paraID <"+paraID+" and length(paragraph) < "+this.lineLength*2/3, "", paraIDs, paras, conn);
				if(paras.size()==limit)
				{return true;}
				//following
				int folLimit = paraID + limit;
				paraIDs = new ArrayList<String>();
				paras = new ArrayList<String>();
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "paraID<="+folLimit+" and paraID >"+paraID+" and length(paragraph) < "+this.lineLength*2/3, "", paraIDs, paras, conn);
				if(paras.size()==limit)
				{return true;} 
			}catch (Exception e){
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * find 
	 * @param paraID
	 * @param source
	 */
	private void markAdd2Last(int paraID, String suffix) {
		String set = "add2last='yes'";
		String condition = "paraID="+paraID;
		try{
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	/**
	 * para is an interrupting point if:
	 * 1. para starts with a lower case letter, or
	 * 2. para starts with a number but is not part of a numbered list
	 * @param para
	 * @return
	 */
	private boolean isInterruptingPoint(int paraID, String para, String source) {
		para = para.trim();

		Pattern pattern = Pattern.compile("^([a-z0-9].*?)\\.($|\\s+[A-Z].*)");//start with a lower case letter or a number
		Matcher m = pattern.matcher(para);
		if(m.matches()){
			String start = m.group(1);
			start = start.replaceAll("[Il]", "1").replaceAll("[\\.\\s]", ""); //OCR errors, mistaken 1 as I or l. Make 2 Ia. =>21a. Make 2.1 =>21
			
			if(start.matches("\\d+") || start.matches("[a-zA-Z]") || start.matches("\\d+[a-zA-Z]")){//matches 2, a, 2a, 1920
				return false; //bullets
			}
			if(/*start.matches("^[a-z].*") &&*/ start.length()>1){
				return true; //para starts with a lower case letter or a number
			}
		}
				
		/*String first = para.substring(0, 1);
		Pattern pattern = Pattern.compile("[a-z]");
		Matcher m = pattern.matcher(first);
		if(m.matches()){
			return true; //para starts with a lower case letter
		}*/
		//if start with a number, then number+1 is not the starting token of the next content paragraph
		/*pattern = Pattern.compile("^(\\d+)(.*?) .*"); //TODO here assume list are numbered without letters, so no 1a., 1.b. 
		m = pattern.matcher(para);
		if(m.matches()){
			String num = m.group(1);
			String text = m.group(2);
			text = text.replaceAll("\\W", "");
			if(text.length()==1){ //1a. , 1.b., 1a-, yes, this is a list, and not an interrupting point 
				return false;
			}
			int next = Integer.parseInt(num)+1;
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			try{
				DatabaseAccessor.selectParagraphsFromSource(prefix, source, "paraID>="+paraID+" and (type not like 'non-content%' or type='unassigned')", "", paraIDs, paras, conn);
				
				text = (String)paras.get(0);
				if(text.startsWith(next+"")){
					return false; //para starts with a number but is a part of a list
				}else{
					return true; //para starts with a number and is NOT a part of a list
				}
			}catch (Exception e){
				e.printStackTrace();
			}
		}*/
		return false;
	}

	/**
	 * need to traceBack if a figure is found/table is found
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	private boolean isFigureTable(int paraID, String para, String source) {
		para = para.trim();
		Pattern pattern = Pattern.compile("(Fig\\.|Figure|Table)\\s+\\d+.*?", Pattern.CASE_INSENSITIVE);
		Matcher m = pattern.matcher(para); 
		if(m.matches()){
			//traceBack
			traceBackFigTblContent(paraID, source);
			return true;
		}
		return false;
	}

	/**
	 * capitalized short text before paraID should be set to non-content
	 * @param paraID
	 * @param source
	 */
	private void traceBackFigTblContent(int paraID, String source) {
		String condition = "paraID < "+paraID+" and paraID > (select max(paraID) from "+prefix+"_paragraphs where type like '%pagenum%' and paraID < "+paraID+") and length(paragraph) <=50 and paragraph COLLATE utf8_bin rlike '^[[:space:]]*[[:upper:]]'";
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			DatabaseAccessor.selectParagraphsFromSource(prefix, source, condition, "", paraIDs, paras, conn);
			int offset = 1;
			for(int i =paraIDs.size()-1; i>=0; i--){
					int pID = Integer.parseInt((String)paraIDs.get(i));
					if(pID ==paraID-offset++){
						markAsType(pID, "noncontent_figtbl_txt");
					}else{
						return;
					}
				}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	/**
	 * [  BURGER: FLORA COSTARICENSIS 5  ]
	 * @param paraID
	 * @param para
	 * @param source
	 * @return
	 */
	private boolean isPageNumber(int paraID, String para, String source) {
		para = para.trim();
		if(para.matches("\\d+")){
			return true;
		}
		Pattern lpattern = Pattern.compile("^\\d+\\s+(.*)");
		Pattern rpattern = Pattern.compile("(.*?)\\s+\\d+$");
		Matcher lm = lpattern.matcher(para);
		Matcher rm = rpattern.matcher(para);
		String header = "";
		if(lm.matches()){
			header = lm.group(1).toLowerCase();
		}else if(rm.matches()){
			header = rm.group(1).toLowerCase();
		}else{
			return false;
		}
		
		if(header.trim().compareTo("") == 0){
			return true; //stand alone page number
		}
		
		//if user provided pageNumberText
		if(this.pageNumberText.contains(header)){
			return true;
		}
		//if not
		header = header.replaceAll("'", "\\\\'");
		String condition = "paragraph rlike '^[[:digit:]]+[[:space:]]+"+header+"' or paragraph rlike '"+header+"[[:space:]]+[[:digit:]]+$'";
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			DatabaseAccessor.selectParagraphsFromSource(prefix, source, condition, "", paraIDs, paras, conn);
			if(paraIDs.size() >=3){return true;}
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}
	/**
	 * true if 
	 * 1. para start with a (footnote) token AND
	 * 2. the next paragraph started with a lower case letter or a non-listing number. That is, it is an interrupting point.
	 * If it is not an interrupting point, then it is safter to treat the para as content.
	 * @param para
	 * @return
	 */
	private boolean isFootNote(int paraID, String para, String source) {
		boolean cond1  = false;
		boolean cond2  = false;
		if(startWithAToken(para)){
			cond1=true;
		}
		if(isInterruptingPoint(++paraID, para, source)){
			cond2=true;
		}
		
		return cond1 && cond2;
	}

	private boolean startWithAToken(String para) {
		para = para.trim();
		String first = para.substring(0, 1);
		Pattern pattern = Pattern.compile("\\w");
		Matcher m = pattern.matcher(first);
		if(!m.matches()){ //any non-word token
			return true;
		}
		if(this.footNoteTokens.contains(first)){ //if the token is a word token, then there must be a non-word token following it
			String second = para.substring(1, 2);
			m=pattern.matcher(second);
			if(!m.matches()){
				return true;
			}
		}
		return false;
	}

	private void markAsType(int paraID, String type) {
		String set = "type='"+type+"'";
		String condition = "paraID="+paraID;
		try{
			DatabaseAccessor.updateParagraph(prefix, set, condition, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String sourceFilePath="X:\\DATA\\BHL\\test";
		ArrayList<String> pageNumberText = new ArrayList<String>();
		String pnt1 = "FIELDIANA: BOTANY, VOLUME 40".toLowerCase();
		String pnt2 = "BURGER: FLORA COSTARICENSIS".toLowerCase();
		pageNumberText.add(pnt1); 
		pageNumberText.add(pnt2);
		
		ArrayList<String> footNoteTokens = new ArrayList<String>();
		String fnt1="'";
		footNoteTokens.add(fnt1);
		
		ContentFetcher cf = new ContentFetcher(pageNumberText, footNoteTokens, sourceFilePath);
		cf.readParagraphs();
		cf.idContent();
	}
}
