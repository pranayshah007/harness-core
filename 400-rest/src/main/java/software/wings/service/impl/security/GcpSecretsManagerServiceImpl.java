/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GCP_KMS_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.helpers.GlobalSecretManagerUtils.isNgHarnessSecretManager;
import static io.harness.persistence.HPersistence.upToOne;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.settings.SettingVariableTypes.GCP_KMS;
import static software.wings.utils.Utils.isJSONValid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpKmsConfig.GcpKmsConfigKeys;
import software.wings.beans.User;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.security.GcpSecretsManagerService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
@Slf4j
public class GcpSecretsManagerServiceImpl extends AbstractSecretServiceImpl implements GcpSecretsManagerService {
  private static final String CREDENTIAL_SUFFIX = "_credentials";
  @Inject private HarnessUserGroupService harnessUserGroupService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private KmsEncryptorsRegistry kmsEncryptorsRegistry;

  @Override
  public GcpKmsConfig getGcpKmsConfig(String accountId, String configId) {
    GcpKmsConfig gcpKmsConfig = wingsPersistence.createQuery(GcpKmsConfig.class)
                                    .field(ID_KEY)
                                    .equal(configId)
                                    .field(SecretManagerConfigKeys.accountId)
                                    .in(Arrays.asList(accountId, GLOBAL_ACCOUNT_ID))
                                    .get();
    Preconditions.checkNotNull(
        gcpKmsConfig, String.format("GCP KMS config not found for id: %s in account: %s", configId, accountId));
    decryptGcpConfigSecrets(gcpKmsConfig, false);
    return gcpKmsConfig;
  }

  @Override
  public GcpKmsConfig getGlobalKmsConfig() {
    GcpKmsConfig gcpKmsConfig = wingsPersistence.createQuery(GcpKmsConfig.class)
                                    .field(SecretManagerConfigKeys.accountId)
                                    .equal(GLOBAL_ACCOUNT_ID)
                                    .field(SecretManagerConfigKeys.encryptionType)
                                    .equal(EncryptionType.GCP_KMS)
                                    .get();
    if (gcpKmsConfig == null) {
      return null;
    }
    decryptGcpConfigSecrets(gcpKmsConfig, false);
    return gcpKmsConfig;
  }

