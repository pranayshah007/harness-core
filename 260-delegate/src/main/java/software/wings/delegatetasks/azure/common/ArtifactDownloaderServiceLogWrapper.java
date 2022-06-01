/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.common.validator.Validators;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class ArtifactDownloaderServiceLogWrapper {
  @Inject private ArtifactDownloaderService artifactDownloaderService;

  public File fetchArtifactFileForDeploymentAndLog(
      ArtifactDownloaderContext artifactDownloaderContext, ILogStreamingTaskClient logStreamingTaskClient) {
    Validators.validateJsr380FailFast(artifactDownloaderContext, InvalidRequestException::new);
    log.info("Start fetching artifact file,  artifactName: {}",
        artifactDownloaderContext.getArtifactStreamAttributes().getArtifactName());
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback("FETCH_ARTIFACT_FILE");

    try {
      logCallback.saveExecutionLog("Fetching artifact file");
      File artifactFile = downloadArtifactFile(artifactDownloaderContext, logCallback);
      logCallback.saveExecutionLog("Artifact file fetched successfully", LogLevel.INFO, SUCCESS);

      return artifactFile;
    } catch (Exception ex) {
      logCallback.saveExecutionLog("Failed to fetch artifact file", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  private File downloadArtifactFile(ArtifactDownloaderContext artifactDownloaderContext, LogCallback logCallback) {
    ArtifactStreamAttributes artifactStreamAttributes = artifactDownloaderContext.getArtifactStreamAttributes();
    return artifactStreamAttributes.isMetadataOnly()
        ? downloadArtifactOnDelegateAndLog(artifactDownloaderContext, logCallback)
        : downloadArtifactFromManagerAndLog(artifactDownloaderContext, logCallback);
  }

  private File downloadArtifactOnDelegateAndLog(
      ArtifactDownloaderContext artifactDownloaderContext, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("Start downloading artifact file %nArtifact file: %s",
        artifactDownloaderContext.getArtifactStreamAttributes().getArtifactName()));
    File artifactFile = artifactDownloaderService.downloadArtifactFile(artifactDownloaderContext);
    logCallback.saveExecutionLog("Artifact file downloaded successfully");

    return artifactFile;
  }

  private File downloadArtifactFromManagerAndLog(
      ArtifactDownloaderContext artifactDownloaderContext, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("Start downloading artifact file from manager%nArtifact file: %s",
        artifactDownloaderContext.getArtifactStreamAttributes().getArtifactName()));
    File artifactFile = artifactDownloaderService.downloadArtifactFileFromManager(artifactDownloaderContext);
    logCallback.saveExecutionLog("Artifact file downloaded successfully");

    return artifactFile;
  }
}
