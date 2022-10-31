package io.harness.migrations.all;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddImmutableDelegateEnabledFieldToAccountCollection implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void migrate() {
    log.info("Migration for adding ImmutableDelegateField to account collection started");
    Set<String> accountIds = featureFlagService.getAccountIds(FeatureName.USE_IMMUTABLE_DELEGATE);

    Query<Account> query = wingsPersistence.createQuery(Account.class);

    try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
      for (Account account : accounts) {
        if (accountIds.contains(account.getUuid())) {
          wingsPersistence.updateField(
              Account.class, account.getUuid(), AccountKeys.immutableDelegateEnabled, Boolean.TRUE);
        } else {
          wingsPersistence.updateField(
              Account.class, account.getUuid(), AccountKeys.immutableDelegateEnabled, Boolean.FALSE);
        }
      }
    }
    log.info("Migration for adding ImmutableDelegateField to account collection finished, {} accounts set to immutable",
        accountIds.size());
  }
}
