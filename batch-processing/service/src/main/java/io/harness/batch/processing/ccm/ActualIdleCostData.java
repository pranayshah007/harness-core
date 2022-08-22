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
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActualIdleCostData {
  String accountId;
  String clusterId;
  String instanceId;
  String parentInstanceId;
  double cost;
  double cpuCost;
  double memoryCost;
  double idleCost;
  double cpuIdleCost;
  double memoryIdleCost;
  double systemCost;
  double cpuSystemCost;
  double memorySystemCost;
  long startTime;
  long endTime;
}
