/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public interface WatcherFactory {
  PodWatcher createPodWatcher(ApiClient apiClient, ClusterDetails params, K8sControllerFetcher controllerFetcher,
      SharedInformerFactory sharedInformerFactory, PVCFetcher pvcFetcher, NamespaceFetcher namespaceFetcher);

  NodeWatcher createNodeWatcher(
      ApiClient apiClient, ClusterDetails params, SharedInformerFactory sharedInformerFactory);

  PVWatcher createPVWatcher(ApiClient apiClient, ClusterDetails params, SharedInformerFactory sharedInformerFactory);

  PVCFetcher createPVCFetcher(ApiClient apiClient, SharedInformerFactory sharedInformerFactory);
  NamespaceFetcher createNamespaceFetcher(ApiClient apiClient, SharedInformerFactory sharedInformerFactory);
}
