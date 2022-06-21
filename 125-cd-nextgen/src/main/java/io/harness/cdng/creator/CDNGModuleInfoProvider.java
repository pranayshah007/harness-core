/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo.CDPipelineModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo.CDStageModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.InfraExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary.ArtifactsSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary.ArtifactsSummary.ArtifactsSummaryBuilder;
import io.harness.cdng.service.steps.ServiceConfigStep;
import io.harness.cdng.service.steps.ServiceSectionStep;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class CDNGModuleInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Inject OutcomeService outcomeService;

  public ArtifactsSummary mapArtifactsOutcomeToSummary(Optional<ArtifactsOutcome> artifactsOutcomeOptional) {
    ArtifactsSummaryBuilder artifactsSummaryBuilder = ArtifactsSummary.builder();
    if (artifactsOutcomeOptional == null || !artifactsOutcomeOptional.isPresent()) {
      return artifactsSummaryBuilder.build();
    }

    ArtifactsOutcome artifactsOutcome = artifactsOutcomeOptional.get();
    if (artifactsOutcome.getPrimary() != null) {
      artifactsSummaryBuilder.primary(artifactsOutcome.getPrimary().getArtifactSummary());
    }

    if (isNotEmpty(artifactsOutcome.getSidecars())) {
      artifactsSummaryBuilder.sidecars(artifactsOutcome.getSidecars()
                                           .values()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .map(ArtifactOutcome::getArtifactSummary)
                                           .collect(Collectors.toList()));
    }

    return artifactsSummaryBuilder.build();
  }

  private Optional<ServiceStepOutcome> getServiceStepOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((ServiceStepOutcome) optionalOutcome.getOutcome());
  }

  private Optional<ArtifactsOutcome> getArtifactsOutcome(OrchestrationEvent event) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((ArtifactsOutcome) optionalOutcome.getOutcome());
  }

  private Optional<EnvironmentOutcome> getEnvironmentOutcome(OrchestrationEvent event) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.OUTPUT));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable(((InfrastructureOutcome) optionalOutcome.getOutcome()).getEnvironment());
  }

  private boolean isServiceNodeAndCompleted(StepType stepType, Status status) {
    return (Objects.equals(stepType, ServiceConfigStep.STEP_TYPE)
               || Objects.equals(stepType, ServiceSectionStep.STEP_TYPE))
        && StatusUtils.isFinalStatus(status);
  }

  private boolean isInfrastructureNodeAndCompleted(StepType stepType, Status status) {
    return Objects.equals(stepType, InfrastructureStep.STEP_TYPE) && StatusUtils.isFinalStatus(status);
  }

  private boolean isGitopsNodeAndCompleted(StepType stepType, Status status) {
    return Objects.equals(stepType, GitopsClustersStep.STEP_TYPE) && StatusUtils.isFinalStatus(status);
  }

  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event) {
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    Ambiance ambiance = event.getAmbiance();
    CDPipelineModuleInfoBuilder cdPipelineModuleInfoBuilder = CDPipelineModuleInfo.builder();
    if (isServiceNodeAndCompleted(stepType, event.getStatus())) {
      Optional<ServiceStepOutcome> serviceOutcome = getServiceStepOutcome(ambiance);
      serviceOutcome.ifPresent(outcome
          -> cdPipelineModuleInfoBuilder.serviceDefinitionType(outcome.getServiceDefinitionType())
                 .serviceIdentifier(outcome.getIdentifier()));
    }
    if (isInfrastructureNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome infraOptionalOutcome = outcomeService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      if (infraOptionalOutcome.isFound()) {
        InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) infraOptionalOutcome.getOutcome();
        cdPipelineModuleInfoBuilder.envIdentifier(infrastructureOutcome.getEnvironment().getIdentifier())
            .environmentType(infrastructureOutcome.getEnvironment().getType())
            .infrastructureType(infrastructureOutcome.getKind());
      }
    } else if (isGitopsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(GitopsClustersStep.GITOPS_SWEEPING_OUTPUT));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitopsClustersOutcome gitopsOutcome = (GitopsClustersOutcome) optionalOutcome.getOutcome();
        gitopsOutcome.getClustersData()
            .stream()
            .map(GitopsClustersOutcome.ClusterData::getEnv)
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::envIdentifier);

        gitopsOutcome.getClustersData()
            .stream()
            .map(GitopsClustersOutcome.ClusterData::getEnvGroup)
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::envGroupIdentifier);
      }
    }
    return cdPipelineModuleInfoBuilder.build();
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event) {
    CDStageModuleInfoBuilder cdStageModuleInfoBuilder = CDStageModuleInfo.builder();
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    if (isServiceNodeAndCompleted(stepType, event.getStatus())) {
      Optional<ServiceStepOutcome> serviceOutcome = getServiceStepOutcome(event.getAmbiance());
      Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(event);
      serviceOutcome.ifPresent(outcome
          -> cdStageModuleInfoBuilder.serviceInfo(ServiceExecutionSummary.builder()
                                                      .identifier(outcome.getIdentifier())
                                                      .displayName(outcome.getName())
                                                      .deploymentType(outcome.getServiceDefinitionType())
                                                      .artifacts(mapArtifactsOutcomeToSummary(artifactsOutcome))
                                                      .build()));
    } else if (isInfrastructureNodeAndCompleted(stepType, event.getStatus())) {
      Optional<EnvironmentOutcome> environmentOutcome = getEnvironmentOutcome(event);
      environmentOutcome.ifPresent(outcome
          -> cdStageModuleInfoBuilder.infraExecutionSummary(InfraExecutionSummary.builder()
                                                                .identifier(outcome.getIdentifier())
                                                                .name(outcome.getName())
                                                                .type(outcome.getType().name())
                                                                .build()));
    } else if (isGitopsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
          event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(GitopsClustersStep.GITOPS_SWEEPING_OUTPUT));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitopsClustersOutcome gitopsOutcome = (GitopsClustersOutcome) optionalOutcome.getOutcome();
        Set<String> envs = gitopsOutcome.getClustersData()
                               .stream()
                               .map(GitopsClustersOutcome.ClusterData::getEnv)
                               .collect(Collectors.toSet());
        if (envs.size() == 1) {
          String envIdentifier = envs.iterator().next();
          cdStageModuleInfoBuilder.infraExecutionSummary(
              InfraExecutionSummary.builder().identifier(envIdentifier).build());
        }
      }
    }
    return cdStageModuleInfoBuilder.build();
  }

  @Override
  public boolean shouldRun(OrchestrationEvent event) {
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    return isServiceNodeAndCompleted(stepType, event.getStatus())
        || isInfrastructureNodeAndCompleted(stepType, event.getStatus())
        || isGitopsNodeAndCompleted(stepType, event.getStatus());
  }
}
