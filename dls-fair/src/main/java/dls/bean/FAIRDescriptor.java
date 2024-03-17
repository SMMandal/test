package dls.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.hibernate.validator.constraints.UniqueElements;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({ "dct:identifier", "permissions", "provenance", "custom" })

public abstract class FAIRDescriptor {

    @NotBlank(message = "{is.mandatory}")
    @JsonProperty("dct:identifier")
    private String identifier;
    @UniqueElements(message = "{duplicate.records}")
    private List<@Valid FAIRPermission> permissions;
    private Map<String,String> custom;
//    private FAIRProvenanceDescriptor provenance;
}


