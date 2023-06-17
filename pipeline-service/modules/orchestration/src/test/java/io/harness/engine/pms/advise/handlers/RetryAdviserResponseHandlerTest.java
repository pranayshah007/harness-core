/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delay.DelayEventHelper;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.pms.resume.EngineWaitRetryCallback;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.RetryAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class RetryAdviserResponseHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptManager interruptManager;
  @Mock private DelayEventHelper delayEventHelper;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Inject @InjectMocks private RetryAdviserResponseHandler retryAdviseHandler;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleAdvise() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String nodeSetupId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(nodeExecutionId).setSetupId(nodeSetupId).build()))
                            .build();
    RetryAdvise advise = RetryAdvise.newBuilder().setWaitInterval(0).setRetryNodeExecutionId(nodeExecutionId).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .nodeId(nodeSetupId)
            .name("DUMMY")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.FAILED)
            .build();
    retryAdviseHandler.handleAdvise(
        nodeExecution, AdviserResponse.newBuilder().setRetryAdvise(advise).setType(AdviseType.RETRY).build());

    ArgumentCaptor<InterruptPackage> argumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);

    verify(interruptManager).register(argumentCaptor.capture());
    InterruptPackage interruptPackage = argumentCaptor.getValue();
    assertThat(interruptPackage.getInterruptType()).isEqualTo(InterruptType.RETRY);
    assertThat(interruptPackage.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(interruptPackage.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(interruptPackage.getInterruptConfig().getConfigCase())
        .isEqualTo(InterruptConfig.ConfigCase.RETRYINTERRUPTCONFIG);
    assertThat(interruptPackage.getInterruptConfig().getIssuedBy().getAdviserIssuer())
        .isEqualTo(AdviserIssuer.newBuilder().setAdviserType(AdviseType.RETRY).build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleAdviseWithWait() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String nodeSetupId = generateUuid();
    String resumeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(nodeExecutionId).setSetupId(nodeSetupId).build()))
                            .build();
    RetryAdvise advise = RetryAdvise.newBuilder().setWaitInterval(100).setRetryNodeExecutionId(nodeExecutionId).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .nodeId(nodeSetupId)
            .name("DUMMY")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.FAILED)
            .build();
    doReturn(resumeId).when(delayEventHelper).delay(eq(100L), any());

    retryAdviseHandler.handleAdvise(
        nodeExecution, AdviserResponse.newBuilder().setRetryAdvise(advise).setType(AdviseType.RETRY).build());

    verify(delayEventHelper).delay(eq(100L), any());

    ArgumentCaptor<EngineWaitRetryCallback> argumentCaptor = ArgumentCaptor.forClass(EngineWaitRetryCallback.class);
    verify(waitNotifyEngine).waitForAllOn(any(), argumentCaptor.capture(), eq(resumeId));

    EngineWaitRetryCallback cb = argumentCaptor.getValue();
    assertThat(cb.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(cb.getNodeExecutionId()).isEqualTo(nodeExecutionId);
  }
}
