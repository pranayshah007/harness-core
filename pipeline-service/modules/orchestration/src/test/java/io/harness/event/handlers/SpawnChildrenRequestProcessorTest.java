/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.rule.Owner;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SpawnChildrenRequestProcessorTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock InitiateNodeHelper initiateNodeHelper;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock OrchestrationEngine engine;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;

  @Inject @InjectMocks SpawnChildrenRequestProcessor processor;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEvent() throws Exception {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();
    String child2Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.FORK).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder().setChildNodeId(child1Id).build())
                                     .addChildren(Child.newBuilder().setChildNodeId(child2Id).build())
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<String> nodeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> runtimeIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(initiateNodeHelper, times(2))
        .publishEvent(eq(ambiance), nodeIdCaptor.capture(), runtimeIdCaptor.capture(), eq(null),
            eq(InitiateMode.CREATE_AND_START));

    List<String> nodeIds = nodeIdCaptor.getAllValues();
    assertThat(nodeIds).hasSize(2);
    assertThat(nodeIds).containsExactly(child1Id, child2Id);

    List<String> runtimeIds = runtimeIdCaptor.getAllValues();
    assertThat(runtimeIds).hasSize(2);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(0)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(0);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);
    assertThat(exIdCaptor.getAllValues().stream().flatMap(Arrays::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(runtimeIds.get(0), runtimeIds.get(1));

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEventWithMaxConcurrency() throws Exception {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();
    String child2Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.FORK).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder().setChildNodeId(child1Id).build())
                                     .addChildren(Child.newBuilder().setChildNodeId(child2Id).build())
                                     .setMaxConcurrency(1)
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    when(engine.initiateNode(any(), anyString(), anyString(), any(), any(), any())).thenReturn(null);
    processor.handleEvent(event);

    ArgumentCaptor<String> nodeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> runtimeIdCaptor = ArgumentCaptor.forClass(String.class);

    ArgumentCaptor<String> notRunningNodeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> notRunningRuntimeIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(initiateNodeHelper, times(1))
        .publishEvent(eq(ambiance), nodeIdCaptor.capture(), runtimeIdCaptor.capture(), eq(null),
            eq(InitiateMode.CREATE_AND_START));

    verify(engine).initiateNode(
        eq(ambiance), notRunningNodeIdCaptor.capture(), notRunningRuntimeIdCaptor.capture(), any(), any(), any());

    List<String> nodeIds = nodeIdCaptor.getAllValues();
    assertThat(nodeIds).hasSize(1);
    assertThat(nodeIds).containsExactly(child1Id);

    nodeIds = notRunningNodeIdCaptor.getAllValues();
    assertThat(nodeIds).hasSize(1);
    assertThat(nodeIds).containsExactly(child2Id);

    List<String> runtimeIds = runtimeIdCaptor.getAllValues();
    runtimeIds.addAll(notRunningRuntimeIdCaptor.getAllValues());
    assertThat(runtimeIds).hasSize(2);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(3)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(2)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(2);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);
    assertThat(exIdCaptor.getAllValues().stream().flatMap(Arrays::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(runtimeIds.get(0), runtimeIds.get(1), runtimeIds.get(0), runtimeIds.get(1));

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEventForRollbackMode() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

    StrategyMetadata rollbackSTrategyMetadata =
        StrategyMetadata.newBuilder()
            .setMatrixMetadata(MatrixMetadata.newBuilder().putMatrixValues("serviceRef", "svc1").build())
            .build();
    StrategyMetadata nonRollbackSTrategyMetadata =
        StrategyMetadata.newBuilder()
            .setMatrixMetadata(MatrixMetadata.newBuilder().putMatrixValues("serviceRef", "svc2").build())
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .setGroup("STAGES")
                    .build())
            .addLevels(
                Level.newBuilder()
                    .setIdentifier("IDENTIFIER")
                    .setStepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.FORK).build())
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(planNodeId)
                    .build())
            .build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILDREN)
            .setSpawnChildrenRequest(
                SpawnChildrenRequest.newBuilder()
                    .setChildren(ChildrenExecutableResponse.newBuilder()
                                     .addChildren(Child.newBuilder()
                                                      .setChildNodeId(child1Id)
                                                      .setStrategyMetadata(rollbackSTrategyMetadata)
                                                      .build())
                                     .addChildren(Child.newBuilder()
                                                      .setChildNodeId(child1Id)
                                                      .setStrategyMetadata(nonRollbackSTrategyMetadata)
                                                      .build())
                                     .build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    doReturn(PlanExecutionMetadata.builder()
                 .postExecutionRollbackInfo(PostExecutionRollbackInfo.newBuilder()
                                                .setPostExecutionRollbackStageId(planNodeId)
                                                .setRollbackStageStrategyMetadata(rollbackSTrategyMetadata)
                                                .build())
                 .build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            planExecutionId, PlanExecutionProjectionConstants.fieldsForPostProdRollback);

    processor.handleEvent(event);

    ArgumentCaptor<String> nodeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> runtimeIdCaptor = ArgumentCaptor.forClass(String.class);

    verify(initiateNodeHelper, times(1))
        .publishEvent(eq(ambiance), nodeIdCaptor.capture(), runtimeIdCaptor.capture(), eq(rollbackSTrategyMetadata),
            eq(InitiateMode.CREATE_AND_START));

    List<String> nodeIds = nodeIdCaptor.getAllValues();
    assertThat(nodeIds).hasSize(1);
    assertThat(nodeIds).contains(child1Id);

    List<String> runtimeIds = runtimeIdCaptor.getAllValues();
    assertThat(runtimeIds).hasSize(1);

    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<String[]> exIdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(0)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(0);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);
    assertThat(exIdCaptor.getAllValues().stream().flatMap(Arrays::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(runtimeIds.get(0));

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetFilteredChildren() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setSetupId("parallelId").build())
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();
    doReturn(PlanExecutionMetadata.builder()
                 .postExecutionRollbackInfo(
                     PostExecutionRollbackInfo.newBuilder().setPostExecutionRollbackStageId("stageId").build())
                 .build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    List<Child> children = Collections.singletonList(Child.newBuilder().build());
    List<Child> filteredChildren = processor.getFilteredChildren(ambiance, children);
    assertThat(filteredChildren.size()).isEqualTo(1);
    assertThat(filteredChildren.get(0)).isEqualTo(children.get(0));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetFilteredChildrenMatrix() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .addLevels(Level.newBuilder().setSetupId("stageId").build())
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();
    List<Child> children = List.of(
        Child.newBuilder()
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(0).build())
                                     .build())
            .build(),
        Child.newBuilder()
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(1).build())
                                     .build())
            .build(),
        Child.newBuilder()
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(2).build())
                                     .build())
            .build());
    doReturn(PlanExecutionMetadata.builder()
                 .postExecutionRollbackInfo(
                     PostExecutionRollbackInfo.newBuilder()
                         .setPostExecutionRollbackStageId("stageId")
                         .setRollbackStageStrategyMetadata(
                             StrategyMetadata.newBuilder()
                                 .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(0).build())
                                 .build())
                         .build())
                 .build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            ambiance.getPlanExecutionId(), PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    List<Child> filteredChildren = processor.getFilteredChildren(ambiance, children);
    assertThat(filteredChildren.size()).isEqualTo(3);
    assertThat(filteredChildren.get(0)).isEqualTo(children.get(0));
  }
}