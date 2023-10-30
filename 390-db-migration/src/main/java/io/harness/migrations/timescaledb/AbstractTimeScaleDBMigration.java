/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.timescaledb;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.TimeScaleDBMigration;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.ScriptRunner;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public abstract class AbstractTimeScaleDBMigration implements TimeScaleDBMigration {
  @Inject TimeScaleDBService timeScaleDBService;

  private void runMigration(Connection connection, String name) {
    InputStream inputstream = getClass().getClassLoader().getResourceAsStream(name);
    if (inputstream == null) {
      log.warn("Skipping migration {} as script not found", name);
      return;
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
  public boolean migrate() {
    if (timeScaleDBService.isValid()) {
      try (Connection connection = timeScaleDBService.getDBConnection()) {
        runMigration(connection, getFileName());
        return true;
      } catch (Exception e) {
        log.error("Failed to run instance rename migration on db", e);
        return false;
      }
    } else {
      log.info("TIMESCALEDBSERVICE NOT AVAILABLE");
      return false;
    }
  }
}
