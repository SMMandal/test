package dls.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

























































































































































































@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FAIRRepositoryDescriptor extends FAIRDescriptor{
//    @NotBlank(message = "{is.mandatory}")
    @JsonProperty("dct:title")
    private String title;
//    @NotBlank(message = "{is.mandatory}")
    @JsonProperty("dct:hasVersion")
    private String hasVersion;
    @JsonProperty("dct:description")
    private String description;
//    @NotBlank(message = "{is.mandatory}")
    @JsonProperty("dct:publisher")
    private String publisher;
    @JsonProperty("dct:language")
    private List<String> languages;
    @JsonProperty("dct:license")
    private String license;
    @JsonProperty("dct:subject")
    private String subject;
    @JsonProperty("dct:alternative")
    private String alternative;
    @JsonProperty("dct:rights")
    private String rights;
//    @NotBlank(message = "{is.mandatory}")
    @JsonProperty("r3d:institution")
    private String institution;

}
