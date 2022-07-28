/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.algorithm.HashGenerator;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import com.google.inject.Inject;
import java.util.Map;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OrchestrationServiceImpl implements OrchestrationService {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private InterruptManager interruptManager;
  @Inject private PlanService planService;

  @Override
  public PlanExecution startExecution(@Valid Plan plan, Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) {
    long start = System.currentTimeMillis();
    Plan savedPlan = planService.save(plan);
    log.info("[PMS_EXECUTE] PlanService plan save time {}ms", System.currentTimeMillis() - start);
    return executePlan(savedPlan, setupAbstractions, metadata, planExecutionMetadata);
  }

  public PlanExecution startExecutionV2(String planId, Map<String, String> setupAbstractions,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) {
    return executePlan(planService.fetchPlan(planId), setupAbstractions, metadata, planExecutionMetadata);
  }

  @Override
  public PlanExecution executePlan(@Valid Plan plan, Map<String, String> setupAbstractions, ExecutionMetadata metadata,
      PlanExecutionMetadata planExecutionMetadata) {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .setPlanExecutionId(metadata.getExecutionUuid())
                            .setPlanId(plan.getUuid())
                            .setMetadata(metadata)
                            .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                            .setStartTs(System.currentTimeMillis())
                            .build();

    return orchestrationEngine.runNode(ambiance, plan, planExecutionMetadata);
  }

  @Override
  public Interrupt registerInterrupt(InterruptPackage interruptPackage) {
    return interruptManager.register(interruptPackage);
  }
}
