/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegatesWithoutProfileMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DelegateService delegateService;
  @Inject private DelegateProfileService delegateProfileService;

  @Override
  public void migrate() {
    log.info("Starting migration of delegates without profile.");

    Query<Delegate> delegatesQuery =
        wingsPersistence.createQuery(Delegate.class).field(DelegateKeys.delegateProfileId).doesNotExist();

    try (HIterator<Delegate> delegatesWithoutProfile = new HIterator<>(delegatesQuery.fetch())) {
      for (Delegate delegate : delegatesWithoutProfile) {
        try (AutoLogContext logContext =
                 new DelegateLogContext(delegate.getUuid(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          updateDelegate(delegate);
        }
      }
    }

    log.info("Migration of delegates without profile finished.");
  }

  private void updateDelegate(Delegate delegate) {
    try {
      log.info("Fetching primary delegate profile.");
      DelegateProfile primaryProfile = delegateProfileService.fetchCgPrimaryProfile(delegate.getAccountId());

      log.info("Updating delegate.");
      Query<Delegate> updateQuery = wingsPersistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, delegate.getAccountId())
                                        .field(DelegateKeys.uuid)
                                        .equal(delegate.getUuid())
                                        .field(DelegateKeys.delegateProfileId)
                                        .doesNotExist();

      UpdateOperations<Delegate> updateOperations = wingsPersistence.createUpdateOperations(Delegate.class)
                                                        .set(DelegateKeys.delegateProfileId, primaryProfile.getUuid());

      wingsPersistence.findAndModify(updateQuery, updateOperations, new FindAndModifyOptions());

      log.info("Delegate updated successfully.");
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing delegate.", ex);
    }
  }
}
