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
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.shell.ScriptType;

import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.beans.delegation.ShellScriptParameters.ShellScriptParametersBuilder;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
}
