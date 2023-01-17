/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.delegate.utils.AzureArtifactsUtils.getAuthHeader;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAzureArtifactDelegateConfig;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDecryptedToken;
import static io.harness.delegate.utils.AzureArtifactsUtils.getDownloadUrl;
import static io.harness.stream.StreamUtils.getInputStreamSize;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsDownloadHelper;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureArtifactsDownloadHelper azureArtifactsDownloadHelper;
  @Inject private AzureArtifactsHelper azureArtifactsHelper;

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig =
        getAzureArtifactDelegateConfig(context.getArtifactDelegateConfig());

    String artifactFileName =
        azureArtifactsHelper.getArtifactFileName(getAzureArtifactDelegateConfig(azureArtifactDelegateConfig));
    String downloadUrl = getDownloadUrl(artifactFileName, azureArtifactDelegateConfig);
    String authHeader = getAuthHeader(getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService));
    return azureArtifactsDownloadHelper.downloadArtifactByUrl(downloadUrl, authHeader);
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    AzureArtifactDelegateConfig azureArtifactDelegateConfig =
        getAzureArtifactDelegateConfig(context.getArtifactDelegateConfig());

    String decryptedToken = getDecryptedToken(azureArtifactDelegateConfig, secretDecryptionService);

    String artifactFileName =
        azureArtifactsHelper.getArtifactFileName(getAzureArtifactDelegateConfig(azureArtifactDelegateConfig));
    String downloadUrl = getDownloadUrl(artifactFileName, azureArtifactDelegateConfig);
    String authHeader = getAuthHeader(decryptedToken);
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    artifactMetadata.put(ArtifactMetadataKeys.artifactName, artifactFileName);
    long size;
    try (InputStream inputStream = azureArtifactsDownloadHelper.downloadArtifactByUrl(downloadUrl, authHeader)) {
      if (inputStream == null) {
        String message =
            format("Failed to get file size for artifact: %s", azureArtifactDelegateConfig.getArtifactPath());
        throw NestedExceptionUtils.hintWithExplanationException(HintException.HINT_AZURE_ARTIFACT_DOWNLOAD_FAILED,
            ExplanationException.AZURE_ARTIFACT_DOWNLOAD_FAILED,
            new InvalidRequestException(message, WingsException.USER));
      }
      size = getInputStreamSize(inputStream);
    } catch (IOException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
    log.info(format(
        "Computed file size: [%d] bytes for artifact Path: %s", size, azureArtifactDelegateConfig.getArtifactPath()));
    return size;
  }
}
