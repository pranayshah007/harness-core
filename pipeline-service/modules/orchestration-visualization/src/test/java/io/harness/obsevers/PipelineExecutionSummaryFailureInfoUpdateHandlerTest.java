/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.obsevers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.category.element.UnitTests;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.observers.PipelineExecutionSummaryFailureInfoUpdateHandler;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.plan.execution.ExecutionSummaryUpdateUtilsTest;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Update;

@RunWith(MockitoJUnitRunner.class)
public class PipelineExecutionSummaryFailureInfoUpdateHandlerTest extends CategoryTest {
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  PlanNode pipelinePlanNode;
  Ambiance ambiance;
  @InjectMocks PipelineExecutionSummaryFailureInfoUpdateHandler pipelineExecutionSummaryFailureInfoUpdateHandler;
  private static final String TESTING = "testing";
  private static final String STEP_VALUE = "step";
  String nodeExecutionId = generateUuid();
  NodeExecution nodeExecution;
  String planExecutionId = generateUuid();

  @Before
  public void setup() {
    pipelinePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("pipeline")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .identifier("pipeline")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(RecastOrchestrationUtils.toMap(
                ExecutionSummaryUpdateUtilsTest.TestStepParameters.builder().param("pipelineValue").build()))))
            .group("PIPELINE")
            .build();
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(planExecutionId)
                   .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                   .build();
    nodeExecution = NodeExecution.builder()
                        .status(Status.FAILED)
                        .uuid(nodeExecutionId)
                        .endTs(System.currentTimeMillis())
                        .ambiance(ambiance)
                        .failureInfo(FailureInfo.newBuilder()
                                         .setErrorMessage(TESTING)
                                         .addFailureData(FailureData.newBuilder()
                                                             .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                             .setLevel(Level.ERROR.name())
                                                             .setCode(GENERAL_ERROR.name())
                                                             .setMessage(TESTING)
                                                             .build())
                                         .build())
                        .module("CD")
                        .build();

    doNothing().when(pmsExecutionSummaryService).update(anyString(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleNodeStatusUpdate() {
    // nodeExecution where status is not failed
    String nodeExecutionId1 = generateUuid();
    NodeExecution expiredNodeExecution =
        NodeExecution.builder()
            .status(Status.EXPIRED)
            .endTs(System.currentTimeMillis())
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage(TESTING)
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage(TESTING)
                                                 .build())
                             .build())
            .uuid(nodeExecutionId1)
            .build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(expiredNodeExecution).build();
    pipelineExecutionSummaryFailureInfoUpdateHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(pmsExecutionSummaryService, times(0)).update(anyString(), any());

    // if its not a pipeline node
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("step")
            .stepType(StepType.newBuilder().setType("STEP").setStepCategory(StepCategory.STEP).build())
            .identifier("step")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(RecastOrchestrationUtils.toMap(
                ExecutionSummaryUpdateUtilsTest.TestStepParameters.builder().param(STEP_VALUE).build()))))
            .group("STEP")
            .build();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .setPlanExecutionId(generateUuid())
                                .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                                .build();
    NodeExecution stepNodeExecution =
        NodeExecution.builder().status(Status.EXPIRED).uuid(nodeExecutionId).ambiance(stepAmbiance).build();
    nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(stepNodeExecution).build();
    pipelineExecutionSummaryFailureInfoUpdateHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(pmsExecutionSummaryService, times(0)).update(anyString(), any());

    // if it's a pipeline node and status is failed
    nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();
    pipelineExecutionSummaryFailureInfoUpdateHandler.onNodeStatusUpdate(nodeUpdateInfo);
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.executionErrorInfo,
        ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
    update.set(
        PlanExecutionSummaryKeys.failureInfo, FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
    verify(pmsExecutionSummaryService, times(1)).update(planExecutionId, update);
  }
}
