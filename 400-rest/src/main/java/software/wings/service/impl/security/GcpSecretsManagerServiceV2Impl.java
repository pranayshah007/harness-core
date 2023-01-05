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
import static io.harness.eraro.ErrorCode.GCP_SECRET_MANAGER_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.helpers.GlobalSecretManagerUtils.isNgHarnessSecretManager;
import static io.harness.persistence.HPersistence.upToOne;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.settings.SettingVariableTypes.GCP_SECRETS_MANAGER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.helpers.ext.gcp.GcpRegion;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.GcpSecretsManagerConfig.GcpSecretsManagerConfigKeys;
import software.wings.service.intfc.security.GcpSecretsManagerServiceV2;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
@Singleton
@Slf4j
public class GcpSecretsManagerServiceV2Impl extends AbstractSecretServiceImpl implements GcpSecretsManagerServiceV2 {
  private static final String CREDENTIAL_SUFFIX = "_credentials";
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public GcpSecretsManagerConfig getGcpSecretsManagerConfig(String accountId, String configId) {
    return getGcpSecretsManagerConfig(accountId, configId, true);
  }

  @NotNull
  private GcpSecretsManagerConfig getGcpSecretsManagerConfig(
      String accountIdParam, String configId, boolean decryptCrdentials) {
    GcpSecretsManagerConfig gcpKmsConfig = wingsPersistence.createQuery(GcpSecretsManagerConfig.class)
                                               .field(ID_KEY)
                                               .equal(configId)
                                               .field(SecretManagerConfigKeys.accountId)
                                               .in(Arrays.asList(accountIdParam, GLOBAL_ACCOUNT_ID))
                                               .get();
    Preconditions.checkNotNull(gcpKmsConfig,
        String.format("GCP Secrets Manager config not found for id: %s in account: %s", configId, accountIdParam));
    if (decryptCrdentials) {
      decryptGcpConfigSecrets(gcpKmsConfig, true);
    }
    return gcpKmsConfig;
  }

