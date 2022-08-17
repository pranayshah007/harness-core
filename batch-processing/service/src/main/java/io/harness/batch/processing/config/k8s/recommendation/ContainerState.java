/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogram;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newMemoryHistogram;

import io.harness.histogram.Histogram;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * ContainerState maintains the live structures for each container, such as cpu & mem histograms and the various
 * timestamps.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContainerState {
  Instant lastUpdateTime;
  Histogram cpuHistogram = newCpuHistogram();
  Histogram memoryHistogram = newMemoryHistogram();

  // Below are all based on cpu
  private Instant firstSampleStart;
  private Instant lastSampleStart;
  private int totalSamplesCount;

  long memoryPeak;
  Instant windowEnd;
  int version;

  static ContainerState fromCheckpoint(ContainerCheckpoint containerCheckpoint) {
    ContainerState containerState = new ContainerState();
    containerState.setLastUpdateTime(containerCheckpoint.getLastUpdateTime());
    containerState.cpuHistogram.loadFromCheckPoint(containerCheckpoint.getCpuHistogram());
    containerState.memoryHistogram.loadFromCheckPoint(containerCheckpoint.getMemoryHistogram());
    containerState.setFirstSampleStart(containerCheckpoint.getFirstSampleStart());
    containerState.setLastSampleStart(containerCheckpoint.getLastSampleStart());
    containerState.setTotalSamplesCount(containerCheckpoint.getTotalSamplesCount());
    containerState.setMemoryPeak(containerCheckpoint.getMemoryPeak());
    containerState.setWindowEnd(containerCheckpoint.getWindowEnd());
    containerState.setVersion(containerCheckpoint.getVersion());
    return containerState;
  }

  ContainerCheckpoint toContainerCheckpoint() {
    return ContainerCheckpoint.builder()
        .lastUpdateTime(lastUpdateTime)
        .cpuHistogram(cpuHistogram.saveToCheckpoint())
        .memoryHistogram(memoryHistogram.saveToCheckpoint())
        .firstSampleStart(firstSampleStart)
        .lastSampleStart(lastSampleStart)
        .totalSamplesCount(totalSamplesCount)
        .memoryPeak(memoryPeak)
        .windowEnd(windowEnd)
        .version(version)
        .build();
  }
}
