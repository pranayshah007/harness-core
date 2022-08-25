/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI;
import static java.math.RoundingMode.HALF_UP;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.custom.Quantity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ResourceAmountUtils {
  static long MAX_RESOURCE_AMOUNT = (long) 1e14;

  public static Map<String, Long> makeResourceMap(long cpuAmount, long memoryAmount) {
    return ImmutableMap.of(CPU, cpuAmount, MEMORY, memoryAmount);
  }

  public static long cpuAmountFromCores(double cores) {
    return resourceAmountFromFloat(cores * 1000.0);
  }

  public static double coresFromCpuAmount(long cpuAmount) {
    return cpuAmount / 1000.0;
  }

  public static long memoryAmountFromBytes(double bytes) {
    return resourceAmountFromFloat(bytes);
  }

  public static double bytesFromMemoryAmount(long memoryAmount) {
    return (double) memoryAmount;
  }

  static long resourceAmountFromFloat(double amount) {
    if (amount < 0) {
      return 0;
    } else if (amount > MAX_RESOURCE_AMOUNT) {
      return MAX_RESOURCE_AMOUNT;
    } else {
      return (long) amount;
    }
  }

  public static long scaleResourceAmount(long amount, double factor) {
    return resourceAmountFromFloat(amount * factor);
  }

  static long cpu(Map<String, Long> resourceMap) {
    return Optional.ofNullable(resourceMap.get(CPU)).orElse(0L);
  }

  static long memory(Map<String, Long> resourceMap) {
    return Optional.ofNullable(resourceMap.get(MEMORY)).orElse(0L);
  }

  public static Map<String, String> convertToReadableForm(Map<String, Long> resourceMap) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
    if (resourceMap.containsKey(CPU)) {
      builder.put(CPU, readableCpuAmount(cpu(resourceMap)));
    }
    if (resourceMap.containsKey(MEMORY)) {
      builder.put(MEMORY, readableMemoryAmount(memory(resourceMap)));
    }
    return builder.build();
  }

  static String readableCpuAmount(long cpuAmount) {
    BigDecimal cpuInCores = BigDecimal
                                // milliCore to core
                                .valueOf(cpuAmount, 3)
                                // round up to nearest milliCore
                                .setScale(3, HALF_UP);
    return toDecimalSuffixedString(cpuInCores);
  }

  // Rounds-up memoryAmount so that it is more human-readable
  // eg: 861M for 860730769
  static String readableMemoryAmount(long memoryAmount) {
    int maxAllowedStringLen = 5;
    BigDecimal memoryInBytes = BigDecimal.valueOf(memoryAmount);
    int scale = 0;
    while (true) {
      String memoryString = toDecimalSuffixedString(memoryInBytes);
      if (memoryString.length() <= maxAllowedStringLen) {
        return memoryString;
      }
      // Keep rounding up to next higher unit until we reach a human readable value
      scale -= 3;
      memoryInBytes = memoryInBytes.setScale(scale, HALF_UP);
    }
  }

  private static String toDecimalSuffixedString(BigDecimal number) {
    return new Quantity(number, DECIMAL_SI).toSuffixedString();
  }
}
