/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretEnvSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class K8SEnvVar extends V1EnvVar {
  public static V1EnvFromSource fromSecret(final V1Secret secret) {
    return new V1EnvFromSource().secretRef(new V1SecretEnvSource().name(secret.getMetadata().getName()));
  }

  public static List<V1EnvVar> fromMap(final Map<String, String> envMap) {
    return envMap.entrySet()
        .stream()
        .map(entry -> new V1EnvVarBuilder().withName(entry.getKey()).withValue(entry.getValue()).build())
        .collect(Collectors.toList());
  }
}
