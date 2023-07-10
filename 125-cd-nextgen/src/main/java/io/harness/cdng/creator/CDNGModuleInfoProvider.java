/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.join;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.gitops.steps.FetchLinkedAppsStep;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStepV2;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo.CDPipelineModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo.CDStageModuleInfoBuilder;
import io.harness.cdng.pipeline.executions.beans.FreezeExecutionInfo;
import io.harness.cdng.pipeline.executions.beans.FreezeExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.GitOpsAppSummary;
import io.harness.cdng.pipeline.executions.beans.GitOpsExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.InfraExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary.ArtifactsSummary;
import io.harness.cdng.pipeline.executions.beans.ServiceExecutionSummary.ArtifactsSummary.ArtifactsSummaryBuilder;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildChainStep;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceConfigStepConstants;
import io.harness.cdng.service.steps.constants.ServiceSectionStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
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
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class CDNGModuleInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Inject OutcomeService outcomeService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  public ArtifactsSummary mapArtifactsOutcomeToSummary(Optional<ArtifactsOutcome> artifactsOutcomeOptional) {
    ArtifactsSummaryBuilder artifactsSummaryBuilder = ArtifactsSummary.builder();
    if (artifactsOutcomeOptional == null || !artifactsOutcomeOptional.isPresent()) {
      return artifactsSummaryBuilder.build();
    }

    ArtifactsOutcome artifactsOutcome = artifactsOutcomeOptional.get();
    if (artifactsOutcome.getPrimary() != null) {
      artifactsSummaryBuilder.primary(artifactsOutcome.getPrimary().getArtifactSummary());
      if (artifactsOutcome.getPrimary().getArtifactSummary() != null) {
        artifactsSummaryBuilder.artifactDisplayName(
            artifactsOutcome.getPrimary().getArtifactSummary().getDisplayName());
      }
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

  private Optional<FreezeOutcome> getFreezeOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.FREEZE_OUTCOME));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((FreezeOutcome) optionalOutcome.getOutcome());
  }

  private Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((ArtifactsOutcome) optionalOutcome.getOutcome());
  }

  private Optional<ArtifactsOutcome> getArtifactsOutcome(OrchestrationEvent event) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((ArtifactsOutcome) optionalOutcome.getOutcome());
  }

  private Optional<InfrastructureOutcome> getInfrastructureOutcome(OrchestrationEvent event) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.OUTPUT));
    if (!optionalOutcome.isFound()) {
      return Optional.empty();
    }
    return Optional.ofNullable((InfrastructureOutcome) optionalOutcome.getOutcome());
  }

  private boolean isServiceNodeAndCompleted(StepType stepType, Status status) {
    return (Objects.equals(stepType, ServiceConfigStepConstants.STEP_TYPE)
               || Objects.equals(stepType, ServiceSectionStepConstants.STEP_TYPE)
               || Objects.equals(stepType, ServiceStepV3Constants.STEP_TYPE))
        && StatusUtils.isFinalStatus(status);
  }

  private boolean isInfrastructureNodeAndCompleted(StepType stepType, Status status) {
    return (Objects.equals(stepType, InfrastructureStep.STEP_TYPE)
               || Objects.equals(stepType, InfrastructureTaskExecutableStep.STEP_TYPE)
               || Objects.equals(stepType, InfrastructureTaskExecutableStepV2.STEP_TYPE))
        && StatusUtils.isFinalStatus(status);
  }

  private boolean isGitOpsNodeAndCompleted(StepType stepType, Status status) {
    return Objects.equals(stepType, GitopsClustersStep.STEP_TYPE) && StatusUtils.isFinalStatus(status);
  }

  private boolean isRollbackNodeAndCompleted(StepType stepType, Status status) {
    return Objects.equals(stepType, RollbackOptionalChildChainStep.STEP_TYPE) && StatusUtils.isFinalStatus(status);
  }

  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event) {
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    Ambiance ambiance = event.getAmbiance();
    CDPipelineModuleInfoBuilder cdPipelineModuleInfoBuilder = CDPipelineModuleInfo.builder();
    Optional<FreezeOutcome> freezeOutcome = getFreezeOutcome(ambiance);
    freezeOutcome.ifPresent(outcome -> {
      List<String> freezeIdentifiers = new LinkedList<>();
      outcome.getGlobalFreezeConfigs().stream().forEach(freezeConfig
          -> freezeIdentifiers.add(
              NGFreezeDtoMapper.getFreezeRef(freezeConfig.getFreezeScope(), freezeConfig.getIdentifier())));
      outcome.getManualFreezeConfigs().stream().forEach(freezeConfig
          -> freezeIdentifiers.add(
              NGFreezeDtoMapper.getFreezeRef(freezeConfig.getFreezeScope(), freezeConfig.getIdentifier())));
      cdPipelineModuleInfoBuilder.freezeIdentifiers(freezeIdentifiers);
    });
    if (isServiceNodeAndCompleted(stepType, event.getStatus())) {
      Optional<ServiceStepOutcome> serviceOutcome = getServiceStepOutcome(ambiance);
      serviceOutcome.ifPresent(outcome
          -> cdPipelineModuleInfoBuilder.serviceDefinitionType(outcome.getServiceDefinitionType())
                 .serviceIdentifier(outcome.getIdentifier()));
      Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);
      artifactsOutcome.ifPresent(outcome -> {
        if (outcome.getPrimary() != null && outcome.getPrimary().getArtifactSummary() != null) {
          cdPipelineModuleInfoBuilder.artifactDisplayName(outcome.getPrimary().getArtifactSummary().getDisplayName());
        }
      });
    }
    if (isInfrastructureNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome infraOptionalOutcome = outcomeService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      if (infraOptionalOutcome.isFound()) {
        InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) infraOptionalOutcome.getOutcome();
        cdPipelineModuleInfoBuilder.envIdentifier(infrastructureOutcome.getEnvironment().getIdentifier())
            .environmentType(infrastructureOutcome.getEnvironment().getType())
            .infrastructureType(infrastructureOutcome.getKind())
            .infrastructureIdentifier(infrastructureOutcome.getInfraIdentifier())
            .infrastructureName(infrastructureOutcome.getInfraName());
        if (EmptyPredicate.isNotEmpty(infrastructureOutcome.getEnvironment().getEnvGroupRef())) {
          cdPipelineModuleInfoBuilder.envGroupIdentifier(infrastructureOutcome.getEnvironment().getEnvGroupRef());
        }
      }
    } else if (isGitOpsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome =
          outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitopsClustersOutcome gitOpsOutcome = (GitopsClustersOutcome) optionalOutcome.getOutcome();
        gitOpsOutcome.getClustersData()
            .stream()
            .map(GitopsClustersOutcome.ClusterData::getEnvId)
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::envIdentifier);

        gitOpsOutcome.getClustersData()
            .stream()
            .map(GitopsClustersOutcome.ClusterData::getEnvType)
            .filter(EmptyPredicate::isNotEmpty)
            .map(EnvironmentType::valueOf)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::environmentType);

        gitOpsOutcome.getClustersData()
            .stream()
            .map(GitopsClustersOutcome.ClusterData::getEnvGroupId)
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::envGroupIdentifier);
      }
    } else if (isFetchLinkedAppsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
          event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(FetchLinkedAppsStep.GITOPS_LINKED_APPS_OUTCOME));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitOpsLinkedAppsOutcome linkedAppsOutcome = (GitOpsLinkedAppsOutcome) optionalOutcome.getOutcome();
        GitOpsAppSummary gitOpsAppSummary =
            GitOpsAppSummary.builder().applications(linkedAppsOutcome.getApps()).build();
        gitOpsAppSummary.getApplications()
            .stream()
            .map(app -> String.format("%s:%s", app.getAgentIdentifier().toLowerCase(), app.getName().toLowerCase()))
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toSet())
            .forEach(cdPipelineModuleInfoBuilder::gitOpsAppIdentifier);
      }
    }
    return cdPipelineModuleInfoBuilder.build();
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event) {
    CDStageModuleInfoBuilder cdStageModuleInfoBuilder = CDStageModuleInfo.builder();
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    Optional<FreezeOutcome> freezeOutcome = getFreezeOutcome(event.getAmbiance());
    freezeOutcome.ifPresent(outcome -> {
      List<FreezeExecutionInfo> executionInfos = new LinkedList<>();
      outcome.getGlobalFreezeConfigs().stream().forEach(freezeConfig
          -> executionInfos.add(FreezeExecutionInfo.builder()
                                    .freezeType(freezeConfig.getType().name())
                                    .identifier(freezeConfig.getIdentifier())
                                    .projectIdentifier(freezeConfig.getProjectIdentifier())
                                    .orgIdentifier(freezeConfig.getOrgIdentifier())
                                    .name(freezeConfig.getName())
                                    .yaml(freezeConfig.getYaml())
                                    .build()));
      outcome.getManualFreezeConfigs().stream().forEach(freezeConfig
          -> executionInfos.add(FreezeExecutionInfo.builder()
                                    .freezeType(freezeConfig.getType().name())
                                    .identifier(freezeConfig.getIdentifier())
                                    .projectIdentifier(freezeConfig.getProjectIdentifier())
                                    .orgIdentifier(freezeConfig.getOrgIdentifier())
                                    .name(freezeConfig.getName())
                                    .yaml(freezeConfig.getYaml())
                                    .build()));
      cdStageModuleInfoBuilder.freezeExecutionSummary(
          FreezeExecutionSummary.builder().freezeExecutionInfoList(executionInfos).build());
    });
    if (isServiceNodeAndCompleted(stepType, event.getStatus())) {
      Optional<ServiceStepOutcome> serviceOutcome = getServiceStepOutcome(event.getAmbiance());
      Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(event);
      serviceOutcome.ifPresent(outcome
          -> cdStageModuleInfoBuilder.serviceInfo(ServiceExecutionSummary.builder()
                                                      .identifier(outcome.getIdentifier())
                                                      .displayName(outcome.getName())
                                                      .deploymentType(outcome.getServiceDefinitionType())
                                                      .gitOpsEnabled(outcome.isGitOpsEnabled())
                                                      .artifacts(mapArtifactsOutcomeToSummary(artifactsOutcome))
                                                      .build()));
    } else if (isInfrastructureNodeAndCompleted(stepType, event.getStatus())) {
      Optional<InfrastructureOutcome> infrastructureOutcome = getInfrastructureOutcome(event);
      infrastructureOutcome.ifPresent(outcome -> {
        if (outcome.getEnvironment() != null) {
          cdStageModuleInfoBuilder.infraExecutionSummary(InfraExecutionSummary.builder()
                                                             .identifier(outcome.getEnvironment().getIdentifier())
                                                             .name(outcome.getEnvironment().getName())
                                                             .type(outcome.getEnvironment().getType().name())
                                                             .infrastructureIdentifier(outcome.getInfraIdentifier())
                                                             .infrastructureName(outcome.getInfraName())
                                                             .envGroupId(outcome.getEnvironment().getEnvGroupRef())
                                                             .envGroupName(outcome.getEnvironment().getEnvGroupName())
                                                             .build());
        }
      });
    } else if (isGitOpsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
          event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitopsClustersOutcome clustersOutcome = (GitopsClustersOutcome) optionalOutcome.getOutcome();
        final Map<String, List<GitopsClustersOutcome.ClusterData>> clusterData = groupGitOpsClusters(optionalOutcome);

        final GitOpsExecutionSummary gitOpsExecutionSummary = new GitOpsExecutionSummary();
        clusterData.values().forEach(cd -> {
          GitopsClustersOutcome.ClusterData data = cd.get(0);
          if (isNotEmpty(data.getEnvGroupId())) {
            gitOpsExecutionSummary.addSingleEnvironmentWithinEnvGroup(
                data.getEnvGroupId(), data.getEnvGroupName(), data.getEnvId(), data.getEnvName(), data.getEnvType());
          } else if (isNotEmpty(data.getEnvId())) {
            gitOpsExecutionSummary.addSingleEnvironment(data.getEnvId(), data.getEnvName(), data.getEnvType());
          }
        });
        populateGitOpsClusters(clustersOutcome, gitOpsExecutionSummary);
        cdStageModuleInfoBuilder.gitopsExecutionSummary(gitOpsExecutionSummary);

        // to maintain backward compatibility, will be removed in future
        if (clusterData.size() == 1) {
          GitopsClustersOutcome.ClusterData cluster = clusterData.values().iterator().next().get(0);
          cdStageModuleInfoBuilder.infraExecutionSummary(
              InfraExecutionSummary.builder().identifier(cluster.getEnvId()).name(cluster.getEnvName()).build());
        }
      }
    } else if (isRollbackNodeAndCompleted(stepType, event.getStatus())) {
      long startTs = AmbianceUtils.getCurrentLevelStartTs(event.getAmbiance());
      cdStageModuleInfoBuilder.rollbackDuration(System.currentTimeMillis() - startTs);
    } else if (isFetchLinkedAppsNodeAndCompleted(stepType, event.getStatus())) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
          event.getAmbiance(), RefObjectUtils.getOutcomeRefObject(FetchLinkedAppsStep.GITOPS_LINKED_APPS_OUTCOME));
      if (optionalOutcome != null && optionalOutcome.isFound()) {
        GitOpsLinkedAppsOutcome linkedAppsOutcome = (GitOpsLinkedAppsOutcome) optionalOutcome.getOutcome();
        GitOpsAppSummary gitOpsAppSummary =
            GitOpsAppSummary.builder().applications(linkedAppsOutcome.getApps()).build();
        cdStageModuleInfoBuilder.gitOpsAppSummary(gitOpsAppSummary);
      }
    }
    return cdStageModuleInfoBuilder.build();
  }

  private void populateGitOpsClusters(
      GitopsClustersOutcome clustersOutcome, GitOpsExecutionSummary gitOpsExecutionSummary) {
    List<GitOpsExecutionSummary.Cluster> clusters = clustersOutcome.getClustersData()
                                                        .stream()
                                                        .map(clustersDatum
                                                            -> GitOpsExecutionSummary.Cluster.builder()
                                                                   .clusterId(clustersDatum.getClusterId())
                                                                   .clusterName(clustersDatum.getClusterName())
                                                                   .envName(clustersDatum.getEnvName())
                                                                   .envGroupName(clustersDatum.getEnvGroupName())
                                                                   .envGroupId(clustersDatum.getEnvGroupId())
                                                                   .envId(clustersDatum.getEnvId())
                                                                   .agentId(clustersDatum.getAgentId())
                                                                   .scope(clustersDatum.getScope())
                                                                   .build())
                                                        .collect(Collectors.toList());
    gitOpsExecutionSummary.setClusters(clusters);
  }

  private boolean isFetchLinkedAppsNodeAndCompleted(StepType stepType, Status status) {
    return Objects.equals(stepType, FetchLinkedAppsStep.STEP_TYPE) && StatusUtils.isFinalStatus(status);
  }

  private Map<String, List<GitopsClustersOutcome.ClusterData>> groupGitOpsClusters(OptionalOutcome optionalOutcome) {
    GitopsClustersOutcome gitopsOutcome = (GitopsClustersOutcome) optionalOutcome.getOutcome();
    // envId -> ClusterData
    return gitopsOutcome.getClustersData().stream().collect(
        Collectors.groupingBy(c -> join("/", defaultIfBlank(c.getEnvGroupId(), ""), c.getEnvId())));
  }

  @Override
  public boolean shouldRun(OrchestrationEvent event) {
    StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    return isServiceNodeAndCompleted(stepType, event.getStatus())
        || isInfrastructureNodeAndCompleted(stepType, event.getStatus())
        || isGitOpsNodeAndCompleted(stepType, event.getStatus())
        || isRollbackNodeAndCompleted(stepType, event.getStatus())
        || isFetchLinkedAppsNodeAndCompleted(stepType, event.getStatus());
  }
}
