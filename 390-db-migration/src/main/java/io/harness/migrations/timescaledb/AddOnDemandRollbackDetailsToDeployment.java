package io.harness.migrations.timescaledb;

public class AddOnDemandRollbackDetailsToDeployment extends AbstractTimeScaleDBMigration {
  @Override
  public String getFileName() {
    return "timescaledb/add_on_demand_rollback_details_to_deployment.sql";
  }
}
