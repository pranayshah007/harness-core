/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH_HINT;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ArtifactoryCommandUnitHandler extends ArtifactCommandUnitHandler {
  private SecretDecryptionService secretDecryptionService;
  private ArtifactoryRequestMapper artifactoryRequestMapper;
  private ArtifactoryNgService artifactoryNgService;

  @Inject
  public ArtifactoryCommandUnitHandler(SecretDecryptionService secretDecryptionService,
      ArtifactoryRequestMapper artifactoryRequestMapper, ArtifactoryNgService artifactoryNgService) {
    this.secretDecryptionService = secretDecryptionService;
    this.artifactoryRequestMapper = artifactoryRequestMapper;
    this.artifactoryNgService = artifactoryNgService;
  }

  @Override
  public InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    ArtifactoryArtifactDelegateConfig artifactoryArtifactConfig = getArtifactoryArtifactDelegateConfig(context);
    ArtifactoryConfigRequest artifactoryConfigRequest =
        getArtifactConfigRequest(artifactoryArtifactConfig, logCallback);
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    String artifactPath =
        Paths.get(artifactoryArtifactConfig.getRepositoryName(), artifactoryArtifactConfig.getArtifactPath())
            .toString();
    artifactMetadata.put(ArtifactMetadataKeys.artifactPath, artifactPath);
    artifactMetadata.put(ArtifactMetadataKeys.artifactName, artifactPath);
    logCallback.saveExecutionLog(
        color(format("Downloading %s artifact with identifier: %s", artifactoryArtifactConfig.getArtifactType(),
                  artifactoryArtifactConfig.getIdentifier()),
            White, Bold));
    logCallback.saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    try {
      return artifactoryNgService.downloadArtifacts(artifactoryConfigRequest,
          artifactoryArtifactConfig.getRepositoryName(), artifactMetadata, ArtifactMetadataKeys.artifactPath,
          ArtifactMetadataKeys.artifactName);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from artifactory", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download artifact from artifactory. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(SshExceptionConstants.ARTIFACT_DOWNLOAD_HINT, artifactoryArtifactConfig.getArtifactType()),
          format(SshExceptionConstants.ARTIFACT_DOWNLOAD_EXPLANATION, artifactoryArtifactConfig.getIdentifier(),
              artifactoryArtifactConfig.getArtifactType()),
          new SshCommandExecutionException(format(SshExceptionConstants.ARTIFACT_DOWNLOAD_FAILED,
              artifactoryArtifactConfig.getArtifactType(), artifactoryArtifactConfig.getIdentifier())));
    }
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    ArtifactoryArtifactDelegateConfig artifactoryArtifactConfig = getArtifactoryArtifactDelegateConfig(context);
    ArtifactoryConfigRequest artifactoryConfigRequest =
        getArtifactConfigRequest(artifactoryArtifactConfig, logCallback);
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    String artifactPath =
        Paths.get(artifactoryArtifactConfig.getRepositoryName(), artifactoryArtifactConfig.getArtifactPath())
            .toString();
    artifactMetadata.put(ArtifactMetadataKeys.artifactPath, artifactPath);
    artifactMetadata.put(ArtifactMetadataKeys.artifactName, artifactPath);

    return artifactoryNgService.getFileSize(
        artifactoryConfigRequest, artifactMetadata, ArtifactMetadataKeys.artifactPath);
  }

  private ArtifactoryArtifactDelegateConfig getArtifactoryArtifactDelegateConfig(SshExecutorFactoryContext context) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = context.getArtifactDelegateConfig();
    if (!(artifactDelegateConfig instanceof ArtifactoryArtifactDelegateConfig)) {
      log.warn("Wrong artifact delegate config submitted");
      throw new InvalidRequestException("Expecting artifactory delegate config");
    }

    return (ArtifactoryArtifactDelegateConfig) artifactDelegateConfig;
  }

  private ArtifactoryConfigRequest getArtifactConfigRequest(
      ArtifactoryArtifactDelegateConfig artifactoryArtifactConfig, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(artifactoryArtifactConfig.getArtifactPath())) {
      logCallback.saveExecutionLog(
          "artifactPath or artifactPathFilter is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(EMPTY_ARTIFACT_PATH_HINT,
          format(EMPTY_ARTIFACT_PATH_EXPLANATION, artifactoryArtifactConfig.getIdentifier()),
          new SshCommandExecutionException(EMPTY_ARTIFACT_PATH));
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) artifactoryArtifactConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryArtifactConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        artifactoryConnectorDTO, artifactoryArtifactConfig.getEncryptedDataDetails());

    return artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);
  }
}
