/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.outbox.autoLog.NodeExecutionLogContext;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver {
  public static final String ACCOUNT_ID = "accountId";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String PIPELINE = "PIPELINE";
  public static final String STAGES = "STAGES";
  @Inject private OutboxService outboxService;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (validatePresenceOfNodeGroup(nodeStartInfo))
      return;

    String nodeGroup = null;
    try (AutoLogContext ignore =
             new NodeExecutionLogContext(nodeStartInfo.getNodeExecution().getUuid(), OVERRIDE_NESTS)) {
      nodeGroup = nodeStartInfo.getNodeExecution().getGroup();
      try {
        switch (nodeGroup) {
          case PIPELINE:
            sendPipelineExecutionEventForAudit(nodeStartInfo);
            break;
          case STAGES:
            sendStageExecutionEventForAudit(nodeStartInfo);
            break;
          default:
            log.info(String.format("Currently Audits are not supported for NodeGroup of type: {}", nodeGroup));
        }
      } catch (Exception ex) {
        log.error(String.format("Unexpected error occurred during handling of nodeGroup: {}", nodeGroup), ex);
      }
    }
  }

  private boolean validatePresenceOfNodeGroup(NodeStartInfo nodeStartInfo) {
    if (nodeStartInfo == null || nodeStartInfo.getNodeExecution() == null
        || nodeStartInfo.getNodeExecution().getGroup() == null) {
      log.error(String.format("Required fields to send an outBoxEvent are not populated in nodeStartInfo!"));
      return true;
    }
    return false;
  }

  private void sendStageExecutionEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    StageStartEvent stageStartEvent =
        StageStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(ACCOUNT_ID))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(ORG_IDENTIFIER))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(PROJECT_IDENTIFIER))
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
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get(ACCOUNT_ID))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get(ORG_IDENTIFIER))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get(PROJECT_IDENTIFIER))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .startTs(nodeStartInfo.getNodeExecution().getStartTs())
            .build();

    outboxService.save(pipelineStartEvent);
  }
}
