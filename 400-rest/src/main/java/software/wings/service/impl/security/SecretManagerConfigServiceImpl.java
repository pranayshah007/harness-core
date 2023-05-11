/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_FROM_SM;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_TO_SM;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGER_ID_NOT_FOUND;
import static io.harness.eraro.ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HPersistence.returnNewOptions;
import static io.harness.security.encryption.AccessType.APP_ROLE;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.LocalEncryptionConfig.HARNESS_DEFAULT_SECRET_MANAGER;
import static software.wings.service.intfc.security.SecretManager.CREATED_AT_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secretmanagers.SecretsManagerRBACService;
import io.harness.secrets.SecretService;
import io.harness.secrets.SecretsDao;
import io.harness.security.encryption.EncryptionType;
import io.harness.templatizedsm.RuntimeCredentialsInjector;

import software.wings.beans.Account;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.VaultConfig;
import software.wings.dl.WingsPersistence;
import software.wings.security.UsageRestrictions;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.AwsSecretsManagerService;
import software.wings.service.intfc.security.AzureSecretsManagerService;
import software.wings.service.intfc.security.CustomSecretsManagerService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.service.intfc.security.GcpSecretsManagerServiceV2;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * @author marklu on 2019-05-31
 */
@OwnedBy(PL)
@ValidateOnExecution
@Singleton
@Slf4j
public class SecretManagerConfigServiceImpl implements SecretManagerConfigService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private KmsService kmsService;
  @Inject private GcpSecretsManagerService gcpSecretsManagerService;
  @Inject private GcpSecretsManagerServiceV2 gcpSecretsManagerServiceV2;
  @Inject private VaultService vaultService;
  @Inject private AwsSecretsManagerService secretsManagerService;
  @Inject private LocalSecretManagerService localSecretManagerService;
  @Inject private AzureSecretsManagerService azureSecretsManagerService;
  @Inject private CustomSecretsManagerService customSecretsManagerService;
  @Inject private SecretsManagerRBACService secretsManagerRBACService;
  @Inject private SecretsDao secretsDao;
  @Inject private SecretService secretService;
  @Inject @Named("hashicorpvault") private RuntimeCredentialsInjector vaultRuntimeCredentialsInjector;
  @Inject private SSHVaultService sshVaultService;

  @Override
  public String save(SecretManagerConfig secretManagerConfig) {
    String accountId = secretManagerConfig.getAccountId();
    log.info("Saving secret manager {} of type {} from account {}", secretManagerConfig.getUuid(),
        secretManagerConfig.getEncryptionType(), accountId);

    // Need to unset other secret managers if the current one to be saved is default.
    if (secretManagerConfig.isDefault()) {
      clearDefaultFlagOfSecretManagers(accountId);
    }

    if (secretManagerConfig.isGlobalKms()) {
      secretManagerConfig.setUsageRestrictions(
          localSecretManagerService.getEncryptionConfig(accountId).getUsageRestrictions());
    }
    secretManagerConfig.setScopedToAccount(false);
    if (isEmpty(secretManagerConfig.getUuid())) {
      secretsManagerRBACService.canSetPermissions(accountId, secretManagerConfig);
    } else {
      SecretManagerConfig oldConfig = wingsPersistence.get(SecretManagerConfig.class, secretManagerConfig.getUuid());
      secretsManagerRBACService.canChangePermissions(accountId, secretManagerConfig, oldConfig);
      if (!(oldConfig.getUsageRestrictions() != null && secretManagerConfig.getUsageRestrictions() != null
              && oldConfig.getUsageRestrictions().getAppEnvRestrictions().equals(
                  secretManagerConfig.getUsageRestrictions().getAppEnvRestrictions()))) {
        secretService.updateConflictingSecretsToInheritScopes(accountId, secretManagerConfig);
      }
    }

    //[PL-11328] DO NOT remove this innocent redundant looking line which is actually setting the encryptionType.
    secretManagerConfig.setEncryptionType(secretManagerConfig.getEncryptionType());
    return wingsPersistence.save(secretManagerConfig);
  }

  @Override
  public boolean delete(String accountId, SecretManagerConfig secretManagerConfig) {
    if (!secretsManagerRBACService.hasAccessToEditSM(accountId, secretManagerConfig)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "You are not authorized to delete this secret manager", USER);
    }
    return wingsPersistence.delete(SecretManagerConfig.class, secretManagerConfig.getUuid());
  }

  @Override
  public void clearDefaultFlagOfSecretManagers(String accountId) {
    Query<SecretManagerConfig> updateQuery = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                 .filter(SecretManagerConfigKeys.accountId, accountId)
                                                 .field(SecretManagerConfigKeys.ngMetadata)
                                                 .equal(null);
    UpdateOperations<SecretManagerConfig> updateOperations =
        wingsPersistence.createUpdateOperations(SecretManagerConfig.class)
            .set(SecretManagerConfigKeys.isDefault, false);
    wingsPersistence.update(updateQuery, updateOperations);
    log.info("Set all other secret managers as non-default in account {}", accountId);
  }

  @Override
  public String getSecretManagerName(String kmsId, String accountId) {
    SecretManagerConfig secretManagerConfig = getSecretManagerInternal(accountId, kmsId);
    if (secretManagerConfig != null) {
      return secretManagerConfig.getName();
    } else {
      log.warn("Secret manager with id {} for account {} can't be resolved.", kmsId, accountId);
      return null;
    }
  }

  @Override
  public SecretManagerConfig getDefaultSecretManager(String accountId) {
    SecretManagerConfig secretManagerConfig = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                  .field(SecretManagerConfigKeys.accountId)
                                                  .equal(accountId)
                                                  .filter(SecretManagerConfigKeys.isDefault, true)
                                                  .field(SecretManagerConfigKeys.ngMetadata)
                                                  .equal(null)
                                                  .get();

    if (secretManagerConfig != null) {
      decryptEncryptionConfigSecrets(accountId, secretManagerConfig, false);
      secretManagerConfig.setDefault(true);
      return secretManagerConfig;
    }
    secretManagerConfig = getGlobalSecretManager(accountId);
    if (secretManagerConfig == null) {
      return localSecretManagerService.getEncryptionConfig(accountId);
    } else {
      return secretManagerConfig;
    }
  }

  @Override
  public SecretManagerConfig getGlobalSecretManager(String accountId) {
    SecretManagerConfig secretManagerConfig = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                  .field(SecretManagerConfigKeys.accountId)
                                                  .equal(GLOBAL_ACCOUNT_ID)
                                                  .field(SecretManagerConfigKeys.encryptionType)
                                                  .equal(EncryptionType.GCP_KMS)
                                                  .get();

    if (secretManagerConfig == null) {
      secretManagerConfig = wingsPersistence.createQuery(SecretManagerConfig.class)
                                .field(SecretManagerConfigKeys.accountId)
                                .equal(GLOBAL_ACCOUNT_ID)
                                .field(SecretManagerConfigKeys.encryptionType)
                                .equal(EncryptionType.KMS)
                                .get();
    }

    if (secretManagerConfig != null) {
      decryptEncryptionConfigSecrets(accountId, secretManagerConfig, false);
      secretManagerConfig.setDefault(true);
    }
    return secretManagerConfig;
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String entityId) {
    return getSecretManager(accountId, entityId, false);
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String kmsId, EncryptionType encryptionType) {
    if (encryptionType == LOCAL && isEmpty(kmsId)) {
      kmsId = accountId;
    }
    return isEmpty(kmsId) ? getDefaultSecretManager(accountId) : getSecretManager(accountId, kmsId);
  }

  @Override
  public SecretManagerConfig getSecretManager(
      String accountId, String kmsId, EncryptionType encryptionType, Map<String, String> runtimeParameters) {
    SecretManagerConfig secretManagerConfig = getSecretManager(accountId, kmsId, encryptionType);
    if (null == secretManagerConfig) {
      String message = String.format("Secret manager with id %s for account %s can't be found.", kmsId, accountId);
      throw new SecretManagementException(SECRET_MANAGER_ID_NOT_FOUND, message, USER);
    }
    if (secretManagerConfig.isTemplatized()) {
      updateRuntimeParameters(secretManagerConfig, runtimeParameters, true);
    }
    updateConfigForAppRoleTokenRenewal(secretManagerConfig, accountId);
    return secretManagerConfig;
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String entityId, boolean maskSecrets) {
    SecretManagerConfig secretManagerConfig = getSecretManagerInternal(accountId, entityId);
    if (secretManagerConfig != null) {
      decryptEncryptionConfigSecrets(accountId, secretManagerConfig, maskSecrets);
      secretManagerConfig.setNumOfEncryptedValue(getEncryptedDataCount(accountId, entityId));
    }
    updateConfigForAppRoleTokenRenewal(secretManagerConfig, accountId);
    return secretManagerConfig;
  }

  @Override
  public SecretManagerConfig getSecretManagerByName(
      String accountId, String entityName, EncryptionType encryptionType, boolean maskSecrets) {
    SecretManagerConfig secretManagerConfig = getSecretManagerInternalByName(accountId, entityName, encryptionType);
    if (secretManagerConfig != null) {
      decryptEncryptionConfigSecrets(accountId, secretManagerConfig, maskSecrets);
      secretManagerConfig.setNumOfEncryptedValue(getEncryptedDataCount(accountId, secretManagerConfig.getUuid()));
    }
    return secretManagerConfig;
  }

  @Override
  public SecretManagerConfig getSecretManagerByName(String accountId, String name) {
    return getSecretManagerInternalByName(accountId, name);
  }

  private SecretManagerConfig getSecretManagerInternal(String accountId, String entityId) {
    if (entityId.equals(accountId)) {
      return localSecretManagerService.getEncryptionConfig(accountId);
    } else {
      return wingsPersistence.createQuery(SecretManagerConfig.class)
          .field(SecretManagerConfigKeys.accountId)
          .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
          .field(SecretManagerConfigKeys.ID_KEY)
          .equal(entityId)
          .field(SecretManagerConfigKeys.ngMetadata)
          .equal(null)
          .get();
    }
  }

  private SecretManagerConfig getSecretManagerInternalByName(
      String accountId, String entityName, EncryptionType encryptionType) {
    if (entityName.equals(HARNESS_DEFAULT_SECRET_MANAGER) && encryptionType == LOCAL) {
      return localSecretManagerService.getEncryptionConfig(accountId);
    } else {
      return wingsPersistence.createQuery(SecretManagerConfig.class)
          .field(SecretManagerConfigKeys.accountId)
          .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
          .disableValidation()
          .field(SecretManagerConfigKeys.name)
          .equal(entityName)
          .enableValidation()
          .field(SecretManagerConfigKeys.encryptionType)
          .equal(encryptionType)
          .field(SecretManagerConfigKeys.ngMetadata)
          .equal(null)
          .get();
    }
  }

  private SecretManagerConfig getSecretManagerInternalByName(String accountId, String secretMangerName) {
    if (secretMangerName.equals(HARNESS_DEFAULT_SECRET_MANAGER)) {
      return localSecretManagerService.getEncryptionConfig(accountId);
    } else {
      return wingsPersistence.createQuery(SecretManagerConfig.class)
          .field(SecretManagerConfigKeys.accountId)
          .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
          .disableValidation()
          .field(SecretManagerConfigKeys.name)
          .equal(secretMangerName)
          .field(SecretManagerConfigKeys.ngMetadata)
          .equal(null)
          .enableValidation()
          .get();
    }
  }

  private void updateConfigForAppRoleTokenRenewal(SecretManagerConfig secretManagerConfig, String accountId) {
    if ((secretManagerConfig instanceof BaseVaultConfig)
        && (((BaseVaultConfig) secretManagerConfig).getAccessType() == APP_ROLE)
        && ((BaseVaultConfig) secretManagerConfig).getRenewAppRoleToken()
        && (accountService.isFeatureFlagEnabled(FeatureName.DO_NOT_RENEW_APPROLE_TOKEN.name(), accountId))) {
      ((BaseVaultConfig) secretManagerConfig).setRenewAppRoleToken(false);
    }
  }

  /**
   * required for admin portal
   * @param accountIds
   * @param includeGlobalSecretManager
   * @return
   */
  @Override
  public List<Integer> getCountOfSecretManagersForAccounts(
      List<String> accountIds, boolean includeGlobalSecretManager) {
    SecretManagerConfig globalSecretManagerConfig = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                        .filter(SecretManagerConfigKeys.accountId, GLOBAL_ACCOUNT_ID)
                                                        .get();

    List<SecretManagerConfig> secretManagerConfigList = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                            .field(SecretManagerConfigKeys.accountId)
                                                            .in(accountIds)
                                                            .field(SecretManagerConfigKeys.ngMetadata)
                                                            .equal(null)
                                                            .asList();

    Map<String, Integer> countOfSecretManagersPerAccount = accountIds.stream().collect(
        Collectors.toMap(accountId -> accountId, accountId -> globalSecretManagerConfig != null ? 1 : 0));
    secretManagerConfigList.forEach(secretManagerConfig -> {
      int secretManagerCount = countOfSecretManagersPerAccount.get(secretManagerConfig.getAccountId());
      countOfSecretManagersPerAccount.put(secretManagerConfig.getAccountId(), secretManagerCount + 1);
    });

    return accountIds.stream().map(countOfSecretManagersPerAccount::get).collect(Collectors.toList());
  }

  // This method will decrypt the secret manager's encrypted fields
  public void decryptEncryptionConfigSecrets(
      String accountId, SecretManagerConfig secretManagerConfig, boolean maskSecrets) {
    EncryptionType encryptionType = secretManagerConfig.getEncryptionType();
    boolean isCertValidationRequired = accountService.isCertValidationRequired(accountId);
    switch (encryptionType) {
      case KMS:
        kmsService.decryptKmsConfigSecrets(accountId, (KmsConfig) secretManagerConfig, maskSecrets);
        break;
      case GCP_KMS:
        gcpSecretsManagerService.decryptGcpConfigSecrets((GcpKmsConfig) secretManagerConfig, maskSecrets);
        break;
      case VAULT:
        vaultService.decryptVaultConfigSecrets(accountId, (VaultConfig) secretManagerConfig, maskSecrets);
        ((VaultConfig) secretManagerConfig).setCertValidationRequired(isCertValidationRequired);
        break;
      case VAULT_SSH:
        sshVaultService.decryptVaultConfigSecrets(accountId, (SSHVaultConfig) secretManagerConfig, maskSecrets);
        ((SSHVaultConfig) secretManagerConfig).setCertValidationRequired(isCertValidationRequired);
        break;
      case AWS_SECRETS_MANAGER:
        secretsManagerService.decryptAsmConfigSecrets(
            accountId, (AwsSecretsManagerConfig) secretManagerConfig, maskSecrets);
        break;
      case GCP_SECRETS_MANAGER:
        gcpSecretsManagerServiceV2.decryptGcpConfigSecrets((GcpSecretsManagerConfig) secretManagerConfig, maskSecrets);
        break;
      case AZURE_VAULT:
        azureSecretsManagerService.decryptAzureConfigSecrets((AzureVaultConfig) secretManagerConfig, maskSecrets);
        break;
      case CUSTOM:
        customSecretsManagerService.setAdditionalDetails((CustomSecretsManagerConfig) secretManagerConfig);
        break;
      case LOCAL:
        break;
      default:
        throw new IllegalArgumentException("Encryption type " + encryptionType + " is not valid");
    }
  }

  @Override
  public EncryptionType getEncryptionBySecretManagerId(String kmsId, String accountId) {
    SecretManagerConfig secretManager = getSecretManagerInternal(accountId, kmsId);
    if (secretManager == null) {
      String errorMessage =
          String.format("Secret manager with id %s for account %s can't be resolved.", kmsId, accountId);
      throw new SecretManagementException(RESOURCE_NOT_FOUND, errorMessage, USER);
    }
    return secretManager.getEncryptionType();
  }

  @Override
  public EncryptionType getEncryptionType(String accountId) {
    final EncryptionType encryptionType;
    if (isLocalEncryptionEnabled(accountId)) {
      // HAR-8025: Respect the account level 'localEncryptionEnabled' configuration.
      encryptionType = LOCAL;
    } else {
      SecretManagerConfig defaultSecretManagerConfig = getDefaultSecretManager(accountId);
      if (defaultSecretManagerConfig == null) {
        encryptionType = LOCAL;
      } else {
        encryptionType = defaultSecretManagerConfig.getEncryptionType();
      }
    }
    return encryptionType;
  }

  @Override
  public List<SecretManagerConfig> listSecretManagers(String accountId, boolean maskSecret) {
    return listSecretManagers(accountId, maskSecret, true);
  }

  @Override
  public List<SecretManagerConfig> listSecretManagers(
      String accountId, boolean maskSecret, boolean includeGlobalSecretManager) {
    // encryptionType null means all secret manager types.
    return listSecretManagersInternal(accountId, null, maskSecret, includeGlobalSecretManager);
  }

  @Override
  public List<SecretManagerConfig> listSecretManagersByType(
      String accountId, EncryptionType encryptionType, boolean maskSecret) {
    return listSecretManagersInternal(accountId, encryptionType, maskSecret, true);
  }

  @Override
  public SecretManagerConfig updateRuntimeParameters(
      SecretManagerConfig secretManagerConfig, Map<String, String> runtimeParameters, boolean shouldUpdateVaultConfig) {
    Optional<SecretManagerConfig> updatedSecretManagerConfig =
        getRuntimeCredentialsInjectorInstance(secretManagerConfig.getEncryptionType())
            .updateRuntimeCredentials(secretManagerConfig, runtimeParameters, shouldUpdateVaultConfig);
    if (!updatedSecretManagerConfig.isPresent()) {
      throw new InvalidRequestException("values of one or more run time fields are missing.");
    }
    return updatedSecretManagerConfig.get();
  }

  @Override
  public void updateUsageRestrictions(String accountId, String secretManagerId, UsageRestrictions usageRestrictions) {
    UpdateOperations<SecretManagerConfig> updateOperations =
        wingsPersistence.createUpdateOperations(SecretManagerConfig.class);
    if (isEmpty(usageRestrictions.getAppEnvRestrictions())) {
      updateOperations.unset(SecretManagerConfigKeys.usageRestrictions);
    } else {
      updateOperations.set(SecretManagerConfigKeys.usageRestrictions, usageRestrictions);
    }
    Query<SecretManagerConfig> query = wingsPersistence.createQuery(SecretManagerConfig.class)
                                           .filter(SecretManagerConfigKeys.accountId, accountId)
                                           .filter(SecretManagerConfigKeys.ID_KEY, secretManagerId)
                                           .field(SecretManagerConfigKeys.ngMetadata)
                                           .equal(null);
    wingsPersistence.findAndModify(query, updateOperations, returnNewOptions);
  }

  @Override
  public UsageRestrictions getMaximalAllowedScopes(String accountId, String secretsManagerId) {
    SecretManagerConfig secretManagerConfig = getSecretManager(accountId, secretsManagerId, true);
    if (secretManagerConfig == null) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "No secret manager with the given secretManagerId", USER);
    }
    return secretsManagerRBACService.getMaximalAllowedScopes(accountId, secretManagerConfig);
  }

  @Override
  public void canTransitionSecrets(String accountId, SecretManagerConfig fromConfig, SecretManagerConfig toConfig) {
    if (!fromConfig.getSecretManagerCapabilities().contains(TRANSITION_SECRET_FROM_SM)) {
      String message = String.format("Cannot transfer secrets from %s secret manager", fromConfig.getName());
      throw new SecretManagementException(UNSUPPORTED_OPERATION_EXCEPTION, message, USER);
    } else if (!toConfig.getSecretManagerCapabilities().contains(TRANSITION_SECRET_TO_SM)) {
      String message = String.format("Cannot transfer secrets to %s secret manager", fromConfig.getName());
      throw new SecretManagementException(UNSUPPORTED_OPERATION_EXCEPTION, message, USER);
    }
    if (!secretsManagerRBACService.areUsageScopesSubset(accountId, fromConfig, toConfig)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "Scopes of destination secrets manager should completely include the scopes of source secrets manager.",
          USER);
    }
  }

  private List<SecretManagerConfig> listSecretManagersInternal(
      String accountId, EncryptionType encryptionType, boolean maskSecret, boolean includeGlobalSecretManager) {
    List<SecretManagerConfig> rv = new ArrayList<>();

    if (isLocalEncryptionEnabled(accountId)) {
      // If account level local encryption is enabled. Mask all other encryption configs.
      SecretManagerConfig localSecretManagerConfig = localSecretManagerService.getEncryptionConfig(accountId);
      localSecretManagerConfig.setNumOfEncryptedValue(
          getEncryptedDataCount(accountId, localSecretManagerConfig.getUuid()));
      rv.add(localSecretManagerConfig);
      return rv;
    } else {
      List<SecretManagerConfig> secretManagerConfigList = wingsPersistence.createQuery(SecretManagerConfig.class)
                                                              .field(SecretManagerConfigKeys.accountId)
                                                              .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                                              .field(SecretManagerConfigKeys.ngMetadata)
                                                              .equal(null)
                                                              .order(Sort.descending(CREATED_AT_KEY))
                                                              .asList();

      boolean defaultSet = false;
      List<SecretManagerConfig> globalSecretManagerConfigList = new ArrayList<>();
      for (SecretManagerConfig secretManagerConfig : secretManagerConfigList) {
        if ((secretManagerConfig.isGlobalKms() && !includeGlobalSecretManager)
            || (!secretManagerConfig.isGlobalKms()
                && !secretsManagerRBACService.hasAccessToReadSM(accountId, secretManagerConfig, null, null))) {
          continue;
        }
        if (encryptionType == null || secretManagerConfig.getEncryptionType() == encryptionType) {
          if (secretManagerConfig.isGlobalKms()) {
            globalSecretManagerConfigList.add(secretManagerConfig);
          } else {
            defaultSet = secretManagerConfig.isDefault() || defaultSet;
            rv.add(secretManagerConfig);
          }
          secretManagerConfig.setNumOfEncryptedValue(getEncryptedDataCount(accountId, secretManagerConfig.getUuid()));
          decryptEncryptionConfigSecrets(accountId, secretManagerConfig, maskSecret);
        }
      }
      SecretManagerConfig globalSecretManager = getDefaultGlobalSecretManager(globalSecretManagerConfigList, accountId);
      if (globalSecretManager != null) {
        globalSecretManager.setDefault(!defaultSet);
        rv.add(globalSecretManager);
      } else if (encryptionType == null) {
        SecretManagerConfig localSecretManagerConfig = localSecretManagerService.getEncryptionConfig(accountId);
        localSecretManagerConfig.setNumOfEncryptedValue(
            getEncryptedDataCount(accountId, localSecretManagerConfig.getUuid()));
        rv.add(localSecretManagerConfig);
      }
    }
    return rv;
  }

  private int getEncryptedDataCount(String accountId, String kmsId) {
    Query<EncryptedData> encryptedDataQuery = wingsPersistence.createQuery(EncryptedData.class)
                                                  .filter(EncryptedDataKeys.accountId, accountId)
                                                  .filter(EncryptedDataKeys.kmsId, kmsId);
    return (int) encryptedDataQuery.count();
  }

  private SecretManagerConfig getDefaultGlobalSecretManager(
      List<SecretManagerConfig> globalSecretManagerConfigList, String accountId) {
    if (globalSecretManagerConfigList.isEmpty()) {
      return null;
    }

    int total = calculateTotalCount(globalSecretManagerConfigList);

    Optional<SecretManagerConfig> optionalSecretManagerConfig =
        globalSecretManagerConfigList.stream()
            .filter(secretManagerConfig -> secretManagerConfig.getEncryptionType() == EncryptionType.GCP_KMS)
            .findFirst();
    if (optionalSecretManagerConfig.isPresent()) {
      SecretManagerConfig secretManagerConfig = optionalSecretManagerConfig.get();
      secretManagerConfig.setNumOfEncryptedValue(total);
      return secretManagerConfig;
    }

    optionalSecretManagerConfig =
        globalSecretManagerConfigList.stream()
            .filter(secretManagerConfig -> secretManagerConfig.getEncryptionType() == EncryptionType.KMS)
            .findFirst();
    if (optionalSecretManagerConfig.isPresent()) {
      SecretManagerConfig secretManagerConfig = optionalSecretManagerConfig.get();
      secretManagerConfig.setNumOfEncryptedValue(total);
      return secretManagerConfig;
    }

    return null;
  }

  private int calculateTotalCount(List<SecretManagerConfig> globalSecretManagerConfigList) {
    return globalSecretManagerConfigList.stream().mapToInt(SecretManagerConfig::getNumOfEncryptedValue).sum();
  }

  private boolean isLocalEncryptionEnabled(String accountId) {
    Account account = wingsPersistence.get(Account.class, accountId);
    return account != null && account.isLocalEncryptionEnabled();
  }

  private RuntimeCredentialsInjector getRuntimeCredentialsInjectorInstance(EncryptionType encryptionType) {
    if (encryptionType == VAULT) {
      return vaultRuntimeCredentialsInjector;
    }
    throw new UnsupportedOperationException("Runtime credentials not supported for encryption type: " + encryptionType);
  }
}
