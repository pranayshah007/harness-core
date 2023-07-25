/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.aggregator;

import io.harness.accesscontrol.commons.metrics.AccessControlMetricsContext;
import io.harness.aggregator.AggregatorMetricsService;
import io.harness.aggregator.SnapshotMetrics;
import io.harness.aggregator.StreamingMetrics;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class AggregatorStackDriverMetricsPublisherImpl implements MetricsPublisher {
  private final AggregatorMetricsService aggregatorMetricsService;
  private final MetricService metricService;
  private final String namespace = System.getenv("NAMESPACE");
  private final String containerName = System.getenv("CONTAINER_NAME");
  private final String serviceName = "access-control";

  @Override
  public void recordMetrics() {
    Optional<StreamingMetrics> streamingMetricsOptional = aggregatorMetricsService.getStreamingMetrics();
    Optional<SnapshotMetrics> snapshotMetricsOptional = aggregatorMetricsService.getSnapshotMetrics();

    if (streamingMetricsOptional.isPresent()) {
      try (AccessControlMetricsContext ignored =
               new AccessControlMetricsContext(namespace, containerName, serviceName)) {
        StreamingMetrics streamingMetrics = streamingMetricsOptional.get();
        metricService.recordMetric(
            "aggregator_streaming_numberOfDisconnects", streamingMetrics.getNumberOfDisconnects());
        metricService.recordMetric(
            "aggregator_streaming_numberOfPrimaryElections", streamingMetrics.getNumberOfPrimaryElections());
        metricService.recordMetric("aggregator_streaming_millisBehindSource", streamingMetrics.getMillisBehindSource());
        metricService.recordMetric("aggregator_streaming_connected", streamingMetrics.isConnected() ? 1 : 0);
        metricService.recordMetric(
            "aggregator_streaming_millisSinceLastEvent", streamingMetrics.getMillisSinceLastEvent());
        metricService.recordMetric("aggregator_streaming_queueTotalCapacity", streamingMetrics.getQueueTotalCapacity());
        metricService.recordMetric(
            "aggregator_streaming_queueRemainingCapacity", streamingMetrics.getQueueRemainingCapacity());
        log.info("Streaming=> Last event seen: {}", streamingMetrics.getLastEvent());
        metricService.recordMetric(
            "aggregator_streaming_totalNumberOfEventsSeen", streamingMetrics.getTotalNumberOfEventsSeen());
        metricService.recordMetric(
            "aggregator_streaming_currentQueueSizeInBytes", streamingMetrics.getCurrentQueueSizeInBytes());
      }
    }

    if (snapshotMetricsOptional.isPresent()) {
      try (AccessControlMetricsContext ignored =
               new AccessControlMetricsContext(namespace, containerName, serviceName)) {
        SnapshotMetrics snapshotMetrics = snapshotMetricsOptional.get();
        metricService.recordMetric(
            "aggregator_snapshot_millisSinceLastEvent", snapshotMetrics.getMillisSinceLastEvent());
        metricService.recordMetric("aggregator_snapshot_queueTotalCapacity", snapshotMetrics.getQueueTotalCapacity());
        metricService.recordMetric(
            "aggregator_snapshot_queueRemainingCapacity", snapshotMetrics.getQueueRemainingCapacity());
        log.info("Snapshot=> Last event seen: {}", snapshotMetrics.getLastEvent());
        metricService.recordMetric(
            "aggregator_snapshot_totalNumberOfEventsSeen", snapshotMetrics.getTotalNumberOfEventsSeen());
        metricService.recordMetric(
            "aggregator_snapshot_currentQueueSizeInBytes", snapshotMetrics.getCurrentQueueSizeInBytes());
        metricService.recordMetric("aggregator_snapshot_snapshotRunning", snapshotMetrics.isSnapshotRunning() ? 1 : 0);
        metricService.recordMetric(
            "aggregator_snapshot_snapshotCompleted", snapshotMetrics.isSnapshotCompleted() ? 1 : 0);
        metricService.recordMetric(
            "aggregator_snapshot_snapshotDurationInSeconds", snapshotMetrics.getSnapshotDurationInSeconds());
      }
    }
  }
}
