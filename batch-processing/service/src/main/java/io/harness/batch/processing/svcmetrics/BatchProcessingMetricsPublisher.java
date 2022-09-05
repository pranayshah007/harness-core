/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.svcmetrics;

import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

// This class is a singleton. @see BatchProcessingModule.configure().
@Slf4j
public class BatchProcessingMetricsPublisher implements MetricsPublisher {
  @Inject private MetricService metricService;

  @Override
  public void recordMetrics() {
    // Collect metrics snapshots e.g. queueSize, runningJobCount etc.
  }
}
