/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.annotations.dev.HarnessModule._440_SECRET_MANAGEMENT_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.ConnectionType.SSH;
import static software.wings.beans.ConnectionType.WINRM;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.shell.ScriptType;

import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.beans.delegation.ShellScriptParameters.ShellScriptParametersBuilder;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.google.common.collect.Sets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@UtilityClass
@TargetModule(_440_SECRET_MANAGEMENT_SERVICE)
public class CustomSecretsManagerValidationUtils {
  static final String OUTPUT_VARIABLE = "secret";

  void validateName(@NotEmpty String name) {
    Pattern nameValidator = Pattern.compile("^[0-9a-zA-Z-' !_]+$");

    if (isEmpty(name) || !nameValidator.matcher(name).find()) {
      String message =
          "Name cannot be empty and can only have alphanumeric, hyphen, single inverted comma, space and exclamation mark characters.";
      throw new InvalidArgumentsException(message, USER);
    }
  }

  void validateVariables(
      @NonNull CustomSecretsManagerConfig customSecretsManagerConfig, @NonNull Set<EncryptedDataParams> testVariables) {
    Set<String> shellScriptVariables =
        new HashSet<>(customSecretsManagerConfig.getCustomSecretsManagerShellScript().getVariables());
    Set<String> receivedVariables =
        testVariables.stream().map(EncryptedDataParams::getName).collect(Collectors.toSet());
    Set<String> diff = Sets.difference(shellScriptVariables, receivedVariables);
    if (!diff.isEmpty()) {
      String message = String.format(
          "The values for the variables %s have not been provided as part of test parameters", String.join(", ", diff));
      throw new InvalidArgumentsException(message, USER);
    }
  }

  void validateConnectionAttributes(@NonNull CustomSecretsManagerConfig customSecretsManagerConfig) {
    if (isEmpty(customSecretsManagerConfig.getCommandPath())) {
      String message = "Command path for the custom secret manager cannot be empty";
      throw new InvalidArgumentsException(message, USER);
    }

    if (!customSecretsManagerConfig.isExecuteOnDelegate()) {
      if (isEmpty(customSecretsManagerConfig.getHost())) {
        String message = "Target host cannot be empty when the secret has to be retrieved from another system.";
        throw new InvalidArgumentsException(message, USER);
      }
    }
  }

  public static ShellScriptParameters buildShellScriptParameters(
      CustomSecretsManagerConfig customSecretsManagerConfig) {
    ShellScriptParametersBuilder shellScriptParametersBuilder =
        ShellScriptParameters.builder()
            .accountId(customSecretsManagerConfig.getAccountId())
            .host(customSecretsManagerConfig.getHost())
            .workingDirectory(customSecretsManagerConfig.getCommandPath())
            .scriptType(ScriptType.valueOf(
                customSecretsManagerConfig.getCustomSecretsManagerShellScript().getScriptType().name()))
            .script(customSecretsManagerConfig.getCustomSecretsManagerShellScript().getScriptString())
            .executeOnDelegate(customSecretsManagerConfig.isExecuteOnDelegate())
            .keyEncryptedDataDetails(new ArrayList<>())
            .winrmConnectionEncryptedDataDetails(new ArrayList<>())
            .activityId(UUIDGenerator.generateUuid())
            .appId(GLOBAL_APP_ID)
            .outputVars(OUTPUT_VARIABLE)
            .saveExecutionLogs(false);

    if (!customSecretsManagerConfig.isExecuteOnDelegate()) {
      if (customSecretsManagerConfig.getRemoteHostConnector().getSettingType() == HOST_CONNECTION_ATTRIBUTES) {
        HostConnectionAttributes hostConnectionAttributes =
            (HostConnectionAttributes) customSecretsManagerConfig.getRemoteHostConnector();
        shellScriptParametersBuilder.connectionType(SSH)
            .hostConnectionAttributes(hostConnectionAttributes)
            .userName(hostConnectionAttributes.getUserName())
            .keyless(hostConnectionAttributes.isKeyless())
            .keyPath(hostConnectionAttributes.getKeyPath())
            .port(hostConnectionAttributes.getSshPort())
            .accessType(hostConnectionAttributes.getAccessType())
            .authenticationScheme(hostConnectionAttributes.getAuthenticationScheme())
            .kerberosConfig(hostConnectionAttributes.getKerberosConfig());
      } else {
        WinRmConnectionAttributes winRmConnectionAttributes =
            (WinRmConnectionAttributes) customSecretsManagerConfig.getRemoteHostConnector();
        shellScriptParametersBuilder.connectionType(WINRM)
            .winrmConnectionAttributes(winRmConnectionAttributes)
            .userName(winRmConnectionAttributes.getUsername());
      }
    }
    return shellScriptParametersBuilder.build();
  }

