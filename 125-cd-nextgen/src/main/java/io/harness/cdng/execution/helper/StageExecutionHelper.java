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

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.ExecutionInfoKeyOutput;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoBuilder;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.StageStatus;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
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

  public boolean shouldSaveStageExecutionInfo(String infrastructureKind) {
    return InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)
        || InfrastructureKind.AZURE_WEB_APP.equals(infrastructureKind);
  }

  public boolean isRollbackArtifactRequiredPerInfrastructure(String infrastructureKind) {
    return InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind);
  }

  public void saveStageExecutionInfoAndPublishExecutionInfoKey(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, String infrastructureKind) {
    saveStageExecutionInfo(ambiance, executionInfoKey, infrastructureKind);
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.EXECUTION_INFO_KEY_OUTPUT_NAME,
        ExecutionInfoKeyOutput.builder().executionInfoKey(executionInfoKey).build(), StepCategory.STAGE.name());
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

    ExecutionDetails executionDetails = getExecutionDetailsByInfraKind(ambiance, infrastructureKind);
    saveStageExecutionInfo(ambiance, executionInfoKey, executionDetails);
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

    throw new InvalidRequestException(
        format("Not supported rollback artifact for infrastructure, infrastructureKind: %s", infrastructureKind));
  }

  public Optional<ArtifactOutcome> getArtifact(Ambiance ambiance) {
    return cdStepHelper.resolveArtifactsOutcome(ambiance);
  }

  public Optional<ExecutionDetails> getLatestSuccessfulStageExecutionDetails(
      @NotNull ExecutionInfoKey executionInfoKey, final String stageExecutionId) {
    Optional<StageExecutionInfo> latestSuccessfulStageExecutionInfo =
        stageExecutionInfoService.getLatestSuccessfulStageExecutionInfo(executionInfoKey, stageExecutionId);
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
            .stageStatus(StageStatus.IN_PROGRESS)
            .executionDetails(executionDetails);

    if (isNotEmpty(executionInfoKey.getDeploymentIdentifier())) {
      stageExecutionInfoBuilder.deploymentIdentifier(executionInfoKey.getDeploymentIdentifier());
    }

    stageExecutionInfoService.save(stageExecutionInfoBuilder.build());
  }

  @Nullable
  private ExecutionDetails getExecutionDetailsByInfraKind(Ambiance ambiance, final String infrastructureKind) {
    if (InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)) {
      Optional<ArtifactOutcome> artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance);
      List<ArtifactOutcome> artifactsOutcome = artifactOutcome.map(Lists::newArrayList).orElse(new ArrayList<>());
      return SshWinRmStageExecutionDetails.builder()
          .artifactsOutcome(artifactsOutcome)
          .configFilesOutcome(cdStepHelper.getConfigFilesOutcome(ambiance).orElse(null))
          .build();
    } else if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructureKind)) {
      return AzureWebAppsStageExecutionDetails.builder().pipelineExecutionId(ambiance.getPlanExecutionId()).build();
    }

    return null;
  }
}
