package dls.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode
public class DlsResponse {

	private String key;
	private String value;
	@EqualsAndHashCode.Exclude
	private Set<String> messages;
	@EqualsAndHashCode.Exclude
	private Integer code;

}
