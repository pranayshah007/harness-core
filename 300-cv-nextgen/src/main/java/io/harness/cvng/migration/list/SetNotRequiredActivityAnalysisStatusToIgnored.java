/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class SetNotRequiredActivityAnalysisStatusToIgnored implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    Query<Activity> query = hPersistence.createQuery(Activity.class, excludeAuthority)
                                .filter(ActivityKeys.analysisStatus, ActivityVerificationStatus.NOT_STARTED)
                                .field(ActivityKeys.verificationJobInstanceIds)
                                .doesNotExist();
    try (HIterator<Activity> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        Activity activity = iterator.next();
        log.info("Setting AnalysisStatus to IGNORED for activity: " + activity.getUuid());
        UpdateOperations<Activity> updateOperations =
            hPersistence.createUpdateOperations(Activity.class)
                .set(ActivityKeys.analysisStatus, ActivityVerificationStatus.IGNORED);
        hPersistence.update(activity, updateOperations);
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
