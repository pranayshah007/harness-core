/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceSettingDTO;
import io.harness.delegate.beans.azure.registry.AzureRegistry;
import io.harness.delegate.beans.azure.registry.AzureRegistryFactory;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppRollbackParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureSecretHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private LocalEncryptor localEncryptor;

  public AzureConfig decryptAndGetAzureConfig(
      AzureConfigDTO azureConfigDTO, List<EncryptedDataDetail> azureConfigEncryptionDetails) {
    secretDecryptionService.decrypt(azureConfigDTO, azureConfigEncryptionDetails);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(azureConfigDTO, azureConfigEncryptionDetails);

    return AzureConfig.builder()
        .clientId(azureConfigDTO.getClientId())
        .tenantId(azureConfigDTO.getTenantId())
        .key(azureConfigDTO.getKey().getDecryptedValue())
        .azureEnvironmentType(azureConfigDTO.getAzureEnvironmentType())
        .build();
  }

  public void decryptAzureVMSSTaskParameters(AzureVMSSTaskParameters azureVMSSTaskParameters) {
    if (AzureVMSSTaskParameters.AzureVMSSTaskType.AZURE_VMSS_SETUP == azureVMSSTaskParameters.getCommandType()) {
      AzureVMSSSetupTaskParameters setupTaskParameters = (AzureVMSSSetupTaskParameters) azureVMSSTaskParameters;
      AzureVMAuthDTO azureVmAuthDTO = setupTaskParameters.getAzureVmAuthDTO();
      List<EncryptedDataDetail> vmAuthDTOEncryptionDetails = setupTaskParameters.getVmAuthDTOEncryptionDetails();
      secretDecryptionService.decrypt(azureVmAuthDTO, vmAuthDTOEncryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(azureVmAuthDTO, vmAuthDTOEncryptionDetails);
    }
  }

  public void decryptAzureAppServiceTaskParameters(AzureAppServiceTaskParameters azureAppServiceTaskParameters) {
    if (AzureAppServiceTaskType.SLOT_SETUP == azureAppServiceTaskParameters.getCommandType()
        && azureAppServiceTaskParameters instanceof AzureWebAppSlotSetupParameters
        && ((AzureWebAppSlotSetupParameters) azureAppServiceTaskParameters).getAzureRegistryType() != null) {
      decryptAzureWebAppSlotSetupParameters((AzureWebAppSlotSetupParameters) azureAppServiceTaskParameters);
    }

    if (AzureAppServiceTaskType.SLOT_ROLLBACK == azureAppServiceTaskParameters.getCommandType()
        && azureAppServiceTaskParameters instanceof AzureWebAppRollbackParameters) {
      decryptAzureWebAppRollbackParameters(
          ((AzureWebAppRollbackParameters) azureAppServiceTaskParameters).getPreDeploymentData());
    }
  }

  private void decryptAzureWebAppSlotSetupParameters(AzureWebAppSlotSetupParameters azureAppServiceTaskParameters) {
    List<EncryptedDataDetail> encryptedDataDetails = azureAppServiceTaskParameters.getEncryptedDataDetails();
    AzureRegistryType azureRegistryType = azureAppServiceTaskParameters.getAzureRegistryType();
    AzureRegistry azureRegistry = AzureRegistryFactory.getAzureRegistry(azureRegistryType);
    ConnectorConfigDTO connectorConfigDTO = azureAppServiceTaskParameters.getConnectorConfigDTO();
    Optional<DecryptableEntity> authCredentialsDTO = azureRegistry.getAuthCredentialsDTO(connectorConfigDTO);
    authCredentialsDTO.ifPresent(credentials -> secretDecryptionService.decrypt(credentials, encryptedDataDetails));
    authCredentialsDTO.ifPresent(
        credentials -> ExceptionMessageSanitizer.storeAllSecretsForSanitizing(credentials, encryptedDataDetails));
  }

  public void decryptAzureWebAppRollbackParameters(AzureAppServicePreDeploymentData preDeploymentData) {
    decryptSettings(preDeploymentData.getAppSettingsToAdd());
    decryptSettings(preDeploymentData.getAppSettingsToRemove());
    decryptSettings(preDeploymentData.getConnStringsToAdd());
    decryptSettings(preDeploymentData.getConnStringsToRemove());
    decryptSettings(preDeploymentData.getDockerSettingsToAdd());
  }

  private <T extends AzureAppServiceSettingDTO> void decryptSettings(Map<String, T> settings) {
    if (isEmpty(settings)) {
      return;
    }
    settings.values().forEach(this::decryptEncryptedRecordByLocalEncryptor);
  }

  private void decryptEncryptedRecordByLocalEncryptor(AzureAppServiceSettingDTO setting) {
    log.info("Decrypting setting by local encryptor, name: {}", setting.getName());
    String accountId = setting.getAccountId();
    EncryptedRecord encryptedRecord = setting.getEncryptedRecord();

    char[] secretValue = localEncryptor.fetchSecretValue(accountId, encryptedRecord, null);
    setting.setValue(new String(secretValue));
  }

  public void encryptAzureAppServicePreDeploymentData(
      AzureAppServicePreDeploymentData azureAppServicePreDeploymentData, final String accountId) {
    encryptPreDeploymentData(azureAppServicePreDeploymentData, accountId);
  }

  public void encryptAzureTaskResponseParams(
      AzureTaskResponse azureTaskResponse, final String accountId, AzureAppServiceTaskType commandType) {
    if ((azureTaskResponse instanceof AzureWebAppSlotSetupResponse)
        && AzureAppServiceTaskType.SLOT_SETUP == commandType) {
      encryptPreDeploymentData(((AzureWebAppSlotSetupResponse) azureTaskResponse).getPreDeploymentData(), accountId);
    }
  }

  private void encryptPreDeploymentData(AzureAppServicePreDeploymentData preDeploymentData, final String accountId) {
    if (preDeploymentData != null) {
      encryptSettings(preDeploymentData.getAppSettingsToRemove(), accountId);
      encryptSettings(preDeploymentData.getAppSettingsToAdd(), accountId);
      encryptSettings(preDeploymentData.getConnStringsToRemove(), accountId);
      encryptSettings(preDeploymentData.getConnStringsToAdd(), accountId);
      encryptSettings(preDeploymentData.getDockerSettingsToAdd(), accountId);
    }
  }

  private <T extends AzureAppServiceSettingDTO> void encryptSettings(Map<String, T> settings, final String accountId) {
    if (isEmpty(settings)) {
      return;
    }
    settings.values().forEach(appServiceSetting -> {
      log.info("Encrypt Azure App service setting by local encryptor, name: {}", appServiceSetting.getName());
      encryptRecordByLocalEncryptor(appServiceSetting, accountId);
    });
  }

  private void encryptRecordByLocalEncryptor(AzureAppServiceSettingDTO appServiceSetting, final String accountId) {
    String value = appServiceSetting.getValue();

    EncryptedRecord encryptedRecord = localEncryptor.encryptSecret(accountId, value, null);
    appServiceSetting.setEncryptedRecord(encryptedRecord);
    appServiceSetting.setValue(EMPTY);
    appServiceSetting.setAccountId(accountId);
  }

  public ArtifactStreamAttributes decryptArtifactStreamAttributes(ArtifactStreamAttributes artifactStreamAttributes) {
    if (artifactStreamAttributes == null) {
      return null;
    }
    SettingValue settingValue = artifactStreamAttributes.getServerSetting().getValue();
    List<EncryptedDataDetail> artifactServerEncryptedDataDetails =
        artifactStreamAttributes.getArtifactServerEncryptedDataDetails();
    secretDecryptionService.decrypt((EncryptableSetting) settingValue, artifactServerEncryptedDataDetails, false);
    return artifactStreamAttributes;
  }
}
