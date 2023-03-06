/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.DelegateSelectionLogHttpClient;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DelegateInfoHelper {
  // Todo: Remove dependency of orchestrationVisualization from 120-ng-manager
  @Inject(optional = true) private DelegateSelectionLogHttpClient delegateSelectionLogHttpClient;

  public LoadingCache<DelegateSelectionLogParamsKey, DelegateSelectionLogParams> taskIdToDelegateSelectionLogParams =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<DelegateSelectionLogParamsKey, DelegateSelectionLogParams>() {
            @Override
            public DelegateSelectionLogParams load(@NotNull DelegateSelectionLogParamsKey key) throws IOException {
              return getDelegateSelectionLogParams(key.getAccountId(), key.getTaskId());
            }
          });

  public DelegateSelectionLogParams getDelegateSelectionLogParams(String accountId, String taskId) {
    try {
      DelegateSelectionLogParams delegateSelectionLogParams =
          SafeHttpCall.execute(delegateSelectionLogHttpClient.getDelegateInfo(accountId, taskId)).getResource();
      if (delegateSelectionLogParams == null) {
        log.warn("DelegateSelectionLogParams is null for account {} and task {}", accountId, taskId);
      }
      return delegateSelectionLogParams;
    } catch (Exception exception) {
      log.warn("Not able to talk to delegate service. Ignoring delegate Information", exception);
      return DelegateSelectionLogParams.builder().build();
    }
  }

  public List<GraphDelegateSelectionLogParams> getDelegateInformationForGivenTask(
      List<ExecutableResponse> executableResponses, ExecutionMode executionMode, String accountId) {
    if (!ExecutionModeUtils.isTaskMode(executionMode)) {
      return new ArrayList<>();
    }
    return executableResponses.stream()
        .map(executableResponse -> {
          try {
            TaskInfo taskInfo = TaskInfo.fromExecutableResponse(executableResponse);
            if (taskInfo.getTaskId() != null) {
              DelegateSelectionLogParams resource = taskIdToDelegateSelectionLogParams.get(
                  DelegateSelectionLogParamsKey.builder().accountId(accountId).taskId(taskInfo.getTaskId()).build());
              return GraphDelegateSelectionLogParams.builder()
                  .selectionLogParams(resource)
                  .taskId(taskInfo.getTaskId())
                  .taskName(taskInfo.getTaskName())
                  .build();
            }
            return null;
          } catch (Exception exception) {
            log.warn("Not able to talk to delegate service. Ignoring delegate Information", exception);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Data
  @Builder
  private static class DelegateSelectionLogParamsKey {
    String accountId;
    String taskId;
  }

  private static class TaskInfo {
    @Getter private String taskId;
    @Getter private String taskName;

    private TaskInfo() {}

    private TaskInfo(String taskId, String taskName) {
      this.taskId = taskId;
      this.taskName = taskName;
    }

    public static TaskInfo fromExecutableResponse(ExecutableResponse response) {
      switch (response.getResponseCase()) {
        case TASK:
          return new TaskInfo(
              nullIfEmpty(response.getTask().getTaskId()), nullIfEmpty(response.getTask().getTaskName()));
        case TASKCHAIN:
          return new TaskInfo(
              nullIfEmpty(response.getTaskChain().getTaskId()), nullIfEmpty(response.getTaskChain().getTaskName()));
        default:
          throw new InvalidRequestException("Trying to extract task id from non task Executable Response");
      }
    }
  }
}
