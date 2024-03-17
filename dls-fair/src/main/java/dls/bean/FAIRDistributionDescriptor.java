package dls.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class FAIRDistributionDescriptor extends FAIRDescriptor {
    @JsonProperty("dct:title")
    private String title;
    @JsonProperty("dct:license")
    private String license;
    @JsonProperty("dct:hasVersion")
    private String hasVersion;
    @JsonProperty("dct:rights")
    private String rights;
    @JsonProperty("dct:description")
    private String description;
    @JsonProperty("dcat:accessURL")
    @NotBlank(message = "{is.mandatory}")
    private String accessURL;
    @JsonProperty("dcat:mediaType")
    private String mediaType;
    @JsonProperty("dcat:format")
    private String format;
    @JsonProperty("dcat:byteSize")
    private Long byteSize;
}
