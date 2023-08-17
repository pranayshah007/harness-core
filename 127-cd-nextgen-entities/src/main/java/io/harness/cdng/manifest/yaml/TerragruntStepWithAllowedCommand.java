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
public enum TerragruntStepWithAllowedCommand {
  INIT(ImmutableSet.of(TerragruntStepsForCliOptions.PLAN, TerragruntStepsForCliOptions.APPLY,
      TerragruntStepsForCliOptions.DESTROY, TerragruntStepsForCliOptions.ROLLBACK)),
  WORKSPACE(ImmutableSet.of(TerragruntStepsForCliOptions.PLAN, TerragruntStepsForCliOptions.APPLY,
      TerragruntStepsForCliOptions.DESTROY, TerragruntStepsForCliOptions.ROLLBACK)),
  PLAN(ImmutableSet.of(TerragruntStepsForCliOptions.PLAN, TerragruntStepsForCliOptions.APPLY)),
  APPLY(ImmutableSet.of(
      TerragruntStepsForCliOptions.APPLY, TerragruntStepsForCliOptions.DESTROY, TerragruntStepsForCliOptions.ROLLBACK)),
  DESTROY(ImmutableSet.of(TerragruntStepsForCliOptions.DESTROY, TerragruntStepsForCliOptions.ROLLBACK));

  private final Set<String> stepsAllowed;

  TerragruntStepWithAllowedCommand(Set<String> stepsAllowed) {
    this.stepsAllowed = stepsAllowed;
  }
}
