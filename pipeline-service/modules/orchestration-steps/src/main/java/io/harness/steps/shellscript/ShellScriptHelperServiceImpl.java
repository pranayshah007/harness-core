/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder;
import io.harness.delegate.task.shell.WinRmShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.WinRmShellScriptTaskParametersNG.WinRmShellScriptTaskParametersNGBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.k8s.K8sConstants;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@Slf4j
public class ShellScriptHelperServiceImpl implements ShellScriptHelperService {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretManagerClient;
  @Inject private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Inject private WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  @Inject private ShellScriptHelperService shellScriptHelperService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private NGSettingsClient settingsClient;

  @Inject private EngineExpressionService engineExpressionService;

  @Override
  public Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables, Ambiance ambiance) {
    Map<String, String> res = new LinkedHashMap<>();
    Map<String, Object> copiedInputVariables = new HashMap<>();

    if (isExportServiceVarsAsEnvVarsEnabled(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance))) {
      copiedInputVariables.putAll(getEnvironmentVariablesFromServiceVars(ambiance));
    }
    if (EmptyPredicate.isNotEmpty(inputVariables)) {
      copiedInputVariables.putAll(inputVariables);
    }

    copiedInputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.fetchFinalValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.fetchFinalValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });

    engineExpressionService.resolve(
        ambiance, res, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED, new HashMap<>());

    // check for unresolved harness expressions
    StringBuilder unresolvedInputVariables = new StringBuilder();
    res.forEach((key, value) -> {
      if (EngineExpressionEvaluator.hasExpressions(value)) {
        unresolvedInputVariables.append(key).append(", ");
      }
    });

    // Remove the trailing comma and whitespace, if any
    if (unresolvedInputVariables.length() > 0) {
      unresolvedInputVariables.setLength(unresolvedInputVariables.length() - 2);
      throw new InvalidRequestException(
          String.format("Env. variables: [%s] found to be unresolved", unresolvedInputVariables));
    }

    return res;
  }

  private Map<String, Object> getEnvironmentVariablesFromServiceVars(Ambiance ambiance) {
    Map<String, Object> variables = new LinkedHashMap<>();

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES));

    if (optionalSweepingOutput.isFound()) {
      VariablesSweepingOutput variablesSweepingOutput = (VariablesSweepingOutput) optionalSweepingOutput.getOutput();

      if (EmptyPredicate.isNotEmpty(variablesSweepingOutput)) {
        variables.putAll(variablesSweepingOutput);
      }
    }
    return variables;
  }

  @Override
  public List<String> getOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }
    // secret variables are stored separately so ignoring them
    List<String> outputVars = new ArrayList<>();
    outputVariables.forEach((key, val) -> {
      if (EmptyPredicate.isEmpty(secretOutputVariables) || !secretOutputVariables.contains(key)) {
        if (val instanceof ParameterField) {
          ParameterField<?> parameterFieldValue = (ParameterField<?>) val;
          if (parameterFieldValue.getValue() == null) {
            throw new InvalidRequestException(String.format("Output variable [%s] value found to be empty", key));
          }
          outputVars.add(((ParameterField<?>) val).getValue().toString());
        } else if (val instanceof String) {
          outputVars.add((String) val);
        } else {
          log.error(String.format(
              "Value other than String or ParameterField found for output variable [%s]. value: [%s]", key, val));
        }
      }
    });
    return outputVars;
  }

  @Override
  public K8sInfraDelegateConfig getK8sInfraDelegateConfig(@Nonnull Ambiance ambiance, @Nonnull String shellScript) {
    if (shellScript.contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH)) {
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
          RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
      if (optionalSweepingOutput.isFound()) {
        K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput =
            (K8sInfraDelegateConfigOutput) optionalSweepingOutput.getOutput();
        return k8sInfraDelegateConfigOutput.getK8sInfraDelegateConfig();
      }
    }
    return null;
  }

  @Override
  public void prepareTaskParametersForExecutionTarget(@Nonnull Ambiance ambiance,
      @Nonnull ShellScriptStepParameters shellScriptStepParameters,
      @Nonnull ShellScriptTaskParametersNGBuilder taskParametersNGBuilder) {
    if (!shellScriptStepParameters.onDelegate.getValue()) {
      ExecutionTarget executionTarget = shellScriptStepParameters.getExecutionTarget();
      validateExecutionTarget(executionTarget);
      SSHKeySpecDTO secretSpec = (SSHKeySpecDTO) getSshKeySpec(ambiance, executionTarget);
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      List<EncryptedDataDetail> sshKeyEncryptionDetails =
          sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(secretSpec, ngAccess);

      taskParametersNGBuilder.sshKeySpecDTO(secretSpec)
          .encryptionDetails(sshKeyEncryptionDetails)
          .host(executionTarget.getHost().getValue());
    }
  }

  private void validateExecutionTarget(ExecutionTarget executionTarget) {
    if (executionTarget == null) {
      throw new InvalidRequestException("Execution Target can't be empty with on delegate set to false");
    }
    if (ParameterField.isNull(executionTarget.getConnectorRef())
        || StringUtils.isEmpty(executionTarget.getConnectorRef().getValue())) {
      throw new InvalidRequestException("Connector Ref in Execution Target can't be empty");
    }
    if (ParameterField.isNull(executionTarget.getHost()) || StringUtils.isEmpty(executionTarget.getHost().getValue())) {
      throw new InvalidRequestException("Host in Execution Target can't be empty");
    }
  }

  @Override
  public String getShellScript(@Nonnull ShellScriptStepParameters stepParameters) {
    ShellScriptInlineSource shellScriptInlineSource = (ShellScriptInlineSource) stepParameters.getSource().getSpec();
    return (String) shellScriptInlineSource.getScript().fetchFinalValue();
  }

  @Override
  public String getWorkingDirectory(
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

  private boolean isExportServiceVarsAsEnvVarsEnabled(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(SettingIdentifiers.EXPORT_SERVICE_VARS_AS_ENV_VARS,
                                accountIdentifier, orgIdentifier, projectIdentifier))
                            .getValue());
  }

  @Override
  public TaskParameters buildShellScriptTaskParametersNG(
      @Nonnull Ambiance ambiance, @Nonnull ShellScriptStepParameters shellScriptStepParameters) {
    SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
    ScriptType scriptType = shellScriptStepParameters.getShell().getScriptType();
    String shellScript = shellScriptHelperService.getShellScript(shellScriptStepParameters);
    ParameterField<String> workingDirectory = (shellScriptStepParameters.getExecutionTarget() != null)
        ? shellScriptStepParameters.getExecutionTarget().getWorkingDirectory()
        : ParameterField.ofNull();

    if (ScriptType.BASH.equals(scriptType)) {
      return buildBashTaskParametersNG(ambiance, shellScriptStepParameters, scriptType, shellScript, workingDirectory);
    } else {
      return getWinRmTaskParametersNG(ambiance, shellScriptStepParameters, scriptType, shellScript, workingDirectory);
    }
  }

  private WinRmShellScriptTaskParametersNG getWinRmTaskParametersNG(@NotNull Ambiance ambiance,
      @NotNull ShellScriptStepParameters shellScriptStepParameters, ScriptType scriptType, String shellScript,
      ParameterField<String> workingDirectory) {
    WinRmShellScriptTaskParametersNGBuilder taskParametersNGBuilder = WinRmShellScriptTaskParametersNG.builder();

    taskParametersNGBuilder.k8sInfraDelegateConfig(
        shellScriptHelperService.getK8sInfraDelegateConfig(ambiance, shellScript));

    if (!shellScriptStepParameters.onDelegate.getValue()) {
      ExecutionTarget executionTarget = shellScriptStepParameters.getExecutionTarget();
      validateExecutionTarget(executionTarget);
      SecretSpecDTO secretSpec = getSshKeySpec(ambiance, executionTarget);
      final List<EncryptedDataDetail> encryptionDetails = winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(
          (WinRmCredentialsSpecDTO) secretSpec, AmbianceUtils.getNgAccess(ambiance));

      taskParametersNGBuilder.sshKeySpecDTO(secretSpec)
          .encryptionDetails(encryptionDetails)
          .host(executionTarget.getHost().getValue());
    }

    taskParametersNGBuilder
        .useWinRMKerberosUniqueCacheFile(pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE))
        .disableCommandEncoding(pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.DISABLE_WINRM_COMMAND_ENCODING_NG))
        .winrmScriptCommandSplit(pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.WINRM_SCRIPT_COMMAND_SPLIT_NG));

    return taskParametersNGBuilder.accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(shellScriptStepParameters.onDelegate.getValue())
        .environmentVariables(shellScriptHelperService.getEnvironmentVariables(
            shellScriptStepParameters.getEnvironmentVariables(), ambiance))
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVars(shellScriptHelperService.getOutputVars(
            shellScriptStepParameters.getOutputVariables(), shellScriptStepParameters.getSecretOutputVariables()))
        .secretOutputVars(shellScriptHelperService.getSecretOutputVars(
            shellScriptStepParameters.getOutputVariables(), shellScriptStepParameters.getSecretOutputVariables()))
        .script(shellScript)
        .scriptType(scriptType)
        .workingDirectory(shellScriptHelperService.getWorkingDirectory(
            workingDirectory, scriptType, shellScriptStepParameters.onDelegate.getValue()))
        .build();
  }

  private ShellScriptTaskParametersNG buildBashTaskParametersNG(@NotNull Ambiance ambiance,
      @NotNull ShellScriptStepParameters shellScriptStepParameters, ScriptType scriptType, String shellScript,
      ParameterField<String> workingDirectory) {
    ShellScriptTaskParametersNGBuilder taskParametersNGBuilder = ShellScriptTaskParametersNG.builder();

    taskParametersNGBuilder.k8sInfraDelegateConfig(
        shellScriptHelperService.getK8sInfraDelegateConfig(ambiance, shellScript));
    shellScriptHelperService.prepareTaskParametersForExecutionTarget(
        ambiance, shellScriptStepParameters, taskParametersNGBuilder);
    return taskParametersNGBuilder.accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(shellScriptStepParameters.onDelegate.getValue())
        .environmentVariables(shellScriptHelperService.getEnvironmentVariables(
            shellScriptStepParameters.getEnvironmentVariables(), ambiance))
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVars(shellScriptHelperService.getOutputVars(
            shellScriptStepParameters.getOutputVariables(), shellScriptStepParameters.getSecretOutputVariables()))
        .secretOutputVars(shellScriptHelperService.getSecretOutputVars(
            shellScriptStepParameters.getOutputVariables(), shellScriptStepParameters.getSecretOutputVariables()))
        .script(shellScript)
        .scriptType(scriptType)
        .workingDirectory(shellScriptHelperService.getWorkingDirectory(
            workingDirectory, scriptType, shellScriptStepParameters.onDelegate.getValue()))
        .build();
  }

  private SecretSpecDTO getSshKeySpec(Ambiance ambiance, ExecutionTarget executionTarget) {
    String sshKeyRef = executionTarget.getConnectorRef().getValue();

    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(sshKeyRef, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    String errorMSg = "No secret configured with identifier: " + sshKeyRef;
    SecretResponseWrapper secretResponseWrapper = NGRestUtils.getResponse(
        secretManagerClient.getSecret(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()),
        errorMSg);
    if (secretResponseWrapper == null) {
      throw new InvalidRequestException(errorMSg);
    }

    return secretResponseWrapper.getSecret().getSpec();
  }

  @Override
  public ShellScriptOutcome prepareShellScriptOutcome(
      Map<String, String> sweepingOutputEnvVariables, Map<String, Object> outputVariables) {
    if (outputVariables == null || sweepingOutputEnvVariables == null) {
      return null;
    }
    Map<String, String> resolvedOutputVariables = new HashMap<>();
    outputVariables.keySet().forEach(name -> {
      Object value = ((ParameterField<?>) outputVariables.get(name)).getValue();
      resolvedOutputVariables.put(name, sweepingOutputEnvVariables.get(value));
    });
    return ShellScriptOutcome.builder().outputVariables(resolvedOutputVariables).build();
  }

  @Override
  public List<String> getSecretOutputVars(Map<String, Object> outputVariables, Set<String> secretOutputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }
    // secret variables are stored separately so ignoring them
    List<String> outputVars = new ArrayList<>();
    outputVariables.forEach((key, val) -> {
      if (secretOutputVariables.contains(key)) {
        if (val instanceof ParameterField) {
          ParameterField<?> parameterFieldValue = (ParameterField<?>) val;
          if (parameterFieldValue.getValue() == null) {
            throw new InvalidRequestException(String.format("Output variable [%s] value found to be empty", key));
          }
          outputVars.add(((ParameterField<?>) val).getValue().toString());
        } else if (val instanceof String) {
          outputVars.add((String) val);
        } else {
          log.error(String.format(
              "Value other than String or ParameterField found for output variable [%s]. value: [%s]", key, val));
        }
      }
    });
    return outputVars;
  }
}
