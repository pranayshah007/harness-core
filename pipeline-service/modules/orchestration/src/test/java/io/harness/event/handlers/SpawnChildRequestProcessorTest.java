/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.InitiateNodeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Update;

public class SpawnChildRequestProcessorTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock InitiateNodeHelper initiateNodeHelper;
  @Mock WaitNotifyEngine waitNotifyEngine;

  @Inject @InjectMocks SpawnChildRequestProcessor processor;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleSpawnChildEvent() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

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
            .setSdkResponseEventType(SdkResponseEventType.SPAWN_CHILD)
            .setSpawnChildRequest(SpawnChildRequest.newBuilder()
                                      .setChild(ChildExecutableResponse.newBuilder().setChildNodeId(child1Id).build())
                                      .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<String> runtimeIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(initiateNodeHelper).publishEvent(eq(ambiance), eq(child1Id), runtimeIdCaptor.capture());

    ArgumentCaptor<EngineResumeCallback> callbackCaptor = ArgumentCaptor.forClass(EngineResumeCallback.class);
    ArgumentCaptor<String> exIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(waitNotifyEngine).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    assertThat(runtimeIdCaptor.getValue()).isEqualTo(exIdCaptor.getValue());

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testHandleSpawnChildrenEvent() {
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

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
            .setSpawnChildRequest(
                SpawnChildRequest.newBuilder()
                    .setChildChain(ChildChainExecutableResponse.newBuilder().setNextChildId(child1Id).build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<String> runtimeIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(initiateNodeHelper).publishEvent(eq(ambiance), eq(child1Id), runtimeIdCaptor.capture());

    ArgumentCaptor<EngineResumeCallback> callbackCaptor = ArgumentCaptor.forClass(EngineResumeCallback.class);
    ArgumentCaptor<String> exIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(waitNotifyEngine).waitForAllOn(any(), callbackCaptor.capture(), exIdCaptor.capture());

    assertThat(callbackCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    assertThat(runtimeIdCaptor.getValue()).isEqualTo(exIdCaptor.getValue());

    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUpdateChildChainEvent() {
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSpawnChildRequest(
                SpawnChildRequest.newBuilder()
                    .setChildChain(ChildChainExecutableResponse.newBuilder().setNextChildId(child1Id).build())
                    .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), consumerCaptor.capture());

    assertThat(consumerCaptor.getValue()).isNotNull();
    Update ops = new Update();
    consumerCaptor.getValue().accept(ops);

    Document addToSet = ops.getUpdateObject().get("$addToSet", Document.class);
    assertThat(addToSet).hasSize(1);

    ExecutableResponse executableResponses = addToSet.get("executableResponses", ExecutableResponse.class);
    assertThat(executableResponses).isNotNull();
    assertThat(executableResponses.getChildChain()).isNotNull();
    assertThat(executableResponses.getChildChain().getNextChildId()).isEqualTo(child1Id);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUpdateChildEvent() {
    String nodeExecutionId = generateUuid();
    String child1Id = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build();

    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder()
            .setSpawnChildRequest(SpawnChildRequest.newBuilder()
                                      .setChild(ChildExecutableResponse.newBuilder().setChildNodeId(child1Id).build())
                                      .build())
            .setAmbiance(ambiance)
            .build();

    processor.handleEvent(event);

    ArgumentCaptor<Consumer> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), consumerCaptor.capture());

    assertThat(consumerCaptor.getValue()).isNotNull();
    Update ops = new Update();
    consumerCaptor.getValue().accept(ops);

    Document addToSet = ops.getUpdateObject().get("$addToSet", Document.class);
    assertThat(addToSet).hasSize(1);

    ExecutableResponse executableResponses = addToSet.get("executableResponses", ExecutableResponse.class);
    assertThat(executableResponses).isNotNull();
    assertThat(executableResponses.getChild()).isNotNull();
    assertThat(executableResponses.getChild().getChildNodeId()).isEqualTo(child1Id);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenChildNodeNotSet() {
    SdkResponseEventProto event =
        SdkResponseEventProto.newBuilder().setSpawnChildRequest(SpawnChildRequest.newBuilder().build()).build();
    assertThatThrownBy(() -> processor.handleEvent(event))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("CHILD or CHILD_CHAIN response should be set");
  }
}
