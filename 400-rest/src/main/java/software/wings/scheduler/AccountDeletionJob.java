/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static software.wings.utils.TimeUtils.isWeekend;

import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.ResponseTimeRecorder;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class AccountDeletionJob implements Runnable {
  private final PersistentLocker persistentLocker;

  private final AccountService accountService;

  private final MainConfiguration mainConfiguration;

  private ExecutorService executorService;

  private static final String LOCK_NAME = "AccountDeletionJobLock";

  @Inject
  public AccountDeletionJob(
      AccountService accountService, MainConfiguration mainConfiguration, PersistentLocker persistentLocker) {
    this.accountService = accountService;
    this.mainConfiguration = mainConfiguration;
    this.persistentLocker = persistentLocker;
    executorService = Executors.newFixedThreadPool(mainConfiguration.getMaxAccountsToDeleteInParallel(),
        new ThreadFactoryBuilder().setNameFormat("AccountDeletionJob").build());
  }

  public void run() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(45))) {
      if (lock == null) {
        log.info("Couldn't acquire lock");
        return;
      }
      execute();
    }
  }

  void execute() {
    if (!isWeekend()) {
      return;
    } else {
      try (ResponseTimeRecorder ignore1 =
               new ResponseTimeRecorder("Deleting accounts that have been MARKED-FOR-DELETION")) {
        try {
          int numberOfAccountsToFetch = mainConfiguration.getMaxAccountsToDeleteInParallel();
          List<Account> accounts = accountService.listAccountsMarkedForDeletion(numberOfAccountsToFetch);
          triggerDeletion(accounts);
        } catch (Exception ex) {
          log.error("Error occurred while Deleting accounts marked for deletion: ", ex);
        }
      }
    }
  }

  void triggerDeletion(List<Account> accounts) {
    try {
      List<Callable<Void>> deletionTasks = new ArrayList<>();

      for (Account account : accounts) {
        deletionTasks.add(() -> {
          handle(account);
          return null;
        });
      }

      executorService.invokeAll(deletionTasks);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Account Deletion tasks were interrupted", e);
    }
  }

  void handle(Account account) {
    try {
      accountService.delete(account.getUuid());
    } catch (Exception ex) {
      log.error("Error Occurred while deletion account {} {}", account.getUuid(), ex);
    }
  }
}
