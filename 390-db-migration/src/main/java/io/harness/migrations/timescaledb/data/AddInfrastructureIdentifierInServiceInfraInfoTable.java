package io.harness.migrations.timescaledb.data;

import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.migrations.timescaledb.AbstractTimeScaleDBMigration;

public class AddInfrastructureIdentifierInServiceInfraInfoTable
    extends AbstractTimeScaleDBMigration implements TimeScaleDBDataMigration {
  @Override
  public String getFileName() {
    return "timescaledb/add_infrastructureIdentifier_to_service_infra_info_table.sql";
  }
}
