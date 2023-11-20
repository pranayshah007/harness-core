/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FileData;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifactBundle.response.ArtifactBundleFetchResponse;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.filesystem.FileIo;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.mchange.v1.db.sql.UnsupportedTypeException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Slf4j
@OwnedBy(CDP)
public class ArtifactBundleFetchTask extends AbstractDelegateRunnableTask {
  @Inject private ArtifactBundleFetchTaskHelper artifactBundleFetchTaskHelper;

  private static final String NOT_DIR_ERROR_MSG = "Not a directory";

  public ArtifactBundleFetchTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public ArtifactBundleFetchResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ArtifactBundleFetchResponse run(TaskParameters parameters) {
    ArtifactBundleFetchRequest artifactBundleFetchRequest = (ArtifactBundleFetchRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = artifactBundleFetchRequest.getCommandUnitsProgress() != null
        ? artifactBundleFetchRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    try {
      log.info("Running ArtifactBundleFetchRequest for activityId {}", artifactBundleFetchRequest.getActivityId());

      LogCallback executionLogCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(),
          K8sCommandUnitConstants.FetchFiles, artifactBundleFetchRequest.isShouldOpenLogStream(), commandUnitsProgress);

      Map<String, List<FileData> > filesFromArtifactBundle;
      ArtifactBundleDelegateConfig artifactBundleDelegateConfig =
          artifactBundleFetchRequest.getArtifactBundleDelegateConfig();

      executionLogCallback.saveExecutionLog(
          color(format("%nStarting Artifact Bundle Fetch Files"), LogColor.White, LogWeight.Bold));

      try {
        filesFromArtifactBundle = fetchManifestsFromFromArtifactBundle(
            artifactBundleDelegateConfig, executionLogCallback, artifactBundleFetchRequest.getActivityId());
      } catch (Exception ex) {
        if (isFileNotFound(ex)) {
          log.info("file not found. " + getMessage(ex), ex);
          executionLogCallback.saveExecutionLog(color(format("file not found. " + getMessage(ex), ex), White));
        }

        String msg = "Exception in processing ArtifactBundleFetchFilesTask. " + getMessage(ex);
        log.error(msg, ex);
        executionLogCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.FAILURE);
        throw ex;
      }

      executionLogCallback.saveExecutionLog(
          color(format("%n Artifact Bundle Fetch Files completed successfully."), LogColor.White, LogWeight.Bold),
          INFO);

      if (artifactBundleFetchRequest.isCloseLogStream()) {
        executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      }
      return ArtifactBundleFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .filesFromArtifactBundle(filesFromArtifactBundle)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception exception) {
      log.error("Exception in Artifact Bundle Fetch Files Task", exception);
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), exception);
    }
  }

  private boolean isFileNotFound(Exception ex) {
    return isANoSuchFileException(ex) || isANotDirectoryException(ex);
  }

  private boolean isANoSuchFileException(Exception ex) {
    return ex.getCause() instanceof NoSuchFileException;
  }

  private boolean isANotDirectoryException(Exception ex) {
    return ex.getCause() instanceof FileSystemException && EmptyPredicate.isNotEmpty(ex.getCause().getMessage())
        && ex.getCause().getMessage().contains(NOT_DIR_ERROR_MSG);
  }

  private Map<String, List<FileData> > fetchManifestsFromFromArtifactBundle(
      ArtifactBundleDelegateConfig artifactBundleDelegateConfig, LogCallback executionLogCallback, String activityId)
      throws IOException, UnsupportedTypeException {
    File workingDirectory = artifactBundleFetchTaskHelper.generateWorkingDirectoryForDeployment();
    executionLogCallback.saveExecutionLog(
        color(format("%n Starting Downloading Artifact Bundle."), LogColor.White, LogWeight.Bold), INFO);
    File artifactBundleFile = artifactBundleFetchTaskHelper.downloadArtifactFile(
        artifactBundleDelegateConfig.getPackageArtifactConfig(), workingDirectory, executionLogCallback);

    if (artifactBundleFile == null) {
      throw new IOException("Failed to download Artifact Bundle from the Artifact source");
    }
    executionLogCallback.saveExecutionLog(
        color(format("%n Successfully Downloaded Artifact Bundle."), LogColor.White, LogWeight.Bold), INFO);
    FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
    return artifactBundleFetchTaskHelper.getManifestFilesFromArtifactBundle(
        workingDirectory, artifactBundleFile, artifactBundleDelegateConfig, activityId, executionLogCallback);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
