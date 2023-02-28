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
import io.harness.outbox.api.OutboxService;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver {
  @Inject private OutboxService outboxService;
  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    switch (nodeStartInfo.getNodeExecution().getGroup()) {
      case "PIPELINE":
        sendPipelineExecutionEventForAudit(nodeStartInfo);
        break;
      case "STAGES":
        sendStageExecutionEventForAudit(nodeStartInfo);
        break;
      default:
        log.info(String.format("Current type of event is not supported for Audits!"));
    }
  }

  private void sendStageExecutionEventForAudit(NodeStartInfo nodeStartInfo) {
    Ambiance ambiance = nodeStartInfo.getNodeExecution().getAmbiance();
    StageStartEvent stageStartEvent =
        StageStartEvent.builder()
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get("account"))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get("orgIdentifier"))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get("projectIdentifier"))
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
            .accountIdentifier(ambiance.getSetupAbstractionsMap().get("account"))
            .orgIdentifier(ambiance.getSetupAbstractionsMap().get("orgIdentifier"))
            .projectIdentifier(ambiance.getSetupAbstractionsMap().get("projectIdentifier"))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .startTs(nodeStartInfo.getNodeExecution().getStartTs())
            .build();

    outboxService.save(pipelineStartEvent);
  }
}
