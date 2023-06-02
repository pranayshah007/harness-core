/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.k8s;

import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import io.harness.delegate.service.core.util.K8SVolumeUtils;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import java.util.Map;

public class K8SJob extends V1Job {
  public K8SJob(final String name, final String namespace) {
    metadata(new V1ObjectMeta().name(name).namespace(namespace)).kind("Job").apiVersion("batch/v1");
    spec(new V1JobSpec()
             .template(new V1PodTemplateSpec().spec(new V1PodSpec().restartPolicy("Never")))
             .ttlSecondsAfterFinished(60));
  }

  public K8SJob addVolume(final V1Volume volume, final V1VolumeMount volumeMount) {
    getSpec().getTemplate().getSpec().addVolumesItem(volume).getContainers().forEach(
        container -> container.addVolumeMountsItem(volumeMount));
    return this;
  }

  public K8SJob addVolume(final V1Volume volume, final String mountPath) {
    final var volumeMount = K8SVolumeUtils.volumeMount(volume, mountPath);
    getSpec().getTemplate().getSpec().addVolumesItem(volume).getContainers().forEach(
        container -> container.addVolumeMountsItem(volumeMount));

    if (getSpec().getTemplate().getSpec().getInitContainers() != null) {
      getSpec().getTemplate().getSpec().getInitContainers().forEach(
          container -> container.addVolumeMountsItem(volumeMount));
    }
    return this;
  }

  public K8SJob addEnvVar(final String key, final String value) {
    getSpec().getTemplate().getSpec().getContainers().forEach(
        container -> container.addEnvItem(new V1EnvVar().name(key).value(value)));
    return this;
  }

  public K8SJob addContainer(final String name, final String image, final String mem, final String cpu) {
    final var container = createContainerSpec(name, image, mem, cpu);
    getSpec().getTemplate().getSpec().addContainersItem(container);
    return this;
  }

  public K8SJob addInitContainer(final String name, final String image, final String mem, final String cpu) {
    final var initContainer = createContainerSpec(name, image, mem, cpu);
    getSpec().getTemplate().getSpec().addInitContainersItem(initContainer);
    return this;
  }

  private V1Container createContainerSpec(final String name, final String image, final String mem, final String cpu) {
    final var resources = new V1ResourceRequirements()
                              .limits(Map.of("memory", Quantity.fromString(mem)))
                              .requests(Map.of("memory", Quantity.fromString(mem), "cpu", Quantity.fromString(cpu)));
    return new V1Container().name(name).image(image).imagePullPolicy("Always").resources(resources);
  }

  public V1Job create(final BatchV1Api api, final String namespace) throws ApiException {
    return api.createNamespacedJob(namespace, this, null, null, DELEGATE_FIELD_MANAGER, "Warn");
  }
}
