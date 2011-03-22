//$Id$
package informationContent;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.apache.log4j.Logger;

/**
 * $Id$
 * @author 
 * accessor for application.properties
 */

public class ApplicationUtilities {

	private static final Logger LOGGER = Logger.getLogger(ApplicationUtilities.class);
	private static Properties properties = null;
	private static FileInputStream fstream = null;
	
	static {
		try {
			fstream = new FileInputStream(System.getProperty("user.dir")+"\\application.properties");
		} catch (FileNotFoundException e) {
			LOGGER.error("couldn't open file in ApplicationUtilities:getProperties", e);
		}
	}
	
	private ApplicationUtilities(){}
	
	public static String getProperty(String key) {
	
		if(properties == null) {
			properties = new Properties();
			try {
				properties.load(fstream);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOGGER.error("couldn't open file in ApplicationUtilities:getProperty", e);
				e.printStackTrace();
			} 
		}
		return properties.getProperty(key);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
