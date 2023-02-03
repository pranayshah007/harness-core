/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.CUSTOM_DASHBOARD_DEPLOYMENT_FETCH_LONGER_RETENTION_DATA;
import static io.harness.beans.FeatureName.CUSTOM_DASHBOARD_INSTANCE_FETCH_LONGER_RETENTION_DATA;

import static software.wings.beans.datatretention.LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION;
import static software.wings.beans.datatretention.LongerDataRetentionState.INSTANCE_LONGER_RETENTION;
import static software.wings.beans.datatretention.LongerDataRetentionState.LongerDataRetentionStateKeys;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.beans.datatretention.LongerDataRetentionState.LongerDataRetentionStateBuilder;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class CreateLongerDataRetention implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void migrate() {
    log.info("Starting migration");
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        log.info("Starting Migration for data retention " + account.getUuid());
        try (AutoLogContext ignore1 =
                 new AccountLogContext(account.getUuid(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          createLongerDataRetention(account);
        } catch (Exception e) {
          log.info("Error in running migration for account: {}", account.getUuid(), e);
        }
      }
    } catch (Exception ex) {
      log.error("Exception while fetching Accounts", ex);
    }
    log.info("Completed migration");
  }

  private void createLongerDataRetention(Account account) {
    LongerDataRetentionStateBuilder longerDataRetentionStateBuilder =
        LongerDataRetentionState.builder().accountId(account.getUuid());

    Map<String, Boolean> keyRetentionCompletedMap = new HashMap<>();

    if (featureFlagService.isEnabled(CUSTOM_DASHBOARD_INSTANCE_FETCH_LONGER_RETENTION_DATA, account.getUuid())) {
      keyRetentionCompletedMap.put(INSTANCE_LONGER_RETENTION, true);
    } else {
      keyRetentionCompletedMap.put(INSTANCE_LONGER_RETENTION, false);
    }

    if (featureFlagService.isEnabled(CUSTOM_DASHBOARD_DEPLOYMENT_FETCH_LONGER_RETENTION_DATA, account.getUuid())) {
      keyRetentionCompletedMap.put(DEPLOYMENT_LONGER_RETENTION, true);
    } else {
      keyRetentionCompletedMap.put(DEPLOYMENT_LONGER_RETENTION, false);
    }
    longerDataRetentionStateBuilder.keyRetentionCompletedMap(keyRetentionCompletedMap);

    UpdateOperations<LongerDataRetentionState> longerDataRetentionStateUpdateOperations =
        wingsPersistence.createUpdateOperations(LongerDataRetentionState.class)
            .set(LongerDataRetentionStateKeys.keyRetentionCompletedMap, keyRetentionCompletedMap)
            .set(LongerDataRetentionStateKeys.accountId, account.getUuid());
    wingsPersistence.upsert(wingsPersistence.createQuery(LongerDataRetentionState.class)
                                .field(LongerDataRetentionStateKeys.accountId)
                                .equal(account.getUuid()),
        longerDataRetentionStateUpdateOperations);
  }
}
