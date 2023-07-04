/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
@RecasterAlias("io.harness.cdng.execution.ExecutionInfoKey")
public class ExecutionInfoKey {
  @NotNull Scope scope;
  @NotNull String envIdentifier;
  @NotNull String infraIdentifier;
  @NotNull String serviceIdentifier;
  @Nullable String deploymentIdentifier;
}
