package io.harness.cdng.googlefunctions.trafficShift;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("googleFunctionsTrafficShiftBaseStepInfo")
@FieldNameConstants(innerTypeName = "GoogleFunctionsTrafficShiftBaseStepInfoKeys")
public class GoogleFunctionsTrafficShiftBaseStepInfo {
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
    ParameterField<List<TaskSelectorYaml>> delegateSelectors;

    @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
    @JsonProperty("trafficPercent")
    ParameterField<Integer> trafficPercent;

    @JsonIgnore String googleFunctionDeployWithoutTrafficStepFnq;

}
