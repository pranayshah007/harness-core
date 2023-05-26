/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.eventGenerator;

import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.deployment.ArtifactDetails;
import io.harness.eventsframework.schemas.deployment.DeploymentEventDTO;
import io.harness.eventsframework.schemas.deployment.ExecutionDetails;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class DeploymentEventGenerator implements OrchestrationEventHandler {
  @Inject OutcomeService outcomeService;

  @Inject @Named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT) Producer producer;

  public static Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> getEngineEventHandlers() {
    Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> handlerMap = new HashMap<>();
    handlerMap.put(NODE_EXECUTION_STATUS_UPDATE, Sets.newHashSet(DeploymentEventGenerator.class));
    return handlerMap;
  }
  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    StepType stepType = AmbianceUtils.getCurrentStepType(ambiance);
    String nodeType = AmbianceUtils.obtainNodeType(ambiance);
    if (!stepType.getType().equals(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName())
        || !StatusUtils.isFinalStatus(event.getStatus()) || Objects.equals(nodeType, "IDENTITY_PLAN_NODE")) {
      return;
    }
    Optional<ServiceStepOutcome> optionalServiceStepOutcome = getServiceOutcomeFromAmbiance(ambiance);
    Optional<InfrastructureOutcome> optionalInfrastructureOutcome = getInfrastructureOutcomeFromAmbiance(ambiance);
    if (!optionalServiceStepOutcome.isPresent() || !optionalInfrastructureOutcome.isPresent()) {
      return;
    }
    publishDeploymentInfoEvent(event, optionalServiceStepOutcome.get().getIdentifier(),
        optionalInfrastructureOutcome.get().getEnvironment().getIdentifier());
  }

  private void publishDeploymentInfoEvent(
      OrchestrationEvent event, String serviceIdentifier, String environmentIdentifier) {
    DeploymentEventDTO deploymentEventDTO = getDeploymentInfo(event, serviceIdentifier, environmentIdentifier);
    producer.send(Message.newBuilder().setData(deploymentEventDTO.toByteString()).build());
  }

  private DeploymentEventDTO getDeploymentInfo(
      OrchestrationEvent event, String serviceIdentifier, String environmentIdentifier) {
    Ambiance ambiance = event.getAmbiance();
    DeploymentEventDTO.Builder deploymentInfoDTOBuilder =
        DeploymentEventDTO.newBuilder()
            .setAccountId(AmbianceUtils.getAccountId(ambiance))
            .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .setServiceIdentifier(serviceIdentifier)
            .setEnvironmentIdentifier(environmentIdentifier)
            .setExecutionDetails(
                ExecutionDetails.newBuilder()
                    .setPlanExecutionId(event.getAmbiance().getPlanExecutionId())
                    .setStageSetupId(AmbianceUtils.getStageLevelFromAmbiance(event.getAmbiance()).get().getSetupId())
                    .setPipelineId(ambiance.getMetadata().getPipelineIdentifier())
                    .setStageId(AmbianceUtils.getStageLevelFromAmbiance(event.getAmbiance()).get().getIdentifier())
                    .build())
            .setDeploymentStatus(event.getStatus().name())
            .setDeploymentStartTime(AmbianceUtils.getCurrentLevelStartTs(event.getAmbiance()))
            .setDeploymentEndTime(Instant.now().toEpochMilli());

    Optional<ArtifactsOutcome> optionalArtifactsOutcome = getArtifactOutcomeFromAmbiance(ambiance);
    if (optionalArtifactsOutcome.isPresent()) {
      deploymentInfoDTOBuilder.setArtifactDetails(
          ArtifactDetails.newBuilder()
              .setArtifactType(optionalArtifactsOutcome.get().getPrimary().getArtifactType())
              .setArtifactTag(optionalArtifactsOutcome.get().getPrimary().getTag())
              .build());
    }
    return deploymentInfoDTOBuilder.build();
  }

  private Optional<ServiceStepOutcome> getServiceOutcomeFromAmbiance(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    if (optionalOutcome.isFound()) {
      return Optional.of((ServiceStepOutcome) optionalOutcome.getOutcome());
    }
    return Optional.empty();
  }

  private Optional<InfrastructureOutcome> getInfrastructureOutcomeFromAmbiance(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    if (optionalOutcome.isFound()) {
      return Optional.of((InfrastructureOutcome) optionalOutcome.getOutcome());
    }
    return Optional.empty();
  }

  private Optional<ArtifactsOutcome> getArtifactOutcomeFromAmbiance(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (optionalOutcome.isFound()) {
      return Optional.of((ArtifactsOutcome) optionalOutcome.getOutcome());
    }
    return Optional.empty();
  }
}
