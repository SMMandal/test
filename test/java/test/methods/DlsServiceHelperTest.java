package test.methods;

import dls.service.DlsServiceHelper;

import java.util.Arrays;
import java.util.regex.Pattern;

import static dls.util.BeanValidationConstraint.SAVEPOINT_REGEX;

public class DlsServiceHelperTest {

	DlsServiceHelper dhelper = new DlsServiceHelper();
	
	//@Test
	public void testGenerateDfsPath() {
		
		/*System.out.println("Path : " + dhelper.generateDfsPath(UserVO.builder().dlsUser("bigyani").tcupUser("chakor").build(), "a.file.txt", "wd") );
		
		System.out.println("Path : " + dhelper.generateDfsPath(UserVO.builder().dlsUser("bigyani").tcupUser("chakor").build(), "file", "wd") );
		
		System.out.println("Path : " + dhelper.generateDfsPath(UserVO.builder().dlsUser("bigyani").tcupUser("chakor").build(), "file", null) );
		
		System.out.println("Path : " + dhelper.generateDfsPath(UserVO.builder().dlsUser("bigyani").tcupUser("chakor").build(), "file", "") );
		*/
		//System.out.println("Path : " + dhelper.generateDfsPath(UserVO.builder().dlsUser(null).tcupUser("chakor").build(), "file", "wd") );
	}
	
	public static void main(String[] args) {
		
		//checkSizeRegex();
		checkMetadataRegex();
//		checkMetadataValueRegex();
//		checkSavepointRegex();
	}


	private static void checkMetadataRegex() {
		
		String regex = "^(([\\w\\*]+|'[^'.]+')?|([\\w\\*]+( = |=| =|= )('[^&.]*'))?(,|, | , | ,)?)+$";
		//String regex = "((\\w*)=?('(\\w*)')?(,|&)?)+";
		//String splitRegex = "\\d+(\\.\\d{1,3})?";
		//Pattern p = Pattern.compile(splitRegex);
		String [] values = {
				"*ver*",
				"'%'",
				"key*='*val*'",
				"*key='val*'",
				"key='*dd*'",
				"'bbb',etaki = 'jalimal'", "k='v'", "kk= 'vv'", "kkk", "'v'", "k1='v1',k2='v2'",  "k1='v1'&k2='v2'", "k",
				"sd,sd", "'aa','bb'", "'aa', bb,11 ,q1","a='sd, sd'", "a1 & a2='b1','c1'",
				
				"'ds", "dsds=='dfdf'", "sa=ds",  "sdsa=ds'sd'", "'ds'='d11'", "sd''sd","", " "};
		for(String v : values) {
			boolean f = java.util.regex.Pattern.matches(regex, v);
			if(f)
				System.out.println(v + " ---> " + f);
			else
				System.err.println(v + " ---> " + f);
			//System.out.println(Arrays.asList(v.split(splitRegex)));
		}
		
	}

	private static void checkSizeRegex() {
		
		String regex = "^(>|<|=|>=|<=)(\\d+(\\.\\d{1,3})?)[kKmMgGpP]?[bB]$";
		String splitRegex = "\\d+(\\.\\d{1,3})?";
		Pattern p = Pattern.compile(splitRegex);
		String [] values = {">5kb", "=10Mb", ">=100.5GB", "<=6B", "<=0.5999kb", "=18b"};
		for(String v : values) {
			System.out.println(v + " is " + java.util.regex.Pattern.matches(regex, v));
			System.out.println(Arrays.asList(v.split(splitRegex)));
		}
		
	}

	private static void checkSavepointRegex() {

		String regex = SAVEPOINT_REGEX;
		String longSP = "a".repeat(151);
		String [] values = {"aaa", "/aaa/sas", "", "/", "/ /","aaa//dsasa", "sass/dsdd/",
				"ds sd", "/ds1/ds2/sd3/ds4/ds5/sd6/ds7/ds8/dd9/sd10/dd11",
				"//sa/ds/",
				longSP
		};
		for(String v : values) {
			System.out.println(v + " is " + java.util.regex.Pattern.matches(regex, v));
		}

	}

	private static void checkMetadataValueRegex() {
		final String KV_REGEX = "(([^\\s,=]+=[^,=]+)(?:,\\s*)?)+";
		final String regex = "[\\sa-zA-Z_0-9\\.\\*\\\\/\\+\\:\\-]+";
		String [] values = {
				"time=dd-MMM-yyyy HH:mm:ss.sss +5:30",
				"time=@latest",
				"@time=dd-MMM-yyyy HH:mm:ss.sss ",
				"time=fd>fd"
		};
		for(String kv : values) {
			System.out.print(kv + " is " + java.util.regex.Pattern.matches(KV_REGEX, kv));
			String v = kv.split("=")[1].trim();
			System.out.println("\t\t\t" + v + " is " + java.util.regex.Pattern.matches(regex, v));
		}
	}

}
