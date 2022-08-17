/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static java.util.Optional.ofNullable;

import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.perpetualtask.k8s.watch.Quantity;

import java.util.Map;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class K8sResourceUtils {
  private static final String K8S_CPU_RESOURCE = "cpu";
  private static final String K8S_MEMORY_RESOURCE = "memory";
  private static final String K8S_POD_RESOURCE = "pods";

  private static final double NANO_TO_UNIT = 1_000_000_000L;
  private static final double UNIT_TO_MEBI = 1 << 20;
  private static final double CPU_UNITS = 1024;
  private static final double GBI_TO_MBI = 1024;

  public static Resource getResource(Map<String, Quantity> resource) {
    Quantity cpuQuantity = resource.get(K8S_CPU_RESOURCE);
    Quantity memQuantity = resource.get(K8S_MEMORY_RESOURCE);

    return Resource.builder()
        .cpuUnits(getCpuUnits(cpuQuantity.getAmount()))
        .memoryMb(getMemoryMb(memQuantity.getAmount()))
        .build();
  }

  public static double getCpuUnits(double nanoCpu) {
    return (nanoCpu / NANO_TO_UNIT) * CPU_UNITS;
  }

  public static double getMemoryMb(double memoryBytes) {
    return memoryBytes / UNIT_TO_MEBI;
  }

  public static Resource getResource(double vCPU, double memoryGB) {
    return Resource.builder().cpuUnits(vCPU * CPU_UNITS).memoryMb(memoryGB * GBI_TO_MBI).build();
  }

  public static double getFargateVCpu(double cpuUnit) {
    double vCpu = cpuUnit / CPU_UNITS;
    if (vCpu <= 0.25) {
      vCpu = 0.25;
    } else if (vCpu <= 0.5) {
      vCpu = 0.5;
    } else {
      vCpu = Math.ceil(vCpu);
    }
    return vCpu;
  }

  public static double getFargateMemoryGb(double memoryMb) {
    double memoryGb = memoryMb / GBI_TO_MBI;
    if (memoryGb <= 0.5) {
      memoryGb = 0.5;
    } else {
      memoryGb = Math.ceil(memoryGb);
    }
    return memoryGb;
  }

  public static StorageResource getCapacity(Quantity capacity) {
    return StorageResource.builder().capacity(getMemoryMb(capacity.getAmount())).build();
  }

  public static long getPodCapacity(Map<String, Quantity> resource) {
    return ofNullable(resource.get(K8S_POD_RESOURCE)).map(Quantity::getAmount).orElse(0L);
  }

  /**
   * EKS kubectl get pods eks-fargate-smwbar-0 -n harness-delegate -o yaml
   * apiVersion: v1
   * kind: Pod
   * metadata:
   *  annotations:
   *    CapacityProvisioned: 2vCPU 9GB
   */
  @Nullable
  public static Resource getResourceFromAnnotationMap(@NonNull final Map<String, String> metadataAnnotationsMap) {
    String cpuAndMemory = metadataAnnotationsMap.get("CapacityProvisioned");
    if (cpuAndMemory != null) {
      try {
        cpuAndMemory = cpuAndMemory.replaceAll("(vCPU|GB)", " ");
        String[] resources = cpuAndMemory.trim().split("\\s+");
        return Resource.builder()
            .cpuUnits(1024D * Double.parseDouble(resources[0]))
            .memoryMb(1024D * Double.parseDouble(resources[1]))
            .build();
      } catch (Exception ex) {
        log.error("Error parsing resource from annotation CapacityProvisioned=[{}]",
            metadataAnnotationsMap.get("CapacityProvisioned"), ex);
      }
    }
    return null;
  }
}
