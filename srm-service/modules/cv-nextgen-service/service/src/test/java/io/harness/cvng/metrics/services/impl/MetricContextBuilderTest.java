/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.metrics.AutoMetricContext;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MetricContextBuilderTest extends CvNextGenTestBase {
  @Inject private MetricContextBuilder metricContextBuilder;
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetContext() {
    String accountId = generateUuid();
    CVNGStepTask cvngStepTask = CVNGStepTask.builder()
                                    .accountId(accountId)
                                    .activityId(generateUuid())
                                    .status(CVNGStepTask.Status.IN_PROGRESS)
                                    .build();
    try (AutoMetricContext autoMetricContext = metricContextBuilder.getContext(cvngStepTask, CVNGStepTask.class)) {
      assertThat(autoMetricContext).isNotNull();
    }
  }
}
