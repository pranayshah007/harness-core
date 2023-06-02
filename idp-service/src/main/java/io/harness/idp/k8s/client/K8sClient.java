/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.k8s.client;

import io.harness.k8s.model.KubernetesConfig;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.List;
import java.util.Map;

public interface K8sClient {
  V1Secret updateSecretData(String namespace, String secretName, Map<String, byte[]> data, boolean replace);
  V1ConfigMap updateConfigMapData(String namespace, String configMapName, Map<String, String> data, boolean replace);
  V1PodList getBackstagePodList(String namespace);
  void removeSecretData(String namespace, String backstageSecret, List<String> envNames);
  KubernetesConfig getKubernetesConfig();
  V1Namespace createNamespace(String namespace);
}
