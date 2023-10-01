/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
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
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SimpleVisitorHelper(helperClass = TasSwapRollbackStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.SWAP_ROLLBACK)
@TypeAlias("TasSwapRollbackStepInfo")
@RecasterAlias("io.harness.cdng.tas.TasSwapRollbackStepInfo")
public class TasSwapRollbackStepInfo extends TasSwapRollbackBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public TasSwapRollbackStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors, String tasSwapRoutesFqn,
      String tasBGSetupFqn, String tasBasicSetupFqn, String tasCanarySetupFqn, String tasResizeFqn,
      ParameterField<Boolean> upsizeInActiveApp) {
    super(delegateSelectors, tasSwapRoutesFqn, tasBGSetupFqn, tasBasicSetupFqn, tasCanarySetupFqn, tasResizeFqn,
        upsizeInActiveApp);
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
    return TasSwapRollbackStepParameters.infoBuilder()
        .upsizeInActiveApp(upsizeInActiveApp)
        .tasCanarySetupFqn(tasCanarySetupFqn)
        .tasResizeFqn(tasResizeFqn)
        .tasSwapRoutesFqn(tasSwapRoutesFqn)
        .tasBGSetupFqn(tasBGSetupFqn)
        .tasBasicSetupFqn(tasBasicSetupFqn)
        .delegateSelectors(this.delegateSelectors)
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
