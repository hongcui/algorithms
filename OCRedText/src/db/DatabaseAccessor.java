/**
 * 
 */
package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.apache.log4j.Logger;
import informationContent.ApplicationUtilities;
/**
 * @author hongcui
 *
 */
public class DatabaseAccessor {
	
	private static final Logger LOGGER = Logger.getLogger(DatabaseAccessor.class);
	//private static String url = ApplicationUtilities.getProperty("database.url");
	
	/*static {
		try {
			Class.forName(ApplicationUtilities.getProperty("database.driverPath"));
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			LOGGER.error("Couldn't find Class in ConfigurationDbAccessor" + e);
			e.printStackTrace();
		}
	}*/
	
	//called by ContentFetcher
	public static void createParagraphTable(String prefix, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			stmt.execute("create table if not exists "+prefix+"_paragraphs (paraID bigint not null primary key , source varchar(50), paragraph text(5000), type varchar(50), add2last varchar(10), remark varchar(50))");
			stmt.execute("delete from "+prefix+"_paragraphs");
		} catch (Exception e) {
			LOGGER.error("Couldn't create table"+prefix+"_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
		
	}
	
	//called by ContentFixer
	public static void createCleanParagraphTable(String prefix, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			stmt.execute("create table if not exists "+prefix+"_paragraphs (paraID bigint not null primary key, orgParaID bigint, source varchar(50), paragraph text(5000), remark varchar(50))");
			stmt.execute("delete from "+prefix+"_paragraphs");
		} catch (Exception e) {
			LOGGER.error("Couldn't create table"+prefix+"_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
		
	}
	public static void insertParagraphs(String prefix, ArrayList paraIDs, ArrayList paras, ArrayList sources, Connection conn) throws Exception{
		//Connection conn = null;
		Statement stmt = null;

		//conn = DriverManager.getConnection(url);
		stmt = conn.createStatement();
		for(int i = 0; i<paras.size(); i++){
			//escape ' in source/para
			int paraID = Integer.parseInt((String)paraIDs.get(i));
			String source = ((String)sources.get(i)).replaceAll("'", "_");
			String para = ((String)paras.get(i)).replaceAll("\\\\", "");
			para = para.replaceAll("'", "\\\\'");
			String value = paraID+", '"+source+"', '"+para+"', 'unassigned'";
			try {
				stmt.execute("insert into "+prefix+"_paragraphs (paraID, source, paragraph, type) values ("+value+")");
			}catch (Exception e) {
				System.out.println("Couldn't insert ("+value+") to table "+prefix+"_paragraphs::");
				LOGGER.error("Couldn't insert ("+value+") to table "+prefix+"_paragraphs::" + e);
				e.printStackTrace();
				System.exit(1);
			}
		}
        /*if (stmt != null) {
			stmt.close();
		}
		if (conn != null) {
			conn.close();
		}*/
		
		
	}
	
	public static void insertCleanParagraphs(String prefix, ArrayList paraIDs, ArrayList orgParaIDs, ArrayList paras, ArrayList sources, Connection conn) throws Exception{
		//Connection conn = null;
		Statement stmt = null;

		//conn = DriverManager.getConnection(url);
		stmt = conn.createStatement();
		for(int i = 0; i<paras.size(); i++){
			//escape ' in source/para
			int paraID = Integer.parseInt((String)paraIDs.get(i));
			int orgParaID = Integer.parseInt((String)orgParaIDs.get(i));
			String source = ((String)sources.get(i)).replaceAll("'", "_");
			String para = ((String)paras.get(i)).replaceAll("'", "\\\\'");
			String value = paraID+", "+orgParaID+", '"+source+"', '"+para+"'";
			try {
				stmt.execute("insert into "+prefix+"_paragraphs (paraID, orgParaID, source, paragraph) values ("+value+")");
			}catch (Exception e) {
				LOGGER.error("Couldn't insert ("+value+") to table "+prefix+"_paragraphs::" + e);
				System.out.println("Couldn't insert ("+value+") to table "+prefix+"_paragraphs::");
				e.printStackTrace();
				System.exit(1);
			}
		}
        /*if (stmt != null) {
			stmt.close();
		}
		if (conn != null) {
			conn.close();
		}*/
		
		
	}

	public static void selectAllParagraphs(String prefix, ArrayList paraIDs, ArrayList paras, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		Hashtable<String, String> results = new Hashtable<String, String>();
		//escape ' in source/para
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select paraID, paragraph from "+prefix+"_paragraphs");
			while(rs.next()){
				paraIDs.add(rs.getInt(1)+"");
				paras.add(rs.getString(2));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select all paragraphs from table"+prefix+"_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}

	public static void selectParagraphs(String prefix, String condition, String orderby, ArrayList paraIDs, ArrayList paras, Connection conn) throws Exception{
		Hashtable<String, String> results = new Hashtable<String, String>(); 
		//Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph from "+prefix+"_paragraphs";
		if(condition.compareTo("") != 0){
			statement += " where "+condition; 
		}
		if(orderby.compareTo("") != 0){
			statement += " order by "+orderby;
		}
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while(rs.next()){
				paraIDs.add(rs.getInt(1)+"");
				paras.add(rs.getString(2));
			}
		} catch (Exception e) {
			System.out.println("Couldn't select paragraphs ["+statement+"]from table"+prefix+"_paragraphs::");
			LOGGER.error("Couldn't select paragraphs ["+statement+"]from table"+prefix+"_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}
	
	public static void selectParagraphsSources(String prefix, String condition, String orderby, ArrayList paraIDs, ArrayList paras, ArrayList sources, Connection conn) throws Exception{
		Hashtable<String, String> results = new Hashtable<String, String>(); 
		//Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph, source from "+prefix+"_paragraphs";
		if(condition.compareTo("") != 0){
			statement += " where "+condition; 
		}
		if(orderby.compareTo("") != 0){
			statement += " order by "+orderby;
		}
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while(rs.next()){
				paraIDs.add(rs.getInt(1)+"");
				paras.add(rs.getString(2));
				sources.add(rs.getString(3));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select paragraphs and sources from table"+prefix+"_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}

	public static void updateParagraph(String prefix, String set, String condition, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			
			if(condition.compareTo("")!=0){
				statement = "update "+prefix+"_paragraphs set "+set+" where "+condition;
			}else{
				statement = "update "+prefix+"_paragraphs set "+set;
			}
			stmt.execute(statement);
		} catch (Exception e) {
			LOGGER.error("Couldn't update table"+prefix+"_paragraphs ["+statement+"]::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}
	
	public static void updateByParaIDs(String prefix, Hashtable<String, String>typed, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			Enumeration en = typed.keys();
			while(en.hasMoreElements()){
				String key = (String)en.nextElement();
				int paraID = Integer.parseInt(key);
				String set = "type='"+(String)typed.get(key)+"'";
				String condition = "paraID="+paraID; 
				if(condition.compareTo("")!=0){
					statement = "update "+prefix+"_paragraphs set "+set+" where "+condition;
				}else{
					statement = "update "+prefix+"_paragraphs set "+set;
				}
				stmt.execute(statement);
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't update table"+prefix+"_paragraphs to update types::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}

	public static void updateByParaIDs(String prefix, String set, ArrayList<String> paraIDs, Connection conn) throws Exception{
		
		//Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			//conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			for(int i =0; i<paraIDs.size(); i++){
				String condition = "paraID="+Integer.parseInt((String)paraIDs.get(i)); 
				if(condition.compareTo("")!=0){
					statement = "update "+prefix+"_paragraphs set "+set+" where "+condition;
				}else{
					statement = "update "+prefix+"_paragraphs set "+set;
				}
			stmt.execute(statement);
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't update table"+prefix+"_paragraphs for set ["+set+"]::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*finally {
            if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		}*/
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
