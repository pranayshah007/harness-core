package io.harness.cdng.tas;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.ElastigroupSetupStep;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.TasAppResizeStepInfoVisitorHelper;
import io.harness.cdng.visitor.helpers.cdstepinfo.TasSwapRollbackStepInfoVisitorHelper;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SimpleVisitorHelper(helperClass = TasSwapRollbackStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.SWAP_ROLLBACK)
@TypeAlias("TasSwapRollbackStepInfo")
@RecasterAlias("io.harness.cdng.tas.TasSwapRollbackStepInfo")
public class TasSwapRollbackStepInfo extends TasSwapRollbackBaseStepInfo implements CDStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public TasSwapRollbackStepInfo(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String tasRollbackFqn, String tasSwapRoutesFqn, String tasBGSetupFqn, String tasBasicSetupFqn, String tasCanarySetupFqn, ParameterField<Boolean> upsizeInActiveApp) {
    super(delegateSelectors, tasRollbackFqn, tasSwapRoutesFqn, tasBGSetupFqn, tasBasicSetupFqn, tasCanarySetupFqn, upsizeInActiveApp);
  }

  @Override
  public StepType getStepType() {
    return TasSwapRollbackStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return TasSwapRollbackStepParameters.infoBuilder().delegateSelectors(this.delegateSelectors).build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
