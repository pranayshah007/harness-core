/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.events;

import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_START;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PipelineStepExecutionUpdateEventHandler implements OrchestrationEventHandler {
  private static final Set<String> STEPS_TO_UPDATE =
      Sets.newHashSet(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.CUSTOM_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.SERVICE_NOW_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.JIRA_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.JIRA_CREATE_STEP_TYPE.getType(), StepSpecTypeConstants.JIRA_UPDATE_STEP_TYPE.getType());

  private static final Set<String> APPROVAL_STEPS =
      Sets.newHashSet(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.CUSTOM_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.SERVICE_NOW_APPROVAL_STEP_TYPE.getType(),
          StepSpecTypeConstants.JIRA_APPROVAL_STEP_TYPE.getType());

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private StepExecutionEntityService stepExecutionEntityService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    if (!STEPS_TO_UPDATE.contains(
            Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(event.getAmbiance())).getStepType().getType())) {
      return;
    }
    if (!pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(event.getAmbiance()), FeatureName.CDS_STEP_EXECUTION_DATA_SYNC)) {
      return;
    }
    if (NODE_EXECUTION_START.equals(event.getEventType())) {
      processNodeExecutionStartEvent(event);
    } else {
      processNodeExecutionStatusUpdateEvent(event, event.getStatus());
    }
  }

  private void processNodeExecutionStartEvent(OrchestrationEvent event) {
    processNodeExecutionStatusUpdateEvent(event, isApprovalStep(event) ? Status.APPROVAL_WAITING : Status.RUNNING);
  }

  private boolean isApprovalStep(OrchestrationEvent event) {
    return APPROVAL_STEPS.contains(
        Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(event.getAmbiance())).getStepType().getType());
  }

  private void processNodeExecutionStatusUpdateEvent(OrchestrationEvent event, Status status) {
    Ambiance ambiance = event.getAmbiance();
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    if (StatusUtils.isFinalStatus(status)) {
      handleTerminalStatus(
          event, status, ambiance, stepExecutionId, accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      handleNonTerminalStatus(status, ambiance, stepExecutionId, accountIdentifier, orgIdentifier, projectIdentifier);
    }
  }

  private void handleNonTerminalStatus(Status status, Ambiance ambiance, String stepExecutionId,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
      if (stepExecutionEntityService.checkIfStepExecutionEntityExists(ambiance)) {
        stepExecutionEntityService.updateStatus(scope, stepExecutionId, status);
      } else {
        stepExecutionEntityService.createStepExecutionEntity(ambiance, status);
      }
    } catch (Exception ex) {
      log.error(
          String.format(
              "Unable to create step execution entity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, planExecutionId: %s, stageExecutionId: %s, stepExecutionId: %s",
              accountIdentifier, orgIdentifier, projectIdentifier, ambiance.getPlanExecutionId(),
              ambiance.getStageExecutionId(), stepExecutionId),
          ex);
    }
  }

  private void handleTerminalStatus(OrchestrationEvent event, Status status, Ambiance ambiance, String stepExecutionId,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
      long endTs = event.getEndTs();
      Map<String, Object> updates = new HashMap<>();
      updates.put(StepExecutionEntityKeys.endts, endTs);
      updates.put(StepExecutionEntityKeys.status, status);
      stepExecutionEntityService.update(scope, stepExecutionId, updates);
    } catch (Exception ex) {
      log.error(
          String.format(
              "[CustomDashboard]: Unable to update step execution entity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, planExecutionId: %s, stageExecutionId: %s, stepExecutionId: %s",
              accountIdentifier, orgIdentifier, projectIdentifier, ambiance.getPlanExecutionId(),
              ambiance.getStageExecutionId(), stepExecutionId),
          ex);
    }
  }
}
