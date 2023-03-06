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
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/***
 * This Class constructs NodeExecutionEvents and
 * sends them to Outbox for audits.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver {
  public static final String PIPELINE = "PIPELINE";
  public static final String STAGE = "STAGE";
  @Inject private OutboxService outboxService;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (!validatePresenceOfNodeGroup(nodeStartInfo)) {
      return;
    }

    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(nodeStartInfo.getNodeExecution().getAmbiance())) {
      String nodeGroup = nodeStartInfo.getNodeExecution().getGroup();
      try {
        switch (nodeGroup) {
          case PIPELINE:
            sendPipelineExecutionEventForAudit(nodeStartInfo);
            break;
          case STAGE:
            sendStageExecutionEventForAudit(nodeStartInfo);
            break;
          default:
            log.info("Currently Audits are not supported for NodeGroup of type: {}", nodeGroup);
        }
      } catch (Exception ex) {
        log.error("Unexpected error occurred during handling of nodeGroup: {}", nodeGroup, ex);
      }
    }
  }

  private boolean validatePresenceOfNodeGroup(NodeStartInfo nodeStartInfo) {
    if (nodeStartInfo != null && nodeStartInfo.getNodeExecution() != null
        && nodeStartInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error("Required fields to send an outBoxEvent are not populated in nodeStartInfo!");
    return false;
  }

  private void sendStageExecutionEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    StageStartEvent stageStartEvent =
        StageStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .stageIdentifier(nodeStartInfo.getNodeExecution().getIdentifier())
            .planExecutionId(nodeStartInfo.getNodeExecution().getAmbiance().getPlanExecutionId())
            .nodeExecutionId(nodeStartInfo.getNodeExecution().getUuid())
            .startTs(nodeStartInfo.getNodeExecution().getStartTs())
            .build();

    outboxService.save(stageStartEvent);
  }

  private void sendPipelineExecutionEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    PipelineStartEvent pipelineStartEvent =
        PipelineStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .startTs(nodeStartInfo.getNodeExecution().getStartTs())
            .build();

    outboxService.save(pipelineStartEvent);
  }
}
