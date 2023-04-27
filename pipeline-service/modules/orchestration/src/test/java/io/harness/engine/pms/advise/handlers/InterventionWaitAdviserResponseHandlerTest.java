/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.InterventionWaitTimeoutCallback;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.TimeoutInstanceRepository;
import io.harness.rule.Owner;
import io.harness.timeout.TimeoutCallback;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutTracker;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;

import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterventionWaitAdviserResponseHandlerTest extends OrchestrationTestBase {
  @Inject InterventionWaitAdviserResponseHandler interventionWaitAdviserResponseHandler;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private TimeoutInstanceRepository timeoutInstanceRepository;

  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String NODE_EXECUTION_ID = generateUuid();
  private static final String NODE_SETUP_ID = generateUuid();

  private NodeExecution nodeExecution;
  private InterventionWaitAdvise advise;

  @Before
  public void setup() {
    planExecutionService.save(PlanExecution.builder().uuid(PLAN_EXECUTION_ID).status(Status.RUNNING).build());
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(PLAN_EXECUTION_ID).build();
    planExecutionMetadataService.save(planExecutionMetadata);

    PlanNode planNode = PlanNode.builder()
                            .uuid(NODE_SETUP_ID)
                            .name("DUMMY")
                            .identifier("dummy")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(NODE_EXECUTION_ID, planNode))
                            .build();
    nodeExecution = NodeExecution.builder()
                        .uuid(NODE_EXECUTION_ID)
                        .ambiance(ambiance)
                        .nodeId(NODE_SETUP_ID)
                        .name("DUMMY")
                        .identifier("dummy")
                        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                        .module("CD")
                        .startTs(System.currentTimeMillis())
                        .status(Status.FAILED)
                        .build();
    nodeExecutionService.save(nodeExecution);
    Duration timeout = Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).build();
    advise = InterventionWaitAdvise.newBuilder()
                 .setTimeout(timeout)
                 .setRepairActionCode(RepairActionCode.END_EXECUTION)
                 .build();
  }

  @Test

  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestHandleAdvise() {
    interventionWaitAdviserResponseHandler.handleAdvise(nodeExecution,
        AdviserResponse.newBuilder().setInterventionWaitAdvise(advise).setType(AdviseType.INTERVENTION_WAIT).build());
    NodeExecution nodeExecution = nodeExecutionService.get(NODE_EXECUTION_ID);
    assertThat(nodeExecution).isNotNull();
    assertThat(nodeExecution.getStatus()).isEqualTo(Status.INTERVENTION_WAITING);
    assertThat(nodeExecution.getAdviserTimeoutInstanceIds()).hasSize(1);
    String timeoutInstanceId = nodeExecution.getAdviserTimeoutInstanceIds().get(0);

    Optional<TimeoutInstance> optionalTimeout = timeoutInstanceRepository.findById(timeoutInstanceId);
    assertThat(optionalTimeout).isPresent();
    TimeoutCallback callback = optionalTimeout.get().getCallback();
    assertThat(callback).isInstanceOf(InterventionWaitTimeoutCallback.class);

    TimeoutTracker tracker = optionalTimeout.get().getTracker();
    assertThat(tracker.getDimension()).isEqualTo(AbsoluteTimeoutTrackerFactory.DIMENSION);
  }
}
