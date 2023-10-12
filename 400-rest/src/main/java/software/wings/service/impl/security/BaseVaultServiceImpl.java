/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.beans.EncryptedData.EncryptedDataKeys;
import static io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.BaseVaultConfig.BaseVaultConfigKeys;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.time.Duration.ofMillis;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SecretManagerConfig;
import io.harness.encryptors.managerproxy.ManagerEncryptorHelper;
import io.harness.exception.SecretManagementException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.VaultConfig;
import software.wings.beans.alert.KmsSetupAlert;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseVaultServiceImpl extends AbstractSecretServiceImpl {
  private static final int NUM_OF_RETRIES = 3;
  private static final String TOKEN_SECRET_NAME_SUFFIX = "_token";
  private static final String SECRET_ID_SECRET_NAME_SUFFIX = "_secret_id";

  @Inject private AccountService accountService;
  @Inject private ManagerEncryptorHelper managerEncryptorHelper;
  @Inject private DelegateTaskService delegateTaskService;

  protected boolean deleteVaultConfigInternal(String accountId, String vaultConfigId, long count) {
    if (count > 0) {
      String message = "Cannot delete the vault configuration since there are secrets encrypted with it. "
          + "Please transition your secrets to another secret manager and try again.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }

    BaseVaultConfig baseVaultConfig = wingsPersistence.get(BaseVaultConfig.class, vaultConfigId);
    checkNotNull(baseVaultConfig, "No SSH vault config found with id " + vaultConfigId);

    if (isNotBlank(baseVaultConfig.getAuthToken())) {
      wingsPersistence.delete(EncryptedData.class, baseVaultConfig.getAuthToken());
      log.info("Deleted encrypted auth token record {} associated with SSH vault secret engine '{}'",
          baseVaultConfig.getAuthToken(), baseVaultConfig.getName());
    }
    if (isNotBlank(baseVaultConfig.getSecretId())) {
      wingsPersistence.delete(EncryptedData.class, baseVaultConfig.getSecretId());
      log.info("Deleted encrypted secret id record {} associated with SSH vault secret engine '{}'",
          baseVaultConfig.getSecretId(), baseVaultConfig.getName());
    }

    return deleteSecretManagerAndGenerateAudit(accountId, baseVaultConfig);
  }

  protected BaseVaultConfig getVaultConfigInternal(Query<BaseVaultConfig> query) {
    BaseVaultConfig baseVaultConfig = query.get();

    if (baseVaultConfig != null) {
      EncryptedData encryptedToken = wingsPersistence.get(EncryptedData.class, baseVaultConfig.getAuthToken());
      EncryptedData encryptedSecretId = wingsPersistence.get(EncryptedData.class, baseVaultConfig.getSecretId());
      if (encryptedToken == null && encryptedSecretId == null && !baseVaultConfig.isUseVaultAgent()
          && !baseVaultConfig.isUseK8sAuth()) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
            "Either auth token or secret Id field needs to be present for vault secret manager.", USER);
      }

      if (encryptedToken != null) {
        char[] decryptToken = decryptUsingAlgoOfSecret(encryptedToken);
        baseVaultConfig.setAuthToken(String.valueOf(decryptToken));
      }

      if (encryptedSecretId != null) {
        char[] decryptedSecretId = decryptUsingAlgoOfSecret(encryptedSecretId);

        baseVaultConfig.setSecretId(String.valueOf(decryptedSecretId));
      }
      boolean isCertValidationRequired = baseVaultConfig.isCertValidationRequired();
      baseVaultConfig.setCertValidationRequired(isCertValidationRequired);
    }
    return baseVaultConfig;
  }

  public KmsSetupAlert getRenewalAlert(BaseVaultConfig baseVaultConfig) {
    return KmsSetupAlert.builder()
        .kmsId(baseVaultConfig.getUuid())
        .message(baseVaultConfig.getName()
            + "(Hashicorp Vault) is not able to renew the token. Please check your setup and ensure that token is renewable")
        .build();
  }

  public VaultAppRoleLoginResult appRoleLogin(BaseVaultConfig baseVaultConfig) {
    int failedAttempts = 0;
    boolean isCertValidationRequired = accountService.isCertValidationRequired(baseVaultConfig.getAccountId());
    baseVaultConfig.setCertValidationRequired(isCertValidationRequired);
    while (true) {
      try {
        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(baseVaultConfig.getAccountId())
                .timeout(Duration.ofSeconds(5).toMillis())
                .appId(baseVaultConfig.getAccountId())
                .correlationId(baseVaultConfig.getUuid())
                .orgIdentifier(baseVaultConfig.getOrgIdentifier())
                .ngTask(isNgTask(baseVaultConfig.getOrgIdentifier(), baseVaultConfig.getProjectIdentifier()))
                .projectIdentifier(baseVaultConfig.getProjectIdentifier())
                .build();

        return delegateProxyFactory.getV2(SecretManagementDelegateService.class, syncTaskContext)
            .appRoleLogin(baseVaultConfig);
      } catch (WingsException e) {
        failedAttempts++;
        log.warn(
            "Vault AppRole login failed Vault server {}. trial num: {}", baseVaultConfig.getName(), failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  public VaultTokenLookupResult tokenLookup(BaseVaultConfig baseVaultConfig) {
    int failedAttempts = 0;
    boolean isCertValidationRequired = accountService.isCertValidationRequired(baseVaultConfig.getAccountId());
    baseVaultConfig.setCertValidationRequired(isCertValidationRequired);
    while (true) {
      try {
        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(baseVaultConfig.getAccountId())
                .timeout(Duration.ofSeconds(5).toMillis())
                .appId(baseVaultConfig.getAccountId())
                .correlationId(baseVaultConfig.getUuid())
                .orgIdentifier(baseVaultConfig.getOrgIdentifier())
                .ngTask(isNgTask(baseVaultConfig.getOrgIdentifier(), baseVaultConfig.getProjectIdentifier()))
                .projectIdentifier(baseVaultConfig.getProjectIdentifier())
                .build();

        return delegateProxyFactory.getV2(SecretManagementDelegateService.class, syncTaskContext)
            .tokenLookup(baseVaultConfig);
      } catch (WingsException e) {
        failedAttempts++;
        log.warn("Vault Token lookup failed for Vault server {}. trial num: {}", baseVaultConfig.getName(),
            failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  public void renewToken(BaseVaultConfig baseVaultConfig) {
    String accountId = baseVaultConfig.getAccountId();
    BaseVaultConfig decryptedVaultConfig = getBaseVaultConfig(accountId, baseVaultConfig.getUuid());

    SyncTaskContext syncTaskContext =
        SyncTaskContext.builder()
            .accountId(accountId)
            .appId(GLOBAL_APP_ID)
            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
            .orgIdentifier(baseVaultConfig.getOrgIdentifier())
            .projectIdentifier(baseVaultConfig.getProjectIdentifier())
            .ngTask(isNgTask(baseVaultConfig.getOrgIdentifier(), baseVaultConfig.getProjectIdentifier()))
            .build();
    boolean isCertValidationRequired = accountService.isCertValidationRequired(accountId);
    baseVaultConfig.setCertValidationRequired(isCertValidationRequired);
    delegateProxyFactory.getV2(SecretManagementDelegateService.class, syncTaskContext)
        .renewVaultToken(decryptedVaultConfig);
    wingsPersistence.updateField(SecretManagerConfig.class, baseVaultConfig.getUuid(), BaseVaultConfigKeys.renewedAt,
        System.currentTimeMillis());
  }

  public BaseVaultConfig getBaseVaultConfig(String accountId, String entityId) {
    if (isBlank(accountId) || isBlank(entityId)) {
      return new VaultConfig();
    }
    Query<BaseVaultConfig> query = wingsPersistence.createQuery(BaseVaultConfig.class)
                                       .filter(SecretManagerConfigKeys.accountId, accountId)
                                       .filter(ID_KEY, entityId);
    return getVaultConfigInternal(query);
  }

  public void renewAppRoleClientToken(BaseVaultConfig baseVaultConfig) {
    wingsPersistence.updateField(
        SecretManagerConfig.class, baseVaultConfig.getUuid(), BaseVaultConfigKeys.renewAppRoleToken, false);
  }

  private String saveSecretField(String accountId, String vaultConfigId, String secretValue, String secretNameSuffix,
      String fieldName, SettingVariableTypes settingVariableTypes) {
    EncryptedData encryptedData = encryptUsingBaseAlgo(accountId, secretValue.toCharArray());
    // Get by auth token encrypted record by Id or name.
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .field(SecretManagerConfigKeys.accountId)
                                     .equal(accountId)
                                     .field(EncryptedDataKeys.name)
                                     .equal(vaultConfigId + secretNameSuffix);

    EncryptedData savedEncryptedData = query.get();
    if (savedEncryptedData != null) {
      savedEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
      savedEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
      savedEncryptedData.setEncryptionType(encryptedData.getEncryptionType());
      savedEncryptedData.setKmsId(encryptedData.getKmsId());
      encryptedData = savedEncryptedData;
    }
    encryptedData.setAccountId(accountId);
    encryptedData.addParent(
        EncryptedDataParent.createParentRef(vaultConfigId, SSHVaultConfig.class, fieldName, settingVariableTypes));
    encryptedData.setType(settingVariableTypes);
    encryptedData.setName(vaultConfigId + secretNameSuffix);
    return wingsPersistence.save(encryptedData);
  }

  protected void saveVaultCredentials(
      BaseVaultConfig savedVaultConfig, String authToken, String secretId, SettingVariableTypes settingVariableTypes) {
    String vaultConfigId = savedVaultConfig.getUuid();
    String accountId = savedVaultConfig.getAccountId();

    // Create a LOCAL encrypted record for Vault authToken
    Preconditions.checkNotNull(authToken);
    String authTokenEncryptedDataId = saveSecretField(accountId, vaultConfigId, authToken, TOKEN_SECRET_NAME_SUFFIX,
        BaseVaultConfigKeys.authToken, settingVariableTypes);
    savedVaultConfig.setAuthToken(authTokenEncryptedDataId);
    // Create a LOCAL encrypted record for Vault secretId
    if (isNotBlank(secretId)) {
      String secretIdEncryptedDataId = saveSecretField(accountId, vaultConfigId, secretId, SECRET_ID_SECRET_NAME_SUFFIX,
          BaseVaultConfigKeys.secretId, settingVariableTypes);
      savedVaultConfig.setSecretId(secretIdEncryptedDataId);
    }
  }

  protected String updateSecretField(String secretFieldUuid, String accountId, String vaultConfigId, String secretValue,
      String secretNameSuffix, String fieldName, SettingVariableTypes settingVariableTypes) {
    EncryptedData encryptedData = encryptUsingBaseAlgo(accountId, secretValue.toCharArray());
    // Get by auth token encrypted record by Id or name.
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .field(SecretManagerConfigKeys.accountId)
                                     .equal(accountId)
                                     .field(ID_KEY)
                                     .equal(secretFieldUuid);

    EncryptedData savedEncryptedData = query.get();
    if (savedEncryptedData == null) {
      throw new UnexpectedException("The vault config is in a bad state. Please contact Harness Support");
    }
    savedEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
    savedEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
    savedEncryptedData.setEncryptionType(encryptedData.getEncryptionType());
    savedEncryptedData.setKmsId(encryptedData.getKmsId());
    savedEncryptedData.setAccountId(accountId);
    savedEncryptedData.addParent(
        EncryptedDataParent.createParentRef(vaultConfigId, VaultConfig.class, fieldName, settingVariableTypes));
    savedEncryptedData.setType(settingVariableTypes);
    savedEncryptedData.setName(vaultConfigId + secretNameSuffix);
    return wingsPersistence.save(savedEncryptedData);
  }

  protected void updateVaultCredentials(
      BaseVaultConfig savedVaultConfig, BaseVaultConfig vaultConfig, SettingVariableTypes settingVariableTypes) {
    String vaultConfigId = savedVaultConfig.getUuid();
    String accountId = savedVaultConfig.getAccountId();
    String authTokenEncryptedDataId = savedVaultConfig.getAuthToken();
    String secretIdEncryptedDataId = savedVaultConfig.getSecretId();

    if (vaultConfig.isUseVaultAgent()) {
      // deleate AppId cred and Saved Token
      if (isNotBlank(savedVaultConfig.getAuthToken())) {
        wingsPersistence.delete(EncryptedData.class, savedVaultConfig.getAuthToken());
        log.info("Deleted encrypted auth token record {} associated with Vault '{}'", savedVaultConfig.getAuthToken(),
            savedVaultConfig.getName());
      }
      if (isNotBlank(savedVaultConfig.getSecretId())) {
        wingsPersistence.delete(EncryptedData.class, savedVaultConfig.getSecretId());
        log.info("Deleted encrypted secret id record {} associated with Vault '{}'", savedVaultConfig.getSecretId(),
            savedVaultConfig.getName());
      }
    } else {
      String authToken = vaultConfig.getAuthToken();
      String secretId = vaultConfig.getSecretId();

      // Create or Update a local encrypted record of Auth token
      if (isNotBlank(authToken)) {
        if (isNotBlank(authTokenEncryptedDataId)) {
          authTokenEncryptedDataId = updateSecretField(authTokenEncryptedDataId, accountId, vaultConfigId, authToken,
              TOKEN_SECRET_NAME_SUFFIX, BaseVaultConfigKeys.authToken, settingVariableTypes);
        } else {
          authTokenEncryptedDataId = saveSecretField(accountId, vaultConfigId, authToken, TOKEN_SECRET_NAME_SUFFIX,
              BaseVaultConfigKeys.authToken, settingVariableTypes);
        }
        savedVaultConfig.setAuthToken(authTokenEncryptedDataId);
      }

      // Create a LOCAL encrypted record for Vault secretId
      if (isNotBlank(secretId)) {
        if (isNotBlank(secretIdEncryptedDataId)) {
          secretIdEncryptedDataId = updateSecretField(secretIdEncryptedDataId, accountId, vaultConfigId, secretId,
              SECRET_ID_SECRET_NAME_SUFFIX, BaseVaultConfigKeys.secretId, settingVariableTypes);
        } else {
          secretIdEncryptedDataId = saveSecretField(accountId, vaultConfigId, secretId, SECRET_ID_SECRET_NAME_SUFFIX,
              BaseVaultConfigKeys.secretId, settingVariableTypes);
        }
        savedVaultConfig.setSecretId(secretIdEncryptedDataId);
      }
    }
  }

  void decryptVaultConfigSecretsInternal(BaseVaultConfig vaultConfig, boolean maskSecret) {
    if (maskSecret) {
      vaultConfig.maskSecrets();
    } else {
      EncryptedData tokenData = wingsPersistence.get(EncryptedData.class, vaultConfig.getAuthToken());
      EncryptedData secretIdData = wingsPersistence.get(EncryptedData.class, vaultConfig.getSecretId());
      if (!vaultConfig.isUseK8sAuth() && !vaultConfig.isUseVaultAgent() && tokenData == null && secretIdData == null) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
            "Either auth token or secret Id field needs to be present for vault secret manager.", USER);
      }

      if (tokenData != null) {
        char[] decryptedToken = decryptUsingAlgoOfSecret(tokenData);
        vaultConfig.setAuthToken(String.valueOf(decryptedToken));
      }
      if (secretIdData != null) {
        char[] decryptedSecretId = decryptUsingAlgoOfSecret(secretIdData);
        vaultConfig.setSecretId(String.valueOf(decryptedSecretId));
      }
    }
  }

  List<SecretEngineSummary> listSecretEnginesInternal(BaseVaultConfig vaultConfig) {
    // HAR-7605: Shorter timeout for decryption tasks, and it should retry on timeout or failure.
    int failedAttempts = 0;
    boolean isCertValidationRequired = accountService.isCertValidationRequired(vaultConfig.getAccountId());
    vaultConfig.setCertValidationRequired(isCertValidationRequired);
    while (true) {
      try {
        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(vaultConfig.getAccountId())
                .timeout(Duration.ofSeconds(10).toMillis())
                .appId(GLOBAL_APP_ID)
                .correlationId(vaultConfig.getUuid())
                .orgIdentifier(vaultConfig.getOrgIdentifier())
                .projectIdentifier(vaultConfig.getProjectIdentifier())
                .ngTask(isNgTask(vaultConfig.getOrgIdentifier(), vaultConfig.getProjectIdentifier()))
                .build();

        return delegateProxyFactory.getV2(SecretManagementDelegateService.class, syncTaskContext)
            .listSecretEngines(vaultConfig);
      } catch (WingsException e) {
        failedAttempts++;
        log.warn("Vault Decryption failed for list secret engines for Vault serverer {}. trial num: {}",
            vaultConfig.getName(), failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  protected boolean isNgTask(String orgIdentifier, String projectIdentifier) {
    return isNotBlank(orgIdentifier) || isNotBlank(projectIdentifier);
  }

  protected boolean isTaskSupportedByDelegate(String accountId, String taskType) {
    return delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, taskType);
  }
}
