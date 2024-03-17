package dls.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class DirectoryCatalog {

    private String path;
    private String name;
    private String type;
    @JsonProperty("created-on")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
    private Date createdOn;

    @JsonProperty("created-by")
    private String createdBy;

    @JsonProperty("permitted-action")
    private String permittedAction;

    @JsonProperty("files")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List <FileDetail> files;
    @JsonProperty("directories")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List <DirectoryCatalog> subDirectories;
    @JsonProperty("file-count")
    private Integer fileCount;
}
