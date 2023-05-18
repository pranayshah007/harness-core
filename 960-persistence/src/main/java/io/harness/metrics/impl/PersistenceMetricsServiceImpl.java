/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.beans.IteratorMetricContext;
import io.harness.metrics.beans.RedisTopicContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class PersistenceMetricsServiceImpl {
  public static final String ITERATOR_ERROR = "iterator_error";
  public static final String ITERATOR_DELAY = "iterator_delay";
  public static final String ITERATOR_WORKING_ON_ENTITY = "iterator_working_on_entity";
  public static final String ITERATOR_PROCESSING_TIME = "iterator_processing_time";
  public static final String ITERATOR_REDIS_LOCK_ACQUIRE_FAIL = "iterator_redis_lock_acq_fail";
  public static final String REDIS_SUBSCRIPTION_CNT = "redis_subscription_count";

  private final MetricService metricService;

  public void recordIteratorMetrics(String iteratorName, String metricName) {
    try (IteratorMetricContext ignore = new IteratorMetricContext(iteratorName)) {
      metricService.incCounter(metricName);
    }
  }
  public void recordIteratorMetricsWithDuration(String iteratorName, Duration duration, String metricName) {
    try (IteratorMetricContext ignore = new IteratorMetricContext(iteratorName)) {
      metricService.recordDuration(metricName, duration);
    }
  }

  public void recordRedisMetric(final String metric, final String topicName, final double value) {
    try (RedisTopicContext ignore = new RedisTopicContext(topicName)) {
      metricService.recordMetric(metric, value);
    }
  }
}
