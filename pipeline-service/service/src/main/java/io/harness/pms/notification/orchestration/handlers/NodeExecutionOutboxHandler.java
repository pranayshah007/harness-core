/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.ngtriggers.Constants.ENABLE_NODE_EXECUTION_AUDIT_EVENTS;
import static io.harness.ngtriggers.Constants.ENABLE_NODE_EXECUTION_AUDIT_EVENTS_TRUE_VALUE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.NodeExecutionEvent;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants;
import io.harness.logging.AutoLogContext;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.NodeExecutionEventUtils;
import io.harness.pms.notification.orchestration.helpers.AbortInfoHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/***
 * This class constructs NodeExecutionEvents and
 * sends them to Outbox for audits.
 */

//@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver, NodeStatusUpdateObserver {
  @Inject private OutboxService outboxService;
  @Inject private AbortInfoHelper abortInfoHelper;
  @Inject private NGSettingsClient settingsClient;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (!validatePresenceOfNodeGroupInNodeStartInfo(nodeStartInfo)) {
      return;
    }

    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeStartInfo.getNodeExecution())
                                        .updatedTs(nodeStartInfo.getUpdatedTs())
                                        .type(NodeExecutionOutboxEventConstants.NODE_START_INFO)
                                        .build();
    sendOutboxEvents(nodeOutboxInfo);
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (!validatePresenceOfNodeGroupInNodeUpdateInfo(nodeUpdateInfo)) {
      return;
    }

    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeUpdateInfo.getNodeExecution())
                                        .updatedTs(nodeUpdateInfo.getUpdatedTs())
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .build();
    sendOutboxEvents(nodeOutboxInfo);
  }

  private void sendOutboxEvents(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    String enableNodeAudit = null;
    try {
      enableNodeAudit = NGRestUtils
                            .getResponse(settingsClient.getSetting(ENABLE_NODE_EXECUTION_AUDIT_EVENTS,
                                ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId), null, null))
                            .getValue();
    } catch (Exception ex) {
      log.debug(String.format("Could not fetch setting: %s", ENABLE_NODE_EXECUTION_AUDIT_EVENTS), ex);
      return;
    }

    if (ENABLE_NODE_EXECUTION_AUDIT_EVENTS_TRUE_VALUE.equals(enableNodeAudit)) {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(nodeOutboxInfo.getNodeExecution().getAmbiance())) {
        String nodeGroup = nodeOutboxInfo.getNodeExecution().getGroup();
        try {
          switch (nodeGroup) {
            case NodeExecutionOutboxEventConstants.PIPELINE:
              sendPipelineExecutionEvents(nodeOutboxInfo);
              break;
            case NodeExecutionOutboxEventConstants.STAGE:
              sendStageExecutionEvents(nodeOutboxInfo);
              break;
            default:
              log.info(String.format(NodeExecutionOutboxEventConstants.AUDIT_NOT_SUPPORTED_MSG, nodeGroup));
          }
        } catch (Exception ex) {
          log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG, nodeGroup), ex);
        }
      }
    }
  }

  private void sendPipelineExecutionEvents(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    Status status = nodeOutboxInfo.getStatus();
    NodeExecutionEvent nodeExecutionEvent = null;

    try {
      // PipelineStartEvent for audit
      if (NodeExecutionOutboxEventConstants.NODE_START_INFO.equals(nodeOutboxInfo.getType())) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineStartEvent(nodeOutboxInfo);
        if (nodeExecutionEvent != null) {
          outboxService.save(nodeExecutionEvent);
        }
        return;
      }

      // PipelineInterruptEvents for audit
      switch (status) {
        case ABORTED:
          nodeExecutionEvent = NodeExecutionEventUtils.mapAmbianceToAbortEvent(
              ambiance, abortInfoHelper.fetchAbortedByInfoFromInterrupts(ambiance.getPlanExecutionId()));
          break;
        case EXPIRED:
          nodeExecutionEvent = NodeExecutionEventUtils.mapAmbianceToTimeoutEvent(ambiance);
          break;
        default:
          log.info(String.format("Currently Audits are not supported for status: %s", status.name()));
      }
      // In case of Abort and Expire we need to send 2 events one being the PipelineEndEvent also!
      if (nodeExecutionEvent != null) {
        outboxService.save(nodeExecutionEvent);
      }

      // PipelineEndEvent for audit
      if (StatusUtils.finalStatuses().contains(status)) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineEndEvent(nodeOutboxInfo);
        if (nodeExecutionEvent != null) {
          outboxService.save(nodeExecutionEvent);
        }
      }
    } catch (Exception ex) {
      log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                    nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }
  }

  private void sendStageExecutionEvents(NodeOutboxInfo nodeOutboxInfo) {
    Status status = nodeOutboxInfo.getStatus();
    NodeExecutionEvent nodeExecutionEvent = null;

    try {
      // StageStartEvent for audit
      if (NodeExecutionOutboxEventConstants.NODE_START_INFO.equals(nodeOutboxInfo.getType())) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToStageStartEvent(nodeOutboxInfo);
      }

      // StageEndEvent for audit
      if (StatusUtils.finalStatuses().contains(status)) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToStageEndEvent(nodeOutboxInfo);
      }

      if (nodeExecutionEvent != null) {
        outboxService.save(nodeExecutionEvent);
      }
    } catch (Exception ex) {
      log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                    nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }
  }

  private boolean validatePresenceOfNodeGroupInNodeStartInfo(NodeStartInfo nodeStartInfo) {
    if (nodeStartInfo != null && nodeStartInfo.getNodeExecution() != null
        && nodeStartInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error(String.format(NodeExecutionOutboxEventConstants.FIELDS_NOT_POPULATED_MSG));
    return false;
  }

  private boolean validatePresenceOfNodeGroupInNodeUpdateInfo(NodeUpdateInfo nodeUpdateInfo) {
    if (nodeUpdateInfo != null && nodeUpdateInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error(String.format(NodeExecutionOutboxEventConstants.FIELDS_NOT_POPULATED_MSG));
    return false;
  }
}