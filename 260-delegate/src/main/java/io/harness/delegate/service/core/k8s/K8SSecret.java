/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import com.google.common.base.Charsets;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.Map;
import lombok.NonNull;

public class K8SSecret extends V1Secret {
  public static K8SSecret secret(@NonNull final String name, @NonNull final String namespace) {
    return new K8SSecret(name, namespace, "Opaque");
  }

  public static K8SSecret imagePullSecret(@NonNull final String name, @NonNull final String namespace) {
    return new K8SSecret(name, namespace, "kubernetes.io/dockercfg");
  }

  private K8SSecret(@NonNull final String name, @NonNull final String namespace, @NonNull final String type) {
    metadata(new V1ObjectMeta().name(name).namespace(namespace)).type(type).apiVersion("v1").kind("Secret");
  }

  @Override
  public K8SSecret putDataItem(final String key, final byte[] dataItem) {
    super.putDataItem(key, dataItem);
    return this;
  }

  public K8SSecret putAllDataItems(final Map<String, byte[]> dataItems) {
    dataItems.forEach(this::putDataItem);
    return this;
  }

  public K8SSecret putAllCharDataItems(final Map<String, char[]> dataItems) {
    dataItems.forEach((key, value) -> putDataItem(key, String.valueOf(value).getBytes(Charsets.UTF_8)));
    return this;
  }

  @Override
  public K8SSecret putStringDataItem(final String key, final String dataItem) {
    super.putStringDataItem(key, dataItem);
    return this;
  }

  public K8SSecret putAllStringDataItems(final Map<String, String> dataItems) {
    dataItems.forEach(this::putStringDataItem);
    return this;
  }

  public V1Secret create(final CoreV1Api coreApi) throws ApiException {
    final var namespace = this.getMetadata().getNamespace();
    return coreApi.createNamespacedSecret(namespace, this, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }
}
