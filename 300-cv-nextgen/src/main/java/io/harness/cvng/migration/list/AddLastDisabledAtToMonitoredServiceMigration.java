/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddLastDisabledAtToMonitoredServiceMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;

  @Override
  public void migrate() {
    log.info("Begin migration for updating SLI with enabled flag");
    Query<MonitoredService> monitoredServiceQuery =
        hPersistence.createQuery(MonitoredService.class).filter(MonitoredServiceKeys.lastDisabledAt, null);
    hPersistence.update(monitoredServiceQuery,
        hPersistence.createUpdateOperations(MonitoredService.class)
            .set(MonitoredServiceKeys.lastDisabledAt, clock.millis()));
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
