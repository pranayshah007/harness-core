/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.modules;

import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.impl.MetricServiceImpl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MetricsModule extends AbstractModule {
  private int exportIntervalMins;
  private MetricService metricService;

  public MetricsModule() {
    this.exportIntervalMins = 1;
    this.metricService = new MetricServiceImpl(this.exportIntervalMins);
  }

  public MetricsModule(int exportIntervalMins) {
    this.exportIntervalMins = exportIntervalMins;
    this.metricService = new MetricServiceImpl(this.exportIntervalMins);
  }

  public MetricsModule(int exportIntervalMins, MetricService metricService) {
    this.exportIntervalMins = exportIntervalMins;
    this.metricService = metricService;
  }

  @Override
  protected void configure() {
    bind(MetricService.class).toInstance(this.metricService);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("metricsPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("metrics-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
  }
}
