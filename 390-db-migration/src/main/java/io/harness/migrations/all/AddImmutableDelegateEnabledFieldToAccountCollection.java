/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddImmutableDelegateEnabledFieldToAccountCollection implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private FeatureFlagService featureFlagService;

  private List<String> immutableDelegateEnabledAccounts = new ArrayList<>();
  private List<String> immutableDelegateDisabledAccounts = new ArrayList<>();

  @Override
  public void migrate() {
    log.info("Migration for adding ImmutableDelegateField to account collection started");
    Set<String> accountIds = featureFlagService.getAccountIds(FeatureName.USE_IMMUTABLE_DELEGATE);

    Query<Account> query = wingsPersistence.createQuery(Account.class);

    try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
      for (Account account : accounts) {
        if (accountIds.contains(account.getUuid())) {
          immutableDelegateEnabledAccounts.add(account.getUuid());
        } else {
          immutableDelegateDisabledAccounts.add(account.getUuid());
        }
      }
    }

    Query<Account> immutableEnabledQuery =
        wingsPersistence.createQuery(Account.class).field(AccountKeys.uuid).hasAnyOf(immutableDelegateEnabledAccounts);
    Query<Account> immutableDisabledQuery =
        wingsPersistence.createQuery(Account.class).field(AccountKeys.uuid).hasAnyOf(immutableDelegateDisabledAccounts);

    wingsPersistence.update(immutableEnabledQuery,
        wingsPersistence.createUpdateOperations(Account.class).set(AccountKeys.immutableDelegateEnabled, true));
    wingsPersistence.update(immutableDisabledQuery,
        wingsPersistence.createUpdateOperations(Account.class).set(AccountKeys.immutableDelegateEnabled, false));

    log.info("Migration for adding ImmutableDelegateField to account collection finished, {} accounts set to immutable",
        accountIds.size());
  }
}
