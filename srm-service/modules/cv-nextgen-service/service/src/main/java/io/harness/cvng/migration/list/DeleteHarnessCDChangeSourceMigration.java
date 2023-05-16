/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.ChangeSource.ChangeSourceKeys;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteHarnessCDChangeSourceMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Inject MonitoredServiceService monitoredServiceService;

  @Override
  public void migrate() {
    log.info("Begin migration for delete Harness CD Change Source Entity and Update Monitored Service Entity");
    Query<ChangeSource> changeSourceQuery = hPersistence.createQuery(ChangeSource.class, HQuery.excludeAuthority)
                                                .filter(ChangeSourceKeys.type, ChangeSourceType.HARNESS_CD);

    try (HIterator<ChangeSource> iterator = new HIterator<>(changeSourceQuery.fetch())) {
      while (iterator.hasNext()) {
        ChangeSource changeSource = iterator.next();
        try {
          MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
              MonitoredServiceParams.builder()
                  .accountIdentifier(changeSource.getAccountId())
                  .orgIdentifier(changeSource.getOrgIdentifier())
                  .projectIdentifier(changeSource.getProjectIdentifier())
                  .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                  .build());
          if (monitoredService != null) {
            List<String> changeSourceIdList =
                monitoredService.getChangeSourceIdentifiers()
                    .stream()
                    .filter(changeSourceId -> !changeSourceId.equals(changeSource.getIdentifier()))
                    .collect(Collectors.toList());
            hPersistence.update(monitoredService,
                hPersistence.createUpdateOperations(MonitoredService.class)
                    .set(MonitoredServiceKeys.changeSourceIdentifiers, changeSourceIdList));
          }
          hPersistence.delete(changeSource);
        } catch (Exception e) {
          log.error("Could not Migrate Change Source with Id {}", changeSource.getIdentifier());
        }
      }
    }
    log.info("Deleted Harness CD Change Sources and Updated corresponding Monitored Services");
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
