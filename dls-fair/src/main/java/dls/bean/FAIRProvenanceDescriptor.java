package dls.bean;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.Map;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class FAIRProvenanceDescriptor {

    @JsonProperty("prov:wasGeneratedBy")
    private String wasGeneratedBy;
    @JsonProperty("prov:atTime")
    @Schema(format = "dd-MMM-yyyy HH:mm:ss Z", implementation = String.class, example = "02-FEB-2020 20:02:20 IST")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy HH:mm:ss Z")
    private Timestamp atTime;
    @JsonProperty("prov:event")
    private String event;
//    @JsonProperty("prov:entity")
//    private String entity;
//    @JsonProperty("prov:value")
//    private String value;
//    @JsonProperty("custom")
    private Map<String,String> custom;
    @JsonAnySetter
    public void add(String key, String value) {
        custom.put(key, value);
    }
}
