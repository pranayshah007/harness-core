/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.processingcontrollers;

import io.harness.persistence.ProcessingController;

import software.wings.beans.account.AccountStatus;
import software.wings.exception.AccountNotFoundException;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationProcessingController implements ProcessingController {
  @Inject private AccountService accountService;

  @Override
  public boolean canProcessAccount(String accountId) {
    String accountStatus;

    try {
      accountStatus = accountService.getAccountStatus(accountId);
    } catch (AccountNotFoundException ex) {
      log.warn("Skipping processing account {}. It does not exist", accountId, ex);
      return false;
    }
    return AccountStatus.ACTIVE.equals(accountStatus);
  }
}
