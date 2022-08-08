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
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class MetricsModule extends AbstractModule {
  private int exportIntervalMins = 1;

  @Override
  protected void configure() {
    bind(MetricService.class).toInstance(new MetricServiceImpl(exportIntervalMins));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("metricsPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("metrics-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
  }
}
