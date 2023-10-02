/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.migration;

import static io.harness.ci.commonconstants.CIExecutionConstants.DEFAULT_BUILD_MULTIPLIER;
import static io.harness.ci.commonconstants.CIExecutionConstants.MACOS_BUILD_MULTIPLIER;
import static io.harness.ci.commonconstants.CIExecutionConstants.WINDOWS_BUILD_MULTIPLIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.migration.NGMigration;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UpdateBuildCreditsTimescaleStageExecutionSummary implements NGMigration {
  private static final int MAX_RETRY_COUNT = 3;
  @Inject TimeScaleDBService timeScaleDBService;

  private static final String UPDATE_STATEMENT = String.format("UPDATE stage_execution_summary_ci "
          + "SET buildmultiplier = CASE "
          + "WHEN ostype = '%s' THEN ? "
          + "WHEN ostype = '%s' THEN ? "
          + "WHEN ostype = '%s' THEN ? "
          + "ELSE ? "
          + "END",
      OSType.Linux, OSType.Windows, OSType.MacOS);

  @Override
  public void migrate() {
    if (timeScaleDBService.isValid()) {
      log.info("Starting build credits data back filling");
      boolean success = false;
      int retryCount = 0;
      while (!success && retryCount < MAX_RETRY_COUNT) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             PreparedStatement updateStatement = connection.prepareStatement(UPDATE_STATEMENT)) {
          updateStatement.setDouble(1, DEFAULT_BUILD_MULTIPLIER);
          updateStatement.setDouble(2, WINDOWS_BUILD_MULTIPLIER);
          updateStatement.setDouble(3, MACOS_BUILD_MULTIPLIER);
          updateStatement.setDouble(4, DEFAULT_BUILD_MULTIPLIER);
          updateStatement.execute();
          success = true;
        } catch (Exception e) {
          log.error("Failed to back fill build credits data, retryCount=[{}], Exception: ", retryCount + 1, e);
          retryCount++;
        }
      }
    } else {
      log.warn("TimeScale DB is not reachable");
    }
  }
}
