/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactDelegateConfig;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactFileName;
import static io.harness.delegate.utils.NexusUtils.getNexusVersion;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.delegate.utils.NexusVersion;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.nexus.NexusRequest;

import software.wings.helpers.ext.nexus.NexusService;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class NexusArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  private NexusService nexusService;
  private NexusMapper nexusMapper;

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    NexusArtifactDelegateConfig nexusArtifactDelegateConfig =
        getNexusArtifactDelegateConfig(context.getArtifactDelegateConfig());
    NexusVersion nexusVersion = getNexusVersion(nexusArtifactDelegateConfig);
    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusArtifactDelegateConfig);
    String artifactUrl = nexusArtifactDelegateConfig.getArtifactUrl();

    logCallback.saveExecutionLog(color(format("Downloading %s artifact with identifier: %s", nexusVersion.name(),
                                           nexusArtifactDelegateConfig.getIdentifier()),
        White, Bold));
    logCallback.saveExecutionLog("Nexus Artifact Url: " + artifactUrl);
    try {
      String artifactName = getNexusArtifactFileName(
          nexusVersion, nexusArtifactDelegateConfig.getRepositoryFormat(), nexusArtifactDelegateConfig.getMetadata());
      return nexusService.downloadArtifactByUrl(nexusRequest, artifactName, artifactUrl).getValue();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading artifact from Nexus", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download artifact from Nexus. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(SshExceptionConstants.NEXUS_ARTIFACT_DOWNLOAD_HINT,
          format(SshExceptionConstants.NEXUS_ARTIFACT_DOWNLOAD_EXPLANATION, nexusVersion),
          new SshCommandExecutionException(format(
              SshExceptionConstants.NEXUS_ARTIFACT_DOWNLOAD_FAILED, nexusArtifactDelegateConfig.getIdentifier())));
    }
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    NexusArtifactDelegateConfig nexusArtifactDelegateConfig =
        getNexusArtifactDelegateConfig(context.getArtifactDelegateConfig());
    NexusVersion nexusVersion = getNexusVersion(nexusArtifactDelegateConfig);
    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusArtifactDelegateConfig);

    String artifactName = getNexusArtifactFileName(
        nexusVersion, nexusArtifactDelegateConfig.getRepositoryFormat(), nexusArtifactDelegateConfig.getMetadata());
    context.getArtifactMetadata().put(ArtifactMetadataKeys.artifactName, artifactName);

    return nexusService.getFileSize(nexusRequest, artifactName, nexusArtifactDelegateConfig.getArtifactUrl());
  }
}
