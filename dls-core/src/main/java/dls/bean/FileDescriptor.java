package dls.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "File descriptor JSON to upload multiple files")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown=true)

public class FileDescriptor {
	private String filename;
	private String savepoint;
	private String [] metadata;
	private String mode;
	private String directory;
	private String comment;

	public enum UploadMode {
		OVERWRITE,
		APPEND,
		ARCHIVE,
		RESTRICT
	}
}

