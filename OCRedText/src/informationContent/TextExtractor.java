/**
 * $Id$
 */
package informationContent;

import java.io.*;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

/**
 * extract clean paragraphs from a volume of text without meaningful doc style
 * input is document.xml from a docx document
 * 
 * the program extracts text in <w:p> tags (text "paragraphs") regardless of the layout of the original word document. 
 * 
 * run TextExtractor first if a doc document is given, then run ContentFetcher, then ContentFixer
 * if txt file is already given, skip TextExtractor
 * 
 * @author hong
 */
public class TextExtractor{
	
	private String source;
	private static final Logger LOGGER = Logger.getLogger(TextExtractor.class);
	private String target;
	private int count;
	protected Element treatment;
	private XMLOutputter outputter;
	public static String start = ".*?(Heading|Name).*"; //starts a treatment
	public String tribegennamestyle = "caps";
	
	
	public TextExtractor(String source, String target) {
		this.source = source;
		this.target = target;
		treatment = new Element("treatment");
	}
	/**
	 * Extract the data from the source file
	 * 
	 * TODO: unzip the document.xml from the docx file
	 */
	public void extract() throws Exception {
		try {
			//listener.progress(1);
			// init the outputter
			outputter = new XMLOutputter(Format.getPrettyFormat());

			// build the root element from the xml file
			SAXBuilder builder = new SAXBuilder();
			Document doc = builder.build(new File(source + "/document.xml"));
			Element root = doc.getRootElement();
			
			// find all <w:p> tags
			List wpList = XPath.selectNodes(root, "/w:document/w:body/w:p");
			//List wpList = XPath.selectNodes(root, "w:p");
			// iterate over the <w:p> tags
			count = 1;
			int total = wpList.size();
			for (Iterator iter = wpList.iterator(); iter.hasNext();) {
				processParagraph((Element) iter.next());
				//System.out.println(count+ " paragraphs found");
				count++;
				//listener.progress((count*100) / total);
			}

			// output the last file
			output();
		} catch (Exception e) {
			LOGGER.error("Unable to parse/ extract the file in VolumeExtractor:extract", e);
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * To process a w:p tag
	 * 
	 * output style:text pairs for each paragraph
	 * @param wp
	 * @throws JDOMException
	 */
	protected void processParagraph(Element wp) throws Exception {
		Attribute att = (Attribute) XPath.selectSingleNode(wp, 	"./w:pPr/w:pStyle/@w:val");
		String style = "";
		if(att == null){//TODO: issue a warning
			//System.out.println("this paragraph has no pStyle attribute");
			//return;
		}else{
			style = att.getValue();
		}
		//System.out.println(style);

		Element se = new Element("style");
		se.setText(style);

		Element pe = new Element("paragraph");
		pe.addContent(se);

		
		extractTextParagraph(wp, pe);
		// add the element to the treatment (root) element
		treatment.addContent(pe);
	}
	
	protected void extractTextParagraph(Element wp, Element pe) throws JDOMException {
		StringBuffer buffer=new StringBuffer();
		
		List textList = XPath.selectNodes(wp, "./w:r/w:t");
		for (Iterator ti = textList.iterator(); ti.hasNext();) {
			Element wt = (Element) ti.next();
			buffer.append(wt.getText()).append("#");
		}
		String text = buffer.toString().replaceAll("-#", "-").replaceAll("#", " ").replaceAll("\\s+", " ").trim();
		System.out.println(text);
		System.out.println();
		Element te = new Element("text");
		te.setText(text);
		pe.addContent(te);
		
	}
	
	/**
	 * To output the <treatment> element
	 * 
	 * @throws IOException
	 */
	private void output() throws Exception {
		try {
			
			String file = target + "extracted\\" + count + ".xml";
			String targetFolder = target + "extracted\\";
			File tf = new File(targetFolder);
			if(! tf.exists() || tf.isFile()){
				tf.mkdir();
			}
			Document doc = new Document(treatment);
			BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(file));
			/* Producer */
			outputter.output(doc, out);
			
			/* Consumer */
			//listener.info(count + "", file);

		} catch (IOException e) {
			throw e;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//[Console output redirected to file:X:\DATA\Treatise\Word\Part D.txt]
		
		
		//String source = "X:/DATA/Treatise/Word/Part D/word";
		//String target = "X:/DATA/Treatise/Word/Part D/word/";
		
		
		//String source ="X:/DATA/Treatise/Word/part D converted to doc test/word";
		//String target ="X:/DATA/Treatise/Word/part D converted to doc test/word/";
		
		//this folder produces broken words, e.g. c la s s 
		//String source ="X:/DATA/Treatise/recent/docs/parte-r-v3-1-200-1-col/word";
		//String target ="X:/DATA/Treatise/recent/docs/parte-r-v3-1-200-1-col/word/";
		
		String source ="X:/DATA/Treatise/recent/docs/part-b-protoctista-1/word";
		String target ="X:/DATA/Treatise/recent/docs/part-b-protoctista-1/word/";
		TextExtractor tet = new TextExtractor(source, target);
		try{
			tet.extract();
		}catch(Exception e){
			e.printStackTrace();
		}

	}
}
