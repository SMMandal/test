package dls.bean;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static dls.util.BeanValidationConstraint.*;

@Schema(description="Provision TCUP user in DLS with TCUP tenant key and TCUP user name")
@Validated
public @Data class Tenant {

//	@Schema(name="ApiKey", example="x", required=true)
	@NotBlank (message="{user.blank.apikey}")
	@JsonProperty("api-key")
	private String apiKey;
	
//	@Schema(name="TcupUser", example="x", required=true)
//	@NotBlank (message="{user.blank.tcupuser}")
	@Schema(example = "username")
	@Length(max = USERNAME_LEN, message = "{invalid.user.length}")
	@Pattern(regexp = USERNAME_REGEX, message = "{invalid.username}")
	@JsonProperty("tcup-user")
	private String tcupUser;
	
//	@Schema(name="AdminDlsKey", example="x")
	@JsonProperty("admin-dls-key")
	private String dlsAdminKey;
	@JsonProperty("admin-user-name")
@Schema(example = "dlsadmin")
	@Length(max = USERNAME_LEN, message = "{invalid.user.length}")
	@Pattern(regexp = USERNAME_REGEX, message = "{invalid.username}")
	private String dlsAdminUser;

	@Schema(example = "tcs")
	@Length(min = ORGANIZATION_LEN_MIN, max = ORGANIZATION_LEN_MAX, message = "{invalid.organization.length}")
	@Pattern(regexp = ORGANIZATION_REGEX, message = "{invalid.organization}")
	private String organization;

}
