package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import dls.util.BeanValidationConstraint;
import lombok.*;
import org.hibernate.validator.constraints.UniqueElements;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

import static dls.util.BeanValidationConstraint.PERMISSION_ACTION_REGEX;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
//@Validated
@EqualsAndHashCode(of = "users")
@ToString(of = "users")
public class FAIRPermission {
	

	@NotEmpty(message = "{no.user.in.permission}")
	@UniqueElements(message = "{duplicate.records}")
	private List <String> users;

	@Pattern(regexp = BeanValidationConstraint.PERMISSION_ACTION_REGEX , message = "{invalid.permission.action}")
	@NotBlank(message =  "{invalid.permission.action}")
	private String action;


	public enum Action {
		R, W, D
	}
}
