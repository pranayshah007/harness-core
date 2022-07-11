/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.observers.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class OrchestrationStartInfo {
  @NotNull Ambiance ambiance;
  @NotNull PlanExecutionMetadata planExecutionMetadata;

  public String getPlanExecutionId() {
    return ambiance.getPlanExecutionId();
  }
}
