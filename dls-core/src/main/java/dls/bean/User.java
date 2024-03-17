package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static dls.util.BeanValidationConstraint.USERNAME_LEN;
import static dls.util.BeanValidationConstraint.USERNAME_REGEX;

@Schema(description="Create user in DLS with TCUP tenant key and DLS key")
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "dlsUser")
public @Data @Builder class User {
	
	@JsonProperty("dls-key")
	@Length(max = USERNAME_LEN, message = "{invalid.key.length}")
	private String dlsKey;

	@Schema(example = "username")
	@NotBlank  (message="{user.blank.dlsuser}")
	@JsonProperty("dls-user")
	@Length(max = USERNAME_LEN, message = "{invalid.user.length}")
	@Pattern(regexp = USERNAME_REGEX, message = "{invalid.username}")
	private String dlsUser;

	
}
