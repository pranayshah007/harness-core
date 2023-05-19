/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static java.util.stream.Collectors.toList;

import io.harness.delegate.core.beans.EmptyDirVolume;
import io.harness.delegate.core.beans.HostPathVolume;
import io.harness.delegate.core.beans.PVCVolume;
import io.harness.delegate.core.beans.Resource;
import io.harness.delegate.service.core.util.AnyUtils;
import io.harness.delegate.service.core.util.K8SVolumeUtils;

import com.google.protobuf.Message;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import java.util.List;
import lombok.experimental.UtilityClass;
@UtilityClass
public class VolumeBuilder {
  public static List<Message> unpackVolumes(final List<Resource> resources) {
    return resources.stream().map(resource -> AnyUtils.<Message>unpack(resource.getSpec())).collect(toList());
  }

  public static List<V1VolumeMount> createVolumeMounts(final List<Message> protoVolumes) {
    return protoVolumes.stream().map(VolumeBuilder::mapVolumeMount).collect(toList());
  }

  public static List<V1Volume> createVolumes(final List<Message> protoVolumes) {
    return protoVolumes.stream().map(VolumeBuilder::mapVolume).collect(toList());
  }

  private static V1VolumeMount mapVolumeMount(final Message protoVolume) {
    if (protoVolume instanceof EmptyDirVolume) {
      final var volume = (EmptyDirVolume) protoVolume;
      return K8SVolumeUtils.volumeMount(volume.getName(), volume.getPath());
    } else if (protoVolume instanceof HostPathVolume) {
      final var volume = (HostPathVolume) protoVolume;
      return K8SVolumeUtils.volumeMount(volume.getName(), volume.getPath());
    } else if (protoVolume instanceof PVCVolume) {
      final var volume = (PVCVolume) protoVolume;
      return K8SVolumeUtils.volumeMount(volume.getName(), volume.getPath());
    } else {
      throw new IllegalArgumentException("Unknown volume type " + protoVolume.getClass());
    }
  }

  private static V1Volume mapVolume(final Message protoVolume) {
    if (protoVolume instanceof EmptyDirVolume) {
      final var volume = (EmptyDirVolume) protoVolume;
      return K8SVolumeUtils.emptyDir(volume.getName());
    } else if (protoVolume instanceof HostPathVolume) {
      final var volume = (HostPathVolume) protoVolume;
      return K8SVolumeUtils.hostPathVolume(volume.getName(), volume.getHostPath(), volume.getType());
    } else if (protoVolume instanceof PVCVolume) {
      final var volume = (PVCVolume) protoVolume;
      return K8SVolumeUtils.pvcVolume(volume.getName(), volume.getPvcName(), volume.getReadonly());
    } else {
      throw new IllegalArgumentException("Unknown volume type " + protoVolume.getClass());
    }
  }
}
