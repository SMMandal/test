package dls.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FAIRDatasetDescriptor extends FAIRDescriptor {
    @JsonProperty("dct:title")
    private String title;
    @JsonProperty("dct:hasVersion")
    private String hasVersion;
    @JsonProperty("dct:publisher")
    private String publisher;
    @JsonProperty("dct:description")
    private String description;
    @JsonProperty("dct:languages")
    private List <String> languages;
    @JsonProperty("dct:license")
    private String license;
    @JsonProperty("dct:rights")
    private String rights;
    @JsonProperty("dcat:theme")
    private List <String> theme;
    @JsonProperty("dcat:contactPoint")
    private String contactPoint;
    @JsonProperty("dcat:keyword")
    private List<String> keywords;
    @JsonProperty("dcat:landingPage")
    private String landingPage;

}
