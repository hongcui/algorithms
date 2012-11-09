package informationContent;

public class Patterns {
	//public static String taxonNamePattern = "^((\\??[A-Z]([a-z]+))|[A-Z]\\.\\s\\([A-Z][a-z]+\\))\\s([A-Zжид]\\.?\\s?([A-Zжид']+))|([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.)";
	public static String taxonExtractPattern 	= "^(\\??[A-Z][a-z]+)\\s([A-Zжид]\\.?\\s?([A-Zжид']+.*[.*])(.*))$";
	public static String taxonNamePattern 		= "(^[A-Z]\\.\\s\\([A-Z][a-z]+\\)(\\.?|(\\s\\([A-Zжид][^\\)]*?\\))?\\s([A-Zжид]\\.?\\s?([A-Zжид']+)))).*?|" +
			"(^\\??[A-Z][a-z]+(\\s\\([A-Zжид][^\\)]*?\\))?\\s([A-Zжид]\\.?\\s?([A-Zжид']+))).*?";
	public static String underGeneraPattern 	= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\)).*?";
	public static String subGeneraPattern 		= "([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.?)(.*?)";
	public static String subGenusPattern 		= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\))(\\s\\([A-Zжид][^\\)]*?\\))?\\s([A-Zжид]\\.?\\s?([A-Zжид']+)).*?";
	public static String taxonNameWithBrackets = "(^[A-Z]\\.\\s\\([A-Z][a-z]+\\)(\\s\\([A-Zжид][^\\)]*?\\))\\s([A-Zжид]\\.?\\s?([A-Zжид']+)))).*?|" +
			"(^\\??[A-Z][a-z]+(\\s\\([A-Zжид][^\\)]*?\\))\\s([A-Zжид]\\.?\\s?([A-Zжид']+))).*?";
	public static String timePatterm 			= "^.*?[A-Z][a-z\\-]+\\.?$";
	public static String nameSplitPattern = "^([A-Z]\\.\\s\\([A-Z][a-z]+\\)|\\??[A-Z][a-z]+)" + //first part
			"(\\s\\([A-Zжид][^\\)]*\\))?" + //2nd part: optional (info)			
			"(\\s[A-Zжид'\\-&]+.*?)" + //3rd part:name
			"(\\snom\\.\\snov\\..*?)?" + //3.5rh part: nom. nov. ...., optional
			//"(\\s[A-Zжид](\\.\\s|')?\\s[A-Zжид-])" + //3rd part: name1
			//"(,?\\s(&\\s|and\\s)[A-Z](\\.\\s|')\\s[A-Zжид-])" + //3rd additional part: name2 &|and name3
			"(\\sin\\s[A-Zжид'\\-&]+.*?)?" + //4th part: publication start with 'in', optional
			"(,?\\s[0-9]{4}b?)" + //5th part: year
			"(,\\sp\\.\\s?[0-9]+)?" + //6th part: page number, optional
			"(.*?)$";
	public static String agePrefix = "\\??(Upper|upper|Lower|lower|Middle|middle|up-per|uppermost|Uppermost|up-permost|Up-per|upper\\smost|lowest)";
	public static String singleAge = "(\\??" + agePrefix + "\\s)?" + "(to\\s)?" +  "(" + agePrefix + "\\s)?" + 
			"\\??[A-Z][a-z-ИМ]+1?" +
			"(\\s\\([\\s\\?A-Za-zИМ,-\\.:;\\?\\[\\]\\(\\)]+\\))?";
	public static String multipleAges = singleAge + "((,|\\sor)\\s" + singleAge + ")?";
	public static String ageOrAgeRange = multipleAges +
			"(-" + multipleAges+ ")?";
	public static String ageAndDescription = "^(.*?(\\.|\\])\\s)(" + ageOrAgeRange +
			"([:]\\s.*?)?[\\.\\]]" + //: + location part
			".*?)$";
	public static String ageWithoutDescription = "^" + ageOrAgeRange + "([:,;]\\s.*?)?[\\.\\]](.*?)$";
	public static String uncertainPattern = "^[A-Z][a-z]+\\s(Uncertain|UNCERTAIN)$";
}


