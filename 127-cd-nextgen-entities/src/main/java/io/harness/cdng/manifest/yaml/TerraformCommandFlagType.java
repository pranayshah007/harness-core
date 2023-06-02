/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import lombok.Getter;

@OwnedBy(CDP)
@Getter
public enum TerraformCommandFlagType {
  INIT(TerraformStepWithAllowedCommand.INIT, TerraformStepsForCliOptions.TerraformYamlConfigAndCloudCli),
  WORKSPACE(TerraformStepWithAllowedCommand.WORKSPACE, TerraformStepsForCliOptions.TerraformYamlConfig),
  REFRESH(TerraformStepWithAllowedCommand.REFRESH, TerraformStepsForCliOptions.TerraformYamlConfig),
  PLAN(TerraformStepWithAllowedCommand.PLAN, TerraformStepsForCliOptions.TerraformYamlConfigAndCloudCli),
  APPLY(TerraformStepWithAllowedCommand.APPLY, TerraformStepsForCliOptions.TerraformYamlConfigAndCloudCli),
  DESTROY(TerraformStepWithAllowedCommand.DESTROY, TerraformStepsForCliOptions.TerraformYamlConfigAndCloudCli);

  private final TerraformStepWithAllowedCommand terraformCommandAllowedStep;
  private final Set<String> terraformYamlConfigType;

  TerraformCommandFlagType(
      TerraformStepWithAllowedCommand terraformCommandAllowedStep, Set<String> terraformYamlConfigType) {
    this.terraformCommandAllowedStep = terraformCommandAllowedStep;
    this.terraformYamlConfigType = terraformYamlConfigType;
  }
}
