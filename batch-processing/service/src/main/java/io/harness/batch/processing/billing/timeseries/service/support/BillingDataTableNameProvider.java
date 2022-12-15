/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.support;

import static io.harness.batch.processing.ccm.BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_AGGREGATION;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_HOURLY;
import static io.harness.batch.processing.ccm.BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION;

import io.harness.batch.processing.ccm.BatchJobType;

import com.google.common.collect.ImmutableSet;
import org.springframework.stereotype.Component;

@Component
public class BillingDataTableNameProvider {
  private static final String DAILY_BILLING_DATA_TABLE = "billing_data";
  private static final String HOURLY_BILLING_DATA_TABLE = "billing_data_hourly";
  private static final String DAILY_BILLING_DATA_AGGREGATED_TABLE = "billing_data_aggregated";
  private static final String DAILY_BILLING_DATA_HOURLY_AGGREGATED_TABLE = "billing_data_hourly_aggregated";

  public static String replaceTableName(String statement, BatchJobType batchJobType) {
    return String.format(statement, getTableName(batchJobType));
  }

  private BillingDataTableNameProvider() {}

  public static String getTableName(BatchJobType batchJobType) {
    String tableName = DAILY_BILLING_DATA_TABLE;
    if (ImmutableSet.of(INSTANCE_BILLING_HOURLY, ACTUAL_IDLE_COST_BILLING_HOURLY).contains(batchJobType)) {
      tableName = HOURLY_BILLING_DATA_TABLE;
    } else if (batchJobType == INSTANCE_BILLING_AGGREGATION) {
      tableName = DAILY_BILLING_DATA_AGGREGATED_TABLE;
    } else if (batchJobType == INSTANCE_BILLING_HOURLY_AGGREGATION) {
      tableName = DAILY_BILLING_DATA_HOURLY_AGGREGATED_TABLE;
    }
    return tableName;
  }
}
