/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git.taskHandlers;

import io.harness.delegate.task.gitpolling.GitPollingSourceDelegateRequest;
import io.harness.delegate.task.gitpolling.response.GitPollingTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;

public abstract class GitPollingTaskHandler<T extends GitPollingSourceDelegateRequest> {
  public GitPollingTaskExecutionResponse getWebhookRecentDeliveryEvents(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }
}
