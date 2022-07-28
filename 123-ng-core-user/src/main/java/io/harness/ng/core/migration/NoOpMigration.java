package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class NoOpMigration implements NGMigration {
  @Override
  public void migrate() {
    log.info("Executing Noop migration");
    // do nothing
  }
}
