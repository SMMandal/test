package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Validated
@EqualsAndHashCode(of="name")
@ToString(of = "name")
@Schema
public @Data @Builder class MetadataRule {


	@Schema(example = "keyname")
	@Pattern(regexp = "[\\w]+", message = "{invalid.metadata.key}")
	@Size(max = 255, message = "{schema.long.name}")
	@NotBlank(message = "{schema.meta.name.blank}")
	private String name;
	@JsonProperty("default-value")
	private String defaultValue;
	@NotBlank(message = "{schema.meta.type.blank}")
	@Pattern(regexp = "(TEXT|text|NUMERIC|numeric)", message = "{invalid.meta.type}")
	private String type;
	@JsonProperty("value-mandatory")
	private Boolean valueMandatory;
	
	
}
