/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.SecretConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GCP_SECRET_MANAGER_OPERATION_ERROR;
import static io.harness.eraro.ErrorCode.GCP_SECRET_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;

import static com.google.datastore.v1.client.DatastoreHelper.getProjectIdFromComputeEngine;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretText;
import io.harness.encryptors.VaultEncryptor;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.gcp.helpers.GcpHttpTransportHelperService;
import io.harness.network.Http;
import io.harness.secretmanagerclient.exception.SecretManagementClientException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.GcpSecretsManagerConfig;

import com.google.api.client.googleapis.auth.oauth2.OAuth2Utils;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.threeten.bp.Duration;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class GcpSecretsManagerEncryptor implements VaultEncryptor {
  public static final int MAX_RETRY_ATTEMPTS = 3;
  public static final int TOTAL_TIMEOUT_IN_SECONDS = 30;
  public static final String GCP_PROJECT = "GCP_PROJECT";
  private final TimeLimiter timeLimiter;

  @Inject
  public GcpSecretsManagerEncryptor(TimeLimiter timeLimiter) {
    this.timeLimiter = timeLimiter;
  }

  @Override
  public EncryptedRecord createSecret(
      String accountId, String name, String plaintext, EncryptionConfig encryptionConfig) {
    return createSecret(accountId, SecretText.builder().name(name).value(plaintext).build(), encryptionConfig);
  }

  @Override
  public EncryptedRecord updateSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    return updateSecret(
        accountId, SecretText.builder().name(name).value(plaintext).build(), existingRecord, encryptionConfig);
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    return renameSecret(accountId, SecretText.builder().name(name).build(), existingRecord, encryptionConfig);
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) encryptionConfig;
    GoogleCredentials googleCredentials = getGoogleCredentials(gcpSecretsManagerConfig);
    String projectId = getProjectId(googleCredentials);
    String secretId = existingRecord.getEncryptionKey();
    try (SecretManagerServiceClient client = getGcpSecretsManagerClient(gcpSecretsManagerConfig)) {
      // get secret name
      if (isNotEmpty(projectId) && isNotEmpty(secretId)) {
        SecretName secretName = SecretName.of(projectId, secretId);
        client.deleteSecret(secretName);
        log.info("deletion of key {} in GCP Secret Manager {} was successful.", existingRecord.getEncryptionKey(),
            ((GcpSecretsManagerConfig) encryptionConfig).toDTO(true));
        return true;
      } else {
        throw new SecretManagementException(
            GCP_SECRET_OPERATION_ERROR, "Cannot delete secret for Empty ProjectId or SecretId", WingsException.USER);
      }
    } catch (IOException e) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR, "Secret Deletion Failed", e, WingsException.USER);
    }
  }

  @Override
  public boolean validateReference(String accountId, String path, EncryptionConfig encryptionConfig) {
    return isNotEmpty(fetchSecretValue(accountId, EncryptedRecordData.builder().path(path).build(), encryptionConfig));
  }

  @Override
  public boolean validateReference(String accountId, SecretText secretText, EncryptionConfig encryptionConfig) {
    return isNotEmpty(fetchSecretValue(accountId,
        EncryptedRecordData.builder()
            .path(secretText.getPath())
            .encryptionKey(secretText.getName())
            .additionalMetadata(secretText.getAdditionalMetadata())
            .name(secretText.getName())
            .build(),
        encryptionConfig));
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) encryptionConfig;
    GoogleCredentials googleCredentials = getGoogleCredentials(gcpSecretsManagerConfig);
    String projectId = getProjectId(googleCredentials);
    try (SecretManagerServiceClient gcpSecretsManagerClient = getGcpSecretsManagerClient(gcpSecretsManagerConfig)) {
      SecretVersionName secretVersionName = null;
      if (isNotEmpty(encryptedRecord.getPath())) {
        String secretName;
        String version;
        if (encryptedRecord.getAdditionalMetadata() != null) {
          secretName = encryptedRecord.getPath();
          version = encryptedRecord.getAdditionalMetadata().getValues().get(VERSION).toString();
        } else {
          secretName = encryptedRecord.getEncryptionKey() != null ? encryptedRecord.getEncryptionKey()
                                                                  : encryptedRecord.getName();
          version = encryptedRecord.getPath();
        }
        if (isEmpty(secretName)) {
          throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR,
              "Secret Referencing Failed - Cannot Reference Secret in Gcp Secret Manager Without Name",
              WingsException.USER);
        }
        // referenced secret
        secretVersionName = SecretVersionName.of(projectId, secretName, version);
      } else if (isNotEmpty(encryptedRecord.getEncryptedValue())) {
        SecretVersionName latestVersionName =
            SecretVersionName.parse(String.valueOf(encryptedRecord.getEncryptedValue()));
        secretVersionName =
            SecretVersionName.of(projectId, encryptedRecord.getEncryptionKey(), latestVersionName.getSecretVersion());
      } else {
        throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR,
            "Secret Read Failed (Corrupt EncryptedRecord): One of EncryptedRecord.value "
                + "or EncryptedRecord.path must be set to resolve secret reference",
            WingsException.USER);
      }
      // Access the secret version.
      if (secretVersionName != null) {
        AccessSecretVersionResponse response = gcpSecretsManagerClient.accessSecretVersion(secretVersionName);
        String payload = response.getPayload().getData().toStringUtf8();
        return payload.toCharArray();
      }
    } catch (IOException e) {
      throw new SecretManagementClientException(
          GCP_SECRET_OPERATION_ERROR, "Secret Read Failed", e, WingsException.USER);
    }
    return null;
  }

  @Override
  public EncryptedRecord createSecret(String accountId, SecretText secretText, EncryptionConfig encryptionConfig) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) encryptionConfig;
    GoogleCredentials googleCredentials = getGoogleCredentials(gcpSecretsManagerConfig);
    String region = getRegionInformation(secretText);
    try (SecretManagerServiceClient gcpSecretsManagerClient = getGcpSecretsManagerClient(gcpSecretsManagerConfig)) {
      Replication replication = getReplication(region);
      Secret secret = Secret.newBuilder().setReplication(replication).build();
      String projectId = getProjectId(googleCredentials);
      ProjectName projectName = ProjectName.of(projectId);
      Secret createdSecret = gcpSecretsManagerClient.createSecret(projectName, secretText.getName(), secret);
      SecretPayload payload =
          SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(secretText.getValue())).build();
      // Add the secret version.
      SecretVersion version = gcpSecretsManagerClient.addSecretVersion(createdSecret.getName(), payload);
      return EncryptedRecordData.builder()
          .additionalMetadata(secretText.getAdditionalMetadata())
          .encryptionKey(secretText.getName())
          .encryptedValue(version.getName().toCharArray())
          .build();
    } catch (IOException e) {
      throw new SecretManagementClientException(
          GCP_SECRET_OPERATION_ERROR, "Secret Creation Failed", e, WingsException.USER);
    }
  }

  @Override
  public EncryptedRecord updateSecret(
      String accountId, SecretText secretText, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) encryptionConfig;
    checkIfSecretCanBeUpdated(secretText, existingRecord);
    GoogleCredentials googleCredentials = getGoogleCredentials(gcpSecretsManagerConfig);
    String region = getRegionInformation(secretText);
    try (SecretManagerServiceClient gcpSecretsManagerClient = getGcpSecretsManagerClient(gcpSecretsManagerConfig)) {
      Replication replication = getReplication(region);
      String projectId = getProjectId(googleCredentials);
      SecretName secretName = SecretName.of(projectId, existingRecord.getEncryptionKey());
      Secret existingSecret = gcpSecretsManagerClient.getSecret(secretName);
      if (existingSecret != null) {
        Secret secret = Secret.newBuilder().mergeFrom(existingSecret).setReplication(replication).build();
        SecretPayload payload =
            SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(secretText.getValue())).build();
        // Add the secret version.
        SecretVersion version = gcpSecretsManagerClient.addSecretVersion(secret.getName(), payload);
        existingRecord = EncryptedRecordData.builder()
                             .name(existingRecord.getName())
                             .additionalMetadata(secretText.getAdditionalMetadata())
                             .encryptionKey(existingRecord.getEncryptionKey())
                             .encryptedValue(version.getName().toCharArray())
                             .build();
        return existingRecord;
      }
    } catch (IOException e) {
      throw new SecretManagementClientException(
          GCP_SECRET_OPERATION_ERROR, "Secret Updation Failed", e, WingsException.USER);
    }
    return existingRecord;
  }

  private void checkIfSecretCanBeUpdated(SecretText secretText, EncryptedRecord existingRecord) {
    if (secretText.getName() != null && !secretText.getName().equals(existingRecord.getEncryptionKey())) {
      throw new SecretManagementException(
          GCP_SECRET_OPERATION_ERROR, "Renaming Secrets in GCP Secret Manager is not supported", USER);
    }
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, SecretText secretText, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    if (existingRecord.getName().equals(secretText.getName())) {
      return existingRecord;
    }
    throw new UnsupportedOperationException("Renaming Secrets in GCP Secret Manager is not supported");
  }

  @NotNull
  private Replication getReplication(String region) {
    Replication replication;
    if (region.isEmpty()) {
      replication = Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder().build()).build();
    } else {
      String[] regions = region.split(",");
      List<Replication.UserManaged.Replica> replicaList = new ArrayList<>();
      for (String regionValue : regions) {
        replicaList.add(Replication.UserManaged.Replica.newBuilder().setLocation(regionValue).build());
      }
      replication = Replication.newBuilder()
                        .setUserManaged(Replication.UserManaged.newBuilder().addAllReplicas(replicaList))
                        .build();
    }
    return replication;
  }

  private String getRegionInformation(SecretText secretText) {
    if (secretText.getAdditionalMetadata() != null) {
      return String.valueOf(secretText.getAdditionalMetadata().getValues().getOrDefault("regions", ""));
    }
    return "";
  }

  @VisibleForTesting
  public SecretManagerServiceClient getGcpSecretsManagerClient(GcpSecretsManagerConfig gcpSecretsManagerConfig)
      throws IOException {
    SecretManagerServiceSettings.Builder settingsBuilder = SecretManagerServiceSettings.newBuilder();
    settingsBuilder.createSecretSettings()
        .getRetrySettings()
        .toBuilder()
        .setMaxAttempts(MAX_RETRY_ATTEMPTS)
        .setTotalTimeout(Duration.ofSeconds(TOTAL_TIMEOUT_IN_SECONDS))
        .build();
    settingsBuilder.accessSecretVersionSettings()
        .getRetrySettings()
        .toBuilder()
        .setMaxAttempts(MAX_RETRY_ATTEMPTS)
        .setTotalTimeout(Duration.ofSeconds(TOTAL_TIMEOUT_IN_SECONDS))
        .build();
    settingsBuilder.getSecretSettings()
        .getRetrySettings()
        .toBuilder()
        .setMaxAttempts(MAX_RETRY_ATTEMPTS)
        .setTotalTimeout(Duration.ofSeconds(TOTAL_TIMEOUT_IN_SECONDS))
        .build();
    settingsBuilder.deleteSecretSettings()
        .getRetrySettings()
        .toBuilder()
        .setMaxAttempts(MAX_RETRY_ATTEMPTS)
        .setTotalTimeout(Duration.ofSeconds(TOTAL_TIMEOUT_IN_SECONDS))
        .build();
    if (BooleanUtils.isNotTrue(gcpSecretsManagerConfig.getAssumeCredentialsOnDelegate())) {
      FixedCredentialsProvider credentialsProvider =
          FixedCredentialsProvider.create(getGoogleCredentials(gcpSecretsManagerConfig));
      settingsBuilder.setCredentialsProvider(credentialsProvider);
    }
    SecretManagerServiceSettings settings = settingsBuilder.build();
    return SecretManagerServiceClient.create(settings);
  }

  @VisibleForTesting
  public GoogleCredentials getGoogleCredentials(GcpSecretsManagerConfig gcpSecretsManagerConfig) {
    try {
      if (BooleanUtils.isTrue(gcpSecretsManagerConfig.getAssumeCredentialsOnDelegate())) {
        return getApplicationDefaultCredentials();
      }
      if (gcpSecretsManagerConfig.getCredentials() == null) {
        throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR,
            "GCP Secret Manager credentials are missing. Please check if the credentials secret exists.", USER);
      }
      return GoogleCredentials
          .fromStream(new ByteArrayInputStream(String.valueOf(gcpSecretsManagerConfig.getCredentials()).getBytes()))
          .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
    } catch (IOException e) {
      throw new SecretManagementClientException(ErrorCode.GCP_SECRET_OPERATION_ERROR,
          "Not able to create Google Credentials from given Configuration " + gcpSecretsManagerConfig.getUuid(), e,
          USER);
    }
  }

  public static GoogleCredentials getApplicationDefaultCredentials() throws IOException {
    // support in case noProxy does not include metadata .google.internal
    return StringUtils.isNotEmpty(Http.getProxyHostName())
            && !Http.shouldUseNonProxy(OAuth2Utils.getMetadataServerUrl())
        ? GoogleCredentials.getApplicationDefault(GcpHttpTransportHelperService.getHttpTransportFactory())
        : GoogleCredentials.getApplicationDefault();
  }

  public String getProjectId(GoogleCredentials credentials) {
    if (credentials instanceof ServiceAccountCredentials) {
      return ((ServiceAccountCredentials) credentials).getProjectId();
    } else if (credentials instanceof UserCredentials) {
      return ((UserCredentials) credentials).getQuotaProjectId();
    } else {
      return getProjectIdFromComputeEngineOrEnvironment();
    }
  }

  private static String getProjectIdFromComputeEngineOrEnvironment() {
    // try to get project id from Compute Engine metadata
    String projectId = getProjectIdFromComputeEngine();
    if (isEmpty(projectId)) {
      // try to get Project ID from ENV variable GCP_PROJECT
      projectId = getProjectIdFromEnvironment();
    }

    if (isEmpty(projectId)) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR,
          "Not able to extract Project Id from provided "
              + "credentials or Compute Engine or Env variable" + GCP_PROJECT,
          USER_SRE);
    }
    return projectId;
  }

  private static String getProjectIdFromEnvironment() {
    return System.getenv(GCP_PROJECT);
  }

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = (GcpSecretsManagerConfig) encryptionConfig;
    try {
      GoogleCredentials credentials = getGoogleCredentials(gcpSecretsManagerConfig);
      SecretManagerServiceClient client = getGcpSecretsManagerClient(gcpSecretsManagerConfig);
      String projectId = getProjectId(credentials);
      ProjectName projectName = ProjectName.of(projectId);
      // Get all secrets.
      SecretManagerServiceClient.ListSecretsPagedResponse pagedResponse = client.listSecrets(projectName);
      // List all secrets.
      pagedResponse.iterateAll().forEach(secret
          -> {
              // do nothing as we are just testing connectivity
          });
    } catch (IOException e) {
      String message =
          "Was not able to reach GCP Secrets Manager using the given credentials. Please check your credentials and try again";
      throw new SecretManagementException(GCP_SECRET_MANAGER_OPERATION_ERROR, message, e, WingsException.USER);
    }
    log.info("Test connection to GCP Secrets Manager Succeeded for {}", gcpSecretsManagerConfig.getName());
    return true;
  }
}