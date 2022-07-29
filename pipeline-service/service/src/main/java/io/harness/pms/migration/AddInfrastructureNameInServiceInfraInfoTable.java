package io.harness.pms.migration;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddInfrastructureNameInServiceInfraInfoTable extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/add_infrastructureName_to_service_infra_info_table.sql";
  }
}
