/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UtilizationData {
  private double maxCpuUtilization;
  private double maxMemoryUtilization;
  private double avgCpuUtilization;
  private double avgMemoryUtilization;
  private double maxCpuUtilizationValue;
  private double maxMemoryUtilizationValue;
  private double avgCpuUtilizationValue;
  private double avgMemoryUtilizationValue;

  private double avgStorageCapacityValue;
  private double avgStorageUsageValue;
  private double avgStorageRequestValue;

  private double maxStorageUsageValue;
  private double maxStorageRequestValue;
}
