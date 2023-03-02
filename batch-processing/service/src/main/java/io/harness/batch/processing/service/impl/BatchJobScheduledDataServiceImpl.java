/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.batch.processing.ccm.BatchJobBucket;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.dao.intfc.BatchJobScheduledDataDao;
import io.harness.batch.processing.service.intfc.BatchJobScheduledDataService;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BatchJobScheduledDataServiceImpl implements BatchJobScheduledDataService {
  @Autowired private BatchJobScheduledDataDao batchJobScheduledDataDao;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired protected LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;

  private static final int MAX_IN_CLUSTER_DATA = 15;

  @Override
  public boolean create(BatchJobScheduledData batchJobScheduledData) {
    return batchJobScheduledDataDao.create(batchJobScheduledData);
  }

  @Override
  public Instant fetchLastBatchJobScheduledTime(String accountId, BatchJobType batchJobType) {
    Instant instant = fetchLastDependentBatchJobScheduledTime(accountId, batchJobType);
    if (null == instant) {
      if (batchJobType.equals(BatchJobType.DELEGATE_HEALTH_CHECK)
          || batchJobType.equals(BatchJobType.RECOMMENDATION_JIRA_STATUS)) {
        return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
      }
      if (ImmutableSet.of(BatchJobBucket.OUT_OF_CLUSTER, BatchJobBucket.OUT_OF_CLUSTER_ECS)
              .contains(batchJobType.getBatchJobBucket())) {
        Instant connectorCreationTime =
            Instant.ofEpochMilli(Instant.now().toEpochMilli()).truncatedTo(ChronoUnit.DAYS).minus(1, ChronoUnit.DAYS);

        if (ImmutableSet
                .of(BatchJobType.AWS_ECS_CLUSTER_SYNC, BatchJobType.AWS_EC2_SERVICE_RECOMMENDATION,
                    BatchJobType.AWS_ECS_SERVICE_RECOMMENDATION)
                .contains(batchJobType)) {
          Instant startInstant = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
          connectorCreationTime = startInstant.isAfter(connectorCreationTime) ? startInstant : connectorCreationTime;
        } else if (BatchJobType.ANOMALY_DETECTION_CLOUD == batchJobType) {
          Instant startInstant =
              Instant.ofEpochMilli(Instant.now().toEpochMilli()).truncatedTo(ChronoUnit.DAYS).minus(2, ChronoUnit.DAYS);
          connectorCreationTime = startInstant.isBefore(connectorCreationTime) ? startInstant : connectorCreationTime;
          log.info("Getting startTime for ANOMALY_DETECTION_CLOUD: {}", connectorCreationTime);
        } else {
          Instant startInstant = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
          connectorCreationTime = startInstant.isAfter(connectorCreationTime) ? startInstant : connectorCreationTime;
        }
        return connectorCreationTime;
      } else {
        instant = lastReceivedPublishedMessageDao.getFirstEventReceivedTime(accountId);
      }
    }

    if (null != instant && batchJobType == BatchJobType.DELEGATE_HEALTH_CHECK) {
      Instant startInstant = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant && batchJobType == BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION) {
      Instant startInstant = Instant.now().minus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant
        && ImmutableSet
               .of(BatchJobType.INSTANCE_BILLING, BatchJobType.ACTUAL_IDLE_COST_BILLING,
                   BatchJobType.CLUSTER_DATA_TO_BIG_QUERY)
               .contains(batchJobType)) {
      Instant startInstant = Instant.now().minus(90, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
      return instant;
    }

    if (null != instant && BatchJobType.ANOMALY_DETECTION_K8S == batchJobType) {
      Instant startInstant = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
      return instant;
    }

    if (null != instant && BatchJobType.ANOMALY_DETECTION_CLOUD == batchJobType) {
      Instant startInstant = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
      return instant;
    }

    if (null != instant
        && !ImmutableSet.of(BatchJobBucket.OUT_OF_CLUSTER, BatchJobBucket.OUT_OF_CLUSTER_ECS)
                .contains(batchJobType.getBatchJobBucket())
        && batchJobType != BatchJobType.INSTANCE_BILLING_AGGREGATION) {
      Instant startInstant = Instant.now().minus(MAX_IN_CLUSTER_DATA, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant
        && ImmutableSet
               .of(BatchJobType.RERUN_JOB, BatchJobType.AWS_ECS_CLUSTER_SYNC,
                   BatchJobType.AWS_EC2_SERVICE_RECOMMENDATION)
               .contains(batchJobType)) {
      Instant startInstant = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant
        && ImmutableSet.of(BatchJobBucket.OUT_OF_CLUSTER, BatchJobBucket.OUT_OF_CLUSTER_ECS)
               .contains(batchJobType.getBatchJobBucket())
        && !ImmutableSet
                .of(BatchJobType.AWS_ECS_CLUSTER_SYNC, BatchJobType.AWS_EC2_SERVICE_RECOMMENDATION,
                    BatchJobType.AWS_ECS_SERVICE_RECOMMENDATION)
                .contains(batchJobType)) {
      Instant startInstant = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant && batchJobType == BatchJobType.K8S_WORKLOAD_RECOMMENDATION) {
      Instant startInstant = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    // We can reduce the last days (to 2-3 days) data to generate, before GA if required.
    if (null != instant
        && ImmutableSet.of(BatchJobType.K8S_NODE_RECOMMENDATION, BatchJobType.AWS_ECS_SERVICE_RECOMMENDATION)
               .contains(batchJobType)) {
      Instant startInstant = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }

    if (null != instant && batchJobType == BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY) {
      Instant startInstant = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      instant = startInstant.isAfter(instant) ? startInstant : instant;
    }
    return instant;
  }

  @Override
  public Instant fetchLastDependentBatchJobScheduledTime(String accountId, BatchJobType batchJobType) {
    BatchJobScheduledData batchJobScheduledData =
        batchJobScheduledDataDao.fetchLastBatchJobScheduledData(accountId, batchJobType);
    if (null != batchJobScheduledData) {
      return batchJobScheduledData.getEndAt();
    }
    return null;
  }

  @Override
  public Instant fetchLastDependentBatchJobCreatedTime(String accountId, BatchJobType batchJobType) {
    BatchJobScheduledData batchJobScheduledData =
        batchJobScheduledDataDao.fetchLastBatchJobScheduledData(accountId, batchJobType);
    if (null != batchJobScheduledData) {
      return Instant.ofEpochMilli(batchJobScheduledData.getCreatedAt());
    }
    return null;
  }

  @Override
  public void invalidateJobs(CEDataCleanupRequest ceDataCleanupRequest) {
    batchJobScheduledDataDao.invalidateJobs(ceDataCleanupRequest);
  }

  @Override
  public void invalidateJobs(String accountId, List<String> batchJobTypes, Instant instant) {
    batchJobScheduledDataDao.invalidateJobs(accountId, batchJobTypes, instant);
  }
}
