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
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.TANZU_COMMAND)
@TypeAlias("tasCommandStepParameters")
@RecasterAlias("io.harness.cdng.tas.TasCommandStepParameters")
public class TasCommandStepParameters extends TasCommandBaseStepInfo implements SpecParameters {
  Map<String, Object> inputVariables;
  Map<String, Object> outputVariables;
  @Builder(builderMethodName = "infoBuilder")
  public TasCommandStepParameters(TasCommandScript script, ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      Map<String, Object> inputVariables, Map<String, Object> outputVariables) {
    super(script, delegateSelectors);
    this.inputVariables = inputVariables;
    this.outputVariables = outputVariables;
  }
}
