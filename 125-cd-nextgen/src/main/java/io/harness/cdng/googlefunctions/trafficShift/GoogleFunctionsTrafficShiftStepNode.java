package io.harness.cdng.googlefunctions.trafficShift;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStepInfo;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStepNode;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT)
@TypeAlias("googleFunctionsDeployWithoutTrafficStepNode")
@RecasterAlias("io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsDeployWithoutTrafficStepNode")
public class GoogleFunctionsTrafficShiftStepNode extends CdAbstractStepNode {
    @JsonProperty("type") @NotNull GoogleFunctionsTrafficShiftStepNode.StepType type =
            GoogleFunctionsTrafficShiftStepNode.StepType.CloudFunctionTrafficShift;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    GoogleFunctionsTrafficShiftStepInfo googleFunctionsTrafficShiftStepInfo;

    @Override
    public String getType() {
        return StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT;
    }

    @Override
    public StepSpecType getStepSpecType() {
        return googleFunctionsTrafficShiftStepInfo;
    }

    enum StepType {
        CloudFunctionTrafficShift(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT);
        @Getter
        String name;
        StepType(String name) {
            this.name = name;
        }
    }
}