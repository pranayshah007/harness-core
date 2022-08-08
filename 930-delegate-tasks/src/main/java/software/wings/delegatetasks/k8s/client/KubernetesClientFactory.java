/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.client;

import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import io.fabric8.kubernetes.client.Client;
import io.fabric8.kubernetes.client.KubernetesClient;

public interface KubernetesClientFactory {
  KubernetesClient newKubernetesClient(K8sClusterConfig k8sClusterConfig);

  <T extends Client> T newAdaptedClient(K8sClusterConfig k8sClusterConfig, Class<T> clazz);
}
