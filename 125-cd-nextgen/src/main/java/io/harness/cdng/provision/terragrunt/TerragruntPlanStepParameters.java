/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntPlanStepParameters")
public class TerragruntPlanStepParameters extends TerragruntPlanBaseStepInfo implements SpecParameters {
  String stepFqn;
  TerragruntPlanExecutionDataParameters configuration;

  @Override
  public SpecParameters getViewJsonObject() {
    TerragruntPlanStepParameters terragruntPlanStepParameters = this;
    // this TerragruntModuleConfig we are settle to null so that it will not show in the input of plan step execution
    terragruntPlanStepParameters.getConfiguration().setTerragruntModuleConfig(null);
    return terragruntPlanStepParameters;
  }

  @Builder(builderMethodName = "infoBuilder")
  public TerragruntPlanStepParameters(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String stepFqn,
      TerragruntPlanExecutionDataParameters configuration) {
    super(provisionerIdentifier, delegateSelectors);
    this.stepFqn = stepFqn;
    this.configuration = configuration;
  }
}
