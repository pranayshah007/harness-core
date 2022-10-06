/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.gitpolling.github.GitPollingWebhookData;

import java.util.List;

public interface GitApiClient {
  DelegateResponseData findPullRequest(GitApiTaskParams gitApiTaskParams);
  DelegateResponseData mergePR(GitApiTaskParams gitApiTaskParams);
  List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(GitHubPollingDelegateRequest attributesRequest);
  DelegateResponseData deleteRef(GitApiTaskParams gitApiTaskParams);
}
