/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration;

import io.harness.abort.AbortedBy;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.beans.custom.executions.NodeExecutionEventData;
import io.harness.audit.beans.custom.executions.TriggeredByInfoAuditDetails;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.PipelineAbortEvent;
import io.harness.engine.pms.audits.events.PipelineEndEvent;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.PipelineTimeoutEvent;
import io.harness.engine.pms.audits.events.StageEndEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.engine.pms.audits.events.TriggeredInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class NodeExecutionEventUtils {
  // Below events are for sending NodeExecutionEvents to Outbox.
  public static PipelineStartEvent mapNodeOutboxInfoToPipelineStartEvent(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    return PipelineStartEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .build();
  }

  public static StageStartEvent mapNodeOutboxInfoToStageStartEvent(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    return StageStartEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .stageIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
        .stageType(ambiance.getMetadata().getModuleType())
        .nodeExecutionId(nodeOutboxInfo.getNodeExecutionId())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .build();
  }

  public static StageEndEvent mapNodeOutboxInfoToStageEndEvent(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    return StageEndEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .stageIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
        .stageType(ambiance.getMetadata().getModuleType())
        .nodeExecutionId(nodeOutboxInfo.getNodeExecutionId())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .status(nodeOutboxInfo.getStatus().name())
        .build();
  }

  public static PipelineTimeoutEvent mapAmbianceToTimeoutEvent(Ambiance ambiance) {
    return PipelineTimeoutEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(ambiance.getMetadata().getRunSequence())
        .build();
  }

  public static PipelineAbortEvent mapAmbianceToAbortEvent(Ambiance ambiance, AbortedBy abortedBy) {
    return PipelineAbortEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(ambiance.getMetadata().getRunSequence())
        .triggeredInfo(getTriggeredInfoFromAbortInfo(ambiance, abortedBy))
        .build();
  }

  public static PipelineEndEvent mapNodeOutboxInfoToPipelineEndEvent(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeOutboxInfo.getNodeExecution().getAmbiance();
    return PipelineEndEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .status(nodeOutboxInfo.getStatus().name())
        .build();
  }

  private static TriggeredInfo buildTriggeredByFromAmbiance(Ambiance ambiance) {
    return TriggeredInfo.builder()
        .type(AmbianceUtils.getTriggerType(ambiance).name())
        .identifier(AmbianceUtils.getTriggerIdentifier(ambiance))
        .extraInfo(AmbianceUtils.getTriggerBy(ambiance).getExtraInfoMap())
        .build();
  }

  private static TriggeredInfo getTriggeredInfoFromAbortInfo(Ambiance ambiance, AbortedBy abortedBy) {
    return TriggeredInfo.builder()
        .type(AmbianceUtils.getTriggerType(ambiance).name())
        .identifier(abortedBy.getUserName())
        .extraInfo(Collections.singletonMap("email", abortedBy.getEmail()))
        .build();
  }

  // Below events are for Publishing NodeExecutionAudits.
  public static NodeExecutionEventData mapPipelineStartEventToNodeExecutionEventData(
      PipelineStartEvent pipelineStartEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineStartEvent.getAccountIdentifier())
        .orgIdentifier(pipelineStartEvent.getOrgIdentifier())
        .projectIdentifier(pipelineStartEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineStartEvent.getPipelineIdentifier())
        .planExecutionId(pipelineStartEvent.getPlanExecutionId())
        .runSequence(pipelineStartEvent.getRunSequence())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineStartEvent.getTriggeredInfo()))
        .startTs(pipelineStartEvent.getStartTs())
        .build();
  }

  public static NodeExecutionEventData mapStageStartEventToNodeExecutionEventData(StageStartEvent stageStartEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(stageStartEvent.getAccountIdentifier())
        .orgIdentifier(stageStartEvent.getOrgIdentifier())
        .projectIdentifier(stageStartEvent.getProjectIdentifier())
        .pipelineIdentifier(stageStartEvent.getPipelineIdentifier())
        .runSequence(stageStartEvent.getRunSequence())
        .stageIdentifier(stageStartEvent.getStageIdentifier())
        .stageType(stageStartEvent.getStageType())
        .planExecutionId(stageStartEvent.getPlanExecutionId())
        .nodeExecutionId(stageStartEvent.getNodeExecutionId())
        .triggeredBy(getTriggeredByInfoAuditDetails(stageStartEvent.getTriggeredInfo()))
        .startTs(stageStartEvent.getStartTs())
        .build();
  }

  public static NodeExecutionEventData mapStageEndEventToNodeExecutionEventData(StageEndEvent stageEndEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(stageEndEvent.getAccountIdentifier())
        .orgIdentifier(stageEndEvent.getOrgIdentifier())
        .projectIdentifier(stageEndEvent.getProjectIdentifier())
        .pipelineIdentifier(stageEndEvent.getPipelineIdentifier())
        .runSequence(stageEndEvent.getRunSequence())
        .stageIdentifier(stageEndEvent.getStageIdentifier())
        .stageType(stageEndEvent.getStageType())
        .planExecutionId(stageEndEvent.getPlanExecutionId())
        .nodeExecutionId(stageEndEvent.getNodeExecutionId())
        .status(stageEndEvent.getStatus())
        .triggeredBy(getTriggeredByInfoAuditDetails(stageEndEvent.getTriggeredInfo()))
        .startTs(stageEndEvent.getStartTs())
        .endTs(stageEndEvent.getEndTs())
        .build();
  }

  public static NodeExecutionEventData mapPipelineTimeoutEventToNodeExecutionEventData(
      PipelineTimeoutEvent pipelineTimeoutEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineTimeoutEvent.getAccountIdentifier())
        .orgIdentifier(pipelineTimeoutEvent.getOrgIdentifier())
        .projectIdentifier(pipelineTimeoutEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineTimeoutEvent.getPipelineIdentifier())
        .planExecutionId(pipelineTimeoutEvent.getPlanExecutionId())
        .runSequence(pipelineTimeoutEvent.getRunSequence())
        .build();
  }

  public static NodeExecutionEventData mapPipelineEndEventToNodeExecutionEventData(PipelineEndEvent pipelineEndEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineEndEvent.getAccountIdentifier())
        .orgIdentifier(pipelineEndEvent.getOrgIdentifier())
        .projectIdentifier(pipelineEndEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineEndEvent.getPipelineIdentifier())
        .planExecutionId(pipelineEndEvent.getPlanExecutionId())
        .runSequence(pipelineEndEvent.getRunSequence())
        .status(pipelineEndEvent.getStatus())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineEndEvent.getTriggeredInfo()))
        .startTs(pipelineEndEvent.getStartTs())
        .endTs(pipelineEndEvent.getEndTs())
        .build();
  }

  public static NodeExecutionEventData mapPipelineAbortEventToNodeExecutionEventData(
      PipelineAbortEvent pipelineAbortEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineAbortEvent.getAccountIdentifier())
        .orgIdentifier(pipelineAbortEvent.getOrgIdentifier())
        .projectIdentifier(pipelineAbortEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineAbortEvent.getPipelineIdentifier())
        .planExecutionId(pipelineAbortEvent.getPlanExecutionId())
        .runSequence(pipelineAbortEvent.getRunSequence())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineAbortEvent.getTriggeredInfo()))
        .build();
  }

  private static TriggeredByInfoAuditDetails getTriggeredByInfoAuditDetails(TriggeredInfo triggeredInfo) {
    return TriggeredByInfoAuditDetails.builder()
        .type(triggeredInfo.getType())
        .identifier(triggeredInfo.getIdentifier())
        .extraInfo(triggeredInfo.getExtraInfo())
        .build();
  }
}
