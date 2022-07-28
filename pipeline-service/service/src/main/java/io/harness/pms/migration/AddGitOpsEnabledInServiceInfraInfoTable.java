package io.harness.pms.migration;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddGitOpsEnabledInServiceInfraInfoTable extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/add_gitOpsEnabled_to_service_infra_info_table.sql";
  }
}
