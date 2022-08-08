/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddMonitoredServiceToCVConfigMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating CVConfig with monitoredServiceIdentifier");
    Query<MonitoredService> monitoredServiceQuery = hPersistence.createQuery(MonitoredService.class);
    try (HIterator<MonitoredService> iterator = new HIterator<>(monitoredServiceQuery.fetch())) {
      while (iterator.hasNext()) {
        MonitoredService monitoredService = iterator.next();
        Query<CVConfig> cvConfigQuery =
            hPersistence.createQuery(CVConfig.class)
                .filter(CVConfigKeys.accountId, monitoredService.getAccountId())
                .filter(CVConfigKeys.projectIdentifier, monitoredService.getProjectIdentifier())
                .filter(CVConfigKeys.orgIdentifier, monitoredService.getOrgIdentifier())
                .filter("serviceIdentifier", monitoredService.getServiceIdentifier())
                .filter("envIdentifier", monitoredService.getEnvironmentIdentifier());

        hPersistence.update(cvConfigQuery,
            hPersistence.createUpdateOperations(CVConfig.class)
                .set(CVConfigKeys.monitoredServiceIdentifier, monitoredService.getIdentifier()));
        log.info("Updated for monitored service {}, {}", monitoredService.getProjectIdentifier(),
            monitoredService.getIdentifier());
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
