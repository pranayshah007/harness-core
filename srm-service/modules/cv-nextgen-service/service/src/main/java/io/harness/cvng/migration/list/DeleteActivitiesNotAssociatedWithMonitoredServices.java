/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DeleteActivitiesNotAssociatedWithMonitoredServices extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for deleting activities whose monitored service does not exist any more");
    List<MonitoredService> monitoredServiceList = hPersistence.createQuery(MonitoredService.class).asList();
    Set<String> monitoredServiceSet =
        monitoredServiceList.stream()
            .map(monitoredService
                -> ScopedInformation.getScopedInformation(monitoredService.getAccountId(),
                    monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier(),
                    monitoredService.getIdentifier()))
            .collect(Collectors.toSet());

    Query<Activity> activityQuery = hPersistence.createQuery(Activity.class);

    List<String> toBeDeletedActivitesUuid = new ArrayList<>();
    final int limit = 1000;

    try (HIterator<Activity> iterator = new HIterator<>(activityQuery.fetch())) {
      while (iterator.hasNext()) {
        try {
          Activity activity = iterator.next();
          if (!monitoredServiceSet.contains(
                  ScopedInformation.getScopedInformation(activity.getAccountId(), activity.getOrgIdentifier(),
                      activity.getProjectIdentifier(), activity.getMonitoredServiceIdentifier()))) {
            toBeDeletedActivitesUuid.add(activity.getUuid());
          }
          if (toBeDeletedActivitesUuid.size() >= limit) {
            hPersistence.deleteOnServer(
                hPersistence.createQuery(Activity.class).field(ActivityKeys.uuid).in(toBeDeletedActivitesUuid));
            toBeDeletedActivitesUuid.clear();
          }
        } catch (Exception ex) {
          log.error("Exception occurred while deleting activity", ex);
        }
      }
      hPersistence.deleteOnServer(
          hPersistence.createQuery(Activity.class).field(ActivityKeys.uuid).in(toBeDeletedActivitesUuid));
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
