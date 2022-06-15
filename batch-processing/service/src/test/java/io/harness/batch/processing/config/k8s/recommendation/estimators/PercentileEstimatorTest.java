/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.bytesFromMemoryAmount;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.coresFromCpuAmount;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.cpu;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.memory;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogram;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newMemoryHistogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.k8s.recommendation.ContainerState;
import io.harness.category.element.UnitTests;
import io.harness.histogram.Histogram;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PercentileEstimatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testPercentileEstimator() throws Exception {
    Instant anyTime = Instant.EPOCH;
    Histogram cpuHistogram = newCpuHistogram();
    cpuHistogram.addSample(1.0, 1.0, anyTime);
    cpuHistogram.addSample(2.0, 1.0, anyTime);
    cpuHistogram.addSample(3.0, 1.0, anyTime);

    Histogram memoryHistogram = newMemoryHistogram();
    memoryHistogram.addSample(1e9, 1.0, anyTime);
    memoryHistogram.addSample(2e9, 1.0, anyTime);
    memoryHistogram.addSample(3e9, 1.0, anyTime);

    ResourceEstimator estimator = PercentileEstimator.of(0.2, 0.5);
    ContainerState cs = new ContainerState();
    cs.setCpuHistogram(cpuHistogram);
    cs.setMemoryHistogram(memoryHistogram);

    Map<String, Long> resourceEstimation = estimator.getResourceEstimation(cs);

    // 5% relative error to account for rounding & fp operations
    double maxRelativeErrorPct = 5;
    assertThat(coresFromCpuAmount(cpu(resourceEstimation))).isCloseTo(1, withinPercentage(maxRelativeErrorPct));
    assertThat(bytesFromMemoryAmount(memory(resourceEstimation))).isCloseTo(2e9, withinPercentage(maxRelativeErrorPct));
  }
}
