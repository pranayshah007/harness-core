/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class AggregatorMetricsServiceImpl implements AggregatorMetricsService {
  private final String aggregatorMongodbName;

  @Inject
  public AggregatorMetricsServiceImpl(AggregatorConfiguration aggregatorConfiguration) {
    this.aggregatorMongodbName = aggregatorConfiguration.getDebeziumConfig().getMongodbName();
  }

  private static final String STREAMING_METRICS = "debezium.mongodb:type=ConnectorDisconnectHandler-metrics,context=streaming,server=%s";
  private static final String SNAPSHOT_METRICS = "debezium.mongodb:type=ConnectorDisconnectHandler-metrics,context=snapshot,server=%s";
  private static final String NUMBER_OF_DISCONNECTS = "NumberOfDisconnects";
  private static final String NUMBER_OF_PRIMARY_ELECTIONS = "NumberOfPrimaryElections";
  private static final String MILLISECONDS_BEHIND_SOURCE = "MilliSecondsBehindSource";
  private static final String CONNECTED_KEY = "Connected";
  private static final String MILLISECONDS_SINCE_LAST_EVENT = "MilliSecondsSinceLastEvent";
  private static final String QUEUE_TOTAL_CAPACITY = "QueueTotalCapacity";
  private static final String QUEUE_REMAINING_CAPACITY = "QueueRemainingCapacity";
  private static final String LAST_EVENT = "LastEvent";
  private static final String TOTAL_NUMBER_OF_EVENTS_SEEN = "TotalNumberOfEventsSeen";
  private static final String CURRENT_QUEUE_SIZE_BYTES = "CurrentQueueSizeInBytes";
  private static final String SNAPSHOT_RUNNING = "SnapshotRunning";
  private static final String SNAPSHOT_COMPLETED = "SnapshotCompleted";
  private static final String SNAPSHOT_DURATION_IN_SECONDS = "SnapshotDurationInSeconds";

  @SneakyThrows
  public Optional<SnapshotMetrics> getSnapshotMetrics() {
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    String snapshotMetrics = String.format(SNAPSHOT_METRICS, aggregatorMongodbName);
    ObjectName snapshotMetricsObjectName = new ObjectName(snapshotMetrics);

    if (!mBeanServer.isRegistered(snapshotMetricsObjectName)) {
      return Optional.empty();
    }

    long milliSinceLastEvent =
        (long) mBeanServer.getAttribute(snapshotMetricsObjectName, MILLISECONDS_SINCE_LAST_EVENT);
    int queueTotalCapacity = (int) mBeanServer.getAttribute(snapshotMetricsObjectName, QUEUE_TOTAL_CAPACITY);
    int queueRemainingCapacity = (int) mBeanServer.getAttribute(snapshotMetricsObjectName, QUEUE_REMAINING_CAPACITY);
    String lastEvent = (String) mBeanServer.getAttribute(snapshotMetricsObjectName, LAST_EVENT);
    long totalEventsSeen = (long) mBeanServer.getAttribute(snapshotMetricsObjectName, TOTAL_NUMBER_OF_EVENTS_SEEN);
    long currentQueueSizeBytes = (long) mBeanServer.getAttribute(snapshotMetricsObjectName, CURRENT_QUEUE_SIZE_BYTES);
    boolean snapshotRunning = (boolean) mBeanServer.getAttribute(snapshotMetricsObjectName, SNAPSHOT_RUNNING);
    boolean snapshotCompleted = (boolean) mBeanServer.getAttribute(snapshotMetricsObjectName, SNAPSHOT_COMPLETED);
    long snapshotDurationSeconds =
        (long) mBeanServer.getAttribute(snapshotMetricsObjectName, SNAPSHOT_DURATION_IN_SECONDS);

    return Optional.of(SnapshotMetrics.builder()
                           .millisSinceLastEvent(milliSinceLastEvent)
                           .queueTotalCapacity(queueTotalCapacity)
                           .queueRemainingCapacity(queueRemainingCapacity)
                           .lastEvent(lastEvent)
                           .totalNumberOfEventsSeen(totalEventsSeen)
                           .currentQueueSizeInBytes(currentQueueSizeBytes)
                           .snapshotRunning(snapshotRunning)
                           .snapshotCompleted(snapshotCompleted)
                           .snapshotDurationInSeconds(snapshotDurationSeconds)
                           .build());
  }

  @SneakyThrows
  public Optional<StreamingMetrics> getStreamingMetrics() {
    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    String streamingMetrics = String.format(STREAMING_METRICS, aggregatorMongodbName);
    ObjectName streamingMetricsObjectName = new ObjectName(streamingMetrics);

    if (!mBeanServer.isRegistered(streamingMetricsObjectName)) {
      return Optional.empty();
    }

    long disconnects = (long) mBeanServer.getAttribute(streamingMetricsObjectName, NUMBER_OF_DISCONNECTS);
    long primaryElections = (long) mBeanServer.getAttribute(streamingMetricsObjectName, NUMBER_OF_PRIMARY_ELECTIONS);
    long millisBehindSource = (long) mBeanServer.getAttribute(streamingMetricsObjectName, MILLISECONDS_BEHIND_SOURCE);
    boolean connected = (boolean) mBeanServer.getAttribute(streamingMetricsObjectName, CONNECTED_KEY);
    long milliSinceLastEvent =
        (long) mBeanServer.getAttribute(streamingMetricsObjectName, MILLISECONDS_SINCE_LAST_EVENT);
    int queueTotalCapacity = (int) mBeanServer.getAttribute(streamingMetricsObjectName, QUEUE_TOTAL_CAPACITY);
    int queueRemainingCapacity = (int) mBeanServer.getAttribute(streamingMetricsObjectName, QUEUE_REMAINING_CAPACITY);
    String lastEvent = (String) mBeanServer.getAttribute(streamingMetricsObjectName, LAST_EVENT);
    long totalEventsSeen = (long) mBeanServer.getAttribute(streamingMetricsObjectName, TOTAL_NUMBER_OF_EVENTS_SEEN);
    long currentQueueSizeBytes = (long) mBeanServer.getAttribute(streamingMetricsObjectName, CURRENT_QUEUE_SIZE_BYTES);

    return Optional.of(StreamingMetrics.builder()
                           .numberOfDisconnects(disconnects)
                           .numberOfPrimaryElections(primaryElections)
                           .millisBehindSource(millisBehindSource)
                           .millisSinceLastEvent(milliSinceLastEvent)
                           .connected(connected)
                           .queueTotalCapacity(queueTotalCapacity)
                           .queueRemainingCapacity(queueRemainingCapacity)
                           .lastEvent(lastEvent)
                           .totalNumberOfEventsSeen(totalEventsSeen)
                           .currentQueueSizeInBytes(currentQueueSizeBytes)
                           .build());
  }
}
