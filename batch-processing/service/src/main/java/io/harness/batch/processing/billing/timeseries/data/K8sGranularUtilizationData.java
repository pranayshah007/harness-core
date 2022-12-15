/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CE)
@Value
@Builder
public class K8sGranularUtilizationData {
  private String accountId;
  // 'instanceId' will be depricated soon for 'actualInstanceId'
  private String instanceId;
  private String actualInstanceId;
  private String instanceType;
  private String clusterId;
  private String settingId;
  private double cpu;
  private double memory;
  private double maxCpu;
  private double maxMemory;
  private double storageUsageValue;
  private double storageRequestValue;
  private long endTimestamp;
  private long startTimestamp;
}
