/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.EncryptedData.EncryptedDataKeys;
import static io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.settings.SettingVariableTypes.VAULT_SSH;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.expression.SecretString;
import io.harness.helpers.ext.vault.SSHVaultAuthResult;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.persistence.HPersistence;
import io.harness.security.encryption.AccessType;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.BaseVaultConfig.BaseVaultConfigKeys;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class SSHVaultServiceImpl extends BaseVaultServiceImpl implements SSHVaultService {
  private static final int NUM_OF_RETRIES = 3;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private AccountService accountService;

  @Override
  public void decryptVaultConfigSecrets(String accountId, SSHVaultConfig sshVaultConfig, boolean maskSecret) {
    decryptVaultConfigSecretsInternal(sshVaultConfig, maskSecret);
  }

  @Override
  public List<SecretEngineSummary> listSecretEngines(SSHVaultConfig sshVaultConfig) {
    if (isNotEmpty(sshVaultConfig.getUuid())) {
      SSHVaultConfig savedVaultConfig = wingsPersistence.get(SSHVaultConfig.class, sshVaultConfig.getUuid());
      decryptVaultConfigSecretsInternal(savedVaultConfig, false);
      if (SecretString.SECRET_MASK.equals(sshVaultConfig.getAuthToken())) {
        sshVaultConfig.setAuthToken(savedVaultConfig.getAuthToken());
      }
      if (SecretString.SECRET_MASK.equals(sshVaultConfig.getSecretId())) {
        sshVaultConfig.setSecretId(savedVaultConfig.getSecretId());
      }
    }
    List<SecretEngineSummary> secretEngineSummaries = new ArrayList<>();
    for (SecretEngineSummary secretEngineSummary : listSecretEnginesInternal(sshVaultConfig)) {
      if (secretEngineSummary.getType() != null && secretEngineSummary.getType().equals("ssh")) {
        secretEngineSummaries.add(secretEngineSummary);
      }
    }
    return secretEngineSummaries;
  }

  @Override
  public SSHVaultConfig getSSHVaultConfig(String accountId, String entityId) {
    if (isEmpty(accountId) || isEmpty(entityId)) {
      return new SSHVaultConfig();
    }
    Query<BaseVaultConfig> query = wingsPersistence.createQuery(BaseVaultConfig.class)
                                       .filter(SecretManagerConfigKeys.accountId, accountId)
                                       .filter(ID_KEY, entityId);
    return (SSHVaultConfig) getVaultConfigInternal(query);
  }

  public String saveOrUpdateSSHVaultConfig(String accountId, SSHVaultConfig sshVaultConfig, boolean validate) {
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);

    return isEmpty(sshVaultConfig.getUuid()) ? saveVaultConfig(accountId, sshVaultConfig, validate)
                                             : updateVaultConfig(accountId, sshVaultConfig, true, validate);
  }

  private void validateSSHVaultConfig(SSHVaultConfig sshVaultConfig) {
    if (isEmpty(sshVaultConfig.getName())) {
      throw new SecretManagementException(VAULT_OPERATION_ERROR, "Name can not be empty", USER);
    }
    if (isEmpty(sshVaultConfig.getVaultUrl())) {
      throw new SecretManagementException(VAULT_OPERATION_ERROR, "SSH Vault URL can not be empty", USER);
    }
    if (isEmpty(sshVaultConfig.getSecretEngineName())) {
      throw new SecretManagementException(VAULT_OPERATION_ERROR, "SSH Secret engine name not specified", USER);
    }
    if (sshVaultConfig.getAccessType() == AccessType.APP_ROLE) {
      VaultAppRoleLoginResult loginResult = appRoleLogin(sshVaultConfig);
      if (loginResult != null && EmptyPredicate.isNotEmpty(loginResult.getClientToken())) {
        sshVaultConfig.setAuthToken(loginResult.getClientToken());
      } else {
        String message =
            "Not able to login Vault using the AppRole auth method. Please check your credentials and try again";
        throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
      }
    }
    SSHVaultAuthResult sshVaultAuthResult = sshVaultAuthResult(sshVaultConfig);
    if (StringUtils.isBlank(sshVaultAuthResult.getPublicKey())) {
      String message =
          "Not able to find config CA for given SSH secret engine. Please check your credentials and try again";
      throw new SecretManagementException(VAULT_OPERATION_ERROR, message, USER);
    }
  }

  @Override
  public SSHVaultAuthResult sshVaultAuthResult(SSHVaultConfig sshVaultConfig) {
    int failedAttempts = 0;
    boolean isCertValidationRequired = accountService.isCertValidationRequired(sshVaultConfig.getAccountId());
    sshVaultConfig.setCertValidationRequired(isCertValidationRequired);

    while (true) {
      try {
        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(sshVaultConfig.getAccountId())
                .timeout(Duration.ofSeconds(10).toMillis())
                .appId(GLOBAL_APP_ID)
                .correlationId(sshVaultConfig.getUuid())
                .orgIdentifier(sshVaultConfig.getProjectIdentifier())
                .projectIdentifier(sshVaultConfig.getProjectIdentifier())
                .ngTask(isNgTask(sshVaultConfig.getOrgIdentifier(), sshVaultConfig.getProjectIdentifier()))
                .build();

        return delegateProxyFactory.get(SecretManagementDelegateService.class, syncTaskContext)
            .validateSSHVault(sshVaultConfig);
      } catch (WingsException e) {
        failedAttempts++;
        log.info("SSH Vault authentication failed for Vault server {}. trial num: {}", sshVaultConfig.getName(),
            failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private String saveVaultConfig(String accountId, SSHVaultConfig sshVaultConfig, boolean validate) {
    if (validate) {
      validateSSHVaultConfig(sshVaultConfig);
    }
    String authToken = sshVaultConfig.getAuthToken();
    String secretId = sshVaultConfig.getSecretId();
    sshVaultConfig.setAccountId(accountId);

    try {
      sshVaultConfig.setAuthToken(null);
      sshVaultConfig.setSecretId(null);
      String sshVaultConfigId = secretManagerConfigService.save(sshVaultConfig);
      sshVaultConfig.setUuid(sshVaultConfigId);
    } catch (DuplicateKeyException e) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Another SSH vault configuration with the same name or URL exists", e, USER_SRE);
    }

    saveVaultCredentials(sshVaultConfig, authToken, secretId, VAULT_SSH);

    Query<SecretManagerConfig> query =
        wingsPersistence.createQuery(SecretManagerConfig.class).field(ID_KEY).equal(sshVaultConfig.getUuid());

    UpdateOperations<SecretManagerConfig> updateOperations =
        wingsPersistence.createUpdateOperations(SecretManagerConfig.class)
            .set(BaseVaultConfigKeys.authToken, sshVaultConfig.getAuthToken());
    if (isNotEmpty(sshVaultConfig.getSecretId())) {
      updateOperations.set(BaseVaultConfigKeys.secretId, sshVaultConfig.getSecretId());
    }
    sshVaultConfig =
        (SSHVaultConfig) wingsPersistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    generateAuditForSecretManager(accountId, null, sshVaultConfig);
    return sshVaultConfig.getUuid();
  }

  private String updateVaultConfig(
      String accountId, SSHVaultConfig sshVaultConfig, boolean auditChanges, boolean validate) {
    SSHVaultConfig savedVaultConfigWithCredentials = getSSHVaultConfig(accountId, sshVaultConfig.getUuid());
    SSHVaultConfig oldConfigForAudit = wingsPersistence.get(SSHVaultConfig.class, sshVaultConfig.getUuid());
    SSHVaultConfig savedVaultConfig = kryoSerializer.clone(oldConfigForAudit);
    // Replaced masked secrets with the real secret value.
    if (SECRET_MASK.equals(sshVaultConfig.getAuthToken())) {
      sshVaultConfig.setAuthToken(savedVaultConfigWithCredentials.getAuthToken());
    }
    if (SECRET_MASK.equals(sshVaultConfig.getSecretId())) {
      sshVaultConfig.setSecretId(savedVaultConfigWithCredentials.getSecretId());
    }

    boolean credentialChanged =
        !Objects.equals(savedVaultConfigWithCredentials.getAuthToken(), sshVaultConfig.getAuthToken())
        || !Objects.equals(savedVaultConfigWithCredentials.getSecretId(), sshVaultConfig.getSecretId());

    // Validate every time when secret manager config change submitted
    if (validate) {
      validateSSHVaultConfig(sshVaultConfig);
    }

    if (credentialChanged) {
      updateVaultCredentials(savedVaultConfig, sshVaultConfig, VAULT_SSH);
    }

    savedVaultConfig.setName(sshVaultConfig.getName());
    savedVaultConfig.setSecretEngineName(sshVaultConfig.getSecretEngineName());
    savedVaultConfig.setAppRoleId(sshVaultConfig.getAppRoleId());
    savedVaultConfig.setVaultUrl(sshVaultConfig.getVaultUrl());
    savedVaultConfig.setRenewalInterval(sshVaultConfig.getRenewalInterval());
    savedVaultConfig.setEngineManuallyEntered(sshVaultConfig.isEngineManuallyEntered());
    savedVaultConfig.setTemplatizedFields(sshVaultConfig.getTemplatizedFields());
    savedVaultConfig.setUsageRestrictions(sshVaultConfig.getUsageRestrictions());
    savedVaultConfig.setScopedToAccount(sshVaultConfig.isScopedToAccount());
    // PL-3237: Audit secret manager config changes.
    if (auditChanges) {
      generateAuditForSecretManager(accountId, oldConfigForAudit, savedVaultConfig);
    }
    return secretManagerConfigService.save(savedVaultConfig);
  }

  public boolean deleteSSHVaultConfig(String accountId, String sshVaultConfigId) {
    long count = wingsPersistence.createQuery(EncryptedData.class)
                     .filter(SecretManagerConfigKeys.accountId, accountId)
                     .filter(EncryptedDataKeys.kmsId, sshVaultConfigId)
                     .filter(EncryptedDataKeys.encryptionType, EncryptionType.VAULT_SSH)
                     .count(upToOne);
    return deleteVaultConfigInternal(accountId, sshVaultConfigId, count);
  }
}
