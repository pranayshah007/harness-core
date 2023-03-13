/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.utils.ResourceAmountUtils;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MarginEstimatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testMarginEstimator() throws Exception {
    ResourceEstimator estimator = ConstEstimator.of(ResourceAmountUtils.makeResourceMap(1000, 200)).withMargin(0.10);
    Map<String, Long> resourceEstimation = estimator.getResourceEstimation(null);
    assertThat(resourceEstimation.get(CPU)).isEqualTo(1100);
    assertThat(resourceEstimation.get(MEMORY)).isEqualTo(220);
  }
}
