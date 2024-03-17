package dls.bean;

import com.fasterxml.jackson.annotation.*;

import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)

public record ExploreCatalog(@JsonProperty("current-page")
                             int page,
                             @JsonProperty("total-page")
                             int totalPage,
                             @JsonProperty("record-count")
                             long count,
                             @JsonInclude(JsonInclude.Include.NON_EMPTY)
                             List<Record> directories,
                             @JsonInclude(JsonInclude.Include.NON_EMPTY)
                             List<Record> files) {

    @JsonInclude(JsonInclude.Include.NON_NULL)

    @JsonPropertyOrder({"type", "name", "path", "created-on", "created-by", "parent", "metadata"})
    public record Record(
            @JsonIgnore
            String type,
            String name,
            String path,
            String parent,
            @JsonProperty("size-bytes")
            Long size,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonProperty("file-count")
            Integer fileCount,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonProperty("subdir-count")
            Integer directoryCount,
            @JsonProperty("created-on")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss z")
            Date createdOn,
            @JsonProperty("created-by")
            String createdBy,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            List<Metadata> metadata
    ) {

    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "value", "updated-by", "privacy"})
    public record Metadata(String name, String value, String privacy,
                           @JsonProperty("updated-by")
                           String modifiedBy) {

    }
}
