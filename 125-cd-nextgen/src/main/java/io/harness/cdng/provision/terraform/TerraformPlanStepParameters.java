/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformPlanStepParameters")
public class TerraformPlanStepParameters extends TerraformPlanBaseStepInfo implements SpecParameters {
  // Not needed. We are calculating the stepFQN during runtime. Remove this stepFqn field in after few releases. Keeping
  // for backward compatibility.
  @Deprecated String stepFqn;
  TerraformPlanExecutionDataParameters configuration;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformPlanStepParameters(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String stepFqn,
      TerraformPlanExecutionDataParameters configuration) {
    super(provisionerIdentifier, delegateSelectors);
    this.stepFqn = stepFqn;
    this.configuration = configuration;
  }
}
