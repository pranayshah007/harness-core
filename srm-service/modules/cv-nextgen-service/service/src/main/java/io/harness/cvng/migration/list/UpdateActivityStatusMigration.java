/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class UpdateActivityStatusMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private ActivityService activityService;

  @Override
  public void migrate() {
    Instant now = Instant.now();
    log.info("Begin migration for updating status of activities");
    Query<Activity> activityQuery = hPersistence.createQuery(Activity.class)
                                        .field(ActivityKeys.verificationJobInstanceIds)
                                        .exists()
                                        .field(ActivityKeys.verificationSummary)
                                        .doesNotExist();

    try (HIterator<Activity> iterator = new HIterator<>(activityQuery.fetch())) {
      while (iterator.hasNext()) {
        try {
          Activity activity = iterator.next();
          activityService.updateActivityStatus(activity);
        } catch (Exception ex) {
          log.error("Exception occurred while updating status of activity", ex);
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