  @Override
  public String saveGcpKmsConfig(String accountId, GcpKmsConfig gcpKmsConfig, boolean validateByCreatingTestSecret) {
    validateUserInput(gcpKmsConfig, accountId);
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);
    gcpKmsConfig.setAccountId(accountId);
    return saveOrUpdateInternal(gcpKmsConfig, null, validateByCreatingTestSecret);
  }

  @Override
  public String updateGcpKmsConfig(String accountId, GcpKmsConfig gcpKmsConfig) {
    return updateGcpKmsConfig(accountId, gcpKmsConfig, true);
  }

  @Override
  public String updateGcpKmsConfig(String accountId, GcpKmsConfig gcpKmsConfig, boolean validate) {
    if (isEmpty(gcpKmsConfig.getUuid())) {
      String message = "Cannot have id as empty when updating secret manager configuration";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }

    GcpKmsConfig savedGcpKmsConfig =
        wingsPersistence.createQuery(GcpKmsConfig.class).field(ID_KEY).equal(gcpKmsConfig.getUuid()).get();

    if (savedGcpKmsConfig == null) {
      String message = "Could not find a secret manager with the given id";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }

    GcpKmsConfig oldConfigForAudit = kryoSerializer.clone(savedGcpKmsConfig);

    if (savedGcpKmsConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)) {
      accountId = GLOBAL_ACCOUNT_ID;
    } else if (!savedGcpKmsConfig.getAccountId().equals(accountId)) {
      String message = "Not allowed to change secret manager config for some other account";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }

    validateUserInput(gcpKmsConfig, accountId);
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);

    boolean updateCallWithMaskedSecretKey = false;
    savedGcpKmsConfig.setRegion(gcpKmsConfig.getRegion());
    savedGcpKmsConfig.setName(gcpKmsConfig.getName());
    savedGcpKmsConfig.setDefault(gcpKmsConfig.isDefault());
    savedGcpKmsConfig.setUsageRestrictions(gcpKmsConfig.getUsageRestrictions());
    savedGcpKmsConfig.setDelegateSelectors(gcpKmsConfig.getDelegateSelectors());

    if (SECRET_MASK.equals(String.valueOf(gcpKmsConfig.getCredentials()))) {
      updateCallWithMaskedSecretKey = true;
    } else {
      savedGcpKmsConfig.setCredentials(gcpKmsConfig.getCredentials());
    }

    if (updateCallWithMaskedSecretKey) {
      generateAuditForSecretManager(accountId, oldConfigForAudit, savedGcpKmsConfig);
      return secretManagerConfigService.save(savedGcpKmsConfig);
    }

    return saveOrUpdateInternal(savedGcpKmsConfig, oldConfigForAudit, validate);
  }

  private String saveOrUpdateInternal(
      GcpKmsConfig gcpKmsConfig, GcpKmsConfig oldKmsConfig, boolean validateByCreatingTestSecret) {
    if (validateByCreatingTestSecret) {
      validateSecretsManagerConfig(gcpKmsConfig.getAccountId(), gcpKmsConfig);
    }
    EncryptedData credentialEncryptedData = getEncryptedDataForSecretField(gcpKmsConfig, gcpKmsConfig.getCredentials());
    gcpKmsConfig.setCredentials(null);
    String gcpKmsConfigId;

    try {
      gcpKmsConfigId = secretManagerConfigService.save(gcpKmsConfig);
    } catch (DuplicateKeyException e) {
      String message = "Another GCP KMS secret configuration with the same display name exists";
      throw new DuplicateFieldException(message, USER_SRE, e);
    }
    String credentialEncryptedDataId = null;
    if (isNgHarnessSecretManager(gcpKmsConfig.getNgMetadata())) {
      EncryptedData globalSecretManagerCredentials = wingsPersistence.createQuery(EncryptedData.class)
                                                         .field(EncryptedDataKeys.accountId)
                                                         .equal(GLOBAL_ACCOUNT_ID)
                                                         .field(EncryptedDataKeys.type)
                                                         .equal(EncryptionType.GCP_KMS)
                                                         .get();

      if (globalSecretManagerCredentials != null) {
        credentialEncryptedDataId = globalSecretManagerCredentials.getUuid();
      }
    } else {
      credentialEncryptedDataId = saveSecretField(gcpKmsConfig, gcpKmsConfigId, credentialEncryptedData);
    }
    if (credentialEncryptedDataId == null) {
      String message = "Failed to save Encrypted Data for GCP KMS credentials. Please retry saving the configuration.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
    gcpKmsConfig.setCredentials(credentialEncryptedDataId.toCharArray());

    generateAuditForSecretManager(gcpKmsConfig.getAccountId(), oldKmsConfig, gcpKmsConfig);
    return secretManagerConfigService.save(gcpKmsConfig);
  }

  private void checkIfValidUser(String accountId) {
    User existingUser = UserThreadLocal.get();

    if (accountId.equals(GLOBAL_ACCOUNT_ID)) {
      if (!harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
        String message = "Not authorized to add a Global KMS";
        throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
      }
    } else {
      boolean checkIfUserInAccount =
          existingUser.getAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
      if (!checkIfUserInAccount) {
        String message = "You cannot add a secret manager for some other account";
        throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
      }
    }
  }

  private void validateUserInput(GcpKmsConfig gcpKmsConfig, String accountId) {
    // TODO{karan} Refactor this valid user/principal check later
    if (gcpKmsConfig.getNgMetadata() == null) {
      checkIfValidUser(accountId);
    }

    Pattern nameValidator = Pattern.compile("^[0-9a-zA-Z-' !]+$");
    Pattern keyValidator = Pattern.compile("^[0-9a-zA-Z-_]+$");
    Pattern locationValidator = Pattern.compile("^[0-9a-zA-Z-]+$");

    if (EmptyPredicate.isEmpty(gcpKmsConfig.getName()) || !nameValidator.matcher(gcpKmsConfig.getName()).find()) {
      String message =
          "Name cannot be empty and can only have alphanumeric, hyphen, single inverted comma, space and exclamation mark characters.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
    if (EmptyPredicate.isEmpty(gcpKmsConfig.getProjectId())
        || !nameValidator.matcher(gcpKmsConfig.getProjectId()).find()) {
      String message =
          "Project name cannot be empty and can only have alphanumeric, hyphen, single inverted comma, space and exclamation mark characters.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
    if (EmptyPredicate.isEmpty(gcpKmsConfig.getKeyRing()) || !keyValidator.matcher(gcpKmsConfig.getKeyRing()).find()) {
      String message = "Key ring cannot be empty and can only have alphanumeric, hyphen and underscore characters";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
    if (EmptyPredicate.isEmpty(gcpKmsConfig.getKeyName()) || !keyValidator.matcher(gcpKmsConfig.getKeyName()).find()) {
      String message = "Key name cannot be empty and can only have alphanumeric, hyphen and underscore characters.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
    if (EmptyPredicate.isEmpty(gcpKmsConfig.getRegion())
        || !locationValidator.matcher(gcpKmsConfig.getRegion()).find()) {
      String message = "Location cannot be empty and can only have alphanumeric and hyphen characters.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }

    if (EmptyPredicate.isEmpty(gcpKmsConfig.getCredentials())) {
      String message = "Credentials file is not uploaded.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }

    String credentialsString = String.valueOf(gcpKmsConfig.getCredentials());
    if (!credentialsString.equals(SECRET_MASK) && !isJSONValid(credentialsString)) {
      String message = "Credentials file is not valid JSON.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER_SRE);
    }
  }

  private String saveSecretField(GcpKmsConfig gcpKmsConfig, String configId, EncryptedData secretFieldEncryptedData) {
    String secretFieldEncryptedDataId = null;
    if (secretFieldEncryptedData != null) {
      secretFieldEncryptedData.setNgMetadata(getNgEncryptedDataMetadata(gcpKmsConfig));
      secretFieldEncryptedData.setAccountId(gcpKmsConfig.getAccountId());
      secretFieldEncryptedData.addParent(
          EncryptedDataParent.createParentRef(configId, GcpKmsConfig.class, GcpKmsConfigKeys.credentials, GCP_KMS));
      secretFieldEncryptedData.setType(GCP_KMS);
      secretFieldEncryptedData.setName(gcpKmsConfig.getName() + CREDENTIAL_SUFFIX);
      secretFieldEncryptedDataId = wingsPersistence.save(secretFieldEncryptedData);
    }
    return secretFieldEncryptedDataId;
  }

  private EncryptedData getEncryptedDataForSecretField(GcpKmsConfig gcpKmsConfig, char[] credentials) {
    if (isEmpty(credentials)) {
      return null;
    }
    EncryptedData encryptedData = GLOBAL_ACCOUNT_ID.equals(gcpKmsConfig.getAccountId())
        ? encryptLocal(credentials)
        : encryptUsingBaseAlgo(gcpKmsConfig.getAccountId(), credentials);
    if (encryptedData != null) {
      // Get by auth token encrypted record by Id or name.
      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
      query.criteria(EncryptedDataKeys.accountId)
          .equal(gcpKmsConfig.getAccountId())
          .or(query.criteria(ID_KEY).equal(gcpKmsConfig.getCredentials()),
              query.criteria(EncryptedDataKeys.name).equal(gcpKmsConfig.getName() + CREDENTIAL_SUFFIX));
      EncryptedData savedEncryptedData = query.get();
      if (savedEncryptedData != null) {
        savedEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
        savedEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
        encryptedData = savedEncryptedData;
      }
    }
    return encryptedData;
  }

  @Override
  public boolean deleteGcpKmsConfig(String accountId, String configId) {
    long count = wingsPersistence.createQuery(EncryptedData.class)
                     .filter(EncryptedDataKeys.accountId, accountId)
                     .filter(EncryptedDataKeys.kmsId, configId)
                     .filter(EncryptedDataKeys.encryptionType, EncryptionType.GCP_KMS)
                     .count(upToOne);
    if (count > 0) {
      String message = "Cannot delete the GCP KMS configuration since there are secrets encrypted with it. "
          + "Please transition your secrets to another secret manager and try again.";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, USER);
    }

    GcpKmsConfig gcpKmsConfig = wingsPersistence.createQuery(GcpKmsConfig.class)
                                    .field(ID_KEY)
                                    .equal(configId)
                                    .field(SecretManagerConfigKeys.accountId)
                                    .equal(accountId)
                                    .get();
    checkNotNull(gcpKmsConfig, "No GCP KMS configuration found with id " + configId);

    if (GLOBAL_ACCOUNT_ID.equals(gcpKmsConfig.getAccountId())) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Can not delete global KMS secret manager", USER);
    }

    if (isNotEmpty(gcpKmsConfig.getCredentials()) && !isNgHarnessSecretManager(gcpKmsConfig.getNgMetadata())) {
      wingsPersistence.delete(EncryptedData.class, String.valueOf(gcpKmsConfig.getCredentials()));
      log.info("Deleted encrypted auth token record {} associated with GCP KMS '{}'", gcpKmsConfig.getCredentials(),
          gcpKmsConfig.getName());
    }
    return deleteSecretManagerAndGenerateAudit(accountId, gcpKmsConfig);
  }

  @Override
  public void validateSecretsManagerConfig(String accountId, GcpKmsConfig gcpKmsConfig) {
    String randomString = UUIDGenerator.generateUuid();
    try {
      kmsEncryptorsRegistry.getKmsEncryptor(gcpKmsConfig)
          .encryptSecret(gcpKmsConfig.getAccountId(), randomString, gcpKmsConfig);
    } catch (Exception e) {
      String message = "Was not able to encrypt using given credentials. Please check your credentials and try again";
      throw new SecretManagementException(GCP_KMS_OPERATION_ERROR, message, e, USER);
    }
  }

  @Override
  public void decryptGcpConfigSecrets(GcpKmsConfig secretManagerConfig, boolean maskSecret) {
    if (maskSecret) {
      secretManagerConfig.maskSecrets();
    } else {
      GcpKmsConfig currentConfig = wingsPersistence.get(GcpKmsConfig.class, secretManagerConfig.getUuid());
      Preconditions.checkNotNull(
          currentConfig, "GCP KMS settings with id: " + secretManagerConfig.getUuid() + " not found in the database");
      Preconditions.checkNotNull(currentConfig.getCredentials(),
          "Credentials field for GCP KMS with id: " + currentConfig.getUuid() + " is null");
      String encryptedDataId = String.copyValueOf(currentConfig.getCredentials());
      EncryptedData secretData = wingsPersistence.get(EncryptedData.class, encryptedDataId);
      Preconditions.checkNotNull(secretData, "encrypted secret key can't be null for " + secretManagerConfig);
      secretManagerConfig.setCredentials(decryptUsingAlgoOfSecret(secretData));
    }
  }
}
