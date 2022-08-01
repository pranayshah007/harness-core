/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.utilities.SshWinRmUtility.getHost;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.rollback.copyartifact.CopyArtifactUnitRollbackDeploymentInfo;
import io.harness.cdng.ssh.rollback.copyconfig.CopyConfigUnitRollbackDeploymentInfo;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.shell.TailFilePatternDto;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.shell.ScriptType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;
import io.harness.steps.shellscript.WinRmInfraDelegateConfigOutput;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class SshCommandStepHelper extends CDStepHelper {
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CommandStepRollbackHelper commandStepRollbackHelper;

  public CommandTaskParameters buildCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    switch (serviceOutcome.getType()) {
      case ServiceSpecType.SSH:
        return buildSshCommandTaskParameters(ambiance, commandStepParameters);
      case ServiceSpecType.WINRM:
        return buildWinRmTaskParameters(ambiance, commandStepParameters);
      default:
        throw new UnsupportedOperationException(
            format("Unsupported service type: [%s] selected for command step", serviceOutcome.getType()));
    }
  }

  private SshCommandTaskParameters buildSshCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    OptionalSweepingOutput optionalInfraOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    if (!optionalInfraOutput.isFound()) {
      throw new InvalidRequestException("No infrastructure output found.");
    }
    SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput =
        (SshInfraDelegateConfigOutput) optionalInfraOutput.getOutput();

    SshWinRmArtifactDelegateConfig artifactDelegateConfig = getArtifactDelegateConfig(ambiance, commandStepParameters);
    FileDelegateConfig fileDelegateConfig = getFileDelegateConfig(ambiance, commandStepParameters);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    return SshCommandTaskParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVars(commandStepParameters.getOutputVariables()))
        .environmentVariables(getEnvironmentVariables(commandStepParameters.getEnvironmentVariables()))
        .sshInfraDelegateConfig(sshInfraDelegateConfigOutput.getSshInfraDelegateConfig())
        .artifactDelegateConfig(artifactDelegateConfig)
        .fileDelegateConfig(fileDelegateConfig)
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(getHost(commandStepParameters))
        .build();
  }

  private WinrmTaskParameters buildWinRmTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    OptionalSweepingOutput optionalInfraOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    if (!optionalInfraOutput.isFound()) {
      throw new InvalidRequestException("No infrastructure output found.");
    }
    WinRmInfraDelegateConfigOutput winRmInfraDelegateConfigOutput =
        (WinRmInfraDelegateConfigOutput) optionalInfraOutput.getOutput();

    SshWinRmArtifactDelegateConfig artifactDelegateConfig = getArtifactDelegateConfig(ambiance, commandStepParameters);
    FileDelegateConfig fileDelegateConfig = getFileDelegateConfig(ambiance, commandStepParameters);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    return WinrmTaskParameters.builder()
        .accountId(accountId)
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVars(commandStepParameters.getOutputVariables()))
        .environmentVariables(getEnvironmentVariables(commandStepParameters.getEnvironmentVariables()))
        .winRmInfraDelegateConfig(winRmInfraDelegateConfigOutput.getWinRmInfraDelegateConfig())
        .artifactDelegateConfig(artifactDelegateConfig)
        .fileDelegateConfig(fileDelegateConfig)
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(getHost(commandStepParameters))
        .useWinRMKerberosUniqueCacheFile(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE))
        .disableWinRMCommandEncodingFFSet(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.DISABLE_WINRM_COMMAND_ENCODING))
        .build();
  }

  @Nullable
  private SshWinRmArtifactDelegateConfig getArtifactDelegateConfig(
      @NotNull Ambiance ambiance, @NotNull CommandStepParameters commandStepParameters) {
    if (commandStepParameters.isRollback) {
      return commandStepRollbackHelper.getCopyArtifactCommandUnitRollbackInfo(ambiance, commandStepParameters)
          .map(CopyArtifactUnitRollbackDeploymentInfo::getArtifactDelegateConfig)
          .orElse(null);
    }

    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    return artifactOutcome.map(outcome -> sshEntityHelper.getArtifactDelegateConfigConfig(outcome, ambiance))
        .orElse(null);
  }

  @Nullable
  private FileDelegateConfig getFileDelegateConfig(
      @NotNull Ambiance ambiance, @NotNull CommandStepParameters commandStepParameters) {
    if (commandStepParameters.isRollback) {
      return commandStepRollbackHelper.getCopyConfigCommandUnitRollbackInfo(ambiance, commandStepParameters)
          .map(CopyConfigUnitRollbackDeploymentInfo::getFileDelegateConfig)
          .orElse(null);
    }

    Optional<ConfigFilesOutcome> configFilesOutcomeOptional = getConfigFilesOutcome(ambiance);
    return configFilesOutcomeOptional
        .map(configFilesOutcome -> sshEntityHelper.getFileDelegateConfig(configFilesOutcome, ambiance))
        .orElse(null);
  }

  private List<NgCommandUnit> mapCommandUnits(List<CommandUnitWrapper> stepCommandUnits, boolean onDelegate) {
    if (isEmpty(stepCommandUnits)) {
      throw new InvalidRequestException("No command units found for configured step");
    }
    List<NgCommandUnit> commandUnits = new ArrayList<>(stepCommandUnits.size() + 2);
    commandUnits.add(NgInitCommandUnit.builder().build());

    List<NgCommandUnit> commandUnitsFromStep =
        stepCommandUnits.stream()
            .map(stepCommandUnit
                -> (stepCommandUnit.isScript()) ? mapScriptCommandUnit(stepCommandUnit.getCommandUnit(), onDelegate)
                                                : mapCopyCommandUnit(stepCommandUnit.getCommandUnit()))
            .collect(Collectors.toList());

    commandUnits.addAll(commandUnitsFromStep);
    commandUnits.add(NgCleanupCommandUnit.builder().build());
    return commandUnits;
  }

  private ScriptCommandUnit mapScriptCommandUnit(StepCommandUnit stepCommandUnit, boolean onDelegate) {
    if (stepCommandUnit == null) {
      throw new InvalidRequestException("Invalid command unit format specified");
    }

    if (!(stepCommandUnit.getSpec() instanceof ScriptCommandUnitSpec)) {
      throw new InvalidRequestException("Invalid script command unit specified");
    }

    ScriptCommandUnitSpec spec = (ScriptCommandUnitSpec) stepCommandUnit.getSpec();
    return ScriptCommandUnit.builder()
        .name(stepCommandUnit.getName())
        .script(getShellScript(spec.getSource()))
        .scriptType(spec.getShell().getScriptType())
        .tailFilePatterns(mapTailFilePatterns(spec.getTailFiles()))
        .workingDirectory(getWorkingDirectory(spec.getWorkingDirectory(), spec.getShell().getScriptType(), onDelegate))
        .build();
  }

  private CopyCommandUnit mapCopyCommandUnit(StepCommandUnit stepCommandUnit) {
    if (stepCommandUnit == null) {
      throw new InvalidRequestException("Invalid command unit format specified");
    }

    if (!(stepCommandUnit.getSpec() instanceof CopyCommandUnitSpec)) {
      throw new InvalidRequestException("Invalid copy command unit specified");
    }

    CopyCommandUnitSpec spec = (CopyCommandUnitSpec) stepCommandUnit.getSpec();
    return CopyCommandUnit.builder()
        .name(stepCommandUnit.getName())
        .sourceType(spec.getSourceType().getFileSourceType())
        .destinationPath(getParameterFieldValue(spec.getDestinationPath()))
        .build();
  }

  private List<TailFilePatternDto> mapTailFilePatterns(@Nonnull List<TailFilePattern> tailFiles) {
    if (isEmpty(tailFiles)) {
      return emptyList();
    }

    return tailFiles.stream()
        .map(it
            -> TailFilePatternDto.builder()
                   .filePath(getParameterFieldValue(it.getTailFile()))
                   .pattern(getParameterFieldValue(it.getTailPattern()))
                   .build())
        .collect(Collectors.toList());
  }

  private String getShellScript(@Nonnull ShellScriptSourceWrapper shellScriptSourceWrapper) {
    ShellScriptInlineSource shellScriptInlineSource = (ShellScriptInlineSource) shellScriptSourceWrapper.getSpec();
    return (String) shellScriptInlineSource.getScript().fetchFinalValue();
  }

  Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  String getWorkingDirectory(
      ParameterField<String> workingDirectory, @Nonnull ScriptType scriptType, boolean onDelegate) {
    if (workingDirectory != null && EmptyPredicate.isNotEmpty(workingDirectory.getValue())) {
      return workingDirectory.getValue();
    }
    String commandPath = null;
    if (scriptType == ScriptType.BASH) {
      commandPath = "/tmp";
    } else if (scriptType == ScriptType.POWERSHELL) {
      commandPath = "%TEMP%";
      if (onDelegate) {
        commandPath = "/tmp";
      }
    }
    return commandPath;
  }

  List<String> getOutputVars(Map<String, Object> outputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }

    List<String> outputVars = new ArrayList<>();
    outputVariables.forEach((key, val) -> {
      if (val instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) val;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Output variable [%s] value found to be null", key));
        }
        outputVars.add(((ParameterField<?>) val).getValue().toString());
      } else if (val instanceof String) {
        outputVars.add((String) val);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for output variable [%s]. value: [%s]", key, val));
      }
    });
    return outputVars;
  }
}
