package io.harness.cdng.ecs;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.EcsBlueGreenRollbackStepInfoVisitorHelper;
import io.harness.cdng.visitor.helpers.cdstepinfo.EcsRollingRollbackStepInfoVisitorHelper;
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
@SimpleVisitorHelper(helperClass = EcsBlueGreenRollbackStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK)
@TypeAlias("ecsBlueGreenRollbackStepInfo")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenRollbackStepInfo")
public class EcsBlueGreenRollbackStepInfo extends EcsBlueGreenRollbackBaseStepInfo implements CDStepInfo, Visitable {
    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    private String uuid;
    // For Visitor Framework Impl
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

    @Builder(builderMethodName = "infoBuilder")
    public EcsBlueGreenRollbackStepInfo(
            ParameterField<List<TaskSelectorYaml>> delegateSelectors, String ecsBlueGreenCreateServiceFnq) {
        super(delegateSelectors, ecsBlueGreenCreateServiceFnq);
    }
    @Override
    public StepType getStepType() {
        return EcsBlueGreenRollbackStep.STEP_TYPE;
    }

    @Override
    public String getFacilitatorType() {
        return OrchestrationFacilitatorType.TASK;
    }

    @Override
    public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
        return getDelegateSelectors();
    }

    @Override
    public SpecParameters getSpecParameters() {
        return EcsBlueGreenRollbackStepParameters.infoBuilder().delegateSelectors(this.getDelegateSelectors()).build();
    }
}
