/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessModule._890_SM_CORE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.security.encryption.AccessType.APP_ROLE;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.settings.SettingVariableTypes.VAULT;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.expression.SecretString;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.persistence.HPersistence;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;
import io.harness.templatizedsm.RuntimeCredentialsInjector;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.BaseVaultConfig.BaseVaultConfigKeys;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.VaultConfig;
import software.wings.beans.alert.AlertType;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.VaultService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 11/2/17.
 */
@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(_890_SM_CORE)
public class VaultServiceImpl extends BaseVaultServiceImpl implements VaultService, RuntimeCredentialsInjector {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Inject private AccountService accountService;

  @Override
  public VaultConfig getVaultConfigByName(String accountId, String name) {
    if (isEmpty(accountId) || isEmpty(name)) {
      return new VaultConfig();
    }
    Query<BaseVaultConfig> query = wingsPersistence.createQuery(BaseVaultConfig.class)
                                       .filter(SecretManagerConfigKeys.accountId, accountId)
                                       .filter(EncryptedDataKeys.name, name);
    return (VaultConfig) getVaultConfigInternal(query);
  }

  String updateVaultConfig(String accountId, VaultConfig vaultConfig, boolean auditChanges, boolean validate) {
    VaultConfig savedVaultConfigWithCredentials = getVaultConfig(accountId, vaultConfig.getUuid());
    VaultConfig oldConfigForAudit = wingsPersistence.get(VaultConfig.class, vaultConfig.getUuid());
    VaultConfig savedVaultConfig = kryoSerializer.clone(oldConfigForAudit);
    // Replaced masked secrets with the real secret value.
    if (SECRET_MASK.equals(vaultConfig.getAuthToken())) {
      vaultConfig.setAuthToken(savedVaultConfigWithCredentials.getAuthToken());
    }
    if (SECRET_MASK.equals(vaultConfig.getSecretId())) {
      vaultConfig.setSecretId(savedVaultConfigWithCredentials.getSecretId());
    }

    boolean credentialChanged = isCredentialChanged(vaultConfig, savedVaultConfigWithCredentials);

    validateVaultConfig(accountId, vaultConfig, validate);

    if (credentialChanged) {
      updateVaultCredentials(savedVaultConfig, vaultConfig, VAULT);
    }

    savedVaultConfig.setName(vaultConfig.getName());
    savedVaultConfig.setRenewalInterval(vaultConfig.getRenewalInterval());
    savedVaultConfig.setDefault(vaultConfig.isDefault());
    savedVaultConfig.setReadOnly(vaultConfig.isReadOnly());
    savedVaultConfig.setBasePath(vaultConfig.getBasePath());
    savedVaultConfig.setSecretEngineName(vaultConfig.getSecretEngineName());
    savedVaultConfig.setSecretEngineVersion(vaultConfig.getSecretEngineVersion());
    savedVaultConfig.setAppRoleId(vaultConfig.getAppRoleId());
    savedVaultConfig.setVaultUrl(vaultConfig.getVaultUrl());
    savedVaultConfig.setEngineManuallyEntered(vaultConfig.isEngineManuallyEntered());
    savedVaultConfig.setTemplatizedFields(vaultConfig.getTemplatizedFields());
    savedVaultConfig.setUsageRestrictions(vaultConfig.getUsageRestrictions());
    savedVaultConfig.setScopedToAccount(vaultConfig.isScopedToAccount());
    savedVaultConfig.setRenewAppRoleToken(vaultConfig.getRenewAppRoleToken());
    savedVaultConfig.setDelegateSelectors(vaultConfig.getDelegateSelectors());
    // Handle vault Agent Properties
    updateVaultAgentConfiguration(vaultConfig, savedVaultConfig);
    updateNameSpace(accountId, vaultConfig, savedVaultConfig);
    // Handle vault k8s Auth Properties
    updateK8sAuthConfig(vaultConfig, savedVaultConfig);
    // PL-3237: Audit secret manager config changes.
    if (auditChanges) {
      generateAuditForSecretManager(accountId, oldConfigForAudit, savedVaultConfig);
    }
    String configId = secretManagerConfigService.save(savedVaultConfig);
    if (isNotBlank(configId)) {
      alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.InvalidKMS, getRenewalAlert(oldConfigForAudit));
    }
    return configId;
  }

  private void updateVaultAgentConfiguration(VaultConfig vaultConfig, VaultConfig savedVaultConfig) {
    if (vaultConfig.isUseVaultAgent()) {
      Preconditions.checkNotNull(vaultConfig.getSinkPath());
      Preconditions.checkNotNull(vaultConfig.getDelegateSelectors());
      // set all set credentials to null
      savedVaultConfig.setAppRoleId(null);
      savedVaultConfig.setAuthToken(null);
      savedVaultConfig.setSecretId(null);
      savedVaultConfig.setSinkPath(vaultConfig.getSinkPath());
      savedVaultConfig.setUseVaultAgent(vaultConfig.isUseVaultAgent());
    } else {
      savedVaultConfig.setUseVaultAgent(false);
      savedVaultConfig.setSinkPath(null);
    }
  }

  private void updateK8sAuthConfig(VaultConfig vaultConfig, VaultConfig savedVaultConfig) {
    if (vaultConfig.isUseK8sAuth()) {
      Preconditions.checkNotNull(vaultConfig.getVaultK8sAuthRole());
      Preconditions.checkNotNull(vaultConfig.getServiceAccountTokenPath());
      Preconditions.checkNotNull(vaultConfig.getDelegateSelectors());
      // set all set credentials to null
      savedVaultConfig.setAppRoleId(null);
      savedVaultConfig.setAuthToken(null);
      savedVaultConfig.setSecretId(null);
      savedVaultConfig.setUseK8sAuth(vaultConfig.isUseK8sAuth());
      savedVaultConfig.setVaultK8sAuthRole(vaultConfig.getVaultK8sAuthRole());
      savedVaultConfig.setServiceAccountTokenPath(vaultConfig.getServiceAccountTokenPath());
      savedVaultConfig.setK8sAuthEndpoint(vaultConfig.getK8sAuthEndpoint());
    } else {
      savedVaultConfig.setUseK8sAuth(false);
      savedVaultConfig.setVaultK8sAuthRole(null);
      savedVaultConfig.setServiceAccountTokenPath(null);
      savedVaultConfig.setK8sAuthEndpoint(null);
    }
  }

  private boolean isCredentialChanged(VaultConfig vaultConfig, VaultConfig savedVaultConfigWithCredentials) {
    return !Objects.equals(savedVaultConfigWithCredentials.getAuthToken(), vaultConfig.getAuthToken())
        || !Objects.equals(savedVaultConfigWithCredentials.getSecretId(), vaultConfig.getSecretId())
        || !Objects.equals(savedVaultConfigWithCredentials.isUseVaultAgent(), vaultConfig.isUseVaultAgent())
        || !Objects.equals(savedVaultConfigWithCredentials.getDelegateSelectors(), vaultConfig.getDelegateSelectors())
        || !Objects.equals(savedVaultConfigWithCredentials.getSinkPath(), vaultConfig.getSinkPath());
  }

  private void updateNameSpace(String accountId, VaultConfig vaultConfig, VaultConfig savedVaultConfig) {
    // get encrypted secrets associated with this VaultConfig
    long count = getEncryptedSecretCount(accountId, savedVaultConfig.getUuid());
    boolean nameSpaceUpdated = !Objects.equals(savedVaultConfig.getNamespace(), vaultConfig.getNamespace());
    log.info("User wants to update namespace for Hashicorp vault:{} From:{} To:{} ", savedVaultConfig.getUuid(),
        savedVaultConfig.getNamespace(), vaultConfig.getNamespace());
    if (count > 0 && nameSpaceUpdated) {
      String message = "Cannot update vault config namespace since there are secrets encrypted with it. "
          + "Please transition your secrets to another vault config with different namespace and try again.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
    savedVaultConfig.setNamespace(vaultConfig.getNamespace());
  }

  private String saveVaultConfig(String accountId, VaultConfig vaultConfig, boolean validateBySavingTestSecret) {
    validateVaultConfig(accountId, vaultConfig, validateBySavingTestSecret);

    String authToken = vaultConfig.getAuthToken();
    String secretId = vaultConfig.getSecretId();

    try {
      vaultConfig.setAuthToken(null);
      vaultConfig.setSecretId(null);
      String vaultConfigId = secretManagerConfigService.save(vaultConfig);
      vaultConfig.setUuid(vaultConfigId);
    } catch (DuplicateKeyException e) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Another vault configuration with the same name or URL exists", e, USER_SRE);
    }

    if (!vaultConfig.isUseVaultAgent() && !vaultConfig.isUseK8sAuth()) {
      saveVaultCredentials(vaultConfig, authToken, secretId, VAULT);

      Query<SecretManagerConfig> query =
          wingsPersistence.createQuery(SecretManagerConfig.class).field(ID_KEY).equal(vaultConfig.getUuid());

      UpdateOperations<SecretManagerConfig> updateOperations =
          wingsPersistence.createUpdateOperations(SecretManagerConfig.class)
              .set(BaseVaultConfigKeys.authToken, vaultConfig.getAuthToken());

      if (isNotBlank(vaultConfig.getSecretId())) {
        updateOperations.set(BaseVaultConfigKeys.secretId, vaultConfig.getSecretId());
      }

      vaultConfig =
          (VaultConfig) wingsPersistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);
    }
    // PL-3237: Audit secret manager config changes.
    generateAuditForSecretManager(accountId, null, vaultConfig);
    return vaultConfig.getUuid();
  }

  @Override
  public String saveOrUpdateVaultConfig(String accountId, VaultConfig vaultConfig, boolean validateBySavingTestSecret) {
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);
    // First normalize the base path value. Set default base path if it has not been specified from input.
    String basePath =
        isBlank(vaultConfig.getBasePath()) ? VaultConfig.DEFAULT_BASE_PATH : vaultConfig.getBasePath().trim();
    vaultConfig.setBasePath(basePath);
    vaultConfig.setAccountId(accountId);

    checkIfTemplatizedSecretManagerCanBeCreatedOrUpdated(vaultConfig);

    // App Role Token renew FF
    vaultConfig.setRenewAppRoleToken(false);

    return isBlank(vaultConfig.getUuid()) ? saveVaultConfig(accountId, vaultConfig, validateBySavingTestSecret)
                                          : updateVaultConfig(accountId, vaultConfig, true, validateBySavingTestSecret);
  }

  private Optional<String> getTemplatizedField(String templatizedField, VaultConfig vaultConfig) {
    if (templatizedField.equals(BaseVaultConfigKeys.authToken)) {
      return Optional.ofNullable(vaultConfig.getAuthToken());
    } else if (templatizedField.equals(BaseVaultConfigKeys.appRoleId)) {
      return Optional.ofNullable(vaultConfig.getAppRoleId());
    } else if (templatizedField.equals(BaseVaultConfigKeys.secretId)) {
      return Optional.ofNullable(vaultConfig.getSecretId());
    }
    return Optional.empty();
  }

  private void checkIfTemplatizedSecretManagerCanBeCreatedOrUpdated(VaultConfig vaultConfig) {
    if (vaultConfig.isTemplatized()) {
      if (vaultConfig.isDefault()) {
        throw new InvalidRequestException("Cannot set a templatized secret manager as default");
      }

      for (String templatizedField : vaultConfig.getTemplatizedFields()) {
        Optional<String> requiredField = getTemplatizedField(templatizedField, vaultConfig);
        if (!requiredField.isPresent() || SECRET_MASK.equals(requiredField.get())) {
          throw new InvalidRequestException("Invalid value provided for templatized field: " + templatizedField);
        }
      }
    }
  }

  @Override
  public boolean deleteVaultConfig(String accountId, String vaultConfigId) {
    long count = getEncryptedSecretCount(accountId, vaultConfigId);

    return deleteVaultConfigInternal(accountId, vaultConfigId, count);
  }

  private long getEncryptedSecretCount(String accountId, String vaultConfigId) {
    return wingsPersistence.createQuery(EncryptedData.class)
        .filter(SecretManagerConfigKeys.accountId, accountId)
        .filter(EncryptedDataKeys.kmsId, vaultConfigId)
        .filter(EncryptedDataKeys.encryptionType, EncryptionType.VAULT)
        .count(upToOne);
  }

  @Override
  public List<SecretEngineSummary> listSecretEngines(VaultConfig vaultConfig) {
    if (isNotBlank(vaultConfig.getUuid())) {
      VaultConfig savedVaultConfig = wingsPersistence.get(VaultConfig.class, vaultConfig.getUuid());
      decryptVaultConfigSecrets(vaultConfig.getAccountId(), savedVaultConfig, false);
      if (SecretString.SECRET_MASK.equals(vaultConfig.getAuthToken())) {
        vaultConfig.setAuthToken(savedVaultConfig.getAuthToken());
      }
      if (SecretString.SECRET_MASK.equals(vaultConfig.getSecretId())) {
        vaultConfig.setSecretId(savedVaultConfig.getSecretId());
      }
    }
    List<SecretEngineSummary> secretEngineSummaries = new ArrayList<>();
    for (SecretEngineSummary secretEngineSummary : listSecretEnginesInternal(vaultConfig)) {
      if (secretEngineSummary.getType() != null && secretEngineSummary.getType().equals("kv")) {
        secretEngineSummaries.add(secretEngineSummary);
      }
    }
    return secretEngineSummaries;
  }

  @Override
  public void decryptVaultConfigSecrets(String accountId, VaultConfig vaultConfig, boolean maskSecret) {
    decryptVaultConfigSecretsInternal(vaultConfig, maskSecret);
  }

  @Override
  public List<SecretChangeLog> getVaultSecretChangeLogs(EncryptedData encryptedData, VaultConfig vaultConfig) {
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(vaultConfig.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    boolean isCertValidationRequired = accountService.isCertValidationRequired(vaultConfig.getAccountId());
    vaultConfig.setCertValidationRequired(isCertValidationRequired);
    return delegateProxyFactory.getV2(SecretManagementDelegateService.class, syncTaskContext)
        .getVaultSecretChangeLogs(encryptedData, vaultConfig);
  }

  private void validateVaultFields(VaultConfig vaultConfig) {
    if (isBlank(vaultConfig.getName())) {
      throw new SecretManagementException(VAULT_OPERATION_ERROR, "Name can not be empty", USER);
    }
    if (isBlank(vaultConfig.getVaultUrl())) {
      throw new SecretManagementException(VAULT_OPERATION_ERROR, "Vault URL can not be empty", USER);
    }
    if (isBlank(vaultConfig.getSecretEngineName()) || vaultConfig.getSecretEngineVersion() == 0) {
      throw new SecretManagementException(
          VAULT_OPERATION_ERROR, "Secret engine or secret engine version was not specified", USER);
    }

    if (vaultConfig.isUseVaultAgent()) {
      if (isBlank(vaultConfig.getSinkPath())) {
        throw new SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide a sink path to read token if you are using VaultAgent", USER);
      }
      if (isEmpty(vaultConfig.getDelegateSelectors())) {
        throw new SecretManagementException(VAULT_OPERATION_ERROR,
            "You must provide a delegate selector to read token if you are using VaultAgent", USER);
      }
    }
    // Need to try using Vault AppRole login to generate a client token if configured so
    if (vaultConfig.getAccessType() == APP_ROLE) {
      VaultAppRoleLoginResult loginResult = appRoleLogin(vaultConfig);
      if (loginResult != null && isNotBlank(loginResult.getClientToken())) {
        vaultConfig.setAuthToken(loginResult.getClientToken());
      } else {
        String message =
            "Was not able to login Vault using the AppRole auth method. Please check your credentials and try again";
        throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
      }
    }

    if (vaultConfig.isUseK8sAuth()) {
      if (isBlank(vaultConfig.getVaultK8sAuthRole())) {
        throw new io.harness.exception.SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide vault role if you are using Vault with K8s Auth method", USER);
      }
      if (isBlank(vaultConfig.getServiceAccountTokenPath())) {
        throw new io.harness.exception.SecretManagementException(VAULT_OPERATION_ERROR,
            "You must provide service account token path if you are using Vault with K8s Auth method", USER);
      }
      if (isEmpty(vaultConfig.getDelegateSelectors())) {
        throw new io.harness.exception.SecretManagementException(VAULT_OPERATION_ERROR,
            "You must provide a delegate selector to read service account token if you are using K8s Auth method",
            USER);
      }
    }
  }

  private void validateRenewalIntervalBasedOnTokenLookup(VaultConfig vaultConfig) {
    if (!isTaskSupportedByDelegate(vaultConfig.getAccountId(), "VAULT_TOKEN_LOOKUP")) {
      log.info("Delegates do not support VAULT_TOKEN_LOOKUP task for this account {}", vaultConfig.getAccountId());
      return;
    }
    // we have to call the token self-lookup api and then figure out this is renewable or not
    VaultTokenLookupResult tokenLookupResult = tokenLookup(vaultConfig);
    if (tokenLookupResult.getExpiryTime() == null) {
      // this means this is root token
      throw new SecretManagementException(
          "The token used is a root token. Please set renewal interval as zero if you are using root token.");
    }
    if (!tokenLookupResult.isRenewable()) {
      // this means the token is not renewable
      throw new SecretManagementException(
          "The token used is a non-renewable token. Please set renewal interval as zero or use a renewable token.");
    }
  }

  private boolean isTokenBased(VaultConfig vaultConfig) {
    return !vaultConfig.isUseK8sAuth() && !vaultConfig.isUseVaultAgent() && !vaultConfig.isUseAwsIam()
        && vaultConfig.getAccessType() != APP_ROLE;
  }

  public void validateVaultConfig(String accountId, VaultConfig vaultConfig) {
    validateVaultConfig(accountId, vaultConfig, true);
  }

  @Override
  public void validateVaultConfig(String accountId, VaultConfig vaultConfig, boolean validateBySavingDummySecret) {
    validateVaultFields(vaultConfig);
    if (!vaultConfig.isReadOnly() && validateBySavingDummySecret) {
      vaultEncryptorsRegistry.getVaultEncryptor(EncryptionType.VAULT)
          .createSecret(accountId, VaultConfig.VAULT_VAILDATION_URL, Boolean.TRUE.toString(), vaultConfig);
    }
    // if config is token based and the renewalInterval is not set to zero, lets check if that is root token and modify
    // the interval based on that
    if (isTokenBased(vaultConfig) && (vaultConfig.getRenewalInterval() != 0)) {
      validateRenewalIntervalBasedOnTokenLookup(vaultConfig);
    }
  }

  @Override
  public VaultConfig getVaultConfig(String accountId, String entityId) {
    return (VaultConfig) getBaseVaultConfig(accountId, entityId);
  }

  @Override
  public Optional<SecretManagerConfig> updateRuntimeCredentials(
      SecretManagerConfig secretManagerConfig, Map<String, String> runtimeParameters, boolean shouldUpdateVaultConfig) {
    if (isEmpty(secretManagerConfig.getTemplatizedFields()) || isEmpty(runtimeParameters)) {
      return Optional.empty();
    }

    VaultConfig vaultConfig = (VaultConfig) kryoSerializer.clone(secretManagerConfig);
    for (String templatizedField : secretManagerConfig.getTemplatizedFields()) {
      String templatizedFieldValue = runtimeParameters.get(templatizedField);
      if (isBlank(templatizedFieldValue)) {
        return Optional.empty();
      }
      if (templatizedField.equals(BaseVaultConfigKeys.appRoleId)) {
        vaultConfig.setAppRoleId(templatizedFieldValue);
      } else if (templatizedField.equals(BaseVaultConfigKeys.secretId)) {
        vaultConfig.setSecretId(templatizedFieldValue);
      } else if (templatizedField.equals(BaseVaultConfigKeys.authToken)) {
        vaultConfig.setAuthToken(templatizedFieldValue);
      }
    }
    if (shouldUpdateVaultConfig) {
      updateVaultConfig(vaultConfig.getAccountId(), vaultConfig, false, true);
    } else if (secretManagerConfig.getTemplatizedFields().contains(BaseVaultConfigKeys.appRoleId)
        || secretManagerConfig.getTemplatizedFields().contains(BaseVaultConfigKeys.secretId)) {
      VaultAppRoleLoginResult loginResult = appRoleLogin(vaultConfig);
      vaultConfig.setAuthToken(loginResult.getClientToken());
    }
    return Optional.of(vaultConfig);
  }
}
