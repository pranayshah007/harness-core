/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.cpuAmountFromCores;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.makeResourceMap;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.memoryAmountFromBytes;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConstEstimatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testConstEstimator() throws Exception {
    Map<String, Long> estimation = makeResourceMap(cpuAmountFromCores(1.234), memoryAmountFromBytes(9876.5));
    ResourceEstimator estimator = ConstEstimator.of(estimation);
    assertThat(estimator.getResourceEstimation(null)).isEqualTo(estimation);
  }
}
