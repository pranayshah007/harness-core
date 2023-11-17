/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate;

import static io.harness.eraro.ErrorCode.ACCOUNT_DOES_NOT_EXIST;

import io.harness.exception.InvalidRequestException;
import io.harness.security.AccountCheckAndCleanupService;

import software.wings.beans.account.AccountStatus;
import software.wings.exception.AccountNotFoundException;
import software.wings.helpers.ext.account.DeleteAccountHelper;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountCheckAndCleanupServiceImpl implements AccountCheckAndCleanupService {
  @Inject AccountService accountService;
  @Inject private DeleteAccountHelper deleteAccountHelper;
  private static final String UNAUTHORIZED = "Unauthorized";

  @Override
  public void ensureAccountIsNotDeleted(String accountId) {
    try {
      String accountStatus = accountService.getAccountStatus(accountId);
      if (AccountStatus.DELETED.equals(accountStatus)) {
        log.debug("account {} does not exist", accountId);
        deleteAccountHelper.deleteDataForDeletedAccount(accountId);
        throw new InvalidRequestException(UNAUTHORIZED, ACCOUNT_DOES_NOT_EXIST, null);
      }
    } catch (AccountNotFoundException exception) {
      log.debug("account {} does not exist", accountId, exception);
      deleteAccountHelper.deleteDataForDeletedAccount(accountId);
      throw new InvalidRequestException(UNAUTHORIZED, ACCOUNT_DOES_NOT_EXIST, null);
    }
  }
}
