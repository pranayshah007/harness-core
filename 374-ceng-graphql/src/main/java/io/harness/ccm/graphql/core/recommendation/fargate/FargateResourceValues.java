/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation.fargate;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class FargateResourceValues {
  // Map of CPU values (cores) to list of memory values (gb)
  private static final Map<Double, List<Double>> allowedResourceValues = new HashMap<>();
  private static final List<Double> allowedCPUValues = new ArrayList<>();

  public FargateResourceValues() {
    allowedResourceValues.put((double) 256, Arrays.asList(0.5, 1.0, 2.0));
    allowedResourceValues.put((double) 512, incrementalList(1, 4, 1));
    allowedResourceValues.put((double) 1024, incrementalList(2, 8, 1));
    allowedResourceValues.put((double) 2048, incrementalList(4, 16, 1));
    allowedResourceValues.put((double) 4096, incrementalList(8, 30, 1));
    allowedResourceValues.put((double) 8192, incrementalList(16, 60, 4));
    allowedResourceValues.put((double) 16384, incrementalList(32, 120, 8));
    allowedCPUValues.addAll(allowedResourceValues.keySet());
  }

  private static List<Double> incrementalList(int from, int to, int increment) {
    List<Double> list = new ArrayList<>();
    for (int i = from; i <= to; i += increment) {
      list.add((double) i);
    }
    return list;
  }

  public CpuMillsAndMemoryBytes get(long cpuMilliUnits, long memoryBytes) {
    double cpuCores = cpuMillisToCores(cpuMilliUnits);
    double memoryGb = bytesToGigabytes(memoryBytes);
    for (Double currentCPU : allowedCPUValues) {
      List<Double> memoryValuesForCurrentCPU = allowedResourceValues.get(currentCPU);
      if (currentCPU < cpuCores || memoryValuesForCurrentCPU.get(memoryValuesForCurrentCPU.size() - 1) < memoryGb) {
        continue;
      }
      for (Double currentMemoryGB : memoryValuesForCurrentCPU) {
        if (currentMemoryGB >= memoryGb) {
          return new CpuMillsAndMemoryBytes(cpuCoresToMillis(currentCPU), gigabytesToBytes(currentMemoryGB));
        }
      }
    }
    return null;
  }

  private static double bytesToGigabytes(long bytes) {
    return (double) bytes / (double) 1073741824;
  }

  private static long gigabytesToBytes(double gb) {
    return (long) (gb * 1073741824L);
  }

  private static double cpuMillisToCores(long cpuMillis) {
    return (double) cpuMillis / (double) 1024;
  }

  private static long cpuCoresToMillis(double cpuCores) {
    return (long) cpuCores * 1024L;
  }
}
