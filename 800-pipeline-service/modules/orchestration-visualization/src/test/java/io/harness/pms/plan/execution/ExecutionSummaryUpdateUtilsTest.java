/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotation.RecasterAlias;
import io.harness.category.element.UnitTests;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Update;

public class ExecutionSummaryUpdateUtilsTest extends CategoryTest {
  PlanNode pipelinePlanNode;
  PlanNode stagePlanNode;

  @Before
  public void setUp() {
    pipelinePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("pipeline")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .identifier("pipeline")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("pipelineValue").build()))))
            .group("PIPELINE")
            .build();
    stagePlanNode = PlanNode.builder()
                        .uuid(generateUuid())
                        .name("stage1")
                        .stepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
                        .identifier("stage1")
                        .skipExpressionChain(false)
                        .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                            RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("stageValue").build()))))
                        .group("STAGE")
                        .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testPipelineUpdateCriteria() {
    Update update = new Update();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.FAILED)
            .endTs(System.currentTimeMillis())
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("testing")
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage("testing")
                                                 .build())
                             .build())

            .build();
    ExecutionSummaryUpdateUtils.addPipelineUpdateCriteria(update, nodeExecution);
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet).hasSize(5);
    assertThat(stringSet).containsOnly(PlanExecutionSummaryKeys.internalStatus, PlanExecutionSummaryKeys.status,
        PlanExecutionSummaryKeys.endTs, PlanExecutionSummaryKeys.executionErrorInfo,
        PlanExecutionSummaryKeys.failureInfo);

    // nodeExecution where status is not failed
    NodeExecution expiredNodeExecution =
        NodeExecution.builder()
            .status(Status.EXPIRED)
            .endTs(System.currentTimeMillis())
            .ambiance(ambiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("testing")
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage("testing")
                                                 .build())
                             .build())
            .build();
    update = new Update();
    ExecutionSummaryUpdateUtils.addPipelineUpdateCriteria(update, expiredNodeExecution);
    stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet).hasSize(3);
    assertThat(stringSet).containsOnly(
        PlanExecutionSummaryKeys.internalStatus, PlanExecutionSummaryKeys.status, PlanExecutionSummaryKeys.endTs);

    // if its not a pipeline node
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("step")
            .stepType(StepType.newBuilder().setType("STEP").setStepCategory(StepCategory.STEP).build())
            .identifier("step")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("stepValue").build()))))
            .group("STEP")
            .build();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .setPlanExecutionId(generateUuid())
                                .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                                .build();
    NodeExecution stepNodeExecution = NodeExecution.builder().status(Status.EXPIRED).ambiance(stepAmbiance).build();
    update = new Update();
    ExecutionSummaryUpdateUtils.addPipelineUpdateCriteria(update, stepNodeExecution);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testStageUpdateCriteriaForBarrierStep() {
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("step")
            .stepType(
                StepType.newBuilder().setType(StepSpecTypeConstants.BARRIER).setStepCategory(StepCategory.STEP).build())
            .identifier("step")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("stepValue").build()))))
            .group("STEP")
            .build();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .setPlanExecutionId(generateUuid())
                                .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                                .build();
    NodeExecution stepNodeExecution = NodeExecution.builder()
                                          .planNode(stepPlanNode)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .status(Status.EXPIRED)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .ambiance(stepAmbiance)
                                          .build();
    Update update = new Update();
    ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, stepNodeExecution);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(0);

    // step having stage and pipeline in the ambiance levels
    stepAmbiance = Ambiance.newBuilder()
                       .setPlanExecutionId(generateUuid())
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                       .build();
    stepNodeExecution = NodeExecution.builder()
                            .status(Status.EXPIRED)
                            .ambiance(stepAmbiance)
                            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                            .build();
    update = new Update();
    ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, stepNodeExecution);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(1);
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet.size()).isEqualTo(1);
    assertThat(stringSet).containsOnly(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + stagePlanNode.getUuid() + ".barrierFound");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testStageUpdateCriteria() {
    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                                 .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.FAILED)
            .planNode(stagePlanNode)
            .endTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType("test").setStepCategory(StepCategory.STEP).build())
            .ambiance(stageAmbiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("testing")
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage("testing")
                                                 .build())
                             .build())
            .build();
    Update update = new Update();
    ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution);
    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + stagePlanNode.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet).containsOnly(prefixLayoutNodeMap + ".status", prefixLayoutNodeMap + ".startTs",
        prefixLayoutNodeMap + ".nodeRunInfo", prefixLayoutNodeMap + ".endTs", prefixLayoutNodeMap + ".failureInfo",
        prefixLayoutNodeMap + ".failureInfoDTO", prefixLayoutNodeMap + ".nodeExecutionId");
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.pms.expressions.ExecutionSummaryUpdateUtilsTest$TestStepParameters")
  public static class TestStepParameters implements StepParameters {
    String param;
  }
}
