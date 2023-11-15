/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.scheduledJobs;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AccountActivityMetricsPublisherService implements Managed {
  @Inject AccountActivityMetricsPublisherJob accountActivityMetricsPublisherJob;

  final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("account-activity-metrics-publisher-thread").build());

  @Override
  public void start() {
    executorService.scheduleWithFixedDelay(accountActivityMetricsPublisherJob, 0L, 12, TimeUnit.HOURS);
  }

  @Override
  public void stop() {
    executorService.shutdown();
  }
}
