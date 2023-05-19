/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.util.K8SResourceHelper;
import io.harness.delegate.service.core.util.K8SVolumeUtils;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1LocalObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PodBuilder extends V1PodBuilder {
  private static final long POD_MAX_TTL_SECS = 86400L; // 1 day
  private static final String GVISOR_RUNTIME_CLASS = "gvisor";

  private static final String ADDON_VOLUME_NAME = "addon";
  private static final String ADDON_MOUNT_PATH = "/addon";
  private static final String WORKDIR_VOLUME_NAME = "harness";
  private static final String WORKDIR_MOUNT_PATH = "/harness";
  private static final V1Volume ADDON_VOLUME = K8SVolumeUtils.emptyDir(ADDON_VOLUME_NAME);
  private static final V1VolumeMount ADDON_VOLUME_MNT = K8SVolumeUtils.volumeMount(ADDON_VOLUME_NAME, ADDON_MOUNT_PATH);
  private static final V1Volume WORKDIR_VOLUME = K8SVolumeUtils.emptyDir(WORKDIR_VOLUME_NAME);
  private static final V1VolumeMount WORKDIR_VOLUME_MNT =
      K8SVolumeUtils.volumeMount(WORKDIR_VOLUME_NAME, WORKDIR_MOUNT_PATH);
  private static final String SERVICE_PORT_SUFFIX = "_SERVICE_PORT";

  //  private final ContainerBuilder containerBuilder;
  //  private final CoreV1Api coreApi;

  public static PodBuilder createSpec(final String taskGroupId) {
    return (PodBuilder) new PodBuilder()
        .withNewMetadata()
        .withName(K8SResourceHelper.getPodName(taskGroupId))
        //        .withLabels(Map.of()) // TODO: Add labels to infra section in the API
        //        .withAnnotations(Map.of()) // TODO: Add annotations to infra section in the API
        .withNamespace(K8SResourceHelper.getRunnerNamespace())
        .endMetadata()
        .withNewSpec()
        .withRestartPolicy("Never")
        .withActiveDeadlineSeconds(getTimeout(null)) // TODO: Calculate based on task timeouts?
        .withServiceAccountName(getServiceAccount())
        .withAutomountServiceAccountToken(true)
        //        .withTolerations(getTolerations(taskDescriptors))
        //        .withNodeSelector(Map.of()) // TODO: Add node selectors to infra section in the API
        //        .withRuntimeClassName(GVISOR_RUNTIME_CLASS) // FixMe: Doesn't work for del-play, says nodes with this
        //        class
        .withVolumes(WORKDIR_VOLUME) // We will always have workdir volume
        //        .withHostAliases(List.of()) // To add entries to pods /etc/hosts
        //        .withPriorityClassName(podParams.getPriorityClassName()); // TODO: Add option for priority classes
        //            to infra spec if needed
        .endSpec();
  }

  public PodBuilder withTasks(final List<V1Container> containers) {
    containers.forEach(container -> container.addVolumeMountsItem(WORKDIR_VOLUME_MNT));
    this.editOrNewSpec().addAllToContainers(containers).endSpec();
    return this;
  }

  public PodBuilder withImagePullSecrets(final List<V1Secret> imagePullSecrets) {
    this.editOrNewSpec().addAllToImagePullSecrets(createSecretRefs(imagePullSecrets)).endSpec();
    return this;
  }

  public V1Pod buildPod(final ContainerBuilder containerBuilder, final ResourceRequirements resource,
      final List<V1Volume> volumes, final V1Secret loggingSecret, final PortMap portMap) {
    return this.withAddon(containerBuilder)
        .withLiteEngine(containerBuilder, resource, loggingSecret, portMap)
        .withVolumes(volumes)
        .build();
  }

  private PodBuilder withLiteEngine(final ContainerBuilder containerBuilder, final ResourceRequirements resource,
      final V1Secret loggingSecret, final PortMap portMap) {
    final var portEnvMap =
        portMap.getPortMap().entrySet().stream().collect(toMap(e -> e.getKey() + SERVICE_PORT_SUFFIX, String::valueOf));

    final var leContainer = containerBuilder.createLEContainer(resource)
                                .addToEnvFrom(K8SEnvVar.fromSecret(loggingSecret))
                                .addAllToEnv(K8SEnvVar.fromMap(portEnvMap))
                                .build();
    this.editOrNewSpec().addToContainers(leContainer).endSpec();
    return this;
  }

  // We want to download ci-addon in the init container
  private PodBuilder withAddon(final ContainerBuilder containerBuilder) {
    final var addonContainer = containerBuilder.createAddonInitContainer().withVolumeMounts(ADDON_VOLUME_MNT).build();
    this.editOrNewSpec().addToInitContainers(addonContainer).addToVolumes(ADDON_VOLUME).endSpec();
    return this;
  }

  // We want to download ci-addon in the init container
  private PodBuilder withVolumes(final List<V1Volume> volumes) {
    this.editOrNewSpec().addAllToVolumes(volumes).endSpec();
    return this;
  }

  @NonNull
  private List<V1LocalObjectReference> createSecretRefs(final List<V1Secret> imageSecrets) {
    return imageSecrets.stream()
        .map(secret -> new V1LocalObjectReferenceBuilder().withName(secret.getMetadata().getName()).build())
        .collect(toList());
  }

  private static Long getTimeout(final List<K8SStep> taskDescriptor) {
    return POD_MAX_TTL_SECS;
  }

  private static String getServiceAccount() {
    return "default"; // TODO: If specified use that (add API option), otherwise cluster admin/namespace admin, same as
                      // delegate
  }

  private List<V1Toleration> getTolerations(final K8SInfra taskDescriptor) {
    final List<V1Toleration> tolerations = new ArrayList<>();
    //    if (isEmpty(podParams.getTolerations())) {
    //      return tolerations;
    //    }
    //
    //    for (PodToleration podToleration : podParams.getTolerations()) {
    //      V1TolerationBuilder tolerationBuilder = new V1TolerationBuilder();
    //      if (isNotEmpty(podToleration.getEffect())) {
    //        tolerationBuilder.withEffect(podToleration.getEffect());
    //      }
    //      if (isNotEmpty(podToleration.getKey())) {
    //        tolerationBuilder.withKey(podToleration.getKey());
    //      }
    //      if (isNotEmpty(podToleration.getOperator())) {
    //        tolerationBuilder.withOperator(podToleration.getOperator());
    //      }
    //      if (podToleration.getTolerationSeconds() != null) {
    //        tolerationBuilder.withTolerationSeconds((long) podToleration.getTolerationSeconds());
    //      }
    //      if (isNotEmpty(podToleration.getValue())) {
    //        tolerationBuilder.withValue(podToleration.getValue());
    //      }
    //
    //      tolerations.add(tolerationBuilder.build());
    //    }
    return tolerations;
  }
}
