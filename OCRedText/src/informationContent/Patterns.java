package informationContent;

public class Patterns {
	//public static String taxonNamePattern = "^((\\??[A-Z]([a-z]+))|[A-Z]\\.\\s\\([A-Z][a-z]+\\))\\s([A-Z���]\\.?\\s?([A-Z���']+))|([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.)";
	public static String taxonExtractPattern 	= "^(\\??[A-Z][a-z]+)\\s([A-Z���]\\.?\\s?([A-Z���']+.*[.*])(.*))$";
	public static String taxonNamePattern 		= "(^[A-Z]\\.\\s\\([A-Z][a-z]+\\)(\\.?|(\\s\\([A-Z���][^\\)]*?\\))?\\s([A-Z���]\\.?\\s?([A-Z���']+)))).*?|" +
			"(^\\??[A-Z][a-z]+(\\s\\([A-Z���][^\\)]*?\\))?\\s([A-Z���]\\.?\\s?([A-Z���']+))).*?";
	public static String underGeneraPattern 	= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\)).*?";
	public static String subGeneraPattern 		= "([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.?)(.*?)";
	public static String subGenusPattern 		= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\))(\\s\\([A-Z���][^\\)]*?\\))?\\s([A-Z���]\\.?\\s?([A-Z���']+)).*?";
	public static String taxonNameWithBrackets = "(^[A-Z]\\.\\s\\([A-Z][a-z]+\\)(\\s\\([A-Z���][^\\)]*?\\))\\s([A-Z���]\\.?\\s?([A-Z���']+)))).*?|" +
			"(^\\??[A-Z][a-z]+(\\s\\([A-Z���][^\\)]*?\\))\\s([A-Z���]\\.?\\s?([A-Z���']+))).*?";
	public static String timePatterm 			= "^.*?[A-Z][a-z\\-]+\\.?$";
	public static String nameSplitPattern = "^([A-Z]\\.\\s\\([A-Z][a-z]+\\)|\\??[A-Z][a-z]+)" + //first part
			"(\\s\\([A-Z���][^\\)]*\\))?" + //2nd part: optional (info)			
			"(\\s[A-Z���'\\-&]+.*?)" + //3rd part:name
			"(\\snom\\.\\snov\\..*?)?" + //3.5rh part: nom. nov. ...., optional
			//"(\\s[A-Z���](\\.\\s|')?\\s[A-Z���-])" + //3rd part: name1
			//"(,?\\s(&\\s|and\\s)[A-Z](\\.\\s|')\\s[A-Z���-])" + //3rd additional part: name2 &|and name3
			"(\\sin\\s[A-Z���'\\-&]+.*?)?" + //4th part: publication start with 'in', optional
			"(,?\\s[0-9]{4}b?)" + //5th part: year
			"(,\\sp\\.\\s?[0-9]+)?" + //6th part: page number, optional
			"(.*?)$";
}


