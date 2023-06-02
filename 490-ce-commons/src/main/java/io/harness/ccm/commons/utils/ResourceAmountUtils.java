/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.BINARY_SI;
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
  public static long MAX_RESOURCE_AMOUNT = (long) 1e14;
  private static final String[] BINARY_SUFFIXES = {"", "Ki", "Mi", "Gi", "Ti", "Pi", "Ei"};

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

  public static long cpu(Map<String, Long> resourceMap) {
    return Optional.ofNullable(resourceMap.get(CPU)).orElse(0L);
  }

  public static long memory(Map<String, Long> resourceMap) {
    return Optional.ofNullable(resourceMap.get(MEMORY)).orElse(0L);
  }

  public static Map<String, String> convertToReadableForm(Map<String, Long> resourceMap, Quantity.Format base) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
    if (resourceMap.containsKey(CPU)) {
      builder.put(CPU, readableCpuAmount(cpu(resourceMap), base));
    }
    if (resourceMap.containsKey(MEMORY)) {
      builder.put(MEMORY, readableMemoryAmount(memory(resourceMap), base));
    }
    return builder.build();
  }

  static String readableCpuAmount(long cpuAmount, Quantity.Format base) {
    // cpuAmount is in milliCores
    BigDecimal cpuInCores;
    if (base.equals(BINARY_SI)) {
      cpuInCores = BigDecimal.valueOf(cpuAmount).divide(BigDecimal.valueOf(1024)).setScale(3, HALF_UP);
      return toDecimalSuffixedString(cpuInCores, BINARY_SI);
    } else {
      cpuInCores = BigDecimal
                       // milliCore to core
                       .valueOf(cpuAmount, 3)
                       // round up to nearest milliCore
                       .setScale(3, HALF_UP);
      return toDecimalSuffixedString(cpuInCores, DECIMAL_SI);
    }
  }

  // Rounds-up memoryAmount so that it is more human-readable
  // eg: 1Mi for 1024*1024 bytes
  static String readableMemoryAmount(long memoryAmount, Quantity.Format base) {
    if (base.equals(BINARY_SI)) {
      return readableBinaryMemoryAmount(memoryAmount);
    }
    return readableDecimalMemoryAmount(memoryAmount);
  }

  static String readableDecimalMemoryAmount(long memoryAmount) {
    int maxAllowedStringLen = 5;
    BigDecimal memoryInBytes = BigDecimal.valueOf(memoryAmount);
    int scale = 0;
    while (true) {
      String memoryString = toDecimalSuffixedString(memoryInBytes, DECIMAL_SI);
      if (memoryString.length() <= maxAllowedStringLen) {
        return memoryString;
      }
      // Keep rounding up to next higher unit until we reach a human readable value
      scale -= 3;
      memoryInBytes = memoryInBytes.setScale(scale, HALF_UP);
    }
  }

  static String readableBinaryMemoryAmount(long memoryAmount) {
    BigDecimal amount = BigDecimal.valueOf(memoryAmount);
    int maxAllowedStringLen = 4;
    String readableAmount = toDecimalSuffixedString(amount, BINARY_SI);
    if (readableAmount.length() < maxAllowedStringLen
        || (readableAmount.length() == maxAllowedStringLen && readableAmount.endsWith("i"))
        || (readableAmount.length() == maxAllowedStringLen && readableAmount.endsWith("m"))) {
      return readableAmount;
    }
    BigDecimal valueWithoutUnits = amount.setScale(1, HALF_UP);
    int suffixIndex = 0;
    if (String.valueOf(valueWithoutUnits.longValue()).length() <= maxAllowedStringLen - 1) {
      return valueWithoutUnits + BINARY_SUFFIXES[suffixIndex];
    }
    while (valueWithoutUnits.compareTo(BigDecimal.ZERO) > 0) {
      valueWithoutUnits = valueWithoutUnits.divide(BigDecimal.valueOf(1024), 1, HALF_UP);
      suffixIndex++;
      if (String.valueOf(valueWithoutUnits.longValue()).length() <= maxAllowedStringLen - 1) {
        return valueWithoutUnits + BINARY_SUFFIXES[suffixIndex];
      }
    }
    return valueWithoutUnits.toString();
  }

  private static String toDecimalSuffixedString(BigDecimal number, Quantity.Format base) {
    return new Quantity(number, base).toSuffixedString();
  }
}
