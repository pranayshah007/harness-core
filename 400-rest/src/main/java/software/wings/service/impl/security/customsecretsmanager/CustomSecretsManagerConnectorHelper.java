/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript.ScriptType.BASH;
import static software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript.ScriptType.POWERSHELL;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.encryption.EncryptedDataParams;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig.CustomSecretsManagerConfigKeys;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript.ScriptType;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
class CustomSecretsManagerConnectorHelper {
  private final WingsPersistence wingsPersistence;

  @Inject
  CustomSecretsManagerConnectorHelper(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  void setConnectorInConfig(
      CustomSecretsManagerConfig customSecretsManagerConfig, Set<EncryptedDataParams> testVariables) {
    String accountId = customSecretsManagerConfig.getAccountId();
    String connectorId = customSecretsManagerConfig.getConnectorId();
    ScriptType scriptType = customSecretsManagerConfig.getCustomSecretsManagerShellScript().getScriptType();

    if (customSecretsManagerConfig.isConnectorTemplatized()) {
      connectorId = getConnectorIdFromTestVariables(testVariables);
    }
    EncryptableSetting remoteHostConnector = getConnector(accountId, connectorId, scriptType);
    customSecretsManagerConfig.setRemoteHostConnector(remoteHostConnector);
  }

  private EncryptableSetting getConnector(
      @NotEmpty String accountId, @NotEmpty String connectorId, @NotEmpty ScriptType scriptType) {
    EncryptableSetting encryptableSetting =
        (EncryptableSetting) Optional.ofNullable(getSettingValueById(accountId, connectorId))
            .<InvalidArgumentsException>orElseThrow(() -> {
              String errorMessage = String.format("Connector with id %s was not found", connectorId);
              throw new InvalidArgumentsException(errorMessage, USER);
            });
    if (!((scriptType == BASH && encryptableSetting.getSettingType() == HOST_CONNECTION_ATTRIBUTES)
            || (scriptType == POWERSHELL && encryptableSetting.getSettingType() == WINRM_CONNECTION_ATTRIBUTES))) {
      String errorMessage =
          "Bash Script can only be used with SSH Connector and Powershell Script can only be used with WinRM Connector";
      throw new InvalidArgumentsException(errorMessage, USER);
    }
    return encryptableSetting;
  }

  private static String getConnectorIdFromTestVariables(Set<EncryptedDataParams> testVariables) {
    return testVariables.stream()
        .filter(secretVariable -> secretVariable.getName().equals(CustomSecretsManagerConfigKeys.connectorId))
        .findFirst()
        .<InvalidArgumentsException>orElseThrow(() -> {
          String errorMessage = "There is no ConnectorDisconnectHandler supplied as a variable although the ConnectorDisconnectHandler was templatized";
          throw new InvalidArgumentsException(errorMessage, USER);
        })
        .getValue();
  }

  private SettingValue getSettingValueById(String accountId, String connectorId) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.uuid, connectorId)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .get();
    if (settingAttribute != null) {
      return settingAttribute.getValue();
    }
    return null;
  }
}
