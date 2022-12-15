/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClusterCostData {
  double totalCost;
  double totalSystemCost;
  double utilizedCost;
  double cpuTotalCost;
  double cpuSystemCost;
  double cpuUtilizedCost;
  double memoryTotalCost;
  double memorySystemCost;
  double memoryUtilizedCost;
  long startTime;
  long endTime;
  String accountId;
  String billingAccountId;
  String clusterName;
  String settingId;
  String region;
  String cloudProvider;
  String workloadType;
  String clusterType;
}
