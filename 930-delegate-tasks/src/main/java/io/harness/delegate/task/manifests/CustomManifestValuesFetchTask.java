/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CustomManifestValuesFetchTask extends AbstractDelegateRunnableTask {
  @Inject private CustomManifestService customManifestService;

  public CustomManifestValuesFetchTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CustomManifestValuesFetchParams fetchParams = (CustomManifestValuesFetchParams) parameters;
    try {
      return fetchValues(fetchParams);
    } catch (Exception e) {
      LogCallback logCallback = getLogStreamingTaskClient().obtainLogCallback(fetchParams.getCommandUnitName());
      log.error("Fetch values from custom manifest failed", e);
      logCallback.saveExecutionLog("Unknown error while trying to fetch values from custom manifest. " + e.getMessage(),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return CustomManifestValuesFetchResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    }
  }

  @VisibleForTesting
  CustomManifestValuesFetchResponse fetchValues(CustomManifestValuesFetchParams fetchParams) {
    List<CustomManifestFetchConfig> orderedFetchConfig = new LinkedList<>();
    for (CustomManifestFetchConfig fetchFileConfig : fetchParams.getFetchFilesList()) {
      if (fetchFileConfig.isDefaultSource()) {
        orderedFetchConfig.add(0, fetchFileConfig);
      } else {
        orderedFetchConfig.add(fetchFileConfig);
      }
    }

    return fetchFilesOrdered(fetchParams, orderedFetchConfig);
  }

  private CustomManifestValuesFetchResponse fetchFilesOrdered(
      CustomManifestValuesFetchParams fetchParams, List<CustomManifestFetchConfig> orderedFetchConfig) {
    LogCallback logCallback = getLogStreamingTaskClient().obtainLogCallback(fetchParams.getCommandUnitName());
    Map<String, Collection<CustomSourceFile>> fetchedFilesContent = new HashMap<>();

    String defaultSourceWorkingDirectory = null;
    for (CustomManifestFetchConfig fetchFileConfig : orderedFetchConfig) {
      String workingDirectory = null;
      boolean shouldCleanUpWorkingDir = false;
      try {
        CustomManifestSource customManifestSource = fetchFileConfig.getCustomManifestSource();
        String activityId = fetchParams.getActivityId();
        if (fetchFileConfig.isDefaultSource()) {
          workingDirectory = customManifestService.getWorkingDirectory();
          defaultSourceWorkingDirectory = workingDirectory;
        } else if (isEmpty(customManifestSource.getScript()) && isNotEmpty(defaultSourceWorkingDirectory)) {
          workingDirectory = defaultSourceWorkingDirectory;
          logCallback.saveExecutionLog("Reusing execution output from service manifest.");
        } else {
          shouldCleanUpWorkingDir = true;
          workingDirectory = customManifestService.getWorkingDirectory();
        }

        logCallback.saveExecutionLog("Fetching following files:");
        logFilePathList(customManifestSource.getFilePaths(), logCallback);

        Collection<CustomSourceFile> valuesContent =
            customManifestService.fetchValues(customManifestSource, workingDirectory, activityId, logCallback, true);
        fetchedFilesContent.put(fetchFileConfig.getKey(), valuesContent);
      } catch (IOException e) {
        Throwable cause = e.getCause();
        boolean isNotFound = e instanceof FileNotFoundException || cause instanceof FileNotFoundException;
        if (isNotFound && !fetchFileConfig.isRequired()) {
          log.info(
              "No values file found for {} and activity {}", fetchFileConfig.getKey(), fetchParams.getActivityId());
          logCallback.saveExecutionLog("Values file not found for " + fetchFileConfig.getKey(), LogLevel.WARN);
          continue;
        }

        String message = format("Failed to fetch values file for %s. %s.", fetchFileConfig.getKey(), e.getMessage());
        log.error(message, e);
        logCallback.saveExecutionLog(message, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        return CustomManifestValuesFetchResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .build();
      } catch (Exception e) {
        String message = format("Failed to execute script. %s", e.getMessage());
        log.error(message, e);
        logCallback.saveExecutionLog(message, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        return CustomManifestValuesFetchResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .build();
      } finally {
        if (shouldCleanUpWorkingDir) {
          customManifestService.cleanup(workingDirectory);
        }
      }
    }

    customManifestService.cleanup(defaultSourceWorkingDirectory);

    return CustomManifestValuesFetchResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .valuesFilesContentMap(fetchedFilesContent)
        .build();
  }

  private void logFilePathList(List<String> filePathList, LogCallback logCallback) {
    if (isEmpty(filePathList)) {
      logCallback.saveExecutionLog("Empty file list. Skip.");
      return;
    }

    StringBuilder sb = new StringBuilder();
    filePathList.forEach(filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));
    logCallback.saveExecutionLog(sb.toString());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
