/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import io.prometheus.client.CollectorRegistry;

/**
 * Created by Pranjal on 11/07/2018
 */
public class MetricRegistryModule extends AbstractModule {
  private HarnessMetricRegistry harnessMetricRegistry;
  private MetricRegistry metricRegistry;

  private CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry;

  public MetricRegistryModule(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
    this.harnessMetricRegistry = new HarnessMetricRegistry(metricRegistry, collectorRegistry);
  }

  @Override
  protected void configure() {
    bind(MetricRegistry.class).toInstance(metricRegistry);
    bind(HarnessMetricRegistry.class).toInstance(harnessMetricRegistry);
  }
}
