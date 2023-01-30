/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.terraformcloud.TerraformCloudRunStepParameters")
public class TerraformCloudRunStepParameters implements SpecParameters {
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  TerraformCloudRunSpecParameters spec;
  ParameterField<String> message;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformCloudRunStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      TerraformCloudRunSpecParameters spec, ParameterField<String> message) {
    this.delegateSelectors = delegateSelectors;
    this.spec = spec;
    this.message = message;
  }
}
