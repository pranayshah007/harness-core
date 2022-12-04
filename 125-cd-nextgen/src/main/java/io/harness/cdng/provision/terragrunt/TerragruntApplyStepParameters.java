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

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
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
import lombok.NonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntApplyStepParameters")
public class TerragruntApplyStepParameters extends TerragruntApplyBaseStepInfo implements SpecParameters {
  @NonNull TerragruntStepConfigurationParameters configuration;

  @Builder(builderMethodName = "infoBuilder")
  public TerragruntApplyStepParameters(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      @NonNull TerragruntStepConfigurationParameters configuration) {
    super(provisionerIdentifier, delegateSelectors);
    this.configuration = configuration;
  }
}
