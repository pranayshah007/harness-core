/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.util;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8SVolumeUtils {
  public static V1Volume fromConfigMap(final V1ConfigMap configMap, final String name) {
    return new V1Volume().name(name).configMap(new V1ConfigMapVolumeSource().name(configMap.getMetadata().getName()));
  }

  public static V1Volume fromSecret(final V1Secret secret, final String name) {
    return new V1Volume().name(name).secret(new V1SecretVolumeSource().secretName(secret.getMetadata().getName()));
  }

  public static V1Volume emptyDir(final String name) {
    return new V1Volume().name(name).emptyDir(new V1EmptyDirVolumeSource());
  }

  public static V1Volume pvcVolume(final String name, final String claimName, final boolean readonly) {
    return new V1VolumeBuilder()
        .withName(name)
        .withPersistentVolumeClaim(
            new V1PersistentVolumeClaimVolumeSourceBuilder().withClaimName(claimName).withReadOnly(readonly).build())
        .build();
  }

  public static V1Volume hostPathVolume(final String name, final String hostPath, final String type) {
    final var volumeBuilder = new V1HostPathVolumeSourceBuilder().withPath(hostPath);
    if (type != null && !"".equals(type)) {
      volumeBuilder.withType(type);
    }
    return new V1VolumeBuilder().withName(name).withHostPath(volumeBuilder.build()).build();
  }

  public static V1VolumeMount volumeMount(final V1Volume volume, final String mountPath) {
    return new V1VolumeMount().name(volume.getName()).mountPath(mountPath);
  }

  public static V1VolumeMount volumeMount(final String volumeName, final String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }
}
