/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.yaml.ParameterField;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
public interface TerraformStepConfigurationInterface {
  TerraformStepConfigurationEnumInterface getType();
  void setType(TerraformStepConfigurationEnumInterface terraformStepConfigurationType);
  TerraformExecutionDataParameters getSpec();
  ParameterField<Boolean> getIsSkipTerraformRefresh();
  List<TerraformCliOptionFlag> getCliOptions();
  void setCliOptions(List<TerraformCliOptionFlag> cliOptionFlags);
  void setEncryptOutputSecretManager(TerraformEncryptOutput terraformEncryptOutput);
  TerraformEncryptOutput getEncryptOutputSecretManager();
}
