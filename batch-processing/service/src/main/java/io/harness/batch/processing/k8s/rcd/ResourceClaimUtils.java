/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
class ResourceClaimUtils {
  private static final BigDecimal SCALE_TO_NANO = BigDecimal.valueOf(1_000_000_000);
  private static final Quantity ZERO = Quantity.fromString("0");

  /*
  Using these Optional.map(xxx)... chains because everything is Nullable in the k8s client models.
   */

  ResourceClaimDiff resourceClaimDiffForPod(V1PodSpec oldPodSpec, V1PodSpec newPodSpec) {
    ResourceClaim oldClaim = getResourceClaim(oldPodSpec);
    ResourceClaim newClaim = getResourceClaim(newPodSpec);
    return new ResourceClaimDiff(oldClaim, newClaim);
  }

  ResourceClaimDiff resourceClaimDiffForPodWithScale(
      V1PodSpec oldPodSpec, int oldScale, V1PodSpec newPodSpec, int newScale) {
    ResourceClaim oldClaim = getResourceClaim(oldPodSpec).scale(oldScale);
    ResourceClaim newClaim = getResourceClaim(newPodSpec).scale(newScale);
    return new ResourceClaimDiff(oldClaim, newClaim);
  }

  private static ResourceClaim getResourceClaim(V1PodSpec oldPodSpec) {
    return Optional.ofNullable(oldPodSpec).map(ResourceClaimUtils::computePodResourceClaim).orElse(ResourceClaim.EMPTY);
  }

  private static ResourceClaim computePodResourceClaim(V1PodSpec spec) {
    long cpuNano = 0;
    long memBytes = 0;
    List<V1Container> containers = defaultIfNull(spec.getContainers(), emptyList());
    for (V1Container container : containers) {
      V1ResourceRequirements resources = container.getResources();
      if (resources != null) {
        Map<String, Quantity> requests = defaultIfNull(resources.getRequests(), emptyMap());
        Quantity cpu = requests.getOrDefault("cpu", ZERO);
        cpuNano += getCpuNano(cpu);
        Quantity memory = requests.getOrDefault("memory", ZERO);
        memBytes += getMemBytes(memory);
      }
    }
    List<V1Container> initContainers = defaultIfNull(spec.getInitContainers(), emptyList());
    for (V1Container initContainer : initContainers) {
      V1ResourceRequirements resources = initContainer.getResources();
      if (resources != null) {
        Map<String, Quantity> requests = defaultIfNull(resources.getRequests(), emptyMap());
        Quantity cpu = requests.getOrDefault("cpu", ZERO);
        cpuNano = Math.max(cpuNano, getCpuNano(cpu));
        Quantity memory = requests.getOrDefault("memory", ZERO);
        memBytes = Math.max(memBytes, getMemBytes(memory));
      }
    }
    return ResourceClaim.builder().cpuNano(cpuNano).memBytes(memBytes).build();
  }

  private static long getCpuNano(Quantity cpu) {
    return cpu.getNumber().multiply(SCALE_TO_NANO).longValue();
  }

  private static long getMemBytes(Quantity mem) {
    return mem.getNumber().longValue();
  }
}
