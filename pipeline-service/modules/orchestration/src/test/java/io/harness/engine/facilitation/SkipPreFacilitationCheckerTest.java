/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class SkipPreFacilitationCheckerTest extends OrchestrationTestBase {
  @Mock EngineExpressionService engineExpressionService;
  @Mock OrchestrationEngine engine;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Inject @InjectMocks NodeExecutionService nodeExecutionService;
  @Inject @InjectMocks SkipPreFacilitationChecker checker;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionFalse() {
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    String skipCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .skipCondition(skipCondition)
                            .serviceName("CD")
                            .identifier("DUMMY")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .nodeId(planNode.getUuid())
                                      .name(planNode.getName())
                                      .stepType(planNode.getStepType())
                                      .identifier(planNode.getIdentifier())
                                      .module(planNode.getServiceName())
                                      .skipGraphType(planNode.getSkipGraphType())
                                      .build();
    nodeExecutionService.save(nodeExecution);

    when(engineExpressionService.evaluateExpression(nodeExecution.getAmbiance(), skipCondition)).thenReturn(false);
    ExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isTrue();
    verify(engine, times(0)).processStepResponse(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionTrue() {
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    String skipCondition = "<+pipeline.name>==\"name\"";
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .name(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .skipCondition(skipCondition)
                            .serviceName("CD")
                            .identifier("DUMMY")
                            .build();
    String nodeExecutionId = generateUuid();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .nodeId(planNode.getUuid())
                                      .name(planNode.getName())
                                      .stepType(planNode.getStepType())
                                      .identifier(planNode.getIdentifier())
                                      .module(planNode.getServiceName())
                                      .skipGraphType(planNode.getSkipGraphType())
                                      .build();
    nodeExecutionService.save(nodeExecution);

    when(engineExpressionService.evaluateExpression(nodeExecution.getAmbiance(), skipCondition)).thenReturn(true);
    ExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isFalse();
    verify(engine, times(1))
        .processStepResponse(ambiance,
            StepResponseProto.newBuilder()
                .setStatus(Status.SKIPPED)
                .setSkipInfo(SkipInfo.newBuilder().setSkipCondition(skipCondition).setEvaluatedCondition(true).build())
                .build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionException() {
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    String skipCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .skipCondition(skipCondition)
                            .serviceName("CD")
                            .identifier("DUMMY")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)

                                      .startTs(System.currentTimeMillis())
                                      .nodeId(planNode.getUuid())
                                      .name(planNode.getName())
                                      .stepType(planNode.getStepType())
                                      .identifier(planNode.getIdentifier())
                                      .module(planNode.getServiceName())
                                      .skipGraphType(planNode.getSkipGraphType())
                                      .build();
    nodeExecutionService.save(nodeExecution);

    InvalidRequestException testException = new InvalidRequestException("TestException");
    when(engineExpressionService.evaluateExpression(nodeExecution.getAmbiance(), skipCondition))
        .thenThrow(testException);
    ExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isFalse();
    verify(engine, times(1)).handleError(nodeExecution.getAmbiance(), testException);
  }
}
