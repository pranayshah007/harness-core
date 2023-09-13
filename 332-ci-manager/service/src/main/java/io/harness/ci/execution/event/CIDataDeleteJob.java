/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CIDataDeleteJob {
  public static final int TWELVE_HOURS = 60 * 60 * 12;

  @Inject @Named("ciDataDeleteScheduler") protected ScheduledExecutorService executorService;
  @Inject CIDataDeletionService ciDataDeletionService;

  public void scheduleTasks() {
    long initialDelay = 10;

    try {
      log.info("CIDataDeleteJob scheduler starting");
      executorService.scheduleAtFixedRate(
          () -> ciDataDeletionService.deleteJob(), initialDelay, TWELVE_HOURS, TimeUnit.SECONDS);
      log.info("CIDataDeleteJob scheduler started");
    } catch (Exception e) {
      log.error("Exception while creating the scheduled job to track CI data deletion", e);
    }
  }
}
