/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.TimeGranularity;

import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@OwnedBy(CE)
public class AnomalyStub {
  public static String accountId = "ACCOUNT_ID";
  public static Instant anomalyTime = Instant.ofEpochMilli(0);

  public static AnomalyDetectionTimeSeries getTimeSeries() {
    TimeSeriesMetaData timeSeriesMetaData = TimeSeriesMetaData.builder()
                                                .accountId(accountId)
                                                .trainStart(anomalyTime.minus(31, ChronoUnit.DAYS))
                                                .trainEnd(anomalyTime.minus(1, ChronoUnit.DAYS))
                                                .testStart(anomalyTime.minus(1, ChronoUnit.DAYS))
                                                .testEnd(anomalyTime)
                                                .timeGranularity(TimeGranularity.DAILY)
                                                .build();
    return AnomalyDetectionTimeSeries.initialiseNewTimeSeries(timeSeriesMetaData);
  }

  public static AnomalyDetectionTimeSeries getNewEntityTimeSeries() {
    TimeSeriesMetaData timeSeriesMetaData = getTimeSeriesMetaData();
    AnomalyDetectionTimeSeries timeSeries = AnomalyDetectionTimeSeries.initialiseNewTimeSeries(timeSeriesMetaData);

    List<Double> trainPoints = timeSeries.getTrainDataPointsList();
    List<Double> testPoints = timeSeries.getTestDataPointsList();
    testPoints.set(0, 150.0);

    return timeSeries;
  }

  public static TimeSeriesMetaData getTimeSeriesMetaData() {
    return TimeSeriesMetaData.builder()
        .accountId(accountId)
        .trainStart(anomalyTime.minus(31, ChronoUnit.DAYS))
        .trainEnd(anomalyTime.minus(1, ChronoUnit.DAYS))
        .testStart(anomalyTime.minus(1, ChronoUnit.DAYS))
        .testEnd(anomalyTime)
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyEntity getClusterAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID1")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("K8S_Anomaly")
        .anomalyScore(12.34)
        .clusterId("CLUSTER_ID")
        .clusterName("CLUSTER_NAME")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyEntity getNamespaceAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("K8S_Anomaly")
        .anomalyScore(12.34)
        .clusterId("CLUSTER_ID")
        .clusterName("CLUSTER_NAME")
        .namespace("NAMESPACE")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyEntity getGcpProjectAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("GCP_Project_Anomaly")
        .anomalyScore(12.34)
        .gcpProject("GCP_PROJECT")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }
  public static AnomalyEntity getAwsAccountAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("Aws_Account_Anomaly")
        .anomalyScore(12.34)
        .awsAccount("AWS_ACCOUNT")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static QLBillingDataFilter getBeforeTimeFilter() {
    return QLBillingDataFilter.builder()
        .startTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(anomalyTime.toEpochMilli()).build())
        .build();
  }

  public static QLBillingDataFilter getAfterTimeFilter() {
    return QLBillingDataFilter.builder()
        .startTime(QLTimeFilter.builder()
                       .operator(QLTimeOperator.AFTER)
                       .value(anomalyTime.minus(15, ChronoUnit.DAYS).toEpochMilli())
                       .build())
        .build();
  }

  public static QLCCMGroupBy getClusterGroupBy() {
    return QLCCMGroupBy.builder().entityGroupBy(QLCCMEntityGroupBy.Cluster).build();
  }
}
