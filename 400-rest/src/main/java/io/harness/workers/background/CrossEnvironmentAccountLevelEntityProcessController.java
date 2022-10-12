/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background;

import io.harness.mongo.EntityProcessController;

import software.wings.beans.Account;
import software.wings.exception.AccountNotFoundException;
import software.wings.service.intfc.AccountService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrossEnvironmentAccountLevelEntityProcessController implements EntityProcessController<Account> {
  private final AccountService accountService;

  public CrossEnvironmentAccountLevelEntityProcessController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  public boolean shouldProcessEntity(Account account) {
    String accountId = account.getUuid();

    if (accountId == null) {
      return false;
    }
    boolean crossEnvironmentAccountStatus;
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
