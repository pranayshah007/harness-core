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
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.tas.TasSwapRollbackStepParameters")
public class TasSwapRollbackStepParameters extends TasSwapRollbackBaseStepInfo implements SpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public TasSwapRollbackStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      String tasSwapRoutesFqn, String tasBGSetupFqn, String tasBasicSetupFqn, String tasCanarySetupFqn,
      String tasResizeFqn, ParameterField<Boolean> upsizeInActiveApp) {
    super(delegateSelectors, tasSwapRoutesFqn, tasBGSetupFqn, tasBasicSetupFqn, tasCanarySetupFqn, tasResizeFqn,
        upsizeInActiveApp);
  }
}
