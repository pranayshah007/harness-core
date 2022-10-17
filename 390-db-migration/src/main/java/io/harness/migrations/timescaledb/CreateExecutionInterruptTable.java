package io.harness.migrations.timescaledb;

public class CreateExecutionInterruptTable extends AbstractTimeScaleDBMigration {
  @Override
  public String getFileName() {
    return "timescaledb/create_table_execution_interrupt.sql";
  }
}
