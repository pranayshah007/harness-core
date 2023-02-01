package io.harness.migrations.all;

import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnableImmutableDelegateForNG implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    Query<Account> query = wingsPersistence.createQuery(Account.class).field(AccountKeys.nextGenEnabled).equal(true);
    UpdateOperations<Account> updateOperations =
        wingsPersistence.createUpdateOperations(Account.class).set(AccountKeys.immutableDelegateEnabled, true);
    wingsPersistence.update(query, updateOperations);

    log.info("immutableDelegateEnabled set for all NG accounts");
  }
}
