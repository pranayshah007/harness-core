/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UuidAware;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ActivityCleanupAndBucketMigration extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;
  @Inject ActivityService activityService;

  @Override
  public void migrate() {
    log.info("Starting cleanup for older activities and creating bucket for newer activities");
    Query<Activity> queryToDeleteOlderRecords =
        hPersistence.createQuery(Activity.class)
            .field(ActivityKeys.eventTime)
            .lessThan(Date.from(OffsetDateTime.now().minusMonths(2).toInstant()))
            .project(UuidAware.UUID_KEY, true);
    while (true) {
      List<Activity> recordsToBeDeleted = queryToDeleteOlderRecords.find(new FindOptions().limit(1000)).toList();
      List<String> uuidsToBeDeleted = recordsToBeDeleted.stream().map(Activity::getUuid).collect(Collectors.toList());
      if (uuidsToBeDeleted.size() == 0) {
        break;
      }
      hPersistence.delete(hPersistence.createQuery(Activity.class).field(UuidAware.UUID_KEY).in(uuidsToBeDeleted));
    }
    Query<Activity> queryToAddToBucket = hPersistence.createQuery(Activity.class);
    try (HIterator<Activity> iterator = new HIterator<>(queryToAddToBucket.fetch())) {
      while (iterator.hasNext()) {
        try {
          Activity activity = iterator.next();
          activityService.saveActivityBucket(activity);
        } catch (Exception ex) {
          log.error("Exception while creating bucket for newer activities", ex);
        }
      }
    } catch (Exception ex) {
      log.error("Exception while creating bucket for newer activities", ex);
    }
  }
}
