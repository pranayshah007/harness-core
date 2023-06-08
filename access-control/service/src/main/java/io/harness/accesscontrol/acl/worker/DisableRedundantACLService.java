/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.worker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class DisableRedundantACLService implements Managed {
  private Future<?> disableRedundantACLJobFuture;
  private final ScheduledExecutorService executorService;
  private static final String DEBUG_MESSAGE = "DisableRedundantACLService: ";
  private final DisableRedundantACLJob disableRedundantACLJob;

  @Inject
  public DisableRedundantACLService(DisableRedundantACLJob disableRedundantACLJob) {
    this.disableRedundantACLJob = disableRedundantACLJob;
    String threadName = "disable-redundant-acl-service-thread";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    Random random = new Random();
    int delay = random.nextInt(15) + 15;
    disableRedundantACLJobFuture =
        executorService.scheduleWithFixedDelay(disableRedundantACLJob, delay, 2880, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    disableRedundantACLJobFuture.cancel(false);
    executorService.shutdown();
  }
}