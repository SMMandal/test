package dls.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class CatalogStatistics {
	@JsonProperty("total-volume")
	private Long totalvolume;
	@JsonProperty("total-count")
	private Integer totalcount;
	@JsonProperty("total-shared")
	private Integer totalshared;
	@JsonProperty("total-failed")
	private Integer totalfailed;
	@JsonProperty("total-external")
	private Integer totalexternal;
	@JsonProperty("total-deleted")
	private Integer totaldeleted;
	@JsonProperty("total-bundled")
	private Integer totalbundled;
	@JsonProperty("last-uploaded")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
	private Date lastuploaded; 
	@JsonProperty("first-uploaded")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
	private Date firstuploaded;
	
	
}
