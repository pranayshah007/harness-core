/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.reflection.ReflectionUtils;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.joor.Reflect;

@Slf4j
public class K8sHandlerUtils<ApiType extends KubernetesObject> {
  private static final String METADATA = "metadata";
  public static final Integer VERSION = 1;

  public ApiType clone(ApiType resource) {
    try {
      @SuppressWarnings("unchecked") ApiType copy = (ApiType) Yaml.load(Yaml.dump(resource));
      return copy;
    } catch (IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("Serialization round trip should clone", e);
      }
      return resource;
    }
  }

  public String yamlDump(ApiType resource) {
    ApiType copy = clone(resource);
    V1ObjectMetaBuilder newV1ObjectMetaBuilder = new V1ObjectMetaBuilder();
    V1ObjectMeta objectMeta = getMetadata(copy);
    if (objectMeta != null) {
      newV1ObjectMetaBuilder.withName(objectMeta.getName())
          .withNamespace(objectMeta.getNamespace())
          .withLabels(objectMeta.getLabels())
          .withAnnotations(objectMeta.getAnnotations())
          .withUid(objectMeta.getUid());
    }
    Reflect.on(copy).set(METADATA, newV1ObjectMetaBuilder.build());
    return Yaml.dump(copy);
  }

  public V1ObjectMeta getMetadata(ApiType resource) {
    return (V1ObjectMeta) ReflectionUtils.getFieldValue(resource, METADATA);
  }

  public String getResourceVersion(ApiType resource) {
    return Optional.ofNullable(getMetadata(resource)).map(V1ObjectMeta::getResourceVersion).orElse("");
  }
}
