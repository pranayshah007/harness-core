/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration.clickhouse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.clickHouse.ClickHouseConstants;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class AddInstanceUsageColumnsMigration implements NGMigration {
  @Inject private ClickHouseService clickHouseService;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject @Nullable @Named("clickHouseConfig") private ClickHouseConfig clickHouseConfig;

  private static final String CLUSTER_DATA = "clusterData";
  private static final String CLUSTER_DATA_HOURLY = "clusterDataHourly";
  private static final String CLUSTER_DATA_AGGREGATED = "clusterDataAggregated";
  private static final String CLUSTER_DATA_HOURLY_AGGREGATED = "clusterDataHourlyAggregated";

  @Override
  public void migrate() {
    log.info("Starting migration to add instance usage columns in cluster tables in clickhouse");
    if (Objects.isNull(clickHouseService)) {
      log.info("ClickHouseService not injected");
      return;
    }
    if (!isClickHouseEnabled) {
      log.info("ClickHouse is not enabled");
      return;
    }
    try {
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(ClickHouseConstants.ALTER_CLUSTER_DATA_INSTANCE_USAGE_COLUMNS, CLUSTER_DATA), Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(ClickHouseConstants.ALTER_CLUSTER_DATA_INSTANCE_USAGE_COLUMNS, CLUSTER_DATA_HOURLY),
          Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(
              ClickHouseConstants.ALTER_CLUSTER_DATA_AGGREGATED_INSTANCE_USAGE_COLUMNS, CLUSTER_DATA_AGGREGATED),
          Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(
              ClickHouseConstants.ALTER_CLUSTER_DATA_AGGREGATED_INSTANCE_USAGE_COLUMNS, CLUSTER_DATA_HOURLY_AGGREGATED),
          Boolean.FALSE);
      log.info("AddInstanceUsageColumnsMigration has been completed");
    } catch (final Exception ex) {
      log.error("Failure occurred in AddInstanceUsageColumnsMigration", ex);
    }
  }
}
