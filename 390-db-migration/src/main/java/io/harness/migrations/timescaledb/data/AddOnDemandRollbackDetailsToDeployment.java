/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.timescale.migrations.DeploymentsMigrationHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddOnDemandRollbackDetailsToDeployment implements TimeScaleDBDataMigration {
  public static final int BATCH_LIMIT = 1000;
  private static final String MIGRATION_NUMBER = "16";
  private static final String MIGRATION_CLASS_NAME = "AddOnDemandRollbackDetailsToDeployment";
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject FeatureFlagService featureFlagService;
  @Inject DeploymentsMigrationHelper deploymentsMigrationHelper;

  private static final String UPDATE_STATEMENT_ROLLBACK =
      "UPDATE DEPLOYMENT SET ON_DEMAND_ROLLBACK=?, ORIGINAL_EXECUTION_ID=? WHERE EXECUTIONID=?";

  private static final String UPDATE_STATEMENT_ORIGINAL =
      "UPDATE DEPLOYMENT SET ROLLBACK_DURATION=?, MANUALLY_ROLLED_BACK=? WHERE EXECUTIONID=?";

  private static final String DEBUG_LINE = "ON_DEMAND_ROLLBACK_TIMESCALE MIGRATION: ";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return false;
    }
    try {
      log.info(DEBUG_LINE + "Migration of deployments table started");
      List<String> accountIds =
          featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC).stream().collect(Collectors.toList());
      deploymentsMigrationHelper.setOnDemandRollbackDetails(accountIds, DEBUG_LINE, BATCH_LIMIT,
          UPDATE_STATEMENT_ORIGINAL, UPDATE_STATEMENT_ROLLBACK, MIGRATION_NUMBER, MIGRATION_CLASS_NAME);
      log.info(DEBUG_LINE + "Migration to onDemandRollback details to timescale deployments successful");
      return true;
    } catch (Exception e) {
      log.error(DEBUG_LINE + "Exception occurred migrating onDemandRollback details to timescale deployments", e);
      return false;
    }
  }
}
