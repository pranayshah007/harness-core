/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.InterventionWaitTimeoutCallback;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.timeout.TimeoutCallback;
import io.harness.timeout.TimeoutEngine;
import io.harness.timeout.TimeoutInstance;
import io.harness.timeout.TimeoutParameters;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutParameters;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;

import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterventionWaitAdviserResponseHandler implements AdviserResponseHandler {
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private TimeoutEngine timeoutEngine;

  @Override
  public void handleAdvise(NodeExecution nodeExecution, AdviserResponse adviserResponse) {
    InterventionWaitAdvise interventionWaitAdvise = adviserResponse.getInterventionWaitAdvise();

    timeoutEngine.deleteTimeouts(nodeExecution.getTimeoutInstanceIds());
    TimeoutCallback timeoutCallback =
        new InterventionWaitTimeoutCallback(nodeExecution.getAmbiance().getPlanExecutionId(), nodeExecution.getUuid());
    TimeoutParameters parameters = AbsoluteTimeoutParameters.builder()
                                       .timeoutMillis(getTimeoutInMillis(interventionWaitAdvise.getTimeout()))
                                       .build();
    TimeoutInstance instance =
        timeoutEngine.registerTimeout(AbsoluteTimeoutTrackerFactory.DIMENSION, parameters, timeoutCallback);

    nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), INTERVENTION_WAITING,
        ops
        -> ops.set(NodeExecutionKeys.adviserTimeoutInstanceIds, Collections.singletonList(instance.getUuid())),
        EnumSet.noneOf(Status.class));
    eventEmitter.emitEvent(OrchestrationEvent.newBuilder()
                               .setEventType(OrchestrationEventType.INTERVENTION_WAIT_START)
                               .setAmbiance(nodeExecution.getAmbiance())
                               .setStatus(nodeExecution.getStatus())
                               .setStepParameters(nodeExecution.getResolvedStepParametersBytes())
                               .setServiceName(nodeExecution.getModule())
                               .build());
  }

  private long getTimeoutInMillis(Duration duration) {
    if (duration == null) {
      return TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS;
    }
    return TimeUnit.SECONDS.toMillis(duration.getSeconds());
  }
}
