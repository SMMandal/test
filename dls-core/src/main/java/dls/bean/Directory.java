package dls.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.UniqueElements;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Date;
import java.util.List;

import static dls.util.BeanValidationConstraint.*;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Validated
@EqualsAndHashCode(of="directory")
@ToString(of = "directory")
@Schema
public class Directory {
	@Schema(example = "/directory")
	@JsonProperty("directory")
	@Length(max = DIRECTORY_PATH_LEN, message = "{too.long.directory.path}")
	@Pattern(regexp = DIRECTORY_REGEX, message = "{invalid.directory.regex}")
	@NotBlank(message = "{blank.directory.name}")
	private String directory;

	@Schema(example = "STANDARD")
	@Pattern(regexp = ENFORCEMENT_REGEX, message = "{invalid.enforcement}")
	private String enforcement;
	@JsonProperty("permissions")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@UniqueElements(message = "{duplicate.records}")
	private List<@Valid Permission> permissions;
	@JsonProperty("metadata-rule")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@UniqueElements(message = "{duplicate.records}")
	private List<@Valid MetadataRule> rule;

	@Schema(example = "key1=value1,keyN=valueN")
//	@Pattern(regexp = DIRECTORY_META_REGEX, message = "{invalid.kv.format}")
	@JsonProperty("meta-data")
	private String metadata;

	@JsonProperty(value ="created-on", access = JsonProperty.Access.READ_ONLY)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
	private Date createdOn;
}
