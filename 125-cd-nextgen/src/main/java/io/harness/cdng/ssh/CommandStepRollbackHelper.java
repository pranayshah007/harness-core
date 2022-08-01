/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.utilities.SshWinRmUtility.getHost;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.rollback.RollbackData;
import io.harness.cdng.rollback.service.RollbackDataService;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.rollback.copyartifact.CopyArtifactUnitRollbackDeploymentInfo;
import io.harness.cdng.ssh.rollback.copyartifact.CopyArtifactUnitRollbackDeploymentInfoKey;
import io.harness.cdng.ssh.rollback.copyconfig.CopyConfigUnitRollbackDeploymentInfo;
import io.harness.cdng.ssh.rollback.copyconfig.CopyConfigUnitRollbackDeploymentInfoKey;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class CommandStepRollbackHelper extends CDStepHelper {
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private RollbackDataService rollbackDataService;

  public void saveRollbackDeploymentInfo(
      @NotNull Ambiance ambiance, @NotNull CommandStepParameters commandStepParameters) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    String serviceIdentifier = serviceOutcome.getIdentifier();
    String hostname = getHost(commandStepParameters);

    if (isCopyCommandUnitIncludedInStep(commandStepParameters, CommandUnitSourceType.Artifact)) {
      saveCopyArtifactCommandUnitRollbackInfo(ambiance, commandStepParameters, serviceIdentifier, hostname);
    }

    if (isCopyCommandUnitIncludedInStep(commandStepParameters, CommandUnitSourceType.Config)) {
      saveCopyConfigCommandUnitDeploymentInfo(ambiance, commandStepParameters, serviceIdentifier, hostname);
    }
  }

  private void saveCopyArtifactCommandUnitRollbackInfo(
      Ambiance ambiance, CommandStepParameters commandStepParameters, String serviceIdentifier, String hostname) {
    Optional<String> artifactDestPath =
        getCopyCommandUnitDestinationPath(commandStepParameters, CommandUnitSourceType.Artifact);
    Optional<ArtifactOutcome> artifactOutcomeOpt = resolveArtifactsOutcome(ambiance);
    if (artifactDestPath.isPresent() && artifactOutcomeOpt.isPresent()) {
      SshWinRmArtifactDelegateConfig artifactDelegateConfigConfig =
          sshEntityHelper.getArtifactDelegateConfigConfig(artifactOutcomeOpt.get(), ambiance);
      CopyArtifactUnitRollbackDeploymentInfoKey rollbackDeploymentInfoKey =
          CopyArtifactUnitRollbackDeploymentInfoKey.builder()
              .serviceId(serviceIdentifier)
              .hostname(hostname)
              .hostArtifactDst(artifactDestPath.get())
              .build();
      CopyArtifactUnitRollbackDeploymentInfo artifactUnitRollbackDeploymentInfo =
          CopyArtifactUnitRollbackDeploymentInfo.builder().artifactDelegateConfig(artifactDelegateConfigConfig).build();

      rollbackDataService.save(RollbackData.builder()
                                   .key(rollbackDeploymentInfoKey.getKey())
                                   .value(artifactUnitRollbackDeploymentInfo)
                                   .stageExecutionId(ambiance.getStageExecutionId())
                                   .stageStatus(StageStatus.IN_PROGRESS)
                                   .build());
    }
  }

  private void saveCopyConfigCommandUnitDeploymentInfo(
      Ambiance ambiance, CommandStepParameters commandStepParameters, String serviceIdentifier, String hostname) {
    Optional<ConfigFilesOutcome> configFilesOutcomeOpt = getConfigFilesOutcome(ambiance);
    Optional<String> configsDestPath =
        getCopyCommandUnitDestinationPath(commandStepParameters, CommandUnitSourceType.Config);
    if (configsDestPath.isPresent() && configFilesOutcomeOpt.isPresent()) {
      FileDelegateConfig fileDelegateConfig =
          sshEntityHelper.getFileDelegateConfig(configFilesOutcomeOpt.get(), ambiance);
      CopyConfigUnitRollbackDeploymentInfoKey rollbackDeploymentInfoKey =
          CopyConfigUnitRollbackDeploymentInfoKey.builder()
              .serviceId(serviceIdentifier)
              .hostname(hostname)
              .hostConfigDst(configsDestPath.get())
              .build();
      CopyConfigUnitRollbackDeploymentInfo configUnitRollbackDeploymentInfo =
          CopyConfigUnitRollbackDeploymentInfo.builder().fileDelegateConfig(fileDelegateConfig).build();

      rollbackDataService.save(RollbackData.builder()
                                   .key(rollbackDeploymentInfoKey.getKey())
                                   .value(configUnitRollbackDeploymentInfo)
                                   .stageExecutionId(ambiance.getStageExecutionId())
                                   .stageStatus(StageStatus.IN_PROGRESS)
                                   .build());
    }
  }

  public Optional<CopyArtifactUnitRollbackDeploymentInfo> getCopyArtifactCommandUnitRollbackInfo(
      @NotNull Ambiance ambiance, @NotNull CommandStepParameters commandStepParameters) {
    if (!isCopyCommandUnitIncludedInStep(commandStepParameters, CommandUnitSourceType.Artifact)) {
      return Optional.empty();
    }

    Optional<String> artifactDestPathOpt =
        getCopyCommandUnitDestinationPath(commandStepParameters, CommandUnitSourceType.Artifact);
    if (!artifactDestPathOpt.isPresent()) {
      return Optional.empty();
    }

    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    String serviceIdentifier = serviceOutcome.getIdentifier();
    String hostname = getHost(commandStepParameters);
    String artifactDestPath = artifactDestPathOpt.get();

    CopyArtifactUnitRollbackDeploymentInfoKey rollbackDeploymentInfoKey =
        CopyArtifactUnitRollbackDeploymentInfoKey.builder()
            .serviceId(serviceIdentifier)
            .hostname(hostname)
            .hostArtifactDst(artifactDestPath)
            .build();

    return rollbackDataService.getLatestRollbackData(rollbackDeploymentInfoKey.getKey(), StageStatus.SUCCEEDED)
        .map(RollbackData::getValue)
        .map(CopyArtifactUnitRollbackDeploymentInfo.class ::cast);
  }

  public Optional<CopyConfigUnitRollbackDeploymentInfo> getCopyConfigCommandUnitRollbackInfo(
      @NotNull Ambiance ambiance, @NotNull CommandStepParameters commandStepParameters) {
    if (!isCopyCommandUnitIncludedInStep(commandStepParameters, CommandUnitSourceType.Config)) {
      return Optional.empty();
    }

    Optional<String> configDestPathOpt =
        getCopyCommandUnitDestinationPath(commandStepParameters, CommandUnitSourceType.Config);
    if (!configDestPathOpt.isPresent()) {
      return Optional.empty();
    }

    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    String serviceIdentifier = serviceOutcome.getIdentifier();
    String hostname = getHost(commandStepParameters);
    String configDestPath = configDestPathOpt.get();

    CopyConfigUnitRollbackDeploymentInfoKey rollbackDeploymentInfoKey =
        CopyConfigUnitRollbackDeploymentInfoKey.builder()
            .serviceId(serviceIdentifier)
            .hostname(hostname)
            .hostConfigDst(configDestPath)
            .build();

    return rollbackDataService.getLatestRollbackData(rollbackDeploymentInfoKey.getKey(), StageStatus.SUCCEEDED)
        .map(RollbackData::getValue)
        .map(CopyConfigUnitRollbackDeploymentInfo.class ::cast);
  }

  private boolean isCopyCommandUnitIncludedInStep(
      @NotNull CommandStepParameters commandStepParameters, CommandUnitSourceType sourceType) {
    if (commandStepParameters == null) {
      throw new InvalidArgumentsException("CommandStepParameters cannot be null");
    }

    return commandStepParameters.getCommandUnits()
        .stream()
        .map(CommandUnitWrapper::getCommandUnit)
        .map(StepCommandUnit::getSpec)
        .filter(spec -> spec instanceof CopyCommandUnitSpec)
        .map(CopyCommandUnitSpec.class ::cast)
        .map(CopyCommandUnitSpec::getSourceType)
        .map(CommandUnitSourceType::getFileSourceType)
        .anyMatch(fileSourceType -> fileSourceType == sourceType.getFileSourceType());
  }

  private Optional<String> getCopyCommandUnitDestinationPath(
      @NotNull CommandStepParameters commandStepParameters, CommandUnitSourceType sourceType) {
    if (commandStepParameters == null) {
      throw new InvalidArgumentsException("CommandStepParameters cannot be null");
    }

    return commandStepParameters.getCommandUnits()
        .stream()
        .map(CommandUnitWrapper::getCommandUnit)
        .map(StepCommandUnit::getSpec)
        .filter(spec -> spec instanceof CopyCommandUnitSpec)
        .map(CopyCommandUnitSpec.class ::cast)
        .filter(copyCommandUnitSpec
            -> copyCommandUnitSpec.getSourceType() != null
                && copyCommandUnitSpec.getSourceType().getFileSourceType() == sourceType.getFileSourceType())
        .map(CopyCommandUnitSpec::getDestinationPath)
        .map(ParameterField::getValue)
        .findFirst();
  }
}
