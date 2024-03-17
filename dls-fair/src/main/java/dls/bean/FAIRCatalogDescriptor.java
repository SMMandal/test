package dls.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FAIRCatalogDescriptor extends FAIRDescriptor {
    @JsonProperty("dct:title")
    private String title;
    @JsonProperty("dct:hasVersion")
    private String hasVersion;
    @JsonProperty("dct:publisher")
    private String publisher;
    @JsonProperty("dct:description")
    private String description;
    @JsonProperty("dct:languages")
    private List<String> languages;
    @JsonProperty("dct:license")
    private String license;
    @JsonProperty("dct:rights")
    private String rights;
    @JsonProperty("foaf:homepage")
    private String homepage;
    @JsonProperty("dct:themeTaxonomy")
    private String themeTaxonomy;

}
