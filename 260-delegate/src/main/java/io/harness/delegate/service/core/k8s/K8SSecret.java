/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import static io.harness.delegate.service.core.k8s.K8SConstants.DELEGATE_FIELD_MANAGER;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import lombok.NonNull;

public class K8SSecret extends V1Secret {
  public K8SSecret(@NonNull final String name, @NonNull final String namespace) {
    metadata(new V1ObjectMeta().name(name).namespace(namespace)).type("Opaque").apiVersion("v1").kind("Secret");
  }

  @Override
  public K8SSecret putDataItem(final String key, final byte[] dataItem) {
    super.putDataItem(key, dataItem);
    return this;
  }

  public V1Secret create(final CoreV1Api coreApi, final String namespace) throws ApiException {
    return coreApi.createNamespacedSecret(namespace, this, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }
}
