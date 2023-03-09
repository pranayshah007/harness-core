/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.pms.audits.events.PipelineAbortEvent;
import io.harness.engine.pms.audits.events.PipelineEndEvent;
import io.harness.engine.pms.audits.events.PipelinePauseEvent;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.PipelineTimeoutEvent;
import io.harness.engine.pms.audits.events.StageEndEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.inject.Inject;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;

/***
 * This Class constructs NodeExecutionEvents and
 * sends them to Outbox for audits.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver, NodeStatusUpdateObserver {
  public static final String PIPELINE = "PIPELINE";
  public static final String STAGE = "STAGE";
  @Inject private OutboxService outboxService;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (!validatePresenceOfNodeGroupInNodeStartInfo(nodeStartInfo)) {
      return;
    }

    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(nodeStartInfo.getNodeExecution().getAmbiance())) {
      String nodeGroup = nodeStartInfo.getNodeExecution().getGroup();
      try {
        switch (nodeGroup) {
          case PIPELINE:
            sendPipelineStartEventForAudit(nodeStartInfo);
            break;
          case STAGE:
            sendStageStartEventForAudit(nodeStartInfo);
            break;
          default:
            log.info("Currently Audits are not supported for NodeGroup of type: {}", nodeGroup);
        }
      } catch (Exception ex) {
        log.error("Unexpected error occurred during handling of nodeGroup: {}", nodeGroup, ex);
      }
    }
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (!validatePresenceOfNodeGroupInNodeUpdateInfo(nodeUpdateInfo)) {
      return;
    }

    Status status = nodeUpdateInfo.getStatus();
    EnumSet<Status> FINAL_STATUSES = StatusUtils.finalStatuses();

    // Sending NodeEndEvents
    if (FINAL_STATUSES.contains(status)) {
      sendNodeEndEventsForAudit(nodeUpdateInfo);
    }

    if (PIPELINE.equals(nodeUpdateInfo.getNodeExecution().getGroup())) {
      handlePipelineExecutionEvents(nodeUpdateInfo);
    }
  }

  private void handlePipelineExecutionEvents(NodeUpdateInfo nodeUpdateInfo) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(nodeUpdateInfo.getNodeExecution().getAmbiance())) {
      Status status = nodeUpdateInfo.getNodeExecution().getStatus();
      try {
        switch (status) {
          case ABORTED:
            sendPipelineAbortEventForAudit(nodeUpdateInfo);
            break;
          case PAUSED:
            sendPipelinePauseEventForAudit(nodeUpdateInfo);
            break;
          case EXPIRED:
            sendPipelineTimeoutEventForAudit(nodeUpdateInfo);
            break;
          default:
            log.info("Currently Audits are not supported for status: {}", status.name());
        }
      } catch (Exception ex) {
        log.error("Unexpected error occurred during handling of nodeExecutionEvent with status: {}", status.name(), ex);
      }
    }
  }

  private void sendNodeEndEventsForAudit(NodeUpdateInfo nodeUpdateInfo) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(nodeUpdateInfo.getNodeExecution().getAmbiance())) {
      String nodeGroup = nodeUpdateInfo.getNodeExecution().getGroup();
      try {
        switch (nodeGroup) {
          case PIPELINE:
            sendPipelineEndEventForAudit(nodeUpdateInfo);
            break;
          case STAGE:
            sendStageEndEventForAudit(nodeUpdateInfo);
            break;
          default:
            log.info("Currently Audits are not supported for NodeGroup of type: {}", nodeGroup);
        }
      } catch (Exception ex) {
        log.error("Unexpected error occurred during handling of nodeGroup: {}", nodeGroup, ex);
      }
    }
  }

  private void sendPipelineTimeoutEventForAudit(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    PipelineTimeoutEvent pipelineTimeoutEvent =
        PipelineTimeoutEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .build();

    outboxService.save(pipelineTimeoutEvent);
  }

  private void sendPipelinePauseEventForAudit(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    PipelinePauseEvent pipelinePauseEvent =
        PipelinePauseEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .triggerType(ambiance.getMetadata().getTriggerInfo().getTriggerType().name())
            .triggeredBy(AmbianceUtils.getEmail(ambiance))
            .build();

    outboxService.save(pipelinePauseEvent);
  }

  private void sendPipelineAbortEventForAudit(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    PipelineAbortEvent pipelineAbortEvent =
        PipelineAbortEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .triggerType(ambiance.getMetadata().getTriggerInfo().getTriggerType().name())
            .triggeredBy(AmbianceUtils.getEmail(ambiance))
            .build();

    outboxService.save(pipelineAbortEvent);
  }

  private void sendStageStartEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    StageStartEvent stageStartEvent =
        StageStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .stageIdentifier(nodeStartInfo.getNodeExecution().getIdentifier())
            .stageType(ambiance.getMetadata().getModuleType())
            .planExecutionId(nodeStartInfo.getNodeExecution().getAmbiance().getPlanExecutionId())
            .nodeExecutionId(nodeStartInfo.getNodeExecution().getUuid())
            .build();
    if (nodeStartInfo.getNodeExecution().getStartTs() != null) {
      stageStartEvent.setStartTs(nodeStartInfo.getNodeExecution().getStartTs());
    }

    outboxService.save(stageStartEvent);
  }

  private void sendPipelineStartEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    PipelineStartEvent pipelineStartEvent =
        PipelineStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .triggerType(ambiance.getMetadata().getTriggerInfo().getTriggerType().name())
            .triggeredBy(AmbianceUtils.getEmail(ambiance))
            .build();
    if (nodeStartInfo.getNodeExecution().getStartTs() != null) {
      pipelineStartEvent.setStartTs(nodeStartInfo.getNodeExecution().getStartTs());
    }

    outboxService.save(pipelineStartEvent);
  }

  private void sendStageEndEventForAudit(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    StageEndEvent stageEndEvent =
        StageEndEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .stageIdentifier(nodeUpdateInfo.getNodeExecution().getIdentifier())
            .stageType(ambiance.getMetadata().getModuleType())
            .planExecutionId(nodeUpdateInfo.getNodeExecution().getAmbiance().getPlanExecutionId())
            .nodeExecutionId(nodeUpdateInfo.getNodeExecution().getUuid())
            .endTs(nodeUpdateInfo.getUpdatedTs())
            .status(nodeUpdateInfo.getStatus().name())
            .build();
    if (nodeUpdateInfo.getNodeExecution().getStartTs() != null) {
      stageEndEvent.setStartTs(nodeUpdateInfo.getNodeExecution().getStartTs());
    }

    outboxService.save(stageEndEvent);
  }

  private void sendPipelineEndEventForAudit(NodeUpdateInfo nodeUpdateInfo) {
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    PipelineEndEvent pipelineEndEvent =
        PipelineEndEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .endTs(nodeUpdateInfo.getUpdatedTs())
            .triggerType(ambiance.getMetadata().getTriggerInfo().getTriggerType().name())
            .triggeredBy(AmbianceUtils.getEmail(ambiance))
            .status(nodeUpdateInfo.getStatus().name())
            .build();
    if (nodeUpdateInfo.getNodeExecution().getStartTs() != null) {
      pipelineEndEvent.setStartTs(nodeUpdateInfo.getNodeExecution().getStartTs());
    }

    outboxService.save(pipelineEndEvent);
  }

  private boolean validatePresenceOfNodeGroupInNodeStartInfo(NodeStartInfo nodeStartInfo) {
    if (nodeStartInfo != null && nodeStartInfo.getNodeExecution() != null
        && nodeStartInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error("Required fields to send an outBoxEvent are not populated in nodeStartInfo!");
    return false;
  }

  private boolean validatePresenceOfNodeGroupInNodeUpdateInfo(NodeUpdateInfo nodeUpdateInfo) {
    if (nodeUpdateInfo != null && nodeUpdateInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error("Required fields to send an outBoxEvent are not populated in nodeUpdateInfo!");
    return false;
  }
}
