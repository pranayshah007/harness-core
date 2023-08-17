/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(CDP)
@Getter
public enum TerragruntCommandFlagType {
  INIT(TerragruntStepWithAllowedCommand.INIT),
  WORKSPACE(TerragruntStepWithAllowedCommand.WORKSPACE),
  PLAN(TerragruntStepWithAllowedCommand.PLAN),
  APPLY(TerragruntStepWithAllowedCommand.APPLY),
  DESTROY(TerragruntStepWithAllowedCommand.DESTROY);

  private final TerragruntStepWithAllowedCommand terragruntCommandAllowedStep;

  TerragruntCommandFlagType(TerragruntStepWithAllowedCommand terragruntCommandAllowedStep) {
    this.terragruntCommandAllowedStep = terragruntCommandAllowedStep;
  }
}
