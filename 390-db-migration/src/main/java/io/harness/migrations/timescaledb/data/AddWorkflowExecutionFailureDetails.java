/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.timescale.migrations.DeploymentsMigrationHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * This will migrate the last 30 days of top level executions to TimeScaleDB
 */
@Slf4j
@Singleton
public class AddWorkflowExecutionFailureDetails implements TimeScaleDBDataMigration {
  public static final int BATCH_LIMIT = 500;
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject FeatureFlagService featureFlagService;

  @Inject DeploymentsMigrationHelper deploymentsMigrationHelper;

  private static final String update_statement =
      "UPDATE DEPLOYMENT SET FAILURE_DETAILS=?,FAILED_STEP_NAMES=?,FAILED_STEP_TYPES=? WHERE EXECUTIONID=?";

  private String debugLine = "EXECUTION_FAILURE_TIMESCALE MIGRATION: ";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return false;
    }
    try {
      log.info(debugLine + "Migration of stateExecutionInstances started");
      List<String> accountIds =
          featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC).stream().collect(Collectors.toList());
      deploymentsMigrationHelper.setFailureDetailsForAccountIds(accountIds, debugLine, BATCH_LIMIT, update_statement);
      log.info(debugLine + "Migration to populate parent pipeline id to timescale deployments successful");
      return true;
    } catch (Exception e) {
      log.error(debugLine + "Exception occurred migrating parent pipeline id to timescale deployments", e);
      return false;
    }
  }
}
