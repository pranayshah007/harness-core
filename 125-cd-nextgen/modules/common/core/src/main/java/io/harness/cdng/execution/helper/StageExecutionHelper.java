/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogCallbackUtils.saveExecutionLogSafely;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.lang.String.join;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.customDeployment.CustomDeploymentExecutionDetails;
import io.harness.cdng.execution.DefaultExecutionDetails;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.ExecutionInfoKeyOutput;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoBuilder;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.spot.elastigroup.ElastigroupStageExecutionDetails;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.cdng.execution.tas.TasStageExecutionDetails;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.cdng.instance.service.InstanceDeploymentInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.instanceinfo.AwsSshWinrmInstanceInfo;
import io.harness.entities.instanceinfo.AzureSshWinrmInstanceInfo;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.PdcInstanceInfo;
import io.harness.entities.instanceinfo.SshWinrmInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.StageStatus;

import software.wings.beans.LogWeight;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionHelper {
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceDeploymentInfoService instanceDeploymentInfoService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  public boolean shouldSaveStageExecutionInfo(String infrastructureKind) {
    return InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)
        || InfrastructureKind.AZURE_WEB_APP.equals(infrastructureKind)
        || InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind)
        || InfrastructureKind.ELASTIGROUP.equals(infrastructureKind);
  }

  public boolean isRollbackArtifactRequiredPerInfrastructure(String infrastructureKind) {
    return isSshWinRmInfrastructureKind(infrastructureKind)
        || InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind);
  }
  public boolean isInfrastructureSupportCommands(String infrastructureKind) {
    return isSshWinRmInfrastructureKind(infrastructureKind)
        || InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind);
  }
  public boolean isSshWinRmInfrastructureKind(String infrastructureKind) {
    return InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind);
  }

  public void saveStageExecutionInfo(
      @NotNull Ambiance ambiance, @Valid ExecutionInfoKey executionInfoKey, @NotNull final String infrastructureKind) {
    if (isEmpty(infrastructureKind)) {
      throw new InvalidArgumentsException(format(
          "Unable to save stage execution info, infrastructure kind cannot be null or empty, infrastructureKind: %s, executionInfoKey: %s",
          infrastructureKind, executionInfoKey.toString()));
    }
    if (executionInfoKey == null) {
      throw new InvalidArgumentsException("Execution info key cannot be null or empty");
    }

    Optional<ExecutionDetails> executionDetails = getExecutionDetailsByInfraKind(ambiance, infrastructureKind);
    if (executionDetails.isPresent()) {
      saveStageExecutionDetails(ambiance, executionInfoKey, executionDetails.get());
      executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.EXECUTION_INFO_KEY_OUTPUT_NAME,
          ExecutionInfoKeyOutput.builder().executionInfoKey(executionInfoKey).build(), StepCategory.STAGE.name());
    }
  }

  public void saveStageExecutionDetails(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, ExecutionDetails executionDetails) {
    if (ngFeatureFlagHelperService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_STAGE_EXECUTION_DATA_SYNC)) {
      saveStageExecutionInfoV2(ambiance, executionInfoKey, executionDetails);
    } else {
      saveStageExecutionInfo(ambiance, executionInfoKey, executionDetails);
    }
  }

  public Optional<ArtifactOutcome> getRollbackArtifact(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, final String infrastructureKind) {
    List<ArtifactOutcome> rollbackArtifacts = getRollbackArtifacts(ambiance, executionInfoKey, infrastructureKind);

    return isNotEmpty(rollbackArtifacts) ? Optional.ofNullable(rollbackArtifacts.get(0)) : Optional.empty();
  }

  public List<ArtifactOutcome> getRollbackArtifacts(
      @NotNull Ambiance ambiance, @NotNull ExecutionInfoKey executionInfoKey, final String infrastructureKind) {
    if (executionInfoKey == null) {
      throw new InvalidArgumentsException("Execution info key cannot be null or empty");
    }
    if (StringUtils.isBlank(infrastructureKind)) {
      throw new InvalidArgumentsException(
          format("Infrastructure kind cannot be null or empty, executionInfoKey: %s", executionInfoKey));
    }

    Optional<ExecutionDetails> executionDetails =
        getLatestSuccessfulStageExecutionDetails(executionInfoKey, ambiance.getStageExecutionId());
    if (!executionDetails.isPresent()) {
      return Collections.emptyList();
    }

    if (InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)) {
      SshWinRmStageExecutionDetails sshWinRmExecutionDetails = (SshWinRmStageExecutionDetails) executionDetails.get();
      return sshWinRmExecutionDetails.getArtifactsOutcome();
    }

    if (InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind)) {
      CustomDeploymentExecutionDetails customDeploymentExecutionDetails =
          (CustomDeploymentExecutionDetails) executionDetails.get();
      return customDeploymentExecutionDetails.getArtifactsOutcome();
    }

    if (executionDetails.get() instanceof DefaultExecutionDetails) {
      DefaultExecutionDetails defaultExecutionDetails = (DefaultExecutionDetails) executionDetails.get();
      return defaultExecutionDetails.getArtifactsOutcome();
    }

    return Collections.emptyList();
  }

  public Optional<ArtifactOutcome> getArtifact(Ambiance ambiance) {
    return cdStepHelper.resolveArtifactsOutcome(ambiance);
  }

  public Optional<StageExecutionInfo> getLatestSuccessfulStageExecutionInfo(
      @NotNull ExecutionInfoKey executionInfoKey, final String stageExecutionId) {
    return stageExecutionInfoService.getLatestSuccessfulStageExecutionInfo(executionInfoKey, stageExecutionId);
  }

  public Optional<ExecutionDetails> getLatestSuccessfulStageExecutionDetails(
      @NotNull ExecutionInfoKey executionInfoKey, final String stageExecutionId) {
    Optional<StageExecutionInfo> latestSuccessfulStageExecutionInfo =
        getLatestSuccessfulStageExecutionInfo(executionInfoKey, stageExecutionId);
    return latestSuccessfulStageExecutionInfo.map(StageExecutionInfo::getExecutionDetails);
  }

  public void addRollbackArtifactToStageOutcomeIfPresent(Ambiance ambiance, StepResponseBuilder stepResponseBuilder,
      ExecutionInfoKey executionInfoKey, final String infrastructureKind) {
    Optional<ArtifactOutcome> rollbackArtifact = getRollbackArtifact(ambiance, executionInfoKey, infrastructureKind);
    rollbackArtifact.ifPresent(artifactOutcome
        -> stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                               .outcome(artifactOutcome)
                                               .name(OutcomeExpressionConstants.ROLLBACK_ARTIFACT)
                                               .group(StepCategory.STAGE.name())
                                               .build()));
  }

  public Set<String> saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(Ambiance ambiance,
      ExecutionInfoKey executionInfoKey, InfrastructureOutcome infrastructureOutcome, Set<String> hosts,
      final String serviceType, boolean skipInstances, LogCallback logCallback) {
    if (!isSkipInstancesSupportedForInfraKind(infrastructureOutcome.getKind())) {
      throw new InvalidArgumentsException(
          format("Skip instances not supported for infrastructure kind: [%s]", infrastructureOutcome.getKind()));
    }
    hosts = isEmpty(hosts) ? new HashSet<>() : hosts;

    if (skipInstances) {
      Set<String> tempHosts = hosts;
      hosts = excludeHostsWithSameArtifactDeployed(ambiance, executionInfoKey, hosts);
      Sets.SetView<String> difference = Sets.difference(tempHosts, hosts);
      if (!difference.isEmpty()) {
        saveExecutionLogSafely(logCallback,
            String.format("Skipping deployment on following instance(s) as Artifact is already deployed:\n%s",
                String.join("\n", difference)));
      } else {
        saveExecutionLogSafely(logCallback, "No instance has the artifact deployed.");
      }
    }
    if (EmptyPredicate.isNotEmpty(hosts)) {
      saveInstances(ambiance, executionInfoKey, infrastructureOutcome, new ArrayList<>(hosts), serviceType);
    } else {
      saveExecutionLogSafely(logCallback,
          color("No nodes selected (Nodes already deployed with the same artifact)", White, LogWeight.Bold));
    }

    return hosts;
  }

  public Set<String> excludeHostsWithSameArtifactDeployed(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, Set<String> infrastructureHosts) {
    Optional<ArtifactDetails> artifactDetailsOptional = getArtifactDetails(ambiance);
    Set<String> hosts = isEmpty(infrastructureHosts) ? new HashSet<>() : infrastructureHosts;
    if (artifactDetailsOptional.isPresent()) {
      // in case of parallel Stages we also need the IN_PROGRESS status updated by parallel Stage
      InstanceDeploymentInfoStatus[] statuses = {
          InstanceDeploymentInfoStatus.SUCCEEDED, InstanceDeploymentInfoStatus.IN_PROGRESS};
      List<String> hostsWithSameArtifactFromDB =
          instanceDeploymentInfoService
              .getByHostsAndArtifact(executionInfoKey, new ArrayList<>(hosts), artifactDetailsOptional.get(), statuses)
              .stream()
              .map(InstanceDeploymentInfo::getInstanceInfo)
              .filter(instanceDeploymentInfo -> instanceDeploymentInfo instanceof SshWinrmInstanceInfo)
              .map(SshWinrmInstanceInfo.class ::cast)
              .map(SshWinrmInstanceInfo::getHost)
              .collect(Collectors.toList());

      log.info("Excluded hosts list: {}", join(", ", hostsWithSameArtifactFromDB));
      return infrastructureHosts.stream()
          .filter(host -> !hostsWithSameArtifactFromDB.contains(host))
          .collect(Collectors.toSet());
    } else {
      return hosts;
    }
  }

  public void saveInstances(Ambiance ambiance, ExecutionInfoKey executionInfoKey,
      InfrastructureOutcome infrastructureOutcome, List<String> hosts, String serviceType) {
    Optional<ArtifactDetails> artifactDetailsOptional = getArtifactDetails(ambiance);

    ArtifactDetails artifactDetails = artifactDetailsOptional.orElse(null);

    List<InstanceInfo> instanceInfoList = hosts.stream()
                                              .map(host -> getInstanceInfo(infrastructureOutcome, serviceType, host))
                                              .collect(Collectors.toList());

    instanceDeploymentInfoService.createAndUpdate(
        executionInfoKey, instanceInfoList, artifactDetails, ambiance.getStageExecutionId());
  }

  private boolean isSkipInstancesSupportedForInfraKind(String infrastructureKind) {
    return isSshWinRmInfrastructureKind(infrastructureKind)
        || InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind);
  }

  private void saveStageExecutionInfo(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, ExecutionDetails executionDetails) {
    StageExecutionInfoBuilder stageExecutionInfoBuilder =
        StageExecutionInfo.builder()
            .accountIdentifier(executionInfoKey.getScope().getAccountIdentifier())
            .orgIdentifier(executionInfoKey.getScope().getOrgIdentifier())
            .projectIdentifier(executionInfoKey.getScope().getProjectIdentifier())
            .envIdentifier(executionInfoKey.getEnvIdentifier())
            .infraIdentifier(executionInfoKey.getInfraIdentifier())
            .serviceIdentifier(executionInfoKey.getServiceIdentifier())
            .stageExecutionId(ambiance.getStageExecutionId())
            .planExecutionId(ambiance.getPlanExecutionId())
            .stageStatus(StageStatus.IN_PROGRESS)
            .executionDetails(executionDetails);

    if (isNotEmpty(executionInfoKey.getDeploymentIdentifier())) {
      stageExecutionInfoBuilder.deploymentIdentifier(executionInfoKey.getDeploymentIdentifier());
    }

    stageExecutionInfoService.save(stageExecutionInfoBuilder.build());
  }

  private void saveStageExecutionInfoV2(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, ExecutionDetails executionDetails) {
    StageExecutionInfo stageExecutionInfo = stageExecutionInfoService.findStageExecutionInfo(
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance), ambiance.getStageExecutionId());
    if (stageExecutionInfo == null) {
      saveStageExecutionInfo(ambiance, executionInfoKey, executionDetails);
      return;
    }
    Map<String, Object> updates = new HashMap<>();
    updates.put(StageExecutionInfoKeys.executionDetails, executionDetails);
    updates.put(StageExecutionInfoKeys.status, Status.RUNNING);
    updates.put(StageExecutionInfoKeys.stageStatus, StageStatus.IN_PROGRESS);
    updates.put(StageExecutionInfoKeys.envIdentifier, executionInfoKey.getEnvIdentifier());
    updates.put(StageExecutionInfoKeys.infraIdentifier, executionInfoKey.getInfraIdentifier());
    updates.put(StageExecutionInfoKeys.serviceIdentifier, executionInfoKey.getServiceIdentifier());

    if (isNotEmpty(executionInfoKey.getDeploymentIdentifier())) {
      updates.put(StageExecutionInfoKeys.deploymentIdentifier, executionInfoKey.getDeploymentIdentifier());
    }

    Scope scope = Scope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));

    stageExecutionInfoService.update(scope, ambiance.getStageExecutionId(), updates);
  }

  private Optional<ExecutionDetails> getExecutionDetailsByInfraKind(
      Ambiance ambiance, final String infrastructureKind) {
    if (InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)) {
      Optional<ArtifactOutcome> artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance);
      List<ArtifactOutcome> artifactsOutcome = artifactOutcome.map(Lists::newArrayList).orElse(new ArrayList<>());
      return Optional.of(SshWinRmStageExecutionDetails.builder()
                             .artifactsOutcome(artifactsOutcome)
                             .configFilesOutcome(cdStepHelper.getConfigFilesOutcome(ambiance).orElse(null))
                             .build());
    } else if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructureKind)) {
      return Optional.of(
          AzureWebAppsStageExecutionDetails.builder().pipelineExecutionId(ambiance.getPlanExecutionId()).build());
    } else if (InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureKind)) {
      Optional<ArtifactOutcome> artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance);
      List<ArtifactOutcome> artifactsOutcome = artifactOutcome.map(Lists::newArrayList).orElse(new ArrayList<>());
      return Optional.of(CustomDeploymentExecutionDetails.builder().artifactsOutcome(artifactsOutcome).build());
    } else if (InfrastructureKind.ELASTIGROUP.equals(infrastructureKind)) {
      return Optional.of(
          ElastigroupStageExecutionDetails.builder().pipelineExecutionId(ambiance.getPlanExecutionId()).build());
    } else if (InfrastructureKind.TAS.equals(infrastructureKind)) {
      Optional<ArtifactOutcome> artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance);
      List<ArtifactOutcome> artifactsOutcome = artifactOutcome.map(Lists::newArrayList).orElse(new ArrayList<>());
      return Optional.of(TasStageExecutionDetails.builder()
                             .artifactsOutcome(artifactsOutcome)
                             .pipelineExecutionId(ambiance.getPlanExecutionId())
                             .build());
    }
    Optional<ArtifactOutcome> artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance);
    List<ArtifactOutcome> artifactsOutcome = artifactOutcome.map(Lists::newArrayList).orElse(new ArrayList<>());
    if (isNotEmpty(artifactsOutcome)) {
      return Optional.of(DefaultExecutionDetails.builder().artifactsOutcome(artifactsOutcome).build());
    }
    return Optional.empty();
  }

  private Optional<ArtifactDetails> getArtifactDetails(Ambiance ambiance) {
    ArtifactOutcome artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance).orElse(null);
    if (artifactOutcome == null) {
      return Optional.empty();
    } else {
      return Optional.of(ArtifactDetails.builder()
                             .artifactId(artifactOutcome.getIdentifier())
                             .tag(artifactOutcome.getTag())
                             .displayName(artifactOutcome.getArtifactSummary().getDisplayName())
                             .build());
    }
  }

  private InstanceInfo getInstanceInfo(InfrastructureOutcome infrastructureOutcome, String serviceType, String host) {
    if (InfrastructureKind.PDC.equals(infrastructureOutcome.getKind())) {
      return PdcInstanceInfo.builder()
          .host(host)
          .serviceType(serviceType)
          .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
          .build();
    } else if (InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureOutcome.getKind())) {
      return AzureSshWinrmInstanceInfo.builder()
          .host(host)
          .serviceType(serviceType)
          .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
          .build();
    } else if (InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureOutcome.getKind())) {
      return AwsSshWinrmInstanceInfo.builder()
          .host(host)
          .serviceType(serviceType)
          .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
          .build();
    }
    throw new InvalidArgumentsException(
        format("Unsupported SshWinRmInstanceInfo for infrastructure kind: [%s]", infrastructureOutcome.getKind()));
  }
}
