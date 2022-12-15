/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.ce.recommendation.entity;

import io.harness.ccm.RecommenderUtils;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.histogram.Histogram;
import io.harness.histogram.HistogramCheckpoint;

import java.time.Instant;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PartialHistogramAggragator {
  public void aggregateInto(final Iterable<PartialRecommendationHistogram> partialRecommendationHistograms,
      final Map<String, Histogram> cpuHistograms, final Map<String, Histogram> memoryHistograms) {
    for (PartialRecommendationHistogram partialRecommendationHistogram : partialRecommendationHistograms) {
      Map<String, ContainerCheckpoint> containerCheckpoints = partialRecommendationHistogram.getContainerCheckpoints();
      for (Map.Entry<String, ContainerCheckpoint> stringContainerCheckpointEntry : containerCheckpoints.entrySet()) {
        String containerName = stringContainerCheckpointEntry.getKey();
        ContainerCheckpoint containerCheckpoint = stringContainerCheckpointEntry.getValue();

        // merge the day's cpu histogram into the aggregate cpu histogram
        HistogramCheckpoint cpuHistogramPartialCheckpoint = containerCheckpoint.getCpuHistogram();
        if (cpuHistogramPartialCheckpoint.getBucketWeights() != null) {
          Histogram cpuHistogramPartial =
              io.harness.ccm.RecommenderUtils.loadFromCheckpointV2(cpuHistogramPartialCheckpoint);
          Histogram cpuHistogram =
              cpuHistograms.getOrDefault(containerName, io.harness.ccm.RecommenderUtils.newCpuHistogramV2());
          cpuHistogram.merge(cpuHistogramPartial);
          cpuHistograms.put(containerName, cpuHistogram);
        }

        // add the day's memory peak into the aggregate memory histogram
        long memoryPeak = containerCheckpoint.getMemoryPeak();
        if (memoryPeak != 0) {
          Histogram memoryHistogram =
              memoryHistograms.getOrDefault(containerName, RecommenderUtils.newMemoryHistogramV2());
          memoryHistogram.addSample(memoryPeak, 1.0, Instant.EPOCH); // timestamp is irrelevant since no decay.
          memoryHistograms.put(containerName, memoryHistogram);
        }
      }
    }
  }
}
