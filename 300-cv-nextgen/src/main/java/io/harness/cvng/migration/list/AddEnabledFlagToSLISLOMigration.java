/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.ServiceLevelObjectiveKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddEnabledFlagToSLISLOMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating SLI with enabled flag");
    Query<MonitoredService> monitoredServiceQuery = hPersistence.createQuery(MonitoredService.class);
    try (HIterator<MonitoredService> iterator = new HIterator<>(monitoredServiceQuery.fetch())) {
      while (iterator.hasNext()) {
        MonitoredService monitoredService = iterator.next();
        Query<ServiceLevelObjective> serviceLevelObjectiveQuery =
            hPersistence.createQuery(ServiceLevelObjective.class)
                .filter(ServiceLevelObjectiveKeys.accountId, monitoredService.getAccountId())
                .filter(ServiceLevelObjectiveKeys.projectIdentifier, monitoredService.getProjectIdentifier())
                .filter(ServiceLevelObjectiveKeys.orgIdentifier, monitoredService.getOrgIdentifier())
                .filter(ServiceLevelObjectiveKeys.monitoredServiceIdentifier, monitoredService.getIdentifier());

        Query<ServiceLevelIndicator> serviceLevelIndicatorQuery =
            hPersistence.createQuery(ServiceLevelIndicator.class)
                .filter(ServiceLevelIndicatorKeys.accountId, monitoredService.getAccountId())
                .filter(ServiceLevelIndicatorKeys.projectIdentifier, monitoredService.getProjectIdentifier())
                .filter(ServiceLevelIndicatorKeys.orgIdentifier, monitoredService.getOrgIdentifier())
                .filter(ServiceLevelIndicatorKeys.monitoredServiceIdentifier, monitoredService.getIdentifier());

        hPersistence.update(serviceLevelObjectiveQuery,
            hPersistence.createUpdateOperations(ServiceLevelObjective.class)
                .set(ServiceLevelObjectiveKeys.enabled, monitoredService.isEnabled()));

        hPersistence.update(serviceLevelIndicatorQuery,
            hPersistence.createUpdateOperations(ServiceLevelIndicator.class)
                .set(ServiceLevelIndicatorKeys.enabled, monitoredService.isEnabled()));
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
