/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.execution.ExecutionInfoUtility.getScope;
import static io.harness.cdng.ssh.utils.CommandStepUtils.getHost;
import static io.harness.cdng.ssh.utils.CommandStepUtils.getOutputVariables;
import static io.harness.cdng.ssh.utils.CommandStepUtils.getWorkingDirectory;
import static io.harness.cdng.ssh.utils.CommandStepUtils.mergeEnvironmentVariables;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.utils.IdentifierRefHelper.getIdentifierRef;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceOutcomeHelper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.rollback.CommandStepRollbackHelper;
import io.harness.cdng.ssh.rollback.SshWinRmRollbackData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
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
import io.harness.eraro.Level;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SkipRollbackException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.models.Secret;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.secretmanagerclient.SecretType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;
import io.harness.steps.shellscript.WinRmInfraDelegateConfigOutput;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class SshCommandStepHelper extends CDStepHelper {
  private static final Pattern SECRETS_GET_VALUE_PATTERN = Pattern.compile("\\<\\+secrets\\.getValue\\(\"(.*?)\"\\)");

  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CommandStepRollbackHelper commandStepRollbackHelper;
  @Inject private SshWinRmConfigFileHelper sshWinRmConfigFileHelper;
  @Inject private SshWinRmArtifactHelper sshWinRmArtifactHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private NGSecretServiceV2 ngSecretServiceV2;

  public CommandTaskParameters buildCommandTaskParameters(
      @Nonnull Ambiance ambiance, @Nonnull CommandStepParameters commandStepParameters) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    InfrastructureOutcome infrastructure = getInfrastructureOutcome(ambiance);
    Map<String, String> mergedEnvVariables = getMergedEnvVariablesMap(ambiance, commandStepParameters, infrastructure);
    switch (serviceOutcome.getType()) {
      case ServiceSpecType.SSH:
        return buildSshCommandTaskParameters(ambiance, commandStepParameters, mergedEnvVariables);
      case ServiceSpecType.WINRM:
        return buildWinRmTaskParameters(ambiance, commandStepParameters, mergedEnvVariables);
      default:
        throw new UnsupportedOperationException(
            format("Unsupported service type: [%s] selected for command step", serviceOutcome.getType()));
    }
  }

  public StepResponse handleTaskException(Ambiance ambiance, StepElementParameters stepElementParameters, Exception e)
      throws Exception {
    // Trying to figure out if exception is coming from command task or it is an exception from delegate service.
    // In the second case we need to close log stream and provide unit progress data as part of response
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }
    CommandStepParameters executeCommandStepParameters = (CommandStepParameters) stepElementParameters.getSpec();
    List<UnitProgress> commandExecutionUnits =
        executeCommandStepParameters.getCommandUnits()
            .stream()
            .map(cu -> UnitProgress.newBuilder().setUnitName(cu.getName()).setStatus(UnitStatus.RUNNING).build())
            .collect(Collectors.toList());

    UnitProgressData currentUnitProgressData = UnitProgressData.builder().unitProgresses(commandExecutionUnits).build();
    UnitProgressData unitProgressData =
        completeUnitProgressData(currentUnitProgressData, ambiance, ExceptionUtils.getMessage(e));
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(emptyIfNull(ExceptionUtils.getMessage(e)))
                                  .build();

    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  private SshCommandTaskParameters buildSshCommandTaskParameters(
      Ambiance ambiance, CommandStepParameters commandStepParameters, Map<String, String> mergedEnvVariables) {
    OptionalSweepingOutput optionalInfraOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    if (!optionalInfraOutput.isFound()) {
      throw new InvalidRequestException("No infrastructure output found.");
    }
    // Rollback Logic
    // Get the rollback data from the latest successful deployment, getting it from DB.
    // If there are no rollback data, use the artifact and config files from the current deployment (the same in CG)
    SshInfraDelegateConfigOutput delegateConfig = (SshInfraDelegateConfigOutput) optionalInfraOutput.getOutput();
    if (commandStepParameters.isRollback) {
      return createRollbackSshTaskParameters(ambiance, commandStepParameters, mergedEnvVariables, delegateConfig);
    } else {
      return createSshTaskParameters(ambiance, commandStepParameters, mergedEnvVariables, delegateConfig);
    }
  }

  private WinrmTaskParameters buildWinRmTaskParameters(
      Ambiance ambiance, CommandStepParameters commandStepParameters, Map<String, String> mergedEnvVariables) {
    OptionalSweepingOutput optionalInfraOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
    if (!optionalInfraOutput.isFound()) {
      throw new InvalidRequestException("No infrastructure output found.");
    }

    // Rollback Logic
    // Get the rollback data from the latest successful deployment, getting it from DB.
    // If there are no rollback data, use the artifact and config files from the current deployment (the same in CG)
    WinRmInfraDelegateConfigOutput delegateConfig = (WinRmInfraDelegateConfigOutput) optionalInfraOutput.getOutput();
    if (commandStepParameters.isRollback) {
      return createRollbackWinRmTaskParameters(ambiance, commandStepParameters, mergedEnvVariables, delegateConfig);
    } else {
      return createWinRmTaskParameters(ambiance, commandStepParameters, mergedEnvVariables, delegateConfig);
    }
  }

  private Map<String, String> getMergedEnvVariablesMap(
      Ambiance ambiance, CommandStepParameters commandStepParameters, InfrastructureOutcome infrastructure) {
    LinkedHashMap<String, Object> evaluatedStageVariables =
        cdExpressionResolver.evaluateExpression(ambiance, "<+stage.variables>", LinkedHashMap.class);

    Map<String, String> finalEnvVariables = new HashMap<>();
    finalEnvVariables = mergeEnvironmentVariables(evaluatedStageVariables, finalEnvVariables);
    finalEnvVariables = mergeEnvironmentVariables(getServiceVariables(ambiance), finalEnvVariables);
    finalEnvVariables = mergeEnvironmentVariables(infrastructure.getEnvironment().getVariables(), finalEnvVariables);
    return mergeEnvironmentVariables(commandStepParameters.getEnvironmentVariables(), finalEnvVariables);
  }

  private HashMap<String, Object> getServiceVariables(Ambiance ambiance) {
    HashMap<String, Object> serviceVariablesMap = new HashMap<>();
    VariablesSweepingOutput serviceVariablesOutput = ServiceOutcomeHelper.getVariablesSweepingOutput(
        ambiance, executionSweepingOutputService, YAMLFieldNameConstants.SERVICE_VARIABLES);

    serviceVariablesOutput.entrySet().stream().filter(e -> e.getValue() instanceof String).forEach(entry -> {
      String value = (String) entry.getValue();

      if (EngineExpressionEvaluator.hasExpressions(value)) {
        serviceVariablesMap.putAll(evaluateExpression(ambiance, entry, value));
      } else {
        serviceVariablesMap.put(entry.getKey(), value);
      }
    });
    return serviceVariablesMap;
  }

  private HashMap<String, Object> evaluateExpression(Ambiance ambiance, Map.Entry<String, Object> entry, String value) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    Optional<String> scopedIdentifier = extractIdentifier(value);
    HashMap<String, Object> serviceVariablesMap = new HashMap<>();
    if (scopedIdentifier.isPresent()) {
      Secret secret =
          ngSecretServiceV2.get(getIdentifierRef(scopedIdentifier.get(), accountId, orgIdentifier, projectIdentifier))
              .orElseThrow(
                  () -> new EntityNotFoundException(format("Secret with id: %s not found.", scopedIdentifier.get())));

      if (!secret.getType().equals(SecretType.SecretFile)) {
        serviceVariablesMap.put(entry.getKey(), cdExpressionResolver.renderExpression(ambiance, value));
      }
    }
    return serviceVariablesMap;
  }

  private Optional<String> extractIdentifier(String expression) {
    if (isEmpty(expression)) {
      return Optional.empty();
    }
    Matcher matcher = SECRETS_GET_VALUE_PATTERN.matcher(expression);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  private SshCommandTaskParameters createSshTaskParameters(Ambiance ambiance,
      CommandStepParameters commandStepParameters, Map<String, String> mergedEnvVariables,
      SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput) {
    commandStepRollbackHelper.updateRollbackData(getScope(ambiance), ambiance.getStageExecutionId(),
        commandStepParameters.getEnvironmentVariables(), commandStepParameters.getOutputVariables());

    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    return SshCommandTaskParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVariables(commandStepParameters.getOutputVariables()))
        .environmentVariables(mergedEnvVariables)
        .sshInfraDelegateConfig(sshInfraDelegateConfigOutput.getSshInfraDelegateConfig())
        .artifactDelegateConfig(getArtifactDelegateConfig(ambiance))
        .fileDelegateConfig(getFileDelegateConfig(ambiance))
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(onDelegate ? null : getHost(commandStepParameters))
        .build();
  }

  private SshCommandTaskParameters createRollbackSshTaskParameters(Ambiance ambiance,
      CommandStepParameters commandStepParameters, Map<String, String> mergedEnvVariables,
      SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput) {
    SshWinRmRollbackData sshWinRmRollbackData = getSshWinRmRollbackData(ambiance, mergedEnvVariables);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    return SshCommandTaskParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(sshWinRmRollbackData.getOutVariables())
        .environmentVariables(sshWinRmRollbackData.getEnvVariables())
        .sshInfraDelegateConfig(sshInfraDelegateConfigOutput.getSshInfraDelegateConfig())
        .artifactDelegateConfig(sshWinRmRollbackData.getArtifactDelegateConfig())
        .fileDelegateConfig(sshWinRmRollbackData.getFileDelegateConfig())
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(onDelegate ? null : getHost(commandStepParameters))
        .build();
  }

  private WinrmTaskParameters createWinRmTaskParameters(Ambiance ambiance, CommandStepParameters commandStepParameters,
      Map<String, String> mergedEnvVariables, WinRmInfraDelegateConfigOutput winRmInfraDelegateConfigOutput) {
    commandStepRollbackHelper.updateRollbackData(getScope(ambiance), ambiance.getStageExecutionId(),
        commandStepParameters.getEnvironmentVariables(), commandStepParameters.getOutputVariables());
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    return WinrmTaskParameters.builder()
        .accountId(accountId)
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(getOutputVariables(commandStepParameters.getOutputVariables()))
        .environmentVariables(mergedEnvVariables)
        .winRmInfraDelegateConfig(winRmInfraDelegateConfigOutput.getWinRmInfraDelegateConfig())
        .artifactDelegateConfig(getArtifactDelegateConfig(ambiance))
        .fileDelegateConfig(getFileDelegateConfig(ambiance))
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(onDelegate ? null : getHost(commandStepParameters))
        .useWinRMKerberosUniqueCacheFile(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE))
        .disableWinRMCommandEncodingFFSet(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.DISABLE_WINRM_COMMAND_ENCODING))
        .build();
  }

  private WinrmTaskParameters createRollbackWinRmTaskParameters(Ambiance ambiance,
      CommandStepParameters commandStepParameters, Map<String, String> mergedEnvVariables,
      WinRmInfraDelegateConfigOutput winRmInfraDelegateConfigOutput) {
    SshWinRmRollbackData sshWinRmRollbackData = getSshWinRmRollbackData(ambiance, mergedEnvVariables);
    Boolean onDelegate = getBooleanParameterFieldValue(commandStepParameters.onDelegate);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    return WinrmTaskParameters.builder()
        .accountId(accountId)
        .executeOnDelegate(onDelegate)
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVariables(sshWinRmRollbackData.getOutVariables())
        .environmentVariables(sshWinRmRollbackData.getEnvVariables())
        .winRmInfraDelegateConfig(winRmInfraDelegateConfigOutput.getWinRmInfraDelegateConfig())
        .artifactDelegateConfig(sshWinRmRollbackData.getArtifactDelegateConfig())
        .fileDelegateConfig(sshWinRmRollbackData.getFileDelegateConfig())
        .commandUnits(mapCommandUnits(commandStepParameters.getCommandUnits(), onDelegate))
        .host(onDelegate ? null : getHost(commandStepParameters))
        .useWinRMKerberosUniqueCacheFile(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE))
        .disableWinRMCommandEncodingFFSet(
            cdFeatureFlagHelper.isEnabled(accountId, FeatureName.DISABLE_WINRM_COMMAND_ENCODING))
        .build();
  }

  private SshWinRmRollbackData getSshWinRmRollbackData(Ambiance ambiance, Map<String, String> mergedEnvVariables) {
    String stageExecutionId = ambiance.getStageExecutionId();
    log.info("Start getting rollback data from DB, stageExecutionId: {}", stageExecutionId);
    Optional<SshWinRmRollbackData> rollbackData =
        commandStepRollbackHelper.getRollbackData(ambiance, mergedEnvVariables);
    if (!rollbackData.isPresent()) {
      log.info("Not found rollback data from DB, hence skipping rollback, stageExecutionId: {}", stageExecutionId);
      throw new SkipRollbackException("Not found previous successful rollback data, hence skipping rollback");
    }

    log.info("Found rollback data in DB, stageExecutionId: {}", stageExecutionId);
    return rollbackData.get();
  }

  @Nullable
  private SshWinRmArtifactDelegateConfig getArtifactDelegateConfig(@NotNull Ambiance ambiance) {
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    return artifactOutcome.map(outcome -> sshWinRmArtifactHelper.getArtifactDelegateConfigConfig(outcome, ambiance))
        .orElse(null);
  }

  @Nullable
  private FileDelegateConfig getFileDelegateConfig(@NotNull Ambiance ambiance) {
    Optional<ConfigFilesOutcome> configFilesOutcomeOptional = getConfigFilesOutcome(ambiance);
    return configFilesOutcomeOptional
        .map(configFilesOutcome -> sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance))
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
                -> (stepCommandUnit.isScript()) ? mapScriptCommandUnit(stepCommandUnit, onDelegate)
                                                : mapCopyCommandUnit(stepCommandUnit))
            .collect(Collectors.toList());

    commandUnits.addAll(commandUnitsFromStep);
    commandUnits.add(NgCleanupCommandUnit.builder().build());
    return commandUnits;
  }

  private ScriptCommandUnit mapScriptCommandUnit(CommandUnitWrapper stepCommandUnit, boolean onDelegate) {
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

  private CopyCommandUnit mapCopyCommandUnit(CommandUnitWrapper stepCommandUnit) {
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

  public Map<String, String> prepareOutputVariables(
      Map<String, String> sweepingOutputEnvVariables, Map<String, Object> outputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables) || EmptyPredicate.isEmpty(sweepingOutputEnvVariables)) {
      return Collections.EMPTY_MAP;
    }

    Map<String, String> resolvedOutputVariables = new HashMap<>();
    outputVariables.keySet().forEach(name -> {
      Object value = ((ParameterField<?>) outputVariables.get(name)).getValue();
      resolvedOutputVariables.put(name, sweepingOutputEnvVariables.get(value));
    });
    return resolvedOutputVariables;
  }
}
