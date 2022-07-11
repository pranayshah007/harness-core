/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume.publisher;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.resume.NodeResumeEvent;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.steps.TestStepParameters;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisNodeResumeEventPublisherTest extends OrchestrationTestBase {
  @Mock PmsEventSender eventSender;
  @Inject @InjectMocks RedisNodeResumeEventPublisher resumeEventPublisher;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testIfStepParametersSentAreResolvedOne() {
    Map<String, Object> sectionStepParams =
        RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("DummySection").build());
    Map<String, Object> resolvedSectionStepParams =
        RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("ResolvedDummySection").build());
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(sectionStepParams)))
                            .serviceName("DUMMY")
                            .build();
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(generateUuid())
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                          .build())
            .status(Status.RUNNING)
            .mode(ExecutionMode.ASYNC)
            .planNode(planNode)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .resolvedStepParameters(resolvedSectionStepParams)
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();
    resumeEventPublisher.publishEvent(ResumeMetadata.fromNodeExecution(nodeExecution), new HashMap<>(), false);
    NodeResumeEvent nodeResumeEvent = NodeResumeEvent.newBuilder()
                                          .setAmbiance(nodeExecution.getAmbiance())
                                          .setExecutionMode(nodeExecution.getMode())
                                          .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                                          .addAllRefObjects(planNode.getRefObjects())
                                          .setAsyncError(false)
                                          .putAllResponse(new HashMap<>())
                                          .build();

    verify(eventSender)
        .sendEvent(nodeExecution.getAmbiance(), nodeResumeEvent.toByteString(), PmsEventCategory.NODE_RESUME,
            nodeExecution.getModule(), true);
  }
}
