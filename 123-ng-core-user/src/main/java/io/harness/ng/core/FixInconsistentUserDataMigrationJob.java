/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class FixInconsistentUserDataMigrationJob implements Managed {
  private Future<?> defaultUserGroupJobFuture;
  private final ScheduledExecutorService executorService;

  public static final String DEBUG_MESSAGE = "FixInconsistentUserDataMigrationJob: ";
  private final FixInconsistentUserDataMigrationService migrationService;

  @Inject
  public FixInconsistentUserDataMigrationJob(FixInconsistentUserDataMigrationService migrationService) {
    this.migrationService = migrationService;
    String threadName = "fix-inconsistent-user-data-thread";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    defaultUserGroupJobFuture = executorService.scheduleWithFixedDelay(migrationService, 15, 240, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    defaultUserGroupJobFuture.cancel(false);
    executorService.shutdownNow();
  }
}
