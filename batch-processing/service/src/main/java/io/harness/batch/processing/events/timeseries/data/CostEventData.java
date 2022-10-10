/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.events.timeseries.data;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CostEventData {
  String accountId;
  String settingId;
  String clusterId;
  String clusterType;
  String instanceId;
  String instanceType;
  String appId;
  String serviceId;
  String envId;
  String cloudProviderId;
  String deploymentId;
  String cloudProvider;
  String eventDescription;
  String costEventType;
  String costEventSource;
  String namespace;
  String workloadName;
  String workloadType;
  String cloudServiceName;
  String taskId;
  String launchType;

  BigDecimal billingAmount;

  long startTimestamp;

  String oldYamlRef;
  String newYamlRef;

  BigDecimal costChangePercent;
}
