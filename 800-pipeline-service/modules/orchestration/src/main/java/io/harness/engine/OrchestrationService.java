/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import java.util.Map;
import javax.validation.Valid;
import lombok.NonNull;

@OwnedBy(CDC)
public interface OrchestrationService {
  PlanExecution startExecution(@Valid Plan plan, @NonNull Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata);

  Interrupt registerInterrupt(@Valid InterruptPackage interruptPackage);

  PlanExecution startExecutionV2(String planId, Map<String, String> setupAbstractions, ExecutionMetadata metadata,
      PlanExecutionMetadata planExecutionMetadata);

  PlanExecution executePlan(@Valid Plan plan, @NonNull Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata);
}
