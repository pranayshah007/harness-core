/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.utils.GithubPackageUtils.getGithubPackagesArtifactDelegateConfig;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.delegate.utils.GithubPackageUtils;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.stream.StreamUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class GithubPackageArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  private GithubPackagesRegistryService githubPackagesRegistryService;
  private SecretDecryptionService secretDecryptionService;

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        getGithubPackagesArtifactDelegateConfig(context.getArtifactDelegateConfig());

    GithubConnectorDTO githubConnectorDTO =
        (GithubConnectorDTO) githubPackagesArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    GithubPackageUtils.decryptRequestDTOs(
        githubConnectorDTO, githubPackagesArtifactDelegateConfig.getEncryptedDataDetails(), secretDecryptionService);
    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubConnectorDTO);

    String artifactName = GithubPackageUtils.getArtifactFileName(
        githubPackagesArtifactDelegateConfig.getPackageType(), githubPackagesArtifactDelegateConfig.getMetadata());
    String artifactUrl = githubPackagesArtifactDelegateConfig.getArtifactUrl();

    logCallback.saveExecutionLog(color(format("Downloading GithubPackage artifact with identifier: %s",
                                           githubPackagesArtifactDelegateConfig.getIdentifier()),
        White, Bold));
    logCallback.saveExecutionLog("GithubPackage Artifact Url: " + artifactUrl);
    try {
      return githubPackagesRegistryService
          .downloadArtifactByUrl(githubPackagesInternalConfig, artifactName, artifactUrl)
          .getValue();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from GithubPackage", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download artifact from GithubPackage. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          SshExceptionConstants.GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_HINT,
          format(SshExceptionConstants.GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_EXPLANATION,
              githubPackagesArtifactDelegateConfig.getIdentifier()),
          new SshCommandExecutionException(format(SshExceptionConstants.GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_FAILED,
              githubPackagesArtifactDelegateConfig.getIdentifier())));
    }
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        getGithubPackagesArtifactDelegateConfig(context.getArtifactDelegateConfig());

    GithubConnectorDTO githubConnectorDTO =
        (GithubConnectorDTO) githubPackagesArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    GithubPackageUtils.decryptRequestDTOs(
        githubConnectorDTO, githubPackagesArtifactDelegateConfig.getEncryptedDataDetails(), secretDecryptionService);
    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubConnectorDTO);

    String artifactName = GithubPackageUtils.getArtifactFileName(
        githubPackagesArtifactDelegateConfig.getPackageType(), githubPackagesArtifactDelegateConfig.getMetadata());
    context.getArtifactMetadata().put(ArtifactMetadataKeys.artifactName, artifactName);

    String artifactUrl = githubPackagesArtifactDelegateConfig.getArtifactUrl();
    long size;
    Pair<String, InputStream> pair =
        githubPackagesRegistryService.downloadArtifactByUrl(githubPackagesInternalConfig, artifactName, artifactUrl);
    if (pair == null) {
      throw new InvalidArtifactServerException(format("Failed to get file size for artifact: [%s]", artifactUrl));
    }
    try {
      size = StreamUtils.getInputStreamSize(pair.getRight());
      pair.getRight().close();
    } catch (IOException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
    log.info(format("Computed file size: [%d] bytes for artifact Path: [%s]", size, artifactUrl));
    return size;
  }
}
