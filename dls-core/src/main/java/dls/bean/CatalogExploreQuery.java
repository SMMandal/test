package dls.bean;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.joda.time.DateTime;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class CatalogExploreQuery {

//	public static final String META_QUERY_REGEX = "^((\\(?(([\\w\\*]+[ ]*(=)[ ]*'[^&!,'\"=]+')(?!([ ]*['\\w\\(][ ]*))|([\\w\\*]+)(?!([ ]*['\\(])[ ]*)|('[^&!,'\"=]+')(?!([ ]*['\\w\\(])[ ]*))\\)?([ ]*[&,]?[ ]*))+)(?<![,& ])$";  // 17-Jul-23
	public static final String META_QUERY_REGEX = "^((\\(?(([\\w\\*]+[ ]*(>=|<=|>|<|!=|=)[ ]*'[^&!,'\"=]+')(?!([ ]*['\\w\\(][ ]*))|([\\w\\*]+)(?!([ ]*['\\(])[ ]*)|('[^&!,'\"=]+')(?!([ ]*['\\w\\(])[ ]*))\\)?([ ]*[&,]?[ ]*))+)(?<![,& ])$";  // 7-Dec-23
//	public static final String META_QUERY_REGEX = "^((\\(?(([\\w\\*]+[ ]*(=)[ ]*'[\\s\\w.*:\\-\\+\\/\\\\]+')(?!([ ]*['\\w\\(][ ]*))|([\\w\\*]+)(?!([ ]*['\\(])[ ]*)|('[\\s\\w.*:\\-\\+\\/\\\\]+')(?!([ ]*['\\w\\(])[ ]*))\\)?([ ]*[&,]?[ ]*))+)(?<![,& ])$";
////	public static final String META_QUERY_REGEX = "^(([\\w\\*]+|'[^'.]+')?|([\\w\\*]+( = |=| =|= )('[^&.]*'))?(,|, | , | ,)?)+$";
	public static final String SIZE_QUERY_REGEX = "^(>|<|=|>=|<=|!=)(\\d+(\\.\\d{1,3})?)[kKmMgGtTpP]?[bB] *$";
	public static final String FILE_COUNT_REGEX = "^(>|<|=|>=|<=|!=)\\d+ *$";
	public static final String URI_QUERY_REGEX = "^(([^%<>:\"\\\\|?*]+)|(\\w+:\\/\\/.+))$";


//	private String text;

	@Size(max=2, message="{query.many.timevalue}")
	@DateTimeFormat(pattern="dd-MMM-yyyy HH:mm:ss Z", fallbackPatterns = {"dd-MMM-yyyy"})
	private List<DateTime> time;
	private List<String> user;
	@Pattern(regexp= SIZE_QUERY_REGEX, message="{query.invalid.size}")
	private String size;

	@Pattern(regexp= FILE_COUNT_REGEX, message="File count parameter syntax is not correct, try '=0'")
	private String fileCount;
//	private Boolean external;
//	private Boolean transferred;
//	private Boolean deleted;
//	private Boolean ownFile;
//	private Boolean bundled;

	@Pattern(regexp= META_QUERY_REGEX, message="{query.invalid.metadata}")
	private String metadata;

	private List <@Pattern(regexp= URI_QUERY_REGEX, message="Invalid character in path") String> path;
	private List <String> name;
	private String nameRegex;
	private List <String> savepoint;

	@Pattern(regexp="(?i)(fileOnly|directoryOnly|fileFirst|directoryFirst)", message="Invalid fetch value")
	private String fetch;
	@Pattern(regexp="(?i)(createdOn|name|size|createdBy)", message="{query.invalid.orderBy}")
	private String sort;
	@Pattern(regexp="(?i)(asc|desc)", message="Invalid order, must be 'asc' or 'desc'")
	private String order;
	private List <String> parent;

	private List <@Pattern(regexp = "(metadata|time|user|size|path|name|nameRegex|parent|fileCount|savepoint)", message = "Invalid AND parameter name") String> and;
//	private Boolean locked;
	@Range(min=1L, max=Integer.MAX_VALUE, message="{query.invalid.pageNo}")
	private Integer pageNo = 1;
	@Range(min=1L, max=Integer.MAX_VALUE, message="Invalid page size")
	private Integer pageSize;


	public interface PropertyNames {

		public static String name_ = "name";
		public static String time_ = "time";
		public static String user_ = "user";
		public static String size_ = "size";
		public static String fileCount_ = "fileCount";
		public static String nameRegex_ = "nameRegex";
		public static String parent_ = "parent";
		public static String metadata_ = "metadata";
		public static String path_ = "path";
	}

	public static class Parser {
		public static List<String> toMetadataList(String metadata) {
			return Optional.ofNullable(metadata)
					.map(m -> Arrays.asList(m.split(" *, *")))
					.orElse(Lists.newArrayList());
		}
	}
}

