/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core;

import static com.google.common.collect.ImmutableList.toImmutableList;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.core.ExecutionMode;
import io.harness.delegate.core.ExecutionPriority;
import io.harness.delegate.core.PluginDescriptor;
import io.harness.delegate.service.common.SimpleDelegateAgent;
import io.harness.delegate.service.core.k8s.K8STaskRunner;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;
import java.util.Arrays;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateService extends SimpleDelegateAgent {
  private final K8STaskRunner taskRunner;

  @Override
  protected void abortTask(final DelegateTaskAbortEvent taskEvent) {}

  @Override
  protected void executeTask(final @NonNull PluginDescriptor pluginDescriptor) {
    try {
      validatePluginData(pluginDescriptor);
      taskRunner.launchTask(pluginDescriptor);
    } catch (IOException e) {
      log.error("Failed to create the task {}", pluginDescriptor.getId(), e);
    } catch (ApiException e) {
      log.error("APIException: {}, {}, {}, {}, {}", e.getCode(), e.getResponseBody(), e.getMessage(),
          e.getResponseHeaders(), e.getCause());
      log.error("Failed to create the task {}", pluginDescriptor.getId(), e);
    }
  }

  private void validatePluginData(final @NonNull PluginDescriptor pluginDescriptor) {
    if (pluginDescriptor.getPriority() == ExecutionPriority.PRIORITY_UNKNOWN) {
      throw new IllegalArgumentException("Task Priority must be specified");
    }
    if (pluginDescriptor.getMode() == ExecutionMode.MODE_UNKNOWN) {
      throw new IllegalArgumentException("Task Mode must be specified");
    }
  }

  @Override
  protected ImmutableList<String> getCurrentlyExecutingTaskIds() {
    return ImmutableList.of("");
  }

  @Override
  protected ImmutableList<TaskType> getSupportedTasks() {
    return Arrays.stream(TaskType.values()).collect(toImmutableList());
  }

  @Override
  protected void onPreResponseSent(final DelegateTaskResponse response) {
    final DelegateMetaInfo responseMetadata =
        DelegateMetaInfo.builder().hostName(HOST_NAME).id(DelegateAgentCommonVariables.getDelegateId()).build();
    if (response.getResponse() instanceof DelegateTaskNotifyResponseData) {
      ((DelegateTaskNotifyResponseData) response.getResponse()).setDelegateMetaInfo(responseMetadata);
    }
  }
}
