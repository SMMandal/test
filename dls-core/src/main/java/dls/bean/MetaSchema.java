package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.Range;
import org.hibernate.validator.constraints.UniqueElements;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Validated
public class MetaSchema {

	@JsonProperty("metadata")
	@UniqueElements(message = "{duplicate.records}")
	private List<@Valid MetadataDef> metadataList;
	@JsonProperty("config")
	private @Valid MetadataConfig metadataConfig;

	@AllArgsConstructor
	@NoArgsConstructor
	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Validated
	public static class MetadataConfig {
		@JsonProperty("max-metadata-per-file")
		@Range(min = 0, max = 1000, message="{schema.invalid.maxMetaPerFile}")
		private Integer maxMetaPerFile;

		@JsonProperty("max-key-len")
		@Range(min = 0, max = 255, message="{schema.invalid.maxKeyLen}")
		private Integer maxKeyLen;

		@JsonProperty("max-value-len")
		@Range(min = 0, max = 255, message="{schema.invalid.maxValueLen}")
		private Integer maxValueLen;

		@JsonProperty("allow-adhoc")
		private Boolean allowAdhoc;
	}


	@AllArgsConstructor
	@NoArgsConstructor
	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Validated
	@ToString
	@EqualsAndHashCode(of = "name")
	@Schema
	public static class MetadataDef {

		@Schema(example = "keyname")
		@Pattern(regexp = "[\\w]+", message = "{invalid.metadata.key}")
		@Size(min = 3, max = 150, message = "{schema.long.name}")
		@NotBlank(message = "{schema.meta.name.blank}")
		private String name;

		@NotBlank(message = "{schema.meta.type.blank}")
		@Pattern(regexp = "(TEXT|text|NUMERIC|numeric)", message = "{invalid.meta.type}")
		private String type;

		@Size(max = 250, message = "{schema.long.description}")
		private String description;

	}

	public enum MetadataType {
		TEXT,
		NUMERIC
	}
}
