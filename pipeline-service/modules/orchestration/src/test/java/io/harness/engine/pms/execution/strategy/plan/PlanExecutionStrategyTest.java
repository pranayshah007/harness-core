/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.PipelineSettingsService;
import io.harness.PlanExecutionSettingResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.GovernanceService;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.governance.GovernanceMetadata;
import io.harness.observer.Subject;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Ambiance.Builder;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

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
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock Subject<OrchestrationStartObserver> orchestrationStartSubject;
  @Mock GovernanceService governanceService;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Spy @Inject PlanExecutionService planExecutionService;
  @Inject @InjectMocks PlanExecutionStrategy executionStrategy;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRunNode() {
    on(executionStrategy).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = generateUuid();
    Builder ambiance = Ambiance.newBuilder()
                           .setPlanExecutionId(planExecutionId)
                           .putAllSetupAbstractions(prepareInputArgs())
                           .setMetadata(ExecutionMetadata.newBuilder().setExecutionUuid(planExecutionId).build())
                           .addLevels(Level.newBuilder().setRuntimeId(generateUuid()).build());
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
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any());
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(true).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    PlanExecution planExecution = executionStrategy.runNode(
        ambiance.build(), plan, PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build());
    assertThat(planExecution.getUuid()).isEqualTo(planExecutionId);
    // shouldQueue is true. So there will be zero interactions with executorService to start the executions.
    verify(executorService, times(0)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.QUEUED);
    // Will be invoked because the current execution is being queued.
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    planExecutionId = generateUuid();
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(false).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build());
    // shouldQueue is false. So executorService will be called to start the execution..
    verify(executorService, times(1)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    // Will not be invoked because the current execution is being started and useNewFlow is false So invocations would
    // remain 1 as above..
    verify(waitNotifyEngine, times(1)).waitForAllOn(any(), any(), any());

    planExecutionId = generateUuid();
    doReturn(PlanExecutionSettingResponse.builder().useNewFlow(true).shouldQueue(false).build())
        .when(pipelineSettingsService)
        .shouldQueuePlanExecution(any(), any());
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build());
    verify(executorService, times(2)).submit(any(Callable.class));
    assertThat(planExecution.getStatus()).isEqualTo(Status.RUNNING);
    // Will be invoked because the current execution is being started but useNewFlow is true So invocations would become
    // 2 now.
    verify(waitNotifyEngine, times(2)).waitForAllOn(any(), any(), any());

    verify(orchestrationStartSubject, times(3)).fireInform(any(), any());

    // Governance will deny. So planExecution should have status errored.
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(governanceService)
        .evaluateGovernancePolicies(any(), any(), any(), any(), any(), any(), any());
    planExecutionId = generateUuid();
    planExecution = executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId).build(), plan,
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build());
    assertThat(planExecution.getStatus()).isEqualTo(Status.ERRORED);

    // Governance is denying the execution. executorService and waitNotifyEngine invocations should remain same.
    verify(executorService, times(2)).submit(any(Callable.class));
    verify(waitNotifyEngine, times(2)).waitForAllOn(any(), any(), any());

    // OrchestrationStartObserver throwing exception. PlanExecution should be marked as ERRORED.
    doThrow(new InvalidRequestException("Error Message")).when(orchestrationStartSubject).fireInform(any(), any());
    String planExecutionId1 = generateUuid();
    assertThatThrownBy(()
                           -> executionStrategy.runNode(ambiance.setPlanExecutionId(planExecutionId1).build(), plan,
                               PlanExecutionMetadata.builder().planExecutionId(planExecutionId1).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error Message");
    assertThat(planExecutionService.get(planExecutionId1).getStatus()).isEqualTo(Status.ERRORED);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldSetServiceName() {
    doReturn(Status.ERRORED).when(planExecutionService).calculateStatus(any());
    doReturn(PlanExecution.builder().status(Status.ERRORED).build())
        .when(planExecutionService)
        .updateStatus(any(), any(), any());
    ArgumentCaptor<OrchestrationEvent> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEvent.class);

    executionStrategy.endNodeExecution(Ambiance.newBuilder().build());

    verify(eventEmitter).emitEvent(argumentCaptor.capture());
    OrchestrationEvent event = argumentCaptor.getValue();
    assertThat(event.getServiceName()).isEqualTo("pms");
  }

  private static Map<String, String> prepareInputArgs() {
    return ImmutableMap.of("accountId", "kmpySmUISimoRrJL6NL73w", "appId", "XEsfW6D_RJm1IaGpDidD3g", "userId",
        triggeredBy.getUuid(), "userName", triggeredBy.getIdentifier(), "userEmail",
        triggeredBy.getExtraInfoOrThrow("email"));
  }
}
