/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import static io.harness.batch.processing.ccm.BatchJobBucket.IN_CLUSTER;
import static io.harness.batch.processing.ccm.BatchJobBucket.IN_CLUSTER_BILLING;
import static io.harness.batch.processing.ccm.BatchJobBucket.IN_CLUSTER_NODE_RECOMMENDATION;
import static io.harness.batch.processing.ccm.BatchJobBucket.IN_CLUSTER_RECOMMENDATION;
import static io.harness.batch.processing.ccm.BatchJobBucket.OTHERS;
import static io.harness.batch.processing.ccm.BatchJobBucket.OUT_OF_CLUSTER;
import static io.harness.batch.processing.ccm.BatchJobBucket.OUT_OF_CLUSTER_ECS;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.springframework.batch.core.Job;

@Getter
@OwnedBy(HarnessTeam.CE)
public enum BatchJobType {
  AWS_EC2_SERVICE_RECOMMENDATION(10, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER_ECS),
  BILLING_DATA_PIPELINE(50, 1, ChronoUnit.HOURS, emptyList(), OUT_OF_CLUSTER),
  SYNC_BILLING_REPORT_S3(100, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER),
  SYNC_BILLING_REPORT_AZURE(100, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER),
  SYNC_BILLING_REPORT_GCP(100, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER),
  SYNC_BILLING_REPORT_S3_TO_CLICKHOUSE(105, 1, ChronoUnit.HOURS, emptyList(), OUT_OF_CLUSTER),
  AWS_ECS_CLUSTER_SYNC(110, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER_ECS),
  AZURE_VM_RECOMMENDATION(110, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER),
  AWS_ECS_CLUSTER_DATA_SYNC(115, 1, ChronoUnit.HOURS, emptyList(), OUT_OF_CLUSTER_ECS),
  AWS_ECS_SERVICE_RECOMMENDATION(130, 1, ChronoUnit.DAYS, singletonList(AWS_ECS_CLUSTER_DATA_SYNC), OUT_OF_CLUSTER_ECS),
  DEPLOYMENT_EVENT(150, 1, ChronoUnit.DAYS, emptyList(), IN_CLUSTER),
  K8S_EVENT(300, 1, ChronoUnit.HOURS, emptyList(), IN_CLUSTER),
  DELEGATE_HEALTH_CHECK(310, 1, ChronoUnit.HOURS, emptyList(), IN_CLUSTER),
  K8S_WATCH_EVENT(350, 1, ChronoUnit.DAYS, singletonList(K8S_EVENT), IN_CLUSTER_BILLING),
  K8S_UTILIZATION(500, 1, ChronoUnit.HOURS, singletonList(K8S_EVENT), IN_CLUSTER_BILLING),
  INSTANCE_BILLING_HOURLY(600, 1, ChronoUnit.HOURS, Arrays.asList(K8S_UTILIZATION), IN_CLUSTER_BILLING),
  ACTUAL_IDLE_COST_BILLING_HOURLY(650, 1, ChronoUnit.HOURS, singletonList(INSTANCE_BILLING_HOURLY), IN_CLUSTER_BILLING),
  CLUSTER_DATA_HOURLY_TO_BIG_QUERY(
      651, 1, ChronoUnit.HOURS, Arrays.asList(ACTUAL_IDLE_COST_BILLING_HOURLY), IN_CLUSTER_BILLING),
  INSTANCE_BILLING(750, 1, ChronoUnit.DAYS, Arrays.asList(K8S_UTILIZATION), IN_CLUSTER_BILLING),
  ACTUAL_IDLE_COST_BILLING(800, 1, ChronoUnit.DAYS, singletonList(INSTANCE_BILLING), IN_CLUSTER_BILLING),
  NODE_POD_COUNT(860, 1, ChronoUnit.DAYS, singletonList(INSTANCE_BILLING), IN_CLUSTER_BILLING),
  K8S_WORKLOAD_RECOMMENDATION(875, 1, ChronoUnit.DAYS, Collections.singletonList(K8S_EVENT), IN_CLUSTER_RECOMMENDATION),
  K8S_NODE_RECOMMENDATION(
      877, 1, ChronoUnit.DAYS, Collections.singletonList(K8S_UTILIZATION), IN_CLUSTER_NODE_RECOMMENDATION),
  INSTANCE_BILLING_HOURLY_AGGREGATION(
      881, 1, ChronoUnit.HOURS, singletonList(ACTUAL_IDLE_COST_BILLING_HOURLY), IN_CLUSTER_BILLING),
  INSTANCE_BILLING_AGGREGATION(887, 1, ChronoUnit.DAYS, Arrays.asList(ACTUAL_IDLE_COST_BILLING), IN_CLUSTER_BILLING),
  CE_SEGMENT_CALL(900, 1, ChronoUnit.DAYS, Arrays.asList(ACTUAL_IDLE_COST_BILLING), OTHERS),
  RECOMMENDATION_JIRA_STATUS(950, 1, ChronoUnit.HOURS, emptyList(), OTHERS),
  MSP_MARKUP_AMOUNT(1000, 4, ChronoUnit.HOURS, emptyList(), OTHERS),
  CLUSTER_DATA_TO_BIG_QUERY(1000, 1, ChronoUnit.DAYS, Arrays.asList(ACTUAL_IDLE_COST_BILLING), IN_CLUSTER_BILLING),
  ANOMALY_DETECTION_K8S(1000, 1, ChronoUnit.DAYS, singletonList(INSTANCE_BILLING), IN_CLUSTER_BILLING),
  ANOMALY_DETECTION_CLOUD(1000, 1, ChronoUnit.DAYS, emptyList(), OUT_OF_CLUSTER),
  DATA_CHECK_BIGQUERY_TIMESCALE(1000, 1, ChronoUnit.DAYS, singletonList(CLUSTER_DATA_TO_BIG_QUERY), IN_CLUSTER_BILLING),
  RERUN_JOB(1500, 1, ChronoUnit.DAYS, emptyList(), IN_CLUSTER_BILLING);

  // Specifies order in which the jobs are to be run
  private final int order;
  private final long interval;
  private final ChronoUnit intervalUnit;
  // dependency jobs that need to be run before this one.
  private final List<BatchJobType> dependentBatchJobs;
  private final BatchJobBucket batchJobBucket;

  BatchJobType(int order, long interval, ChronoUnit intervalUnit, List<BatchJobType> dependentBatchJobs,
      BatchJobBucket batchJobBucket) {
    this.order = order;
    this.interval = interval;
    this.intervalUnit = intervalUnit;
    this.dependentBatchJobs = dependentBatchJobs;
    this.batchJobBucket = batchJobBucket;
  }

  public static BatchJobType fromJob(Job job) {
    return BatchJobType.valueOf(job.getName());
  }
}
