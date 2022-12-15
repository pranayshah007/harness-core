/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceUtilizationData {
  private String accountId;
  private String instanceId;
  private String instanceType;
  private String clusterId;
  private String settingId;
  private double cpuUtilizationAvg;
  private double cpuUtilizationMax;
  private double memoryUtilizationAvg;
  private double memoryUtilizationMax;
  private double cpuUtilizationAvgValue;
  private double cpuUtilizationMaxValue;
  private double memoryUtilizationAvgValue;
  private double memoryUtilizationMaxValue;

  private double storageCapacityAvgValue;
  private double storageRequestAvgValue;
  private double storageUsageAvgValue;

  private double storageRequestMaxValue;
  private double storageUsageMaxValue;

  private long endTimestamp;
  private long startTimestamp;
}
