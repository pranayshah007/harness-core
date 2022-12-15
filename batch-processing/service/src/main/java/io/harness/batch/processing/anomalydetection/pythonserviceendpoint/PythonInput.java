/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.pythonserviceendpoint;

import io.harness.ccm.anomaly.entities.TimeGranularity;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PythonInput {
  String id;
  String accountId;
  TimeGranularity timeGranularity;
  String region;
  String cloudProvider;
  String clusterId;
  String clusterName;
  String workloadName;
  String workloadType;
  String namespace;
  String gcpProject;
  String gcpSKUId;
  String gcpSKUDescription;
  String gcpProduct;
  String awsAccount;
  String awsService;
  String awsInstanceType;
  String awsUsageType;
  String azureSubscription;
  String azureResourceGroup;
  String azureMeterCategory;

  APITimeSeries data;

  @Builder
  static class APITimeSeries {
    List<DataPoint> train;
    List<DataPoint> test;
  }

  @Builder
  static class DataPoint {
    Long time;
    Double y;
  }
}
