/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@OwnedBy(CDP)
@Getter
public enum TerraformStepWithAllowedCommand {
  INIT(ImmutableSet.of(TerraformStepsForCliOptions.PLAN, TerraformStepsForCliOptions.APPLY,
      TerraformStepsForCliOptions.DESTROY, TerraformStepsForCliOptions.ROLLBACK)),
  WORKSPACE(ImmutableSet.of(TerraformStepsForCliOptions.PLAN, TerraformStepsForCliOptions.APPLY,
      TerraformStepsForCliOptions.DESTROY, TerraformStepsForCliOptions.ROLLBACK)),
  REFRESH(ImmutableSet.of(TerraformStepsForCliOptions.PLAN, TerraformStepsForCliOptions.APPLY,
      TerraformStepsForCliOptions.DESTROY, TerraformStepsForCliOptions.ROLLBACK)),
  PLAN(ImmutableSet.of(TerraformStepsForCliOptions.PLAN, TerraformStepsForCliOptions.APPLY)),
  APPLY(ImmutableSet.of(
      TerraformStepsForCliOptions.APPLY, TerraformStepsForCliOptions.DESTROY, TerraformStepsForCliOptions.ROLLBACK)),
  DESTROY(ImmutableSet.of(TerraformStepsForCliOptions.DESTROY, TerraformStepsForCliOptions.ROLLBACK));

  private final Set<String> stepsAllowed;

  TerraformStepWithAllowedCommand(Set<String> stepsAllowed) {
    this.stepsAllowed = stepsAllowed;
  }
}
