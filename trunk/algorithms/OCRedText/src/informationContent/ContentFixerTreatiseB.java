/**
 * $Id$
 *//**
 * 
 */
package informationContent;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.regex.*;

import db.DatabaseAccessor;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.jdom.Content;
import org.jdom.Text;


/**
 * $Id
 * @author hongcui
 * 
 * remove all non-description parts to
 * form descriptions
 * one description contains: name, description, other-info sections
 * 
 * outputs xml files a filename2taxon table
 * 
 * This was written for part B of the Treatise. Not extending ContentFixer because there is little that can be inhered. 
 * This program directly output treatments in XML format, which can then be processed as Type 4 Document in the semantic parser.
 */

public class ContentFixerTreatiseB /*extends ContentFixer*/ {
	//configureables
	//part b
	protected String descriptionStartHeading = "SYSTEMATIC DESCRIPTIONS";
	protected String descriptionEndHeading = "NOMINA DUBIA AND GENERIC NAMES WRONGLY";
	//part o
	//protected String descriptionStartHeading ="SYSTEMATIC DESCRIPTIONS OF THE CLASS TRILOBITA";
	//protected String descriptionEndHeading = "INDEX";
	protected String uncertain = "UNCERTAIN"; //eg. Family UNCERTAIN
	//private String genusPattern = "^[A-Z][a-z]+ [-A-Z()ÄÉ.&, ]+, 1\\d{3}.*";
	/*covers examples such as:
	 * Linyiechara XINLUN in WANG Shui & others, 1978,
	 * Heptorella FEIST & GRAMBAST-FESSARD, nom. nov.  herein, nom. nov. pro Septorella GRAMBAST, 1962b,
	 * N. (Nitellopsis) (HY, 1889, p. 398) GRAMBAST & SOULIÉ-MÄRSCHE, 1972,
	*/
	private String genusPattern ="^([A-Z][a-z]+|[A-Z]\\. \\([A-Z][a-z]+\\)[^;×]*?) ([-A-Z()ÄÉ.&, ]|in|others)+,?[a-zA-ZÄÉ .,&]*? 1\\d{3}.*?p\\. \\d+ \\[.*?";
	private String genusNomenPattern = "(.*?\\].?)(\\s*$|\\s+[A-Z].*)";
	private Pattern genusNomenPtn = Pattern.compile(genusNomenPattern);
	private String figPattern = "——\\s*FIG";
	private Pattern figPtn = Pattern.compile(figPattern);
	private String speciesNamePattern = "^G>[A-Z]\\..*?";
	private String distributionPattern = ".*?([A-Z][a-z]+\\s*){2,}.*"; //two consecutive capitalized words
	
	
	//regular properties
	protected Connection conn = null;
	protected static String url = ApplicationUtilities.getProperty("database.url");
	private String prefix = null;
	private String source = null;
	private int end = 0;
	private int start  = 0;
	private ArrayList<String> paragraphs = null;
	private ArrayList<String> ptypes = null;
	private ArrayList<String> padd2last = null;
	private ArrayList<String> ranks = new ArrayList<String>();
	private ArrayList<String> ranktaxon = null;
	//private ArrayList<String> paraIDs = null;
	private ArrayList<ArrayList<String>> allsegments = new ArrayList<ArrayList<String>>();
	private File outputfolder = null;
	
