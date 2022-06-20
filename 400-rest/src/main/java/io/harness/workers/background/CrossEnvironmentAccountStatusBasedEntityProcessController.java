/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.workers.background;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.PersistentIterable;
import io.harness.mongo.EntityProcessController;
import io.harness.persistence.AccountAccess;

import software.wings.exception.AccountNotFoundException;
import software.wings.service.intfc.AccountService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(GTM)
@TargetModule(HarnessModule._945_ACCOUNT_MGMT)
/**
 * The account status check cross cg and ng environment
 */
public class CrossEnvironmentAccountStatusBasedEntityProcessController<T extends PersistentIterable & AccountAccess>
    implements EntityProcessController<T> {
  private final AccountService accountService;

  public CrossEnvironmentAccountStatusBasedEntityProcessController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  public boolean shouldProcessEntity(T entity) {
    String accountId = entity.getAccountId();
    boolean crossEnvironmentAccountStatus;
    if (accountId == null) {
      return false;
    }
    try {
      crossEnvironmentAccountStatus = accountService.isAccountActivelyUsed(accountId);
    } catch (AccountNotFoundException ex) {
      log.warn("Skipping processing entity. Account {} does not exist", accountId, ex);
      accountService.handleNonExistentAccount(accountId);
      return false;
    }
    return crossEnvironmentAccountStatus;
  }
}
