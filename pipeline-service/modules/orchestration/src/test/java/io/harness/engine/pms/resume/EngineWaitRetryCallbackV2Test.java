/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class EngineWaitRetryCallbackV2Test extends OrchestrationTestBase {
  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock OrchestrationEngine engine;

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testNotify() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeExecutionId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder()
                                            .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                            .setCurrentIteration(1)
                                            .setTotalIterations(1)
                                            .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .setPlanId(planExecutionId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode, strategyMetadata, false))
            .build();
    EngineWaitRetryCallbackV2 callback = EngineWaitRetryCallbackV2.builder().ambiance(ambiance).build();
    Reflect.on(callback).set("executorService", executorService);
    callback.notify(new HashMap<>());
    verify(executorService).submit(any(Runnable.class));
  }
}
