/**$Id$
 * 
 */
package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import javax.xml.transform.Source;

import org.apache.log4j.Logger;

import beans.ParagraphBean;
import informationContent.ApplicationUtilities;

/**
 * @author hongcui
 * 
 */
public class DatabaseAccessor {

	private static final Logger LOGGER = Logger
			.getLogger(DatabaseAccessor.class);

	// private static String url =
	// ApplicationUtilities.getProperty("database.url");

	/*
	 * static { try {
	 * Class.forName(ApplicationUtilities.getProperty("database.driverPath")); }
	 * catch (ClassNotFoundException e) { // TODO Auto-generated catch block
	 * LOGGER.error("Couldn't find Class in ConfigurationDbAccessor" + e);
	 * e.printStackTrace(); } }
	 */

	// called by ContentFetcher
	public static void createParagraphTable(String prefix, Connection conn)
			throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			// make the paraID auto_increment
			stmt.execute("create table if not exists " + prefix
					+ "_paragraphs ("
					+ "paraID bigint not null primary key auto_increment , "
					+ "source varchar(500), paragraph text(5000), "
					+ "type varchar(50), add2last varchar(10), "
					+ "remark varchar(50), " + "pageNum varchar(20),"
					+ "y1 int" + ")");
			stmt.execute("delete from " + prefix + "_paragraphs");
		} catch (Exception e) {
			LOGGER.error("Couldn't create table" + prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */

	}

	/**
	 * SET NAMES 'utf8'; set character_set_server='utf8'; set
	 * character_set_dabatase='utf8'; set character_set_system = 'utf8';
	 * 
	 * @param conn
	 * @throws Exception
	 */
	public static void setCharacterSet(Connection conn) throws Exception {
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			stmt.execute("SET NAMES 'utf8'");
			stmt.execute("set character_set_server='utf8';");
			// stmt.execute("set character_set_dabatase='utf8';");
			// stmt.execute("set character_set_system = 'utf8';");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// called by ContentFixer
	public static void createCleanParagraphTable(String tableName,
			Connection conn) throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			stmt.execute("drop table if exists " + tableName);
			stmt.execute("create table if not exists " + tableName
					+ " (paraID bigint not null primary key auto_increment, "
					+ "orgParaID bigint, " + "source varchar(50), "
					+ "paragraph text(5000), " + "remark varchar(50))");
		} catch (Exception e) {
			LOGGER.error("Couldn't create table" + tableName + "::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */

	}

	public static void createXMLFileRelationsTable(String tableName,
			Connection conn) {
		Statement stmt = null;
		try {
			stmt = conn.createStatement();
			stmt.execute("drop table if exists " + tableName
					+ "_taxon_relation");
			String sql = "create table if not exists " + tableName
					+ "_taxon_relation " + " (name varchar(200), "
					+ "filename varchar(50), " + "taxon_hierarchy varchar(1000))";
			stmt.execute(sql);
		} catch (Exception e) {
			LOGGER.error("Couldn't create tabke " + tableName + " :: " + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void inserParagraph(String prefix, String para,
			String source, String pageNum, Connection conn, String type)
			throws Exception {
		Statement stmt = null;
		stmt = conn.createStatement();

		String value = "'" + source + "', '" + para + "', '" + type + "', '"
				+ pageNum + "'";
		try {
			stmt.execute("insert into " + prefix
					+ "_paragraphs (source, paragraph, type, pageNum) values ("
					+ value + ")");
		} catch (Exception e) {
			System.out.println("Couldn't insert (" + value + ") to table "
					+ prefix + "_paragraphs::");
			LOGGER.error("Couldn't insert (" + value + ") to table " + prefix
					+ "_paragraph::" + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void inserParagraph(String prefix, String para, Integer y1,
			String source, String pageNum, Connection conn, String type)
			throws Exception {
		Statement stmt = null;
		stmt = conn.createStatement();

		String value = "'" + source + "', '" + para + "', '" + type + "', '"
				+ pageNum + "', " + y1;
		try {
			stmt.execute("insert into "
					+ prefix
					+ "_paragraphs (source, paragraph, type, pageNum, y1) values ("
					+ value + ")");
		} catch (Exception e) {
			System.out.println("Couldn't insert (" + value + ") to table "
					+ prefix + "_paragraphs::");
			LOGGER.error("Couldn't insert (" + value + ") to table " + prefix
					+ "_paragraph::" + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Overload insertParagraphs without paraID because paraID is now
	 * auto_increment
	 * 
	 * @param prefix
	 * @param paras
	 * @param sources
	 * @param conn
	 * @param types
	 * @throws Exception
	 */
	public static void insertParagraphs(String prefix, ArrayList<String> paras,
			String source, Connection conn, ArrayList<String> types,
			String pageNum) throws Exception {
		source = source.replaceAll("'", "_");
		for (int i = 0; i < paras.size(); i++) {
			// escape ' in source/para
			String para = ((String) paras.get(i)).replaceAll("\\\\", "");
			para = para.replaceAll("'", "\\\\'");
			String type = "unassigned";
			if (types != null) {
				type = (String) types.get(i);
				if (type == null || type.equals("")) {
					type = "unassigned";
				}
			}

			inserParagraph(prefix, para, source, pageNum, conn, type);
		}
	}

	public static void insertParagraphs(String prefix, ArrayList<String> paras,
			String source, Connection conn, ArrayList<String> types,
			String pageNum, ArrayList<Integer> y1s) throws Exception {
		source = source.replaceAll("'", "_");
		for (int i = 0; i < paras.size(); i++) {
			// escape ' in source/para
			String para = ((String) paras.get(i)).replaceAll("\\\\", "");
			para = para.replaceAll("'", "\\\\'");

			String type = "unassigned";
			if (types != null) {
				type = (String) types.get(i);
				if (type == null || type.equals("")) {
					type = "unassigned";
				}
			}

			int y1 = (y1s != null ? y1s.get(i) : 0);
			inserParagraph(prefix, para, y1, source, pageNum, conn, type);
		}
	}

	/*
	 * discarded
	 */
	public static void insertParagraphs(String prefix, ArrayList paraIDs,
			ArrayList paras, ArrayList sources, Connection conn, ArrayList types)
			throws Exception {
		// Connection conn = null;
		Statement stmt = null;

		// conn = DriverManager.getConnection(url);
		stmt = conn.createStatement();
		for (int i = 0; i < paras.size(); i++) {
			// escape ' in source/para
			int paraID = Integer.parseInt((String) paraIDs.get(i));
			String source = ((String) sources.get(i)).replaceAll("'", "_");
			String para = ((String) paras.get(i)).replaceAll("\\\\", "");
			para = para.replaceAll("'", "\\\\'");
			String type = "unassigned";
			if (types != null) {
				type = (String) types.get(i);
				if (type == null && type.equals("")) {
					type = "unassigned";
				}
			}

			String value = paraID + ", '" + source + "', '" + para + "', '"
					+ type + "'";
			try {
				stmt.execute("insert into "
						+ prefix
						+ "_paragraphs (paraID, source, paragraph, type) values ("
						+ value + ")");
			} catch (Exception e) {
				System.out.println("Couldn't insert (" + value + ") to table "
						+ prefix + "_paragraphs::");
				LOGGER.error("Couldn't insert (" + value + ") to table "
						+ prefix + "_paragraphs::" + e);
				e.printStackTrace();
				System.exit(1);
			}
		}
		/*
		 * if (stmt != null) { stmt.close(); } if (conn != null) { conn.close();
		 * }
		 */

	}

	/**
	 * insert one record
	 * 
	 * @param cleanTableName
	 * @param pb
	 * @param conn
	 * @throws Exception
	 */
	public static void insertCleanParagraph(String cleanTableName,
			ParagraphBean pb, Connection conn) throws Exception {
		Statement stmt = null;

		// conn = DriverManager.getConnection(url);
		stmt = conn.createStatement();
		String source = pb.getSource().replaceAll("'", "_");
		String para = pb.getPara().replaceAll("'", "\\\\'");
		String value = pb.getOriginalID() + ", '" + source + "', '" + para
				+ "'";
		try {
			stmt.execute("insert into " + cleanTableName
					+ " (orgParaID, source, paragraph) values (" + value + ")");
		} catch (Exception e) {
			LOGGER.error("Couldn't insert (" + value + ") to table "
					+ cleanTableName + "::" + e);
			System.out.println("Couldn't insert (" + value + ") to table "
					+ cleanTableName + "::");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void insertCleanParagraphs(String cleanTableName,
			ArrayList<String> paraIDs, ArrayList<String> orgParaIDs,
			ArrayList<String> paras, ArrayList<String> sources, Connection conn)
			throws Exception {
		// Connection conn = null;
		Statement stmt = null;

		// conn = DriverManager.getConnection(url);
		stmt = conn.createStatement();
		for (int i = 0; i < paras.size(); i++) {
			// escape ' in source/para
			int paraID = Integer.parseInt((String) paraIDs.get(i));
			int orgParaID = Integer.parseInt((String) orgParaIDs.get(i));
			String source = ((String) sources.get(i)).replaceAll("'", "_");
			String para = ((String) paras.get(i)).replaceAll("'", "\\\\'");
			String value = paraID + ", " + orgParaID + ", '" + source + "', '"
					+ para + "'";
			try {
				stmt.execute("insert into " + cleanTableName
						+ " (paraID, orgParaID, source, paragraph) values ("
						+ value + ")");
			} catch (Exception e) {
				LOGGER.error("Couldn't insert (" + value + ") to table "
						+ cleanTableName + "::" + e);
				System.out.println("Couldn't insert (" + value + ") to table "
						+ cleanTableName + "::");
				e.printStackTrace();
				System.exit(1);
			}
		}
		/*
		 * if (stmt != null) { stmt.close(); } if (conn != null) { conn.close();
		 * }
		 */

	}

	public static void selectAllParagraphs(String prefix, ArrayList paraIDs,
			ArrayList paras, Connection conn) throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		Hashtable<String, String> results = new Hashtable<String, String>();
		// escape ' in source/para
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select paraID, paragraph from "
					+ prefix + "_paragraphs");
			while (rs.next()) {
				paraIDs.add(rs.getInt(1) + "");
				paras.add(rs.getString(2));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select all paragraphs from table" + prefix
					+ "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	/**
	 * use condition to filter records in prefix_paragraphs and return number of
	 * records
	 * 
	 * @param prefix
	 * @param condition
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public static int numberOfRecordsInParagraph(String prefix,
			String condition, Connection conn) throws Exception {
		ResultSet rs = null;
		assert (!condition.equals(""));
		int rvalue = 0;
		try {
			Statement stmt = conn.createStatement();
			String sql = "select count(*) from " + prefix
					+ "_paragraphs where " + condition;
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				rvalue = rs.getInt(1);
			}

		} catch (Exception e) {
			System.out.println("couldn't get numberOfRecordsInParagraph: " + e);
			LOGGER.error("couldn't get numberOfRecordsInParagraph: " + e);
			System.exit(1);
		} finally {
			rs = null;
		}
		return rvalue;
	}

	/**
	 * This function is to replace selectParagraph which convert ResultSet to
	 * two ArrayList. Just return the ResultSet itself to reduce memory usage
	 * 
	 * @param prefix
	 * @param condition
	 * @param orderby
	 * @param columns
	 *            : specify which columns you want back to reduce memory usage,
	 *            can be ""
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public static ResultSet getParagraphsByCondition(String prefix,
			String condition, String orderby, String columns, Connection conn)
			throws Exception {
		Statement stmt = null;
		ResultSet rs = null;

		String statement = "select paraID, paragraph from " + prefix
				+ "_paragraphs";
		if (!columns.equals("")) {
			statement = "select " + columns + " from " + prefix + "_paragraphs";
		}
		if (condition.compareTo("") != 0) {
			statement += " where " + condition;
		}
		if (orderby.compareTo("") != 0) {
			statement += " order by " + orderby;
		}
		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery(statement);
			return rs;

		} catch (Exception e) {
			System.out.println("Failed in function getParagraphsByCondition ["
					+ statement + "]from table" + prefix + "_paragraphs::");
			LOGGER.error("Failed in function getParagraphsByCondition ["
					+ statement + "]from table" + prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		}
		return rs;
	}

	public static String getParaByCondition(String prefix, String condition,
			Connection conn) throws Exception {
		ResultSet rs = null;
		assert (!condition.equals(""));
		String rvalue = "";
		try {
			Statement stmt = conn.createStatement();
			String sql = "select paragraph from " + prefix
					+ "_paragraphs where " + condition;
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				rvalue = rs.getString(1);
			}

		} catch (Exception e) {
			System.out.println("couldn't get getParaByCondition: " + e);
			LOGGER.error("couldn't get getParaByCondition: " + e);
			System.exit(1);
		} finally {
			rs = null;
		}
		return rvalue;
	}

	public static void selectParagraphs(String prefix, String condition,
			String orderby, ArrayList<String> paraIDs, ArrayList<String> paras,
			Connection conn) throws Exception {
		Hashtable<String, String> results = new Hashtable<String, String>();
		// Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph from " + prefix
				+ "_paragraphs";
		if (condition.compareTo("") != 0) {
			statement += " where " + condition;
		}
		if (orderby.compareTo("") != 0) {
			statement += " order by " + orderby;
		}
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while (rs.next()) {
				paraIDs.add(rs.getInt(1) + "");
				paras.add(rs.getString(2));
			}
		} catch (Exception e) {
			System.out.println("Couldn't select paragraphs [" + statement
					+ "]from table" + prefix + "_paragraphs::");
			LOGGER.error("Couldn't select paragraphs [" + statement
					+ "]from table" + prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void selectParagraphsSources(String prefix, String condition,
			String orderby, ArrayList paraIDs, ArrayList paras,
			ArrayList sources, Connection conn) throws Exception {
		Hashtable<String, String> results = new Hashtable<String, String>();
		// Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph, source from " + prefix
				+ "_paragraphs";
		if (condition.compareTo("") != 0) {
			statement += " where " + condition;
		}
		if (orderby.compareTo("") != 0) {
			statement += " order by " + orderby;
		}
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while (rs.next()) {
				paraIDs.add(rs.getInt(1) + "");
				paras.add(rs.getString(2));
				sources.add(rs.getString(3));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select paragraphs and sources from table"
					+ prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void selectParagraphsTypes(String prefix, String condition,
			String orderby, ArrayList paraIDs, ArrayList paras,
			ArrayList types, Connection conn) throws Exception {
		Hashtable<String, String> results = new Hashtable<String, String>();
		// Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph, type from " + prefix
				+ "_paragraphs";
		if (condition.compareTo("") != 0) {
			statement += " where " + condition;
		}
		if (orderby.compareTo("") != 0) {
			statement += " order by " + orderby;
		}
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while (rs.next()) {
				paraIDs.add(rs.getInt(1) + "");
				paras.add(rs.getString(2));
				types.add(rs.getString(3));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select paragraphs and sources from table"
					+ prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void selectParagraphsTypesAdd2Last(String prefix,
			String condition, String orderby, ArrayList<String> paraIDs,
			ArrayList<String> paras, ArrayList<String> types,
			ArrayList<String> add2last, Connection conn) throws Exception {
		// Connection conn = null;
		Statement stmt = null;
		String statement = "select paraID, paragraph, type, add2last from "
				+ prefix + "_paragraphs";
		if (condition.compareTo("") != 0) {
			statement += " where " + condition;
		}
		if (orderby.compareTo("") != 0) {
			statement += " order by " + orderby;
		}
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while (rs.next()) {
				paraIDs.add(rs.getInt(1) + "");
				paras.add(rs.getString(2));
				types.add(rs.getString(3));
				add2last.add(rs.getString(4));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select paragraphs and sources from table"
					+ prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void selectDistinctSources(String prefix, ArrayList sources,
			Connection conn) throws Exception {
		Hashtable<String, String> results = new Hashtable<String, String>();
		// Connection conn = null;
		Statement stmt = null;
		String statement = "select distinct source from " + prefix
				+ "_paragraphs";

		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(statement);
			while (rs.next()) {
				sources.add(rs.getString(1));
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't select distinct sources from table "
					+ prefix + "_paragraphs::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	/**
	 * select the smallest row that is marked to be prolog, and update all
	 * previous records to be prolog
	 * 
	 * @param prefix
	 * @param conn
	 * @throws Exception
	 */
	public static void updateProlog(String prefix, Connection conn)
			throws Exception {
		Statement stmt = null;
		String statement = "";
		ResultSet rs = null;
		try {
			int startID = 0;
			stmt = conn.createStatement();
			statement = "select paraID from "
					+ prefix
					+ "_paragraphs where type = 'noncontent_prolog' order by paraID desc limit 1";
			rs = stmt.executeQuery(statement);
			if (rs.next()) {
				startID = rs.getInt(1);
				statement = "update " + prefix
						+ "_paragraphs set type = 'noncontent_prolog' "
						+ "where paraID < " + startID;
				stmt.execute(statement);
			}

		} catch (Exception e) {
			LOGGER.error("couldn't update prolog in table " + prefix
					+ "_paragraphs [" + statement + "]::" + e);
			e.printStackTrace();
			System.exit(1);
		} finally {
			rs = null;
		}
	}

	public static void updateParagraph(String prefix, String set,
			String condition, Connection conn) throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();

			if (condition.compareTo("") != 0) {
				statement = "update " + prefix + "_paragraphs set " + set
						+ " where " + condition;
			} else {
				statement = "update " + prefix + "_paragraphs set " + set;
			}
			stmt.execute(statement);
		} catch (Exception e) {
			LOGGER.error("Couldn't update table" + prefix + "_paragraphs ["
					+ statement + "]::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void updateByParaIDs(String prefix,
			Hashtable<String, String> typed, Connection conn) throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			Enumeration en = typed.keys();
			while (en.hasMoreElements()) {
				String key = (String) en.nextElement();
				int paraID = Integer.parseInt(key);
				String set = "type='" + (String) typed.get(key) + "'";
				String condition = "paraID=" + paraID;
				if (condition.compareTo("") != 0) {
					statement = "update " + prefix + "_paragraphs set " + set
							+ " where " + condition;
				} else {
					statement = "update " + prefix + "_paragraphs set " + set;
				}
				stmt.execute(statement);
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't update table" + prefix
					+ "_paragraphs to update types::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	public static void updateByParaIDs(String prefix, String set,
			ArrayList<String> paraIDs, Connection conn) throws Exception {

		// Connection conn = null;
		Statement stmt = null;
		String statement = "";
		try {
			// conn = DriverManager.getConnection(url);
			stmt = conn.createStatement();
			for (int i = 0; i < paraIDs.size(); i++) {
				String condition = "paraID="
						+ Integer.parseInt((String) paraIDs.get(i));
				if (condition.compareTo("") != 0) {
					statement = "update " + prefix + "_paragraphs set " + set
							+ " where " + condition;
				} else {
					statement = "update " + prefix + "_paragraphs set " + set;
				}
				stmt.execute(statement);
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't update table" + prefix
					+ "_paragraphs for set [" + set + "]::" + e);
			e.printStackTrace();
			System.exit(1);
		} /*
		 * finally { if (stmt != null) { stmt.close(); } if (conn != null) {
		 * conn.close(); } }
		 */
	}

	/**
	 * Originally in class GlossaryFixer.java, move to this class since it is db
	 * related No need for a new class
	 * 
	 * @param prefix
	 * @param glossHeading
	 * @param conn
	 */
	public static void fixGlossary(String prefix, String glossHeading,
			Connection conn) {
		try {
			String condition = "paragraph COLLATE utf8_bin rlike '^[[.space.]]*"
					+ glossHeading + "[[.space.]]*$'";
			condition = "paragraph rlike '^[[.space.]]*" + glossHeading
					+ "[[.space.]]*$'";
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID",
					paraIDs, paras, conn);
			for (int i = 0; i < paraIDs.size(); i++) { // do one glossary at a
														// time
				int glossId = Integer.parseInt(paraIDs.get(i)); // start gloss
																// section
				condition = "remark like '%heading%' and paraID >" + glossId;
				ArrayList<String> paraIDs1 = new ArrayList<String>();
				ArrayList<String> paras1 = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix, condition, "paraID",
						paraIDs1, paras1, conn);
				String cond = "";
				if (paraIDs1.size() >= 1) {
					int glossIdE = Integer.parseInt(paraIDs1.get(0));
					cond = "paraID > " + glossId + " and paraID < " + glossIdE;
				} else {
					cond = "paraID > " + glossId;
				}
				String set = "remark = 'content-gloss'";
				DatabaseAccessor.updateParagraph(prefix, set, cond, conn);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Originally in class ReferencesFixer.java, move to here since it is db
	 * related
	 * 
	 * @param prefix
	 * @param refHeading
	 * @param conn
	 */
	public static void fixReferences(String prefix, String refHeading,
			Connection conn) {
		try {
			String condition = "paragraph COLLATE utf8_bin rlike '^[[.space.]]*"
					+ refHeading + "[[.space.]]*$'";
			condition = "paragraph rlike '^[[.space.]]*" + refHeading
					+ "[[.space.]]*$'";
			ArrayList<String> paraIDs = new ArrayList<String>();
			ArrayList<String> paras = new ArrayList<String>();
			DatabaseAccessor.selectParagraphs(prefix, condition, "paraID",
					paraIDs, paras, conn);
			for (int i = 0; i < paraIDs.size(); i++) { // do one glossary at a
														// time
				int refId = Integer.parseInt(paraIDs.get(i)); // start gloss
																// section
				condition = "remark like '%heading%' and paraID >" + refId;
				ArrayList<String> paraIDs1 = new ArrayList<String>();
				ArrayList<String> paras1 = new ArrayList<String>();
				DatabaseAccessor.selectParagraphs(prefix, condition, "paraID",
						paraIDs1, paras1, conn);
				String cond = "";
				if (paraIDs1.size() >= 1) {
					int refIdE = Integer.parseInt(paraIDs1.get(0));
					cond = "paraID > " + refId + " and paraID < " + refIdE;
				} else {
					cond = "paraID > " + refId;
				}
				String set = "remark = 'content-ref' ";
				DatabaseAccessor.updateParagraph(prefix, set, cond, conn);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Originally in class HeadingFixer.java, move to this class since it is db
	 * related
	 * 
	 * @param prefix
	 * @param headingStyle
	 * @param conn
	 */
	public static void fixHeading(String prefix, String headingStyle,
			Connection conn) {
		if (headingStyle.compareTo("ALLCAP") == 0) {
			String set = "remark = 'content-heading' ";
			String cond = "paragraph rlike '^[A-Z ]+$'";
			// String cond = "paragraph COLLATE utf8_bin rlike '^[A-Z ]+$'";
			try {
				DatabaseAccessor.updateParagraph(prefix, set, cond, conn); // update
																			// clean_paragraphs
																			// table
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * insert a record indicating the xml filename to any taxon
	 * @param prefix
	 * @param name
	 * @param taxon_hierarchy
	 * @param fileCount
	 * @param conn
	 * @throws Exception
	 */
	public static void insertTaxonFileRelation(String prefix, String name,
			String taxon_hierarchy, int fileCount, Connection conn)
			throws Exception {
		PreparedStatement ps = null;
		try {
			ps = conn.prepareStatement("insert into "
					+ prefix
					+ "_taxon_relation (name, filename, taxon_hierarchy) values (?, ?, ?)");
			ps.setString(1, name);
			ps.setString(2, fileCount + ".xml");
			ps.setString(3, taxon_hierarchy);
			ps.execute();
		} catch (Exception e) {
			System.out.println("Couldn't insert to table "
					+ prefix + "_taxon_relation::");
			LOGGER.error("Couldn't insert to table " + prefix
					+ "_taxon_relation::" + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
