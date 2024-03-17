package dls.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.joda.time.DateTime;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class CatalogQuery {

	public static final String META_QUERY_REGEX = "^((\\(?(([\\w\\*]+[ ]*(=|>|<|>=|<=|!=)[ ]*'[^&!,'\"=]+')(?!([ ]*['\\w\\(][ ]*))|([\\w\\*]+)(?!([ ]*['\\(])[ ]*)|('[^&!,'\"=]+')(?!([ ]*['\\w\\(])[ ]*))\\)?([ ]*[&,]?[ ]*))+)(?<![,& ])$"; // 17-Jul-23
//	public static final String META_QUERY_REGEX = "^((\\(?(([\\w\\*]+[ ]*(=|>|<|>=|<=|!=)[ ]*'[\\s\\w.*:\\-\\+\\/\\\\]+')(?!([ ]*['\\w\\(][ ]*))|([\\w\\*]+)(?!([ ]*['\\(])[ ]*)|('[\\s\\w.*:\\-\\+\\/\\\\]+')(?!([ ]*['\\w\\(])[ ]*))\\)?([ ]*[&,]?[ ]*))+)(?<![,& ])$";
//	public static final String META_QUERY_REGEX = "^(([\\w\\*]+|'[^'.]+')?|([\\w\\*]+( = |=| =|= )('[^&.]*'))?(,|, | , | ,)?)+$";
	public static final String SIZE_QUERY_REGEX = "^(>|<|=|>=|<=)(\\d+(\\.\\d{1,3})?)[kKmMgGpP]?[bB]$";
	public static final String URI_QUERY_REGEX = "^(([^%<>:\"\\\\|?*]+)|(\\w+:\\/\\/.+))$";


	private String text;

	@Size(max=2, message="{query.many.timevalue}")
	@DateTimeFormat(pattern="dd-MMM-yyyy HH:mm:ss Z") 
	private List<DateTime> time;
	@Pattern(regexp= SIZE_QUERY_REGEX, message="{query.invalid.size}")
	private String size;
	private Boolean external;
	private Boolean transferred;
	private Boolean deleted;
	private Boolean ownFile;
	private Boolean bundled;

	@Pattern(regexp= META_QUERY_REGEX, message="{query.invalid.metadata}")
	private String metadata;
	@Pattern(regexp= URI_QUERY_REGEX, message="{query.invalid.uri}")
	private String uri;
	private String filename;
	private String savepoint;
	@Pattern(regexp="(createdOn|fileName|size)", message="{query.invalid.orderBy}")
	private String sort;
	private String directory;
	private Boolean locked;
	@Range(min=1L, max=Integer.MAX_VALUE, message="{query.invalid.pageNo}")
	private Integer pageNo = 1;
	private Integer pageSize;
	
}

