package io.harness.migrations.timescaledb;

public class CreateDeploymentStepTable extends AbstractTimeScaleDBMigration {
  @Override
  public String getFileName() {
    return "timescaledb/create_table_deployment_step.sql";
  }
}