	private int filecount = 1;
	private boolean debug = true;
	/**
	 * 
	 */
	public ContentFixerTreatiseB(String paraTableName, String outputfolder) {
		//super(paraTableName);
		ranks.add("Phylum");
		ranks.add("Class");
		ranks.add("Subclass");
		ranks.add("Order");
		ranks.add("Suborder");
		ranks.add("Family");
		ranks.add("Subfamily");	
		ranks.add("Genus");	
		ranks.add("Species");
		ranktaxon = (ArrayList<String>)ranks.clone();
		prefix = paraTableName.replaceFirst("_\\w+$", "");
		this.outputfolder = new File(outputfolder);
		
		
		Iterator<String> it = ranks.iterator();
		String tabledef = "filename varchar(20), isdescription tinyint, ";
		while(it.hasNext()){
			tabledef += it.next()+" varchar(100),";
		}
		tabledef = tabledef.replaceFirst(",$", "").replace("Order", "Oorder"); //Order is a reserved mysql keyword
		try{
			//paraID not null primary key longint, source varchar(50), paragraph text(5000), type varchar(10), add2last varchar(5), remark varchar(50)
			Class.forName(ApplicationUtilities.getProperty("database.driverPath"));
			conn = DriverManager.getConnection(url);
			Statement stmt = conn.createStatement();
			stmt.execute("drop table if exists "+prefix+"_filename2taxon");
			stmt.execute("create table "+prefix+"_filename2taxon" + "("+tabledef+")");
			//DatabaseAccessor.createCleanParagraphTable(this.prefix+"_clean", conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	protected void makeCleanContent(){
		try{
			ArrayList<String> sources = new ArrayList<String>();
			DatabaseAccessor.selectDistinctSources(this.prefix, sources, this.conn);
			Iterator<String> it = sources.iterator();
			while(it.hasNext()){
				source = it.next();
				//0. output cleaned paragraphs
				//outputCleanParagraphs(source);
				//1. select descriptions
				getDescriptions(source);
				//2. merge description parts
				segmentDescriptions(source);
				//3. format descriptions
				structureDescriptions(source);
				//4. output xml files and a filename2taxon table
				outputDescriptions(source);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/*private void outputCleanParagraphs(String source) {
		try{
			this.paraIDs = new ArrayList<String> ();
			this.paragraphs = new ArrayList<String> ();
			String condition = "source = '"+source+"' and type in ('content','content_heading')";
			this.ptypes = new ArrayList<String>();
			this.padd2last = new ArrayList<String>();
			String orderby = "";
			DatabaseAccessor.selectParagraphsTypesAdd2Last(prefix, condition, orderby, paraIDs, paragraphs, ptypes, padd2last, conn);
			appendAdd2last();
			//output Cleaned
			Iterator<String> it = this.paragraphs.iterator();
			ArrayList<String> pIDs = new ArrayList<String>();
			ArrayList<String> sources = new ArrayList<String>();
			int count = 0;
			while(it.hasNext()){
				it.next();
				pIDs.add(count+"");
				sources.add(source);
				count++;
			}
			DatabaseAccessor.insertCleanParagraphs(this.prefix+"_clean", pIDs, paraIDs, paragraphs, sources, conn);
		}catch(Exception e){
			e.printStackTrace();
		}
	}*/

	/**
	 * output xml files
	 * @param source
	 */
	private void outputDescriptions(String source) {
		Iterator<ArrayList<String>> it = this.allsegments.iterator();
		while(it.hasNext()){
			ArrayList<String> segment = it.next();
			outputSegment(segment);
		}		
	}

	private void outputSegment(ArrayList<String> segment) {
		// TODO Auto-generated method stub
		Iterator<String> it = segment.iterator();
		while(it.hasNext()){
			String treatment = it.next();
			outputTreatment(treatment, filecount++);
		}
	}

	private void outputTreatment(String treatment, int filecount){
		String[] parts = treatment.split("<"); //parts[0] = ""
		try{
			//create doc
			Element root = new Element("treatment");
			Document doc = new Document(root);
			//set up top level structure
			Element meta = new Element("meta");
			root.addContent(meta);
			Element nomen = new Element("nomenclature");
			root.addContent(nomen);
			Element description = new Element("description");
			root.addContent(description);
			Element otherinfo = new Element("otherinfo");
			root.addContent(otherinfo);
			//populate meta
			Element source = new Element("source");
			Element volume = new Element("volume");
			source.setText("Treatise on Invertebrate Paeontology");
			volume.setText("Part B");
			meta.addContent(source);
			meta.addContent(volume);
			
			//populate nomen
			Element th = new Element("taxon_hierarchy");
			th.setText(getTH(parts[1], filecount));
			nomen.addContent(th);
			Element name = new Element("name");
			name.setText(parts[1].replaceFirst("^[NG]>", ""));
			nomen.addContent(name);
			
			//populate description
			description.setText(parts[2].replaceFirst("^[NG]D>", ""));
			
			//populate otherinfo
			otherinfo.setText(parts[3].replaceFirst("^[NG]O>", ""));
			
			//output doc
			File f = new File(outputfolder, filecount+".xml");
			XMLOutputter serializer = new XMLOutputter();
			serializer.output(doc, new DataOutputStream(new FileOutputStream(f)));
		}catch(Exception e){
			e.printStackTrace();
		}
	}
		
	/**
	 * also populate filename2taxon table
	 * @param string
	 * @param filecount
	 * @return
	 */
	private String getTH(String string, int filecount) {
		String rank = "";
		String taxon = "";
		String th = "";
		String values = "";
		String fields = "";
		if(string.startsWith("N>")){
			String[] tmp = string.replaceFirst("^N>", "").split("\\s+");
			rank = tmp[0];
			taxon = tmp[1];
		}else{
			if(string.matches(speciesNamePattern)){
				rank = "Species";
				//N. (Nitellopsis)
				taxon = string.substring(string.indexOf(">")+1, string.indexOf(")")+1);
			}else{
				rank = "Genus";
				taxon = string.substring(string.indexOf(">")+1, string.indexOf(" "));
			}
		}
		
		int r = ranks.indexOf(rank);
		this.ranktaxon.set(r, taxon);
		//reset lower ranks
		for(int i = r+1; i < ranktaxon.size(); i++){
			ranktaxon.set(i, "");
		}
		//read up to current rank
		for(int i = 0; i <= r; i++){
			if(ranktaxon.get(i).trim().length()>0){
				th += ranks.get(i)+"_"+ranktaxon.get(i)+"_";
			}
			values += "'"+ranktaxon.get(i)+"',";
			fields += ranks.get(i)+",";
		}
		th = th.replaceFirst("_$", "");
		values = values.replaceFirst(",$", "");
		fields = fields.replaceFirst(",$", "").replaceFirst("Order", "Oorder");
		
		//populate filename2taxon table
		try{
			Statement stmt = conn.createStatement();
			String q = "insert into "+prefix+"_filename2taxon ("+ fields +") values ("+values+")";
			if(debug){
				//System.out.println(q);
			}
			stmt.execute(q);
			stmt.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		return th;
	}

	private void structureDescriptions(String source) {
		for(int i = 0; i < this.allsegments.size(); i++){
			ArrayList<String> segment = this.allsegments.get(i);
			segment = structureSegment(segment);
			if(debug){
				System.out.println(segment);
			}
			this.allsegments.set(i, segment);
		}		
	}
	
	/**
	 * A ranked taxon may have multiple descriptions at genus level.
	 * Collects higher taxon's name and a paragraph of description
	 * Then
	 * Collects genus descriptions and format them one by one
	 * @param segment
	 * @return
	 */
	private ArrayList<String> structureSegment(ArrayList<String> segment) {
		int i = 0;
		StringBuffer sb = new StringBuffer();
		String nomenclature = "<N>";
		if(!segment.get(i).matches("^[A-Z][a-z]+\\s+"+this.uncertain+"$")){ //not a parent rank uncertain group, collect information about the rank
			do{
				nomenclature+=segment.get(i) + " ";
				i++;
			}while(i<segment.size() && !segment.get(i).matches("^\\[.*?\\]$"));		
			nomenclature += segment.get(i++);
			sb.append(nomenclature);
			
			if(i<segment.size()){
				String description = "<ND>"+segment.get(i++);
				sb.append(description);
			}
			
			String othertext = "<NO>";
			while(i<segment.size() && !segment.get(i).matches(this.genusPattern)){//genus description
				othertext += "<P>"+segment.get(i++);
			}
			sb.append(othertext).append("_BREAK_");
		}else{
			nomenclature += segment.get(i++);
			sb.append(nomenclature).append("_BREAK_");
		}
		
		
		//start to collect genus descriptions
		while(i < segment.size()){
			String genus = "";
			do{
				genus += segment.get(i++);
			}while(i<segment.size() && !segment.get(i).matches(this.genusPattern));
			if(debug){
				System.out.println();
				System.out.println(genus);
			}
			//structure individual genus description
			String[] threeparts = structureGenus(genus);
			sb.append("<G>"+threeparts[0]+"<GD>"+threeparts[1]+"<GO>"+threeparts[2]).append("_BREAK_");
		}
		
		//re-populate segment
		//a segment is an arraylist of strings
		//each string is a taxon description, containing a set of <[NG]> <[NG]D> and <[NG]O>
		segment = new ArrayList(Arrays.asList(sb.toString().replaceFirst("_BREAK_$", "").split("_BREAK_")));
		return segment;
	}

	/**
	 * split a genus description into 3 parts: name, description, and other
	 * <N>Name [][]. <D>Description. [Discussion?.] Discussion?. Time [.:] Distribution?. <O>—— FIG.
	 * @param genus
	 * @return
	 */
	private String[] structureGenus(String genus) {
		genus = genus.trim();
		String[] threeparts = new String[3];
		//threeparts[0] = genus.substring(0, genus.indexOf("].")+2); //some without the period
		Matcher m = genusNomenPtn.matcher(genus);
		if(m.matches()){
			threeparts[0] = m.group(1);
		}else{
			System.err.println("genus nomenclature information not found in :"+genus);
			System.exit(2);
		}
		genus = genus.replace(threeparts[0], "").trim();
		m = this.figPtn.matcher(genus);
		int i = -1;
		if(m.find()){
			i = m.start();
		}
		//int i = genus.indexOf("—— FIG.");
		if(i>=0){
			threeparts[1] = genus.substring(0, i).trim();
			threeparts[2] = genus.substring(i).trim();
		}else{
			threeparts[1] = genus;
			threeparts[2] = "";
		}
		//remove discussions from threeparts[1]
		i = threeparts[1].indexOf("[");
		if(i>=0){
			//remove discussion
			String descp = threeparts[1];
			threeparts[1] = threeparts[1].substring(0, i).trim();
			String rest = descp.substring(i).trim();
			rest = rest.replaceFirst("^\\[.*?\\]", "").trim(); //still may have some discussion, followed by Time:Distribution (which are all capitalized words)
			String[] sents = rest.split("[.]");
			for(int j = 0; j<sents.length; j++){
				if(sents[j].replaceAll("\\W", "").trim().matches(distributionPattern)){//containing two or more consecutive capitalized words
					threeparts[1] +=" "+sents[j++].trim();
					while(j<sents.length){
						threeparts[1]+=" "+sents[j++].trim();
					}
				}
			}		
		}else{
			//do nothing
		}
		
		return threeparts;
	}

	private void segmentDescriptions(String source) {
		int i = 0;
		//fastforward to the first heading with a rank
		while(i<ptypes.size() && ptypes.get(i).indexOf("heading") < 0 
				|| ! this.ranks.contains(paragraphs.get(i).trim().split("\\s+")[0])){
			i++;
		}
		//collect everything until the next rank is encountered
		while(i<ptypes.size()){
			ArrayList<String> segment = new ArrayList<String>();
			do{
				segment.add(paragraphs.get(i));
				i++;
			}while(i<ptypes.size() && (ptypes.get(i).indexOf("heading") < 0 || !this.ranks.contains(paragraphs.get(i).trim().split("\\s+")[0])));
			//add to all
			allsegments.add(segment);
		}
	}

	/**
	 * get from start-heading to end-heading
	 * append add2last text to the last chunk
	 */
	private void getDescriptions(String source) {
		try{
			ArrayList<String> paraIDs = new ArrayList<String> ();
			ArrayList<String> paras = new ArrayList<String> ();
			String condition = "source = '"+source+"' and paragraph ='"+this.descriptionStartHeading+"' and paraID >"+end;
			String orderby = "";
			DatabaseAccessor.selectParagraphs(prefix, condition, orderby, paraIDs, paras, conn);
			if(paraIDs.size()>0){
				start = Integer.parseInt(paraIDs.get(0));
			}else{
				System.err.println("descriptionStartHeading not found");
				System.exit(3);
			}
			
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source = '"+source+"' and paragraph ='"+this.descriptionEndHeading+"' and paraID >"+start;
			orderby = "";
			DatabaseAccessor.selectParagraphs(prefix, condition, orderby, paraIDs, paras, conn);
			if(paraIDs.size()>0){
				end = Integer.parseInt(paraIDs.get(0));
			}else{
				System.err.println("descriptionStartHeading not found");
				System.exit(3);
			}
			/*remove non description paragraphs
			for(int i = this.paraIDs.size()-1; i >=0; i--){
				int id = Integer.parseInt(this.paraIDs.get(i));
				if(id <= start){
					this.paragraphs.remove(i);
					this.ptypes.remove(i);
					this.padd2last.remove(i);
				}
				if(id >= end){
					this.paragraphs.remove(i);
					this.ptypes.remove(i);
					this.padd2last.remove(i);
				}
			}
			*/
			paraIDs = new ArrayList<String> ();
			paras = new ArrayList<String> ();
			condition = "source = '"+source+"' and type in ('content','content_heading') and paraID < "+end +" and paraID > "+start;
			ArrayList<String> types = new ArrayList<String>();
			ArrayList<String> add2last = new ArrayList<String>();
			orderby = "";
			DatabaseAccessor.selectParagraphsTypesAdd2Last(prefix, condition, orderby, paraIDs, paras, types, add2last, conn);
			this.paragraphs = paras;
			this.ptypes = types;
			this.padd2last = add2last;
			appendAdd2last();
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	/**
	 * operates on this.paragraphs, this.types, and this.padd2last
	 * all add2last text are "content" type.
	 */
	private void appendAdd2last() {
		for(int i = padd2last.size()-1;  i>0; i--){
			if(padd2last.get(i)!=null && padd2last.get(i).startsWith("y")){
				this.paragraphs.set(i-1, this.paragraphs.get(i-1)+" "+this.paragraphs.get(i));
				this.paragraphs.remove(i);
				this.ptypes.remove(i);
				this.padd2last.remove(i);
				//this.paraIDs.remove(i);
			}
		}
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String paraTableName = "testb_paragraphs";
		String outputfolder = "Z:\\DATA\\Treatise\\recent\\xml\\partB";
		ContentFixerTreatiseB cftb = new ContentFixerTreatiseB(paraTableName, outputfolder);
		cftb.makeCleanContent();
	}

}
