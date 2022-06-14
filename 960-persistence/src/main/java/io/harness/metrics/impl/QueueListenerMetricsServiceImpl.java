/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.beans.QueueListenerMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class QueueListenerMetricsServiceImpl {
  public static final String LISTENER_ERROR = "queue_listener_error";
  public static final String LISTENER_DELAY = "queue_listener_delay";
  public static final String LISTENER_WORKING_ON_MESSAGE = "queue_listener_working_on_message";
  public static final String LISTENER_PROCESSING_TIME = "queue_listener_processing_time";

  @Inject private MetricService metricService;

  public void recordQueueListenerMetrics(String queueConsumerName, String metricName) {
    try (QueueListenerMetricContext ignore = new QueueListenerMetricContext(queueConsumerName)) {
      metricService.incCounter(metricName);
    }
  }
  public void recordQueueListenerMetricsWithDuration(String queueConsumerName, Duration duration, String metricName) {
    try (QueueListenerMetricContext ignore = new QueueListenerMetricContext(queueConsumerName)) {
      metricService.recordDuration(metricName, duration);
    }
  }
}
