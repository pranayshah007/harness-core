/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.ExecutionEnvironment;
import io.harness.delegate.core.beans.TaskDescriptor;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.inject.Inject;
import io.harness.delegate.service.core.util.K8SVolumeUtils;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PodBuilder {
  private static final long POD_MAX_TTL_SECS = 86400L; // 1 day
  private static final String GVISOR_RUNTIME_CLASS = "gvisor";

  private static final String ADDON_VOLUME = "addon";
  private static final String ADDON_VOL_MOUNT_PATH = "/addon";
  private static final String WORKDIR_VOLUME = "harness";
  private static final String WORKDIR_MOUNT_PATH = "/harness";

  private final ContainerBuilder containerBuilder;

  public V1PodBuilder createSpec(final String taskGroupId, final List<TaskDescriptor> taskDescriptor) {
    // Create volumes & volume mounts - they should be task group specific, not task specific
    final var addonVol = K8SVolumeUtils.emptyDir(ADDON_VOLUME);
    final var workdirVol = K8SVolumeUtils.emptyDir(WORKDIR_VOLUME);
    final var addonVolMnt = K8SVolumeUtils.createVolumeMount(addonVol, ADDON_VOL_MOUNT_PATH);
    final var workdirVolMnt = K8SVolumeUtils.createVolumeMount(workdirVol, WORKDIR_MOUNT_PATH);

    final List<V1Volume> volumes = List.of(addonVol, workdirVol);
    final List<V1VolumeMount> volumeMounts = List.of(addonVolMnt, workdirVolMnt);

    return new V1PodBuilder()
        .withNewMetadata()
        .withName(K8SResourceHelper.getPodName(taskGroupId))
        //        .withLabels(Map.of()) // TODO: Add labels to infra section in the API
        //        .withAnnotations(Map.of()) // TODO: Add annotations to infra section in the API
        .withNamespace(K8SResourceHelper.getRunnerNamespace())
        .endMetadata()
        .withNewSpec()
        .withContainers(getContainers(taskDescriptor, volumeMounts))
        .withRestartPolicy("Never")
        .withActiveDeadlineSeconds(getTimeout(taskDescriptor))
        .withServiceAccountName(getServiceAccount())
        .withAutomountServiceAccountToken(true)
        //        .withTolerations(getTolerations(taskDescriptor))
        //        .withNodeSelector(Map.of()) // TODO: Add node selectors to infra section in the API
        .withRuntimeClassName(GVISOR_RUNTIME_CLASS)
        .withInitContainers(getInitContainers(volumeMounts))
        .withImagePullSecrets(getImagePullSecrets(taskDescriptor))
        //        .withHostAliases(List.of()) // To add entries to pods /etc/hosts
        .withVolumes(volumes)
        //            .withPriorityClassName(podParams.getPriorityClassName()); // TODO: Add option for priority classes
        //            to infra spec if needed
        .endSpec();
  }

  private List<V1LocalObjectReference> getImagePullSecrets(final List<TaskDescriptor> taskDescriptor) {
    return List.of();
  }

  private Long getTimeout(final List<TaskDescriptor> taskDescriptor) {
    return POD_MAX_TTL_SECS;
  }

  // TODO: We should add LE container here instead on manager (that's property of runner)
  private List<V1Container> getContainers(
      final List<TaskDescriptor> taskDescriptors, final List<V1VolumeMount> volumeMounts) {
    int port = 20002; // FixMe each container one port
    final var containers = taskDescriptors.stream()
            .map(descriptor -> createContainer(descriptor.getId(), descriptor.getRuntime(), volumeMounts, port))
            .collect(Collectors.toList());
    // Add LE Container - FixMe: possibly doesn't need all volumeMounts or even none
    // TODO: Does LE need port map? How does it work now, is it orcestrated by CI Manager
    containers.add(containerBuilder.createLEContainer(getLeMemory(taskDescriptors), getLeCpu(taskDescriptors)).addAllToVolumeMounts(volumeMounts).build());
    return containers;
  }

  private String getLeCpu(final List<TaskDescriptor> taskDescriptors) {
    return null;
  }

  private String getLeMemory(final List<TaskDescriptor> taskDescriptors) {
    return null;
  }

  private V1Container createContainer(
      final String taskId, final ExecutionEnvironment runtime, final List<V1VolumeMount> volumeMounts, final int port) {
    return containerBuilder.createContainer(taskId, runtime, port).addAllToVolumeMounts(volumeMounts).build();
  }

  // We want to download ci-addon in the init container
  private List<V1Container> getInitContainers(final List<V1VolumeMount> volumes) {
    // FixMe: Probably only needs addon volume
    return List.of(containerBuilder.createAddonInitContainer().addAllToVolumeMounts(volumes).build());
  }

  private String getServiceAccount() {
    return "default"; // TODO: If specified use that (add API option), otherwise cluster admin/namespace admin, same as
                      // delegate
  }

  private List<V1Toleration> getTolerations(final TaskDescriptor taskDescriptor) {
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
