/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.cdng.execution;

import io.harness.annotation.RecasterAlias;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RecasterAlias("io.harness.delegate.cdng.execution.StepExecutionInstanceInfo")
public class StepExecutionInstanceInfo {
  @NotNull List<StepInstanceInfo> deployedServiceInstances;
  @NotNull List<StepInstanceInfo> serviceInstancesBefore;
  @NotNull List<StepInstanceInfo> serviceInstancesAfter;
  // Add additional info required for CV
}
