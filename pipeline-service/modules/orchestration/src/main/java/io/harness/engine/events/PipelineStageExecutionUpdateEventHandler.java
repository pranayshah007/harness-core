/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.events;
import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.stage.StageExecutionEntityService;
import io.harness.execution.stage.StageExecutionEntityUpdateDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.StageStatus;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PipelineStageExecutionUpdateEventHandler implements OrchestrationEventHandler {
  private static final Set<String> STAGES_TO_UPDATE = Sets.newHashSet(OrchestrationStepTypes.CUSTOM_STAGE);

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private StageExecutionEntityService stageExecutionEntityService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    if (!STAGES_TO_UPDATE.contains(
            Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(event.getAmbiance())).getStepType().getType())) {
      return;
    }
    if (!StatusUtils.isFinalStatus(event.getStatus())) {
      return;
    }
    if (!pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(event.getAmbiance()), FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC)) {
      return;
    }
    processNodeExecutionStatusUpdateEvent(event, event.getStatus());
  }

  private void processNodeExecutionStatusUpdateEvent(OrchestrationEvent event, Status status) {
    Ambiance ambiance = event.getAmbiance();
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    handleTerminalStatus(event, status, ambiance, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private void handleTerminalStatus(OrchestrationEvent event, Status status, Ambiance ambiance,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      StageExecutionEntityUpdateDTO stageExecutionEntityUpdateDTO =
          StageExecutionEntityUpdateDTO.builder()
              .endTs(event.getEndTs())
              .status(status)
              .stageStatus(Status.SUCCEEDED.equals(status) ? StageStatus.SUCCEEDED : StageStatus.FAILED)
              .build();
      stageExecutionEntityService.updateStageExecutionEntity(ambiance, stageExecutionEntityUpdateDTO);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Unable to update stage execution entity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, planExecutionId: %s, stageExecutionId: %s",
              accountIdentifier, orgIdentifier, projectIdentifier, ambiance.getPlanExecutionId(),
              ambiance.getStageExecutionId()),
          ex);
    }
  }
}