/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteCVAlertsMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    List<Account> harnessAccounts =
        wingsPersistence.createQuery(Account.class).filter(AccountKeys.accountName, "Harness.io").asList();
    if (isEmpty(harnessAccounts)) {
      log.info("There are no harness accounts in DeleteCVAlertsMigration. Returning");
      return;
    }

    List<String> harnessAccountIds = new ArrayList<>();
    harnessAccounts.forEach(account -> harnessAccountIds.add(account.getUuid()));

    Query<Alert> alertQuery = wingsPersistence.createQuery(Alert.class)
                                  .filter(AlertKeys.type, "CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT");

    List<Alert> alerts = alertQuery.asList();
    log.info("Total number of CV Datacollection alerts to be deleted {}", alerts.size());
    wingsPersistence.delete(alertQuery);
    log.info("{} CV Datacollection alerts have been deleted", alerts.size());
  }
}
