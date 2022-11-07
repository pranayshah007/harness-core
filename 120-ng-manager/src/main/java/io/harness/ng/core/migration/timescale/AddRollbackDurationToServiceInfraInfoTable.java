package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AddRollbackDurationToServiceInfraInfoTable extends NGAbstractTimeScaleMigration {
  public String getFileName() {
    return "timescale/add_rollback_duration_to_service_infra_info_timescale.sql";
  }
}
