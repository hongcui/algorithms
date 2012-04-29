package informationContent;

public class Patterns {
	//public static String taxonNamePattern = "^((\\??[A-Z]([a-z]+))|[A-Z]\\.\\s\\([A-Z][a-z]+\\))\\s([A-Zжид]\\.?\\s?([A-Zжид']+))|([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.)";
	public static String taxonExtractPattern 	= "^(\\??[A-Z][a-z]+)\\s([A-Zжид]\\.?\\s?([A-Zжид']+.*[.*])(.*))$";
	public static String taxonNamePattern 		= "(^[A-Z]\\.\\s\\([A-Z][a-z]+\\)(\\.?|\\s([A-Zжид]\\.?\\s?([A-Zжид']+)))).*?|" +
			"(^\\??[A-Z][a-z]+\\s([A-Zжид]\\.?\\s?([A-Zжид']+))).*?";
	public static String bellowGeneraPattern 	= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\)).*?";
	public static String subGeneraPattern 		= "([A-Z]\\.\\s\\([A-Z][a-z]+\\)\\.?)(.*?)";
	public static String subGenusPattern 		= "^([A-Z]\\.\\s\\([A-Z][a-z]+\\))\\s([A-Zжид]\\.?\\s?([A-Zжид']+)).*?";
	public static String timePatterm 			= "^.*?[A-Z][a-z\\-]+\\.?$";
}


