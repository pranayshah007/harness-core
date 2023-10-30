/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.registries.AdviserRegistry;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeAdviseHelperTest extends OrchestrationTestBase {
  @Mock private NodeAdviseEventPublisher nodeAdviseEventPublisher;
  @Mock private AdviserRegistry adviserRegistry;

  @Inject @InjectMocks private NodeAdviseHelper helper;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldQueueAdvisingEvent() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    PlanNode planNode = PlanNode.builder().build();
    when(nodeAdviseEventPublisher.publishEvent(nodeExecution, planNode, Status.SUCCEEDED)).thenReturn(null);

    helper.queueAdvisingEvent(nodeExecution, planNode, Status.SUCCEEDED);

    verify(nodeAdviseEventPublisher).publishEvent(nodeExecution, planNode, Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetResponseInCaseOfNoCustomAdviser() {
    Level level =
        Level.newBuilder()
            .setRuntimeId("runtimeId")
            .setSetupId("setupId")
            .setStepType(StepType.newBuilder().setType("DEPLOY_PHASE").setStepCategory(StepCategory.STEP).build())
            .setGroup("PHASE")
            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").addLevels(level).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .ambiance(ambiance)
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("code").build()).build())
            .interruptHistories(new ArrayList<>())
            .status(Status.ABORTED)
            .notifyId("notifyId")
            .build();
    AdviserType adviserType = AdviserType.newBuilder().setType("Type1").build();
    when(adviserRegistry.obtain(adviserType)).thenReturn(new Type1Adviser());
    AdviserObtainment adviserObtainment = AdviserObtainment.newBuilder().setType(adviserType).build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("planNodeId")
            .identifier("test")
            .stepType(StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build())
            .adviserObtainment(adviserObtainment)
            .serviceName("CD")
            .build();
    SdkResponseEventProto responseEventProto =
        helper.getResponseInCaseOfNoCustomAdviser(nodeExecution, planNode, Status.RUNNING);
    assertThat(responseEventProto).isNotNull();
    assertThat(responseEventProto.getSdkResponseEventType()).isEqualTo(SdkResponseEventType.HANDLE_ADVISER_RESPONSE);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetResponseInCaseOfNoCustomAdviserForErrorCase() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .ambiance(ambiance)
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("code").build()).build())
            .interruptHistories(new ArrayList<>())
            .status(Status.ABORTED)
            .notifyId("notifyId")
            .build();
    AdviserType adviserType = AdviserType.newBuilder().setType("Type1").build();
    when(adviserRegistry.obtain(adviserType)).thenReturn(new Type1Adviser());
    AdviserObtainment adviserObtainment = AdviserObtainment.newBuilder().setType(adviserType).build();
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("planNodeId")
            .identifier("test")
            .stepType(StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build())
            .adviserObtainment(adviserObtainment)
            .serviceName("CD")
            .build();
    SdkResponseEventProto responseEventProto =
        helper.getResponseInCaseOfNoCustomAdviser(nodeExecution, planNode, Status.RUNNING);
    assertThat(responseEventProto).isNotNull();
    assertThat(responseEventProto.getSdkResponseEventType()).isEqualTo(SdkResponseEventType.HANDLE_EVENT_ERROR);
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .ambiance(ambiance)
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("code").build()).build())
            .interruptHistories(new ArrayList<>())
            .status(Status.ABORTED)
            .build();
    responseEventProto = helper.getResponseInCaseOfNoCustomAdviser(nodeExecution1, planNode, Status.RUNNING);
    assertThat(responseEventProto).isNull();
  }

  private class Type1Adviser implements Adviser {
    Type1Adviser() {}

    @Override
    public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
      return null;
    }

    @Override
    public boolean canAdvise(AdvisingEvent advisingEvent) {
      return true;
    }
  }
}
