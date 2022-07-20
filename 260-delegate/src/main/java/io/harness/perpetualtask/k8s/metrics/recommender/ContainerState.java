/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.metrics.recommender;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.MIN_SAMPLE_WEIGHT;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogram;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogramV2;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.histogram.Histogram;

import java.time.Instant;
import lombok.Data;

@Data
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class ContainerState {
  private final String namespace;
  private final String workloadKind;
  private final String workloadName;
  private final String containerName;

  /*
  Only the peak memory for the 24h period should be added to the histogram. For cpu, we should add all samples.
   */
  private long memoryPeak;
  private Instant memoryPeakTime;
  private final Histogram cpuHistogram;
  private final Histogram cpuHistogramV2;
  private Instant firstSampleStart;
  private Instant lastSampleStart;
  private int totalSamplesCount;

  public ContainerState(String namespace, String workloadKind, String workloadName, String containerName) {
    this.namespace = namespace;
    this.workloadKind = workloadKind;
    this.workloadName = workloadName;
    this.containerName = containerName;
    this.cpuHistogram = newCpuHistogram();
    this.cpuHistogramV2 = newCpuHistogramV2();
  }

  public void addCpuSample(double containerCpuCores, String timestamp) {
    Instant now = Instant.parse(timestamp);
    if (lastSampleStart != null && !now.isAfter(lastSampleStart)) {
      // skip duplicate or out-of-order
      return;
    }
    cpuHistogram.addSample(containerCpuCores, MIN_SAMPLE_WEIGHT, now);
    cpuHistogramV2.addSample(containerCpuCores, MIN_SAMPLE_WEIGHT, now);
    if (firstSampleStart == null) {
      firstSampleStart = now;
    }
    lastSampleStart = now;
    totalSamplesCount++;
  }

  public void addMemorySample(long containerMemoryBytes, String timestamp) {
    if (containerMemoryBytes >= memoryPeak) {
      memoryPeak = containerMemoryBytes;
      memoryPeakTime = Instant.parse(timestamp);
    }
  }
}
