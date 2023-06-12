/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.rancher;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.ClusterSelectionCriteriaEntry;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class RancherResolveClustersTask extends AbstractDelegateRunnableTask {
  public static final String COMMAND_UNIT_NAME = "Execute";
  public static final String COMMAND_NAME = "Rancher Cluster Resolve";
  @Inject private TimeLimiter timeLimiter;

  @Inject private RancherTaskHelper helper;

  public RancherResolveClustersTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> postExecute,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public RancherResolveClustersResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public RancherResolveClustersResponse run(TaskParameters parameters) throws IOException {
    LogCallback logCallback = getLogStreamingTaskClient().obtainLogCallback(COMMAND_UNIT_NAME);
    if (!(parameters instanceof RancherResolveClustersTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("parameters", "Must be instance of RancherResolveClustersTaskParameters"));
    }

    RancherResolveClustersTaskParameters resolveTaskParams = (RancherResolveClustersTaskParameters) parameters;
    RancherClusterDataResponse rancherClusterData;
    try {
      logCallback.saveExecutionLog(
          "Fetching list of clusters and labels from Rancher: " + resolveTaskParams.getRancherConfig().getRancherUrl(),
          LogLevel.INFO);
      if (resolveTaskParams.isTimeoutSupported()) {
        rancherClusterData =
            HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(resolveTaskParams.getTimeout()), () -> {
              return helper.resolveRancherClusters(
                  resolveTaskParams.getRancherConfig(), resolveTaskParams.getEncryptedDataDetails());
            });
      } else {
        rancherClusterData = helper.resolveRancherClusters(
            resolveTaskParams.getRancherConfig(), resolveTaskParams.getEncryptedDataDetails());
      }

      if (CollectionUtils.isNotEmpty(rancherClusterData.getData())) {
        logCallback.saveExecutionLog("Fetched clusters list: "
                + rancherClusterData.getData()
                      .stream()
                      .map(clusterData -> clusterData.getName())
                      .collect(Collectors.toList()),
            LogLevel.INFO);
      } else {
        logCallback.saveExecutionLog(
            "Rancher returned an empty list of clusters.", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        return RancherResolveClustersResponse.builder()
            .isTimeoutError(resolveTaskParams.isTimeoutSupported())
            .executionStatus(ExecutionStatus.FAILED)
            .build();
      }
    } catch (Exception e) {
      log.error("Caught exception while fetching clusters data from rancher", e);
      logCallback.saveExecutionLog(
          "Error while fetching clusters data from Rancher. Exception: " + e.getLocalizedMessage(), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      RancherResolveClustersResponse response =
          RancherResolveClustersResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
      response.setErrorMessage(ExceptionMessageSanitizer.sanitizeException(e).getLocalizedMessage());
      return response;
    }

    Map<String, Set<String>> selectionParams =
        getClusterSelectionParams(resolveTaskParams.getClusterSelectionCriteria());
    List<String> filteredClusters = filterClustersForCriteria(rancherClusterData, selectionParams);

    if (CollectionUtils.isEmpty(filteredClusters)) {
      logCallback.saveExecutionLog(
          "No eligible cluster found after filtering", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return RancherResolveClustersResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }

    logCallback.saveExecutionLog("Eligible clusters list after applying label filters: " + filteredClusters,
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return RancherResolveClustersResponse.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .clusters(filteredClusters)
        .build();
  }

  private List<String> filterClustersForCriteria(
      final RancherClusterDataResponse rancherClusterData, final Map<String, Set<String>> selectionParams) {
    if (MapUtils.isEmpty(selectionParams)) {
      return rancherClusterData.getData()
          .stream()
          .map(RancherClusterDataResponse.ClusterData::getName)
          .collect(Collectors.toList());
    }

    List<String> filteredClusters = new ArrayList<>();
    rancherClusterData.getData().forEach(clusterData -> {
      if (!selectionParams.keySet().stream().allMatch(labelKey -> clusterData.getLabels().containsKey(labelKey))) {
        return;
      }

      if (!selectionParams.keySet().stream().allMatch(
              labelKey -> selectionParams.get(labelKey).contains(clusterData.getLabels().get(labelKey)))) {
        return;
      }

      filteredClusters.add(clusterData.getName());
    });

    return filteredClusters;
  }

  private Map<String, Set<String>> getClusterSelectionParams(
      final List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria) {
    if (CollectionUtils.isEmpty(clusterSelectionCriteria)) {
      return new HashMap<>();
    }

    return clusterSelectionCriteria.stream().collect(Collectors.toMap(
        clusterSelectionCriteriaEntry -> clusterSelectionCriteriaEntry.getLabelName().trim(), entry -> {
          List<String> labels = Arrays.asList(entry.getLabelValues().split(","));
          Set<String> trimmedLabels = new HashSet<>();
          labels.forEach(label -> trimmedLabels.add(label.trim()));

          return trimmedLabels;
        }));
  }
}
