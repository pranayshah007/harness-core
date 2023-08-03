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
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class SetupClickHouseTablesAndDatabase implements NGMigration {
  @Inject ClickHouseService clickHouseService;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  private static final String CLUSTER_DATA_TABLE_NAME = "clusterData";
  private static final String CLUSTER_DATA_AGGREGATED = "clusterDataAggregated";
  private static final String CLUSTER_DATA_HOURLY = "clusterDataHourly";
  private static final String CLUSTER_DATA_HOURLY_AGGREGATED = "clusterDataHourlyAggregated";

  @Override
  public void migrate() {
    log.info("Starting migration to add ccm db and tables in clickhouse");
    if (clickHouseService == null) {
      log.info("clickHouseService not injected");
      return;
    }
    if (!isClickHouseEnabled) {
      log.info("clickHouse is not enabled");
      return;
    }
    try {
      clickHouseService.executeClickHouseQuery(clickHouseConfig, ClickHouseConstants.createCCMDBQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(
          clickHouseConfig, ClickHouseConstants.createAwsCurTableQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(
          clickHouseConfig, ClickHouseConstants.createUnifiedTableTableQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(
          clickHouseConfig, ClickHouseConstants.createPreAggregatedTableQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(
          clickHouseConfig, ClickHouseConstants.createCostAggregatedTableQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(
          clickHouseConfig, ClickHouseConstants.createConnectorDataSyncStatusTableQuery, Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(ClickHouseConstants.CLUSTER_DATA_TABLE_CREATION_QUERY, CLUSTER_DATA_TABLE_NAME), Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(ClickHouseConstants.CLUSTER_DATA_AGGREGATED_TABLE_CREATION_QUERY, CLUSTER_DATA_AGGREGATED),
          Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(ClickHouseConstants.CLUSTER_DATA_TABLE_CREATION_QUERY, CLUSTER_DATA_HOURLY), Boolean.FALSE);
      clickHouseService.executeClickHouseQuery(clickHouseConfig,
          String.format(
              ClickHouseConstants.CLUSTER_DATA_AGGREGATED_TABLE_CREATION_QUERY, CLUSTER_DATA_HOURLY_AGGREGATED),
          Boolean.FALSE);
      log.info("SetupClickHouseTablesAndDatabase has been completed");
    } catch (Exception e) {
      log.error("Failure occurred in SetupClickHouseTablesAndDatabase: {}", e.getMessage());
    }
  }
}
