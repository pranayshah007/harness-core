/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionStrategyTest extends OrchestrationTestBase {
  private static final String DUMMY_NODE_1_ID = generateUuid();
  private static final String DUMMY_NODE_2_ID = generateUuid();
  private static final String DUMMY_NODE_3_ID = generateUuid();

  private static final StepType DUMMY_STEP_TYPE = StepType.newBuilder().setType("DUMMY").build();

  private static final TriggeredBy triggeredBy =
      TriggeredBy.newBuilder().putExtraInfo("email", PRASHANT).setIdentifier(PRASHANT).setUuid(generateUuid()).build();

  @Mock @Named("EngineExecutorService") ExecutorService executorService;
  @Mock OrchestrationEngine orchestrationEngine;
  @Mock PlanService planService;
  @Inject @InjectMocks PlanExecutionStrategy executionStrategy;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(prepareInputArgs())
                            .setMetadata(ExecutionMetadata.newBuilder().setExecutionUuid(planExecutionId).build())
                            .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build())
                            .build();
    PlanNode startingNode = PlanNode.builder()
                                .uuid(DUMMY_NODE_1_ID)
                                .name("Dummy Node 1")
                                .stepType(DUMMY_STEP_TYPE)
                                .identifier("dummy1")
                                .build();
    when(planService.fetchNode(any(), eq(DUMMY_NODE_1_ID))).thenReturn(startingNode);
    Plan plan = Plan.builder()
                    .planNode(startingNode)
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_2_ID)
                                  .name("Dummy Node 2")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy2")
                                  .build())
                    .planNode(PlanNode.builder()
                                  .uuid(DUMMY_NODE_3_ID)
                                  .name("Dummy Node 3")
                                  .stepType(DUMMY_STEP_TYPE)
                                  .identifier("dummy3")
                                  .build())
                    .startingNodeId(DUMMY_NODE_1_ID)
                    .build();

    PlanExecution planExecution = executionStrategy.runNode(
        ambiance, plan, PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build());
    // TODO: make this better
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }
}
