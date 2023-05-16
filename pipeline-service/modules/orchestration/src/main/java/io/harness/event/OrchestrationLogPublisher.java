/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationEventLog;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class OrchestrationLogPublisher implements PlanStatusUpdateObserver, StepDetailsUpdateObserver {
  @Inject private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject @Named(EventsFrameworkConstants.ORCHESTRATION_LOG) private Producer producer;
  @Inject @Named("orchestrationLogCache") Cache<String, Long> orchestrationLogCache;
  @Inject OrchestrationLogConfiguration orchestrationLogConfiguration;

  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    createAndHandleEventLog(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecutionId(),
        OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    createAndHandleEventLog(ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance),
        OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE);
  }

  public void onNodeUpdate(NodeUpdateInfo nodeUpdateInfo) {
    createAndHandleEventLog(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecutionId(),
        OrchestrationEventType.NODE_EXECUTION_UPDATE);
  }

  private void createAndHandleEventLog(
      String planExecutionId, String nodeExecutionId, OrchestrationEventType eventType) {
    orchestrationEventLogRepository.save(
        OrchestrationEventLog.builder()
            .createdAt(System.currentTimeMillis())
            .nodeExecutionId(nodeExecutionId)
            .orchestrationEventType(eventType)
            .planExecutionId(planExecutionId)
            .validUntil(Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()))
            .build());
    batchAndSendLogEventIfRequired(planExecutionId);
  }

  private void batchAndSendLogEventIfRequired(String planExecutionId) {
    try {
      Long currentValue = orchestrationLogCache.get(planExecutionId);
      if (currentValue != null) {
        if (currentValue >= orchestrationLogConfiguration.getOrchestrationLogBatchSize()) {
          sendLogEvent(planExecutionId);
          orchestrationLogCache.put(planExecutionId, 1L);
        } else {
          orchestrationLogCache.put(planExecutionId, currentValue + 1);
        }
      } else {
        orchestrationLogCache.put(planExecutionId, 1L);
      }
    } catch (Exception ex) {
      log.error(
          "Exception occurred while publishing orchestrationLogEvent for planExecutionId: {}", planExecutionId, ex);
    }
  }

  public void sendLogEvent(String planExecutionId) {
    OrchestrationLogEvent orchestrationLogEvent =
        OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();
    producer.send(Message.newBuilder()
                      .putAllMetadata(ImmutableMap.of("planExecutionId", planExecutionId))
                      .setData(orchestrationLogEvent.toByteString())
                      .build());
  }

  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    createAndHandleEventLog(nodeStartInfo.getNodeExecution().getPlanExecutionId(),
        nodeStartInfo.getNodeExecution().getUuid(), OrchestrationEventType.NODE_EXECUTION_START);
  }

  @Override
  public void onStepDetailsUpdate(StepDetailsUpdateInfo stepDetailsUpdateInfo) {
    createAndHandleEventLog(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getNodeExecutionId(),
        OrchestrationEventType.STEP_DETAILS_UPDATE);
  }

  @Override
  public void onStepInputsAdd(StepDetailsUpdateInfo stepDetailsUpdateInfo) {
    createAndHandleEventLog(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getNodeExecutionId(),
        OrchestrationEventType.STEP_INPUTS_UPDATE);
  }
}
