package io.harness.ng.core.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("CopyTemplateVariableRequest")
public class CopyTemplateVariableRequestDTO {
    @NotNull String templateYaml;
    Map<String, String> variableValues;
}
