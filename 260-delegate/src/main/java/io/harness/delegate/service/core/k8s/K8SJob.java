/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import lombok.NonNull;

public class K8SJob extends V1Job {
  public K8SJob namespace(@NonNull final String namespace) {
    if (this.getMetadata() != null) {
      this.getMetadata().namespace(namespace);
    } else {
      this.metadata(new V1ObjectMeta().namespace(namespace));
    }
    return this;
  }

  public K8SJob name(@NonNull final String name) {
    if (this.getMetadata() != null) {
      this.getMetadata().name(name);
    } else {
      this.metadata(new V1ObjectMeta().name(name));
    }
    return this;
  }

  public K8SJob addVolume(final V1Volume volume, final V1VolumeMount volumeMount) {
    getSpec().getTemplate().getSpec().addVolumesItem(volume).getContainers().forEach(
        container -> container.addVolumeMountsItem(volumeMount));
    return this;
  }

  public K8SJob addVolume(final V1Volume volume, final String mountPath) {
    final var volumeMount = K8SVolumeUtils.createVolumeMount(volume, mountPath);
    getSpec().getTemplate().getSpec().addVolumesItem(volume).getContainers().forEach(
        container -> container.addVolumeMountsItem(volumeMount));
    return this;
  }

  public K8SJob addEnvVar(final String key, final String value) {
    getSpec().getTemplate().getSpec().getContainers().forEach(
        container -> container.addEnvItem(new V1EnvVar().name(key).value(value)));
    return this;
  }
}
