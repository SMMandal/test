package dls.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Schema(description="Create Comment while uploading file or later")
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public @Data @Builder class Comment {
	
	@JsonProperty("comment")
	private String comment;
	
	@JsonProperty("commented-on")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
	private Timestamp createdOn;
	@JsonProperty("file-uri")
	private String fileUri;
	private String user;
//	@JsonProperty("organization-position")
//	private String orgPosition;

//	private Boolean admin;
	
	
}
