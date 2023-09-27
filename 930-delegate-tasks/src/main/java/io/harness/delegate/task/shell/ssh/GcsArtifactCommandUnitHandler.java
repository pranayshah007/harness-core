/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.BAD_ARTIFACT_TYPE;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.BAD_ARTIFACT_TYPE_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.BAD_ARTIFACT_TYPE_HINT;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.ssh.artifact.GoogleCloudStorageArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlecloudstorage.GcsHelperService;
import io.harness.googlecloudstorage.GcsInternalConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class GcsArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  @Inject private GcsHelperService gcsHelperService;
  @Inject private DecryptionHelper decryptionHelper;

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    validateGcsArtifactDelegateConfig(context);
    GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig =
        (GoogleCloudStorageArtifactDelegateConfig) context.getArtifactDelegateConfig();
    validateGcsArtifactPath(gcsArtifactDelegateConfig, logCallback);
    updateArtifactMetadata(context);
    return getGcsArtifactInputStream(gcsArtifactDelegateConfig, logCallback);
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    validateGcsArtifactDelegateConfig(context);
    GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig =
        (GoogleCloudStorageArtifactDelegateConfig) context.getArtifactDelegateConfig();
    validateGcsArtifactPath(gcsArtifactDelegateConfig, logCallback);
    updateArtifactMetadata(context);
    return getObjectSize(gcsArtifactDelegateConfig, logCallback);
  }

  private Long getObjectSize(
      GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig, LogCallback logCallback) {
    GcsInternalConfig gcsInternalConfig =
        getGcsInternalConfig(gcsArtifactDelegateConfig.getConnectorDTO(), gcsArtifactDelegateConfig);
    try {
      return gcsHelperService.getObjectSize(gcsInternalConfig, gcsArtifactDelegateConfig.getArtifactPath());
    } catch (Exception exception) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
      log.error("Error while calculating GCS artifact size", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to calculate GCS artifact size. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          SshExceptionConstants.GCS_ARTIFACT_CALCULATE_SIZE_FAILED_HINT,
          format(SshExceptionConstants.GCS_ARTIFACT_CALCULATE_SIZE_FAILED_EXPLANATION,
              gcsArtifactDelegateConfig.getArtifactPath(), gcsArtifactDelegateConfig.getBucket(),
              gcsArtifactDelegateConfig.getProject()),
          new SshCommandExecutionException(format(SshExceptionConstants.GCS_ARTIFACT_CALCULATE_SIZE_FAILED,
              gcsArtifactDelegateConfig.getArtifactPath(), gcsArtifactDelegateConfig.getBucket(),
              gcsArtifactDelegateConfig.getProject())));
    }
  }

  private InputStream getGcsArtifactInputStream(
      GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig, LogCallback logCallback) {
    GcsInternalConfig gcsInternalConfig =
        getGcsInternalConfig(gcsArtifactDelegateConfig.getConnectorDTO(), gcsArtifactDelegateConfig);
    try {
      return gcsHelperService.downloadObject(gcsInternalConfig, gcsArtifactDelegateConfig.getArtifactPath());
    } catch (Exception exception) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
      log.error("Error while fetching GCS artifact", sanitizedException);
      logCallback.saveExecutionLog("Failed to download GCS artifact. " + ExceptionUtils.getMessage(sanitizedException),
          ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.GCS_ARTIFACT_DOWNLOAD_HINT,
          format(SshExceptionConstants.GCS_ARTIFACT_DOWNLOAD_EXPLANATION, gcsArtifactDelegateConfig.getArtifactPath(),
              gcsArtifactDelegateConfig.getBucket(), gcsArtifactDelegateConfig.getProject()),
          new SshCommandExecutionException(
              format(SshExceptionConstants.GCS_ARTIFACT_DOWNLOAD_FAILED, gcsArtifactDelegateConfig.getArtifactPath(),
                  gcsArtifactDelegateConfig.getBucket(), gcsArtifactDelegateConfig.getProject())));
    }
  }

  private void decryptEntities(
      GcpConnectorDTO gcpConnectorDTO, GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig) {
    List<DecryptableEntity> decryptableEntities = gcpConnectorDTO.getDecryptableEntities();
    List<EncryptedDataDetail> encryptedDataDetails = gcsArtifactDelegateConfig.getEncryptedDataDetails();
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        decryptionHelper.decrypt(decryptableEntity, encryptedDataDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity, encryptedDataDetails);
      }
    }
  }

  private void updateArtifactMetadata(SshExecutorFactoryContext context) {
    GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig =
        (GoogleCloudStorageArtifactDelegateConfig) context.getArtifactDelegateConfig();
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    String artifactPath =
        Paths.get(gcsArtifactDelegateConfig.getBucket(), gcsArtifactDelegateConfig.getArtifactPath()).toString();

    artifactMetadata.put(io.harness.artifact.ArtifactMetadataKeys.artifactPath, artifactPath);
    artifactMetadata.put(ArtifactMetadataKeys.artifactName, artifactPath);
  }

  private void validateGcsArtifactDelegateConfig(SshExecutorFactoryContext context) {
    if (!(context.getArtifactDelegateConfig() instanceof GoogleCloudStorageArtifactDelegateConfig)) {
      throw NestedExceptionUtils.hintWithExplanationException(BAD_ARTIFACT_TYPE,
          format(BAD_ARTIFACT_TYPE_HINT, context.getArtifactDelegateConfig().getClass().getSimpleName()),
          new InvalidArgumentsException(Pair.of("artifact type",
              format(BAD_ARTIFACT_TYPE_EXPLANATION, GoogleCloudStorageArtifactDelegateConfig.class.getSimpleName()))));
    }
  }

  private void validateGcsArtifactPath(
      GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(gcsArtifactDelegateConfig.getArtifactPath())) {
      logCallback.saveExecutionLog(
          "Failed to copy artifact. Missing artifact path.", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_HINT,
          SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_EXPLANATION,
          new SshCommandExecutionException(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH));
    }
  }

  public GcsInternalConfig getGcsInternalConfig(
      ConnectorInfoDTO connectorInfoDTO, GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig) {
    if (connectorInfoDTO == null || connectorInfoDTO.getConnectorConfig() == null) {
      throw new InvalidArgumentsException("GCP Connector cannot be null");
    }
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorInfoDTO.getConnectorConfig();
    decryptEntities(gcpConnectorDTO, gcsArtifactDelegateConfig);
    boolean isUseDelegate = false;
    char[] serviceAccountKeyFileContent = new char[0];
    GcpConnectorCredentialDTO credential = gcpConnectorDTO.getCredential();
    if (credential == null) {
      throw new InvalidArgumentsException("GCP Connector credential cannot be null");
    }
    if (INHERIT_FROM_DELEGATE == credential.getGcpCredentialType()) {
      isUseDelegate = true;
    } else {
      SecretRefData secretRef = ((GcpManualDetailsDTO) credential.getConfig()).getSecretKeyRef();
      if (secretRef == null) {
        throw new SecretNotFoundRuntimeException("Invalid GCS credentials provided, failed to locate secret ref.");
      }

      if (secretRef.getDecryptedValue() == null) {
        throw new SecretNotFoundRuntimeException("Could not find secret " + secretRef.getIdentifier()
                + " under the scope of current " + secretRef.getScope(),
            secretRef.getIdentifier(), secretRef.getScope().toString(), connectorInfoDTO.getIdentifier());
      }
      serviceAccountKeyFileContent = secretRef.getDecryptedValue();
    }
    return GcsInternalConfig.builder()
        .serviceAccountKeyFileContent(serviceAccountKeyFileContent)
        .isUseDelegate(isUseDelegate)
        .bucket(gcsArtifactDelegateConfig.getBucket())
        .project(gcsArtifactDelegateConfig.getProject())
        .build();
  }
}
