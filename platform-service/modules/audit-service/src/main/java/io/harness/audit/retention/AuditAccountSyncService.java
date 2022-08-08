/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.retention;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class AuditAccountSyncService implements Managed {
  @Inject AuditAccountSyncJob auditAccountSyncJob;
  private Future<?> auditAccountSyncJobFuture;
  final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("audit-account-sync-thread").build());

  @Override
  public void start() {
    auditAccountSyncJobFuture = executorService.scheduleAtFixedRate(auditAccountSyncJob, 1, 6, TimeUnit.HOURS);
  }

  @Override
  public void stop() {
    auditAccountSyncJobFuture.cancel(true);
    executorService.shutdownNow();
  }
}
