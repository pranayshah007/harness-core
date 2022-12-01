package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.TasAppSetupStepInfoVisitorHelper;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = TasAppSetupStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.TAS_BASIC_APP_SETUP)
@TypeAlias("TasBasicAppSetupStepInfo")
@RecasterAlias("io.harness.cdng.tas.TasBasicAppSetupStepInfo")
public class TasBasicAppSetupStepInfo extends TasAppSetupBaseStepInfo implements CDStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @Builder(builderMethodName = "infoBuilder")
  public TasBasicAppSetupStepInfo(TasInstanceCountType instanceCount, ParameterField<Integer> existingVersionToKeep,
      ParameterField<List<String>> additionalRoutes, ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(instanceCount, existingVersionToKeep, additionalRoutes, delegateSelectors);
  }

  @Override
  public StepType getStepType() {
    return TasBasicAppSetupStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return TasBasicAppSetupStepParameters.infoBuilder().delegateSelectors(this.getDelegateSelectors()).build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
