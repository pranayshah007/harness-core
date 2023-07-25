/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;
import static io.harness.delegate.service.core.util.LabelHelper.HARNESS_NAME_LABEL;
import static io.harness.delegate.service.core.util.LabelHelper.HARNESS_TASK_GROUP_LABEL;
import static io.harness.delegate.service.core.util.LabelHelper.normalizeLabel;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import java.util.List;
import java.util.Map;

public class K8SService extends V1Service {
  public static K8SService clusterIp(
      final String taskGroupId, final String namespace, final String selector, final int port) {
    final V1ServicePort portSpec = new V1ServicePort().port(port);
    final V1ServiceSpec clusterIpSpec =
        new V1ServiceSpec().type("ClusterIP").ports(List.of(portSpec)).selector(Map.of(HARNESS_NAME_LABEL, selector));
    return (K8SService) new K8SService(taskGroupId + "-service", namespace, taskGroupId).spec(clusterIpSpec);
  }

  // Service name must be lower case alphanumeric characters or '-', start with an alphabetic character, and end with an
  // alphanumeric character Validation regex for service name is [a-z]([-a-z0-9]*[a-z0-9])?
  private K8SService(final String name, final String namespace, final String taskGroupId) {
    final var normalizedName =
        name.replaceAll("[._]", "").replaceAll("^[\\d\\-]+", "").replaceAll("$-+", "").toLowerCase();
    metadata(new V1ObjectMeta()
                 .name(normalizedName)
                 .namespace(namespace)
                 .putLabelsItem(HARNESS_TASK_GROUP_LABEL, normalizeLabel(taskGroupId)))
        .apiVersion("v1")
        .kind("Service");
  }

  public V1Service create(final CoreV1Api api) throws ApiException {
    return api.createNamespacedService(
        this.getMetadata().getNamespace(), this, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }
}
