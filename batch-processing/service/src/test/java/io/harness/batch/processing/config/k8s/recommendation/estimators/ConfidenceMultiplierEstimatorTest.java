/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.MAX_RESOURCE_AMOUNT;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.coresFromCpuAmount;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.cpu;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.cpuAmountFromCores;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.makeResourceMap;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.memoryAmountFromBytes;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.k8s.recommendation.ContainerState;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConfidenceMultiplierEstimatorTest extends CategoryTest {
  private static final double FP_ERROR_PCT = 0.0001; // floating point error pct.

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testConfidenceMultiplier() throws Exception {
    ResourceEstimator baseEstimator =
        ConstEstimator.of(makeResourceMap(cpuAmountFromCores(3.14), memoryAmountFromBytes(3.14e9)));
    ResourceEstimator testedEstimator = baseEstimator.withConfidenceMultiplier(0.1, 2.0);
    ContainerState cs = new ContainerState();
    // Add 9 CPU samples at the frequency of 1/(2 mins)
    Instant timestamp = Instant.ofEpochMilli(0);
    cs.setFirstSampleStart(timestamp);
    for (int i = 1; i <= 9; i++) {
      cs.getCpuHistogram().addSample(1.0, 1.0, timestamp);
      cs.setTotalSamplesCount(cs.getTotalSamplesCount() + 1);
      cs.setLastSampleStart(timestamp);
      timestamp = timestamp.plus(Duration.ofMinutes(2));
    }

    // expected confidence = 9/(60*24) = 0.00625
    assertThat(((ConfidenceMultiplierEstimator) testedEstimator).getConfidence(cs))
        .isCloseTo(0.00625, withinPercentage(FP_ERROR_PCT));
    Map<String, Long> resourceEstimation = testedEstimator.getResourceEstimation(cs);
    // expected cpu estimation = 3.14 * (1+0.1/0.00625)^2 = 907.46
    assertThat(coresFromCpuAmount(cpu(resourceEstimation))).isCloseTo(907.46, withinPercentage(FP_ERROR_PCT));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testConfidenceMultiplierNoHistory() throws Exception {
    ResourceEstimator baseEstimator =
        ConstEstimator.of(makeResourceMap(cpuAmountFromCores(3.14), memoryAmountFromBytes(3.14e9)));
    ResourceEstimator testedEstimator1 = baseEstimator.withConfidenceMultiplier(1.0, 1.0);
    ResourceEstimator testedEstimator2 = baseEstimator.withConfidenceMultiplier(1.0, -1.0);
    ContainerState cs = new ContainerState();
    cs.setFirstSampleStart(Instant.EPOCH);
    cs.setLastSampleStart(Instant.EPOCH);
    assertThat(testedEstimator1.getResourceEstimation(cs).get(CPU)).isEqualTo(MAX_RESOURCE_AMOUNT);
    assertThat(testedEstimator2.getResourceEstimation(cs).get(CPU)).isEqualTo(0);
  }
}
