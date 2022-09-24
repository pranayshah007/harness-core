package io.harness.cdng.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.EcsBlueGreenSwapTargetGroupsStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = EcsBlueGreenSwapTargetGroupsStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
@TypeAlias("ecsBlueGreenSwapTargetGroupsStepInfo")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepInfo")
public class EcsBlueGreenSwapTargetGroupsStepInfo
    extends EcsBlueGreenSwapTargetGroupsBaseStepInfo implements CDStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public EcsBlueGreenSwapTargetGroupsStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      String ecsBlueGreenCreateServiceFnq, String ecsBlueGreenSwapTargetGroupsFnq) {
    super(delegateSelectors, ecsBlueGreenCreateServiceFnq, ecsBlueGreenSwapTargetGroupsFnq);
  }
  @Override
  public StepType getStepType() {
    return EcsBlueGreenSwapTargetGroupsStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return EcsBlueGreenSwapTargetGroupsStepParameters.infoBuilder()
        .delegateSelectors(this.getDelegateSelectors())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
