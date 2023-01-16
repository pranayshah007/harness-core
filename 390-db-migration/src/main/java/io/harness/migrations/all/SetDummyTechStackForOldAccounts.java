/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.TechStack;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script to add dummy tech stack to all existing accounts. This will allow UI to show new trial experience
 * for only new account users.
 * @author rktummala on 06/04/19
 */
@Slf4j
public class SetDummyTechStackForOldAccounts implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  @Override
  public void migrate() {
    log.info("SetDummyTechStack - Start");
    Query<Account> accountsQuery = wingsPersistence.createQuery(Account.class, excludeAuthority);
    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();
          Set<TechStack> techStacks = account.getTechStacks();
          if (isNotEmpty(techStacks)) {
            log.info("SetDummyTechStack - skip for account {}", account.getUuid());
            continue;
          }

          techStacks = new HashSet<>();
          techStacks.add(TechStack.builder().technology("NONE").category("NONE").build());

          accountService.updateTechStacks(account.getUuid(), techStacks);
          log.info("SetDummyTechStack - Updated dummy tech stacks for account {}", account.getUuid());
        } catch (Exception ex) {
          log.error("SetDummyTechStack - Error while updating dummy tech stacks for account: {}",
              account != null ? account.getAccountName() : "", ex);
        }
      }

      log.info("SetDummyTechStack - Done - Updated dummy tech stacks ");
    } catch (Exception ex) {
      log.error("SetDummyTechStack - Failed - Updated dummy tech stacks ", ex);
    }
  }
}
