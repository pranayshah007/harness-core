/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.trigger.TriggerExecution;

import dev.morphia.Key;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AddAccountIdToTriggerExecutions extends AddAccountIdToAppEntities {
  @Override
  public void migrate() {
    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createAnalyticsQuery(Account.class, excludeAuthority)
                                 .project(Account.ID_KEY2, true)
                                 .fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();
        final String accountId = account.getUuid();

        List<Key<Application>> appIdKeyList =
            wingsPersistence.createQuery(Application.class).filter(ApplicationKeys.accountId, accountId).asKeyList();

        if (isNotEmpty(appIdKeyList)) {
          Set<String> appIdSet =
              appIdKeyList.stream().map(applicationKey -> (String) applicationKey.getId()).collect(Collectors.toSet());

          bulkSetAccountId(accountId, TriggerExecution.class, appIdSet);
        }
      }
    }
  }
}
