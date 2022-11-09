/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryServiceImplTest extends OrchestrationVisualizationTestBase {
  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Spy @Inject PmsExecutionSummaryServiceImpl pmsExecutionSummaryService;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId("planExecution")
                                                                        .accountId("acc")
                                                                        .orgIdentifier("org")
                                                                        .projectIdentifier("project")
                                                                        .build();
    pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    assertEquals(pmsExecutionSummaryService.getPipelineExecutionSummary("acc", "org", "project", "planExecution"),
        Optional.of(pipelineExecutionSummaryEntity));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateStrategyNode() {
    Update update = new Update();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder().concurrentChildInstance(ConcurrentChildInstance.builder().build()).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .executableResponse(ExecutableResponse.newBuilder().build())
                                      .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().build()).build())
                                      .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                      .build();
    pmsExecutionSummaryService.updateStrategyNode("planExecution", nodeExecution, update);
    verify(pmsExecutionSummaryService, times(0)).updateStrategyNodeFields(nodeExecution, update, false);
    nodeExecution = NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder()
                                      .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                      .addLevels(Level.newBuilder().build())
                                      .build())
                        .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                        .planNode(PlanNode.builder().build())
                        .build();
    pmsExecutionSummaryService.updateStrategyNode("planExecution", nodeExecution, update);
    verify(pmsExecutionSummaryService, times(1)).updateStrategyNodeFields(nodeExecution, update, false);
  }
}
