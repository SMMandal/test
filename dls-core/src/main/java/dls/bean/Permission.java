package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.hibernate.validator.constraints.UniqueElements;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;

import static dls.util.BeanValidationConstraint.PERMISSION_ACTION_REGEX;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Validated
@EqualsAndHashCode(of = "users")
@ToString(of = "users")
@Schema
public class Permission {

	@NotEmpty(message = "{no.user.in.permission}")
	@UniqueElements(message = "{duplicate.records}")
	private List <String> users;

	@Schema(example = "RWD")
	@Pattern(regexp = PERMISSION_ACTION_REGEX , message = "{invalid.permission.action}")
	@NotBlank(message =  "{invalid.permission.action}")
	private String action;

	@Schema(example = "RWD")
	@Pattern(regexp = PERMISSION_ACTION_REGEX , message = "{invalid.permission.action}")
//	@NotBlank(message =  "{invalid.permission.action}")
	@JsonProperty("directory-action")
	private String directoryAction;

//	@Pattern(regexp = PERMISSION_ACTION_REGEX , message = "{invalid.permission.action}")
//	@NotBlank(message =  "{invalid.permission.action}")
//	@JsonProperty("metadata-action")
//	private String metadataAction;


	public enum Action {
		R, W, D
	}
	public static class Util {
		public static final char DIRECTORY_READ = 'A';
		public static final char DIRECTORY_CREATE = 'B';
		public static final char DIRECTORY_DELETE = 'C';

		public static final char META_READ = 'X';
		public static final char META_CREATE = 'Y';
		public static final char META_DELETE = 'Z';

		public static String buildAction(String action, String directoryAction/*, String metadataAction*/) {

			String str = Optional.ofNullable(action).orElse("")
					.concat(Optional.ofNullable(directoryAction)
							.orElse("")
							.toUpperCase()
							.replace('R', 'A')
							.replace('W', 'B')
							.replace('D', 'C'));
//					.concat(Optional.ofNullable(metadataAction)
//							.orElse("")
//							.toUpperCase()
//							.replace('R', 'X')
//							.replace('W', 'Y')
//							.replace('D', 'Z'));
			return str.isEmpty() ? null : str;
		}

		public static void parseAction(final String dbActionString, Permission permission) {

			if(null != dbActionString) {
				String action = dbActionString
						.replace("A","").replace("a","")
						.replace("B","").replace("b","")
						.replace("C","").replace("c","")
						.replace("X","").replace("x","")
						.replace("Y","").replace("y","")
						.replace("Z","").replace("z","");
				permission.setAction(action.isEmpty()?null:action);
				String dirAction = dbActionString
						.replace("R","").replace("r","")
						.replace("W","").replace("w","")
						.replace("D","").replace("d","")
						.replace("A","R").replace("a","R")
						.replace("B","W").replace("b","W")
						.replace("C","D").replace("c","D")
						.replace("X","").replace("x","")
						.replace("Y","").replace("y","")
						.replace("Z","").replace("z","");
				permission.setDirectoryAction(dirAction.isEmpty()?null:dirAction);
//				String metaAction = dbActionString
//						.replace("A","")
//						.replace("B","")
//						.replace("C","")
//						.replace("R","")
//						.replace("W","")
//						.replace("D","")
//						.replace("X","R")
//						.replace("Y","W")
//						.replace("Z","D");
//				permission.setMetadataAction(metaAction.isEmpty()?null:metaAction);
			}
		}

	}

}
