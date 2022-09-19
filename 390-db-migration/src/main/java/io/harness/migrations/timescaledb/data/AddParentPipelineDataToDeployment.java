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
public class AddParentPipelineDataToDeployment implements TimeScaleDBDataMigration {
  public static final int BATCH_LIMIT = 1000;
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject FeatureFlagService featureFlagService;
  @Inject DeploymentsMigrationHelper deploymentsMigrationHelper;

  private static final String update_statement =
      "UPDATE DEPLOYMENT SET PARENT_PIPELINE_ID=?, WORKFLOWS=?, CREATED_BY_TYPE=? WHERE EXECUTIONID=?";

  private static final String query_statement = "SELECT * FROM DEPLOYMENT WHERE EXECUTIONID=?";

  private String debugLine = "PARENT_PIPELINE_TIMESCALE MIGRATION: ";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return false;
    }
    try {
      log.info(debugLine + "Migration of deployments table started");
      List<String> accountIds =
          featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC).stream().collect(Collectors.toList());
      deploymentsMigrationHelper.setParentPipelineForAccountIds(accountIds, debugLine, BATCH_LIMIT, update_statement);
      log.info(debugLine + "Migration to populate parent pipeline id to timescale deployments successful");
      return true;
    } catch (Exception e) {
      log.error(debugLine + "Exception occurred migrating parent pipeline id to timescale deployments", e);
      return false;
    }
  }
}
