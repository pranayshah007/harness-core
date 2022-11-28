package io.harness.migrations.timescaledb;

import io.harness.migrations.TimeScaleDBMigration;

public class BaseTimeScaleDBMigration implements TimeScaleDBMigration {
  @Override
  public boolean migrate() {
    return true;
  }
}
