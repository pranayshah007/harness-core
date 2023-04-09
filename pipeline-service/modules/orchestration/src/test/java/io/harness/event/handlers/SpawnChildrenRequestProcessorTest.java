/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.execution.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Arrays;
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
    verify(waitNotifyEngine, times(3)).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getAllValues().get(2)).isInstanceOf(EngineResumeCallback.class);
    EngineResumeCallback engineResumeCallback = (EngineResumeCallback) callbackCaptor.getAllValues().get(2);
    assertThat(engineResumeCallback.getAmbiance()).isEqualTo(ambiance);
    assertThat(exIdCaptor.getAllValues().stream().flatMap(Arrays::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(runtimeIds.get(0), runtimeIds.get(1), runtimeIds.get(0), runtimeIds.get(1));

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }
}