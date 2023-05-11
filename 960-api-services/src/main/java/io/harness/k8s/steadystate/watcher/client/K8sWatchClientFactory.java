/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.openapi.ApiClient;
import java.util.concurrent.ExecutorService;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class K8sWatchClientFactory {
  @Inject @Named("k8sSteadyStateExecutor") private ExecutorService k8sSteadyStateExecutor;

  public K8sWatchClient create(ApiClient apiClient, Retry retryConfig) {
    return new K8sBlockingWatchClient(apiClient, retryConfig, k8sSteadyStateExecutor);
  }
}