  /*
  --account Id
  --executeOnDelegate
  environmentVariables
  executionId
  -- outputVars
  --script
  --scriptType
  --workingDirectory
  k8sInfraDelegateConfig

  taskParametersNGBuilder.accountId(AmbianceUtils.getAccountId(ambiance))
        .executeOnDelegate(shellScriptStepParameters.onDelegate.getValue())
        .environmentVariables(
            shellScriptHelperService.getEnvironmentVariables(shellScriptStepParameters.getEnvironmentVariables()))
        .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .outputVars(shellScriptHelperService.getOutputVars(shellScriptStepParameters.getOutputVariables()))
        .script(shellScript)
        .scriptType(scriptType)
        .workingDirectory(shellScriptHelperService.getWorkingDirectory(
            workingDirectory, scriptType, shellScriptStepParameters.onDelegate.getValue()))
        .build();
   */
  // All backend changes in PL-25548
  // UI up
  // Template will be up
  // Use yaml ( create via yaml ) - in db
  // test connection ( via postman ) with connector identifier ( of yaml in db )
  // Test connection --> debug
  public static ShellScriptTaskParametersNG buildShellScriptTaskParametersNG(
      String accountId, EncryptedRecord encryptedRecord, CustomSecretNGManagerConfig customSecretNGManagerConfig) {
    ScriptType scriptType = ScriptType.BASH;
    ShellScriptTaskParametersNG.ShellScriptTaskParametersNGBuilder taskParametersNGBuilder =
        ShellScriptTaskParametersNG.builder();
    String script = encryptedRecord.getParameters()
                        .stream()
                        .filter(encryptedDataParams -> encryptedDataParams.getName().equals("Script"))
                        .findFirst()
                        .get()
                        .getValue();
    /*
    taskParametersNGBuilder.k8sInfraDelegateConfig(
            shellScriptHelperService.getK8sInfraDelegateConfig(ambiance, shellScript));
    shellScriptHelperService.prepareTaskParametersForExecutionTarget(
            ambiance, shellScriptStepParameters, taskParametersNGBuilder);

     */
    Map<String, String> envVars = new HashMap<>();
    customSecretNGManagerConfig.getTestVariables().forEach(
        encryptedDataParams -> envVars.put(encryptedDataParams.getName(), encryptedDataParams.getValue()));
    return taskParametersNGBuilder.accountId(accountId)
        .executeOnDelegate(customSecretNGManagerConfig.isOnDelegate())
        .environmentVariables(envVars)
        .outputVars(Collections.singletonList(OUTPUT_VARIABLE))
        .script(script)
        .scriptType(scriptType)
        .workingDirectory(customSecretNGManagerConfig.getWorkingDirectory())
        .host(customSecretNGManagerConfig.getHost())
        .build();
  }

  /*public K8sInfraDelegateConfig getK8sInfraDelegateConfig(@Nonnull Ambiance ambiance, @Nonnull String shellScript) {
    if(shellScript.contains(K8sConstants.HARNESS_KUBE_CONFIG_PATH))
    {
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
              RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME));
      if (optionalSweepingOutput.isFound()) {
        K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput =
                (K8sInfraDelegateConfigOutput) optionalSweepingOutput.getOutput();
        return k8sInfraDelegateConfigOutput.getK8sInfraDelegateConfig();
      }
    }
    return null;
  }*/
}
