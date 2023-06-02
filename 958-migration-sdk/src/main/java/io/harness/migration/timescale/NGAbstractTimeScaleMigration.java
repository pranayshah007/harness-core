/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationException;
import io.harness.migration.NGMigration;
import io.harness.migration.TimeScaleNotAvailableException;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.ScriptRunner;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public abstract class NGAbstractTimeScaleMigration implements NGMigration {
  @Inject TimeScaleDBService timeScaleDBService;

  private void runMigration(Connection connection, String name) throws Exception {
    InputStream inputstream = getClass().getClassLoader().getResourceAsStream(name);
    if (inputstream == null) {
      throw new MigrationException(
          String.format("[Migration]: Unable to run migration %s as script %s not found", getClass(), name));
    }
    InputStreamReader inputStreamReader = new InputStreamReader(inputstream, StandardCharsets.UTF_8);
    ScriptRunner scriptRunner = new ScriptRunner(connection);
    scriptRunner.setStopOnError(true);
    scriptRunner.setSendFullScript(executeFullScript());
    scriptRunner.runScript(inputStreamReader);
  }

  public abstract String getFileName();

  public boolean executeFullScript() {
    return false;
  }

  @Override
  public void migrate() {
    if (timeScaleDBService.isValid()) {
      try (Connection connection = timeScaleDBService.getDBConnection()) {
        runMigration(connection, getFileName());
      } catch (Exception e) {
        throw new MigrationException(String.format("[Migration]: Migration %s failed", getClass()), e);
      }
    } else {
      throw new TimeScaleNotAvailableException(
          String.format("[Migration]: Migration %s failed - TIMESCALEDBSERVICE NOT AVAILABLE", getClass()));
    }
  }
}
