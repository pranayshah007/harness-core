/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddEnvRefsToMonitoredServiceMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating monitored service with envIdentifierList");
    Query<MonitoredService> monitoredServiceQuery = hPersistence.createQuery(MonitoredService.class);
    try (HIterator<MonitoredService> iterator = new HIterator<>(monitoredServiceQuery.fetch())) {
      while (iterator.hasNext()) {
        MonitoredService monitoredService = iterator.next();
        if (isEmpty(monitoredService.getEnvironmentIdentifierList())) {
          UpdateResults updateResults = hPersistence.update(monitoredService,
              hPersistence.createUpdateOperations(MonitoredService.class)
                  .set(MonitoredServiceKeys.environmentIdentifierList,
                      Collections.singletonList(monitoredService.getEnvironmentIdentifier())));
          log.info("Updated for monitored service {}, {}, Update Result {}", monitoredService.getProjectIdentifier(),
              monitoredService.getIdentifier(), updateResults);
        } else {
          log.info("Skipping as the env list is already set.");
        }
      }
    }
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
