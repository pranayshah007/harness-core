/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.support.reconciliation;

import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentService;
import io.harness.accesscontrol.support.SupportService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class SupportRoleAssignmentsReconciliationJob implements Runnable {
  private final PersistentLocker persistentLocker;
  private final SupportService supportService;
  private final PrivilegedRoleAssignmentService privilegedRoleAssignmentService;
  private static final String SUPER_VIEWER_ROLE_IDENTIFIER = "_super_account_viewer";
  private static final String JOB_LOCK_NAME = "SUPPORT_ROLE_ASSIGNMENTS_RECONCILIATION_LOCK";

  @Inject
  public SupportRoleAssignmentsReconciliationJob(PersistentLocker persistentLocker, SupportService supportService,
      PrivilegedRoleAssignmentService privilegedRoleAssignmentService) {
    this.persistentLocker = persistentLocker;
    this.supportService = supportService;
    this.privilegedRoleAssignmentService = privilegedRoleAssignmentService;
  }

  @Override
  public void run() {
    try (AcquiredLock<?> acquiredLock = acquireLock()) {
      if (acquiredLock == null) {
        return;
      }
      RLock lockObject = (RLock) acquiredLock.getLock();
      while (lockObject.isHeldByCurrentThread()) {
        Set<String> users = supportService.fetchSupportUsers();
        Set<Principal> principals =
            users.stream()
                .map(user -> Principal.builder().principalType(PrincipalType.USER).principalIdentifier(user).build())
                .collect(Collectors.toSet());
        privilegedRoleAssignmentService.syncRoleAssignments(principals, SUPER_VIEWER_ROLE_IDENTIFIER);
        TimeUnit.SECONDS.sleep(300);
      }
    } catch (InterruptedException e) {
      log.error("Interrupted while syncing support role assignments", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Exception while syncing support role assignments", e);
    }
  }

  private AcquiredLock<?> acquireLock() {
    AcquiredLock<?> lock = null;
    do {
      try {
        TimeUnit.SECONDS.sleep(150);
        lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(JOB_LOCK_NAME, Duration.ofSeconds(5));
      } catch (InterruptedException e) {
        log.info("Interrupted while trying to get {}. Exiting", JOB_LOCK_NAME);
        Thread.currentThread().interrupt();
        return null;
      } catch (Exception ex) {
        log.info("Unable to get {}, going to sleep for 150 seconds.", JOB_LOCK_NAME);
      }
    } while (lock == null);
    return lock;
  }
}
