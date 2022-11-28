package io.harness.cdng.temp;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.TasAppResizeStepInfoVisitorHelper;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SimpleVisitorHelper(helperClass = TasAppResizeStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.TAS_SWAP_ROUTES)
@TypeAlias("TasSwapRoutesStepInfo")
@RecasterAlias("io.harness.cdng.pcf.TasSwapRoutesStepInfo")
public class TasSwapRoutesStepInfo extends TasSwapRoutesBaseStepInfo implements CDStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public TasSwapRoutesStepInfo(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, boolean downSizeOldApplication, String tasSetupFqn) {
    super(delegateSelectors, downSizeOldApplication, tasSetupFqn);
  }

  @Override
  public StepType getStepType() {
    return null;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return TasSwapRoutesStepParameters.infoBuilder()
        .downSizeOldApplication(this.downSizeOldApplication)
        .delegateSelectors(this.delegateSelectors)
        .build()
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
