/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.StepDetailInstance;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.data.OutcomeInstance;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PmsNodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.plan.Node;
import io.harness.plan.NodeEntity;
import io.harness.plan.Plan;
import io.harness.pms.sdk.PmsSdkInstance;

import java.util.Set;

@OwnedBy(CDC)
public class OrchestrationBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ExecutionInputInstance.class);
    set.add(NodeExecution.class);
    set.add(PlanExecution.class);
    set.add(Interrupt.class);
    set.add(OutcomeInstance.class);
    set.add(ExecutionSweepingOutputInstance.class);
    set.add(PmsSdkInstance.class);
    set.add(Plan.class);
    set.add(PlanExecutionMetadata.class);
    set.add(StepDetailInstance.class);
    set.add(NodeExecutionsInfo.class);
    set.add(Node.class);
    set.add(PmsNodeExecution.class);
    set.add(PmsNodeExecutionMetadata.class);
    set.add(NodeEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}