  @Override
  public String saveGcpSecretsManagerConfig(
      String accountId, GcpSecretsManagerConfig gcpSecretsManagerConfig, boolean validate) {
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);
    gcpSecretsManagerConfig.setAccountId(accountId);
    return saveInternal(accountId, gcpSecretsManagerConfig, validate);
  }

  @Override
  public String updateGcpSecretsManagerConfig(String accountId, GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    return updateGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, true);
  }

  private String updateGcpSecretsManagerConfig(
      String accountId, GcpSecretsManagerConfig gcpSecretsManagerConfig, boolean validate) {
    if (isEmpty(gcpSecretsManagerConfig.getUuid())) {
      String message = "Cannot have id as empty when updating secret manager configuration";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER_SRE);
    }

    GcpSecretsManagerConfig savedGcpSecretManagerConfig = wingsPersistence.createQuery(GcpSecretsManagerConfig.class)
                                                              .field(ID_KEY)
                                                              .equal(gcpSecretsManagerConfig.getUuid())
                                                              .get();

    if (savedGcpSecretManagerConfig == null) {
      String message = "Could not find a secret manager with the given id";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER_SRE);
    }

    GcpSecretsManagerConfig oldConfigForAudit = kryoSerializer.clone(savedGcpSecretManagerConfig);

    if (savedGcpSecretManagerConfig.getAccountId().equals(GLOBAL_ACCOUNT_ID)) {
      accountId = GLOBAL_ACCOUNT_ID;
    } else if (!savedGcpSecretManagerConfig.getAccountId().equals(accountId)) {
      String message = "Not allowed to change secret manager config for some other account";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER_SRE);
    }

    validateUserInput(gcpSecretsManagerConfig);
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);

    boolean updateCallWithMaskedSecretKey = false;

    savedGcpSecretManagerConfig.setName(gcpSecretsManagerConfig.getName());
    savedGcpSecretManagerConfig.setDefault(gcpSecretsManagerConfig.isDefault());
    savedGcpSecretManagerConfig.setUsageRestrictions(gcpSecretsManagerConfig.getUsageRestrictions());
    savedGcpSecretManagerConfig.setDelegateSelectors(gcpSecretsManagerConfig.getDelegateSelectors());

    if (SECRET_MASK.equals(String.valueOf(gcpSecretsManagerConfig.getCredentials()))) {
      updateCallWithMaskedSecretKey = true;
    } else {
      savedGcpSecretManagerConfig.setCredentials(gcpSecretsManagerConfig.getCredentials());
    }

    if (updateCallWithMaskedSecretKey) {
      generateAuditForSecretManager(accountId, oldConfigForAudit, savedGcpSecretManagerConfig);
      return secretManagerConfigService.save(savedGcpSecretManagerConfig);
    }

    return updateInternal(accountId, savedGcpSecretManagerConfig, validate);
  }

  @Override
  public boolean deleteGcpSecretsManagerConfig(String accountId, String configId) {
    long count = wingsPersistence.createQuery(EncryptedData.class)
                     .filter(EncryptedDataKeys.accountId, accountId)
                     .filter(EncryptedDataKeys.kmsId, configId)
                     .filter(EncryptedDataKeys.encryptionType, EncryptionType.GCP_SECRETS_MANAGER)
                     .count(upToOne);

    if (count > 0) {
      String message = "Cannot delete the Gcp Secrets Manager configuration since there are secrets encrypted with it. "
          + "Please transition your secrets to another secret manager and try again.";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER);
    }

    GcpSecretsManagerConfig secretsManagerConfig = wingsPersistence.get(GcpSecretsManagerConfig.class, configId);
    checkNotNull(secretsManagerConfig, "No Gcp Secrets Manager configuration found with id " + configId);

    if (isNotEmpty(secretsManagerConfig.getUuid())) {
      wingsPersistence.delete(EncryptedData.class, String.valueOf(secretsManagerConfig.getCredentials()));
      log.info("Deleted encrypted auth token record {} associated with Gcp Secrets Manager '{}'",
          secretsManagerConfig.getUuid(), secretsManagerConfig.getName());
    }

    return deleteSecretManagerAndGenerateAudit(accountId, secretsManagerConfig);
  }

  @Override
  public void validateSecretsManagerConfig(String accountId, GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    validateUserInput(gcpSecretsManagerConfig);
    try {
      GoogleCredentials credentials =
          GoogleCredentials
              .fromStream(new ByteArrayInputStream(String.valueOf(gcpSecretsManagerConfig.getCredentials()).getBytes()))
              .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
      FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
      SecretManagerServiceSettings settings =
          SecretManagerServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
      String projectId = getProjectId(credentials);
      try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
        ProjectName projectName = ProjectName.of(projectId);
        // Get all secrets.
        SecretManagerServiceClient.ListSecretsPagedResponse pagedResponse = client.listSecrets(projectName);
        // List all secrets.
        pagedResponse.iterateAll().forEach(secret
            -> {
                // do nothing as we are just testing connectivity
            });
      }
    } catch (IOException e) {
      String message =
          "Was not able to reach GCP Secrets Manager using given credentials. Please check your credentials and try again";
      throw new SecretManagementException(
          ErrorCode.GCP_SECRET_MANAGER_OPERATION_ERROR, message, e, WingsException.USER);
    }
    log.info("Test connection to GCP Secrets Manager V2 Succeeded for {}", gcpSecretsManagerConfig.getName());
  }

  private void validateUserInput(GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    Pattern nameValidator = Pattern.compile("^[0-9a-zA-Z-' !]+$");
    if (EmptyPredicate.isEmpty(gcpSecretsManagerConfig.getName())
        || !nameValidator.matcher(gcpSecretsManagerConfig.getName()).find()) {
      String message =
          "Name cannot be empty and can only have alphanumeric, hyphen, single inverted comma, space and exclamation mark characters.";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER_SRE);
    }
    if (EmptyPredicate.isEmpty(gcpSecretsManagerConfig.getCredentials())) {
      String message = "Credentials file is not uploaded.";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, USER_SRE);
    }
  }

  @Override
  public void decryptGcpConfigSecrets(GcpSecretsManagerConfig gcpSecretsManagerConfig, boolean maskSecret) {
    if (maskSecret) {
      gcpSecretsManagerConfig.maskSecrets();
    } else {
      GcpSecretsManagerConfig currentConfig =
          wingsPersistence.get(GcpSecretsManagerConfig.class, gcpSecretsManagerConfig.getUuid());
      Preconditions.checkNotNull(currentConfig,
          "GCP Secret Manager settings with id: " + gcpSecretsManagerConfig.getUuid() + " not found in the database");
      Preconditions.checkNotNull(currentConfig.getCredentials(),
          "Credentials field for GCP Secret Manager with id: " + currentConfig.getUuid() + " is null");
      String encryptedDataId = String.copyValueOf(currentConfig.getCredentials());
      EncryptedData secretData = wingsPersistence.get(EncryptedData.class, encryptedDataId);
      Preconditions.checkNotNull(secretData, "encrypted secret key can't be null for " + gcpSecretsManagerConfig);
      gcpSecretsManagerConfig.setCredentials(decryptUsingAlgoOfSecret(secretData));
    }
  }

  @Override
  public List<String> getAllAvailableRegions(String accountId, String configId) {
    return Arrays.stream(GcpRegion.values()).map(GcpRegion::getName).collect(Collectors.toList());
  }

  public String getProjectId(GoogleCredentials credentials) {
    if (credentials instanceof ServiceAccountCredentials) {
      return ((ServiceAccountCredentials) credentials).getProjectId();
    } else if (credentials instanceof UserCredentials) {
      return ((UserCredentials) credentials).getQuotaProjectId();
    } else {
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR,
          "Not able to extract Project Id from provided "
              + "credentials",
          USER_SRE);
    }
  }

  private String saveInternal(String accountId, GcpSecretsManagerConfig gcpSecretsManagerConfig, boolean validate) {
    if (validate) {
      validateSecretsManagerConfig(accountId, gcpSecretsManagerConfig);
    }
    char[] credentialsToEncrypt = gcpSecretsManagerConfig.getCredentials();
    String secretManagerConfigUuid = getSecretManagerUuid(gcpSecretsManagerConfig);
    EncryptedData credentialEncryptedData =
        getEncryptedDataForSecretField(gcpSecretsManagerConfig, credentialsToEncrypt);
    String encryptedDataId =
        getEncryptedDataId(gcpSecretsManagerConfig, secretManagerConfigUuid, credentialEncryptedData);
    if (encryptedDataId != null) {
      gcpSecretsManagerConfig.setCredentials(encryptedDataId.toCharArray());
      return saveConfig(gcpSecretsManagerConfig);
    }
    return "";
  }

  private String updateInternal(
      String accountId, GcpSecretsManagerConfig savedGcpSecretManagerConfig, boolean validate) {
    return saveInternal(accountId, savedGcpSecretManagerConfig, validate);
  }

  private String getSecretManagerUuid(GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    String secretManagerConfigUuid;
    if (!isEmpty(gcpSecretsManagerConfig.getUuid())) {
      secretManagerConfigUuid = gcpSecretsManagerConfig.getUuid();
    } else {
      gcpSecretsManagerConfig.setCredentials(null);
      secretManagerConfigUuid = saveConfig(gcpSecretsManagerConfig);
    }
    return secretManagerConfigUuid;
  }

  private String getEncryptedDataId(GcpSecretsManagerConfig gcpSecretsManagerConfig, String secretManagerConfigId,
      EncryptedData credentialEncryptedData) {
    String credentialEncryptedDataId = "";
    if (isNgHarnessSecretManager(gcpSecretsManagerConfig.getNgMetadata())) {
      EncryptedData globalSecretManagerCredentials = wingsPersistence.createQuery(EncryptedData.class)
                                                         .field(EncryptedDataKeys.accountId)
                                                         .equal(GLOBAL_ACCOUNT_ID)
                                                         .field(EncryptedDataKeys.type)
                                                         .equal(EncryptionType.GCP_SECRETS_MANAGER)
                                                         .get();

      if (globalSecretManagerCredentials != null) {
        credentialEncryptedDataId = globalSecretManagerCredentials.getUuid();
      }
    } else {
      credentialEncryptedDataId =
          saveSecretField(gcpSecretsManagerConfig, secretManagerConfigId, credentialEncryptedData);
    }
    return credentialEncryptedDataId;
  }

  private String saveSecretField(
      GcpSecretsManagerConfig gcpSecretsManagerConfig, String configId, EncryptedData secretFieldEncryptedData) {
    String secretFieldEncryptedDataId = null;
    if (secretFieldEncryptedData != null) {
      secretFieldEncryptedData.setAccountId(gcpSecretsManagerConfig.getAccountId());
      secretFieldEncryptedData.addParent(EncryptedDataParent.createParentRef(
          configId, GcpSecretsManagerConfig.class, GcpSecretsManagerConfigKeys.credentials, GCP_SECRETS_MANAGER));
      secretFieldEncryptedData.setType(GCP_SECRETS_MANAGER);
      secretFieldEncryptedData.setName(gcpSecretsManagerConfig.getName() + CREDENTIAL_SUFFIX);
      secretFieldEncryptedDataId = wingsPersistence.save(secretFieldEncryptedData);
    }
    return secretFieldEncryptedDataId;
  }

  private String saveConfig(GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    try {
      return secretManagerConfigService.save(gcpSecretsManagerConfig);
    } catch (DuplicateKeyException e) {
      String message = "Another GCP Secret Manager configuration with the same display name exists";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, e, USER_SRE);
    }
  }

  private EncryptedData getEncryptedDataForSecretField(
      GcpSecretsManagerConfig gcpSecretsManagerConfig, char[] credentials) {
    EncryptedData encryptedData =
        isNotEmpty(credentials) ? encryptUsingBaseAlgo(gcpSecretsManagerConfig.getAccountId(), credentials) : null;
    if (gcpSecretsManagerConfig != null && encryptedData != null) {
      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
      query.criteria(EncryptedDataKeys.accountId)
          .equal(gcpSecretsManagerConfig.getAccountId())
          .or(query.criteria(ID_KEY).equal(gcpSecretsManagerConfig.getCredentials()),
              query.criteria(EncryptedDataKeys.name).equal(gcpSecretsManagerConfig.getName() + CREDENTIAL_SUFFIX));
      EncryptedData savedEncryptedData = query.get();
      if (savedEncryptedData != null) {
        savedEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
        savedEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
        savedEncryptedData.setEncryptionType(encryptedData.getEncryptionType());
        savedEncryptedData.setKmsId(encryptedData.getKmsId());
        encryptedData = savedEncryptedData;
      }
    }
    return encryptedData;
  }
}
