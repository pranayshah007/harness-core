/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.event;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.telemetry.CITelemetryStatusRepositoryCustomImpl;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.CIAccountDataStatusRepository;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIBuildInfoRepositoryCustomImpl;
import io.harness.repositories.CIExecutionConfigRepository;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.repositories.ExecutionQueueLimitRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Slf4j
public class CIDataDeletionService {
  // 12 * 3600 * 1000 - 10 * 60 * 1000
  private static final long TWELVE_HOURS_MINUS_TEN_MINUTES = 42600000;
  private static final String LOCK_NAME = "CI_DATA_DELETION_LOCK";

  @Inject private CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject private CIBuildInfoRepositoryCustomImpl ciBuildInfoRepositoryCustom;
  @Inject private CIExecutionConfigRepository ciExecutionConfigRepository;
  @Inject private CITelemetryStatusRepositoryCustomImpl ciTelemetryStatusRepository;
  @Inject private ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Inject private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject private CILogServiceUtils ciLogServiceUtils;
  @Inject private TIServiceUtils tiServiceUtils;
  @Inject private CIAccountDataStatusRepository ciAccountDataStatusRepository;
  @Inject private PersistentLocker persistentLocker;
  @Inject @Named("ciDataDeletionExecutor") private ExecutorService executorService;

  public void deleteJob() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.info("Could not acquire lock");
        return;
      }

      List<CIAccountDataStatus> deletionList = ciAccountDataStatusRepository.findAllByDeleted(false);
      if (deletionList.isEmpty()) {
        log.info("No account data pending for deletion");
        return;
      }

      for (CIAccountDataStatus account : deletionList) {
        String accountId = account.getAccountId();
        if (account.lastSent == null
            || account.lastSent < System.currentTimeMillis() - TWELVE_HOURS_MINUS_TEN_MINUTES) {
          saveSentTimeStamp(account);

          executorService.submit(() -> {
            boolean entitiesDeleted = delete(accountId);
            if (entitiesDeleted) {
              account.setDeleted(true);
              ciAccountDataStatusRepository.save(account);
              log.info("Successfully deleted all the CI data for accountId " + accountId);
            }
          });
        } else {
          log.info(format("Deletion request for accountId %s was already sent at %d", accountId, account.lastSent));
        }
      }
    }
  }

  private void saveSentTimeStamp(CIAccountDataStatus account) {
    account.setLastSent(System.currentTimeMillis());
    ciAccountDataStatusRepository.save(account);
  }

  private boolean delete(String accountId) {
    log.info("Starting CI data deletion for accountId " + accountId);
    boolean deletedAll = true;

    deletedAll = deleteData(() -> ciTaskDetailsRepository.deleteAllByAccountId(accountId), "CITaskDetails", accountId)
        && deletedAll;
    deletedAll = deleteData(() -> ciBuildInfoRepositoryCustom.deleteAllByAccountId(accountId), "CIBuildInfo", accountId)
        && deletedAll;
    deletedAll =
        deleteData(
            () -> ciExecutionConfigRepository.deleteAllByAccountIdentifier(accountId), "CIExecutionConfig", accountId)
        && deletedAll;
    deletedAll =
        deleteData(() -> ciTelemetryStatusRepository.deleteAllByAccountId(accountId), "CITelemetryStatus", accountId)
        && deletedAll;
    deletedAll = deleteData(()
                                -> executionQueueLimitRepository.deleteAllByAccountIdentifier(accountId),
                     "ExecutionQueueLimit", accountId)
        && deletedAll;
    deletedAll = deleteData(()
                                -> ciAccountExecutionMetadataRepository.deleteAllByAccountId(accountId),
                     "CIAccountExecutionMetadata", accountId)
        && deletedAll;
    deletedAll =
        deleteData(() -> ciExecutionRepository.deleteAllByAccountId(accountId), "CIExecution", accountId) && deletedAll;
    deletedAll = deleteLogs(accountId) && deletedAll;
    deletedAll = deleteTIData(accountId) && deletedAll;

    return deletedAll;
  }

  private boolean deleteData(Runnable deleteAction, String dataType, String accountId) {
    try {
      deleteAction.run();
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting %s data for accountId %s", dataType, accountId), e);
      return false;
    }
    return true;
  }

  private boolean deleteTIData(String accountId) {
    try {
      tiServiceUtils.clean(accountId);
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting ti data for accountId %s", accountId), e);
      return false;
    }
    return true;
  }

  private boolean deleteLogs(String accountId) {
    String key = "accountId:" + accountId;
    boolean logsExist = ciLogServiceUtils.checkIfLogsExist(accountId, key);
    if (logsExist == false) {
      return true;
    }
    try {
      ciLogServiceUtils.deleteLogs(accountId, key);
    } catch (Exception e) {
      log.error(String.format("Exception occurred while deleting step logs for accountId %s", accountId), e);
    }
    return false;
  }
}
