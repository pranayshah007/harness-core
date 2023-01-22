package io.harness.cdng.googlefunctions.trafficShift;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficBaseStepInfo;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStep;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStepParameters;
import io.harness.cdng.pipeline.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.googlefunctions.GoogleFunctionsDeployStepInfoVisitorHelper;
import io.harness.cdng.visitor.helpers.cdstepinfo.googlefunctions.GoogleFunctionsTrafficShiftStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = GoogleFunctionsTrafficShiftStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT)
@TypeAlias("googleFunctionsTrafficShiftStepInfo")
@RecasterAlias("io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsTrafficShiftStepInfo")
public class GoogleFunctionsTrafficShiftStepInfo extends GoogleFunctionsTrafficShiftBaseStepInfo
        implements CDAbstractStepInfo, Visitable {
    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    private String uuid;
    // For Visitor Framework Impl
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

    @Builder(builderMethodName = "infoBuilder")
    public GoogleFunctionsTrafficShiftStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
                                                       ParameterField<Integer> trafficPercent,
                                               String googleFunctionDeployWithoutTrafficStepFnq) {
        super(delegateSelectors, trafficPercent, googleFunctionDeployWithoutTrafficStepFnq);
    }
    @Override
    public StepType getStepType() {
        return GoogleFunctionsTrafficShiftStep.STEP_TYPE;
    }

    @Override
    public String getFacilitatorType() {
        return OrchestrationFacilitatorType.TASK;
    }

    @Override
    public SpecParameters getSpecParameters() {
        return GoogleFunctionsTrafficShiftStepParameters.infoBuilder()
                .delegateSelectors(this.getDelegateSelectors())
                .trafficPercent(this.getTrafficPercent())
                .build();
    }

    @Override
    public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
        return getDelegateSelectors();
    }
}
