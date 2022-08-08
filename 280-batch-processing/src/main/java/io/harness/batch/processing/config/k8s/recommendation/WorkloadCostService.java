/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl.executeStatementAndGetCost;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.google.common.annotations.VisibleForTesting;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkloadCostService {
  /**
   *  1,6-accountId
   *  2,7-clusterId
   *  3,8-namespace
   *  4,9-workloadtype
   *  5,10-workloadname
   *  11 - startInclusive
   */
  @VisibleForTesting
  static final String LAST_AVAILABLE_DAY_COST = "SELECT SUM(CPUBILLINGAMOUNT), SUM(MEMORYBILLINGAMOUNT)"
      + "FROM BILLING_DATA WHERE INSTANCETYPE IN ('K8S_POD', 'K8S_POD_FARGATE') AND ACCOUNTID = ? AND CLUSTERID = ? AND NAMESPACE = ? "
      + "AND WORKLOADTYPE = ? AND WORKLOADNAME = ? AND STARTTIME = "
      + "(SELECT MAX(STARTTIME) FROM BILLING_DATA WHERE INSTANCETYPE IN ('K8S_POD', 'K8S_POD_FARGATE') AND ACCOUNTID = ? AND CLUSTERID = ? "
      + "AND NAMESPACE = ? AND WORKLOADTYPE = ? AND WORKLOADNAME = ? AND STARTTIME >= ?)";

  private final TimeScaleDBService timeScaleDBService;

  public WorkloadCostService(TimeScaleDBService timeScaleDBService) {
    this.timeScaleDBService = timeScaleDBService;
  }

  private boolean isTruncatedToDay(Instant instant) {
    return instant.truncatedTo(ChronoUnit.DAYS).equals(instant);
  }

  /**
   * Get actual cost incurred by this workload, in the last day for which cost is available.
   */
  public Cost getLastAvailableDayCost(ResourceId workloadId, Instant startInclusive) {
    checkState(timeScaleDBService.isValid());
    checkArgument(isTruncatedToDay(startInclusive));
    for (int retryCount = 0; retryCount < 5; retryCount++) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(LAST_AVAILABLE_DAY_COST)) {
        statement.setString(1, workloadId.getAccountId());
        statement.setString(2, workloadId.getClusterId());
        statement.setString(3, workloadId.getNamespace());
        statement.setString(4, workloadId.getKind());
        statement.setString(5, workloadId.getName());
        statement.setString(6, workloadId.getAccountId());
        statement.setString(7, workloadId.getClusterId());
        statement.setString(8, workloadId.getNamespace());
        statement.setString(9, workloadId.getKind());
        statement.setString(10, workloadId.getName());
        statement.setTimestamp(11, new Timestamp(startInclusive.toEpochMilli()));
        return executeStatementAndGetCost(statement);
      } catch (SQLException e) {
        log.error("Failed to fetch cost. retryCount=[{}]", retryCount, e);
      }
    }
    return null;
  }
}
