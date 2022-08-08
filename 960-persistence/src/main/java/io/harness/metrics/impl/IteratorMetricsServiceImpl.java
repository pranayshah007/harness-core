/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentIterable;
import io.harness.metrics.beans.IteratorMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class IteratorMetricsServiceImpl {
  public static final String ITERATOR_ERROR = "iterator_error";
  public static final String ITERATOR_DELAY = "iterator_delay";
  public static final String ITERATOR_WORKING_ON_ENTITY = "iterator_working_on_entity";
  public static final String ITERATOR_PROCESSING_TIME = "iterator_processing_time";

  @Inject private MetricService metricService;

  public <T extends PersistentIterable> void recordIteratorMetrics(String iteratorName, String metricName) {
    try (IteratorMetricContext ignore = new IteratorMetricContext(iteratorName)) {
      metricService.incCounter(metricName);
    }
  }
  public <T extends PersistentIterable> void recordIteratorMetricsWithDuration(
      String iteratorName, Duration duration, String metricName) {
    try (IteratorMetricContext ignore = new IteratorMetricContext(iteratorName)) {
      metricService.recordDuration(metricName, duration);
    }
  }
}
