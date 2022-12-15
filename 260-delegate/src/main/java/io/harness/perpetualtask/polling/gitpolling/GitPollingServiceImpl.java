/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.gitpolling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.git.taskHandlers.GitPollingTaskHandler;
import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.delegate.task.gitpolling.GitPollingTaskType;
import io.harness.delegate.task.gitpolling.request.GitPollingTaskParameters;
import io.harness.delegate.task.gitpolling.response.GitPollingTaskExecutionResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class GitPollingServiceImpl {
  @Inject GitPollingServiceRegistryNg serviceRegistry;

  public GitPollingTaskExecutionResponse getWebhookRecentDeliveryEvents(GitPollingTaskParameters taskParams) {
    GitPollingSourceDelegateRequest attributes = taskParams.getAttributes();
    GitPollingTaskHandler handler = serviceRegistry.getWebhookPollingService(attributes.getSourceType());
    GitPollingTaskType gitPollingTaskType = taskParams.getGitPollingTaskType();
    if (gitPollingTaskType == null) {
      log.error("No task associated with Git Polling");
      return null;
    }
    if (taskParams.getGitPollingTaskType().equals(GitPollingTaskType.GET_WEBHOOK_EVENTS)) {
      return handler.getWebhookRecentDeliveryEvents(attributes);
    }
    return null;
  }
}
