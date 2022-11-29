/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import static io.harness.cvng.metrics.CVNGMetricsUtils.METRIC_LABEL_PREFIX;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Map;
import org.apache.logging.log4j.ThreadContext;
import org.assertj.core.data.MapEntry;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGMetricAnalysisContextTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAddLabelsAndClose() throws Exception {
    CVNGMetricAnalysisContext analysisContext = new CVNGMetricAnalysisContext("myAccount", "myTask");
    Map<String, String> context = ThreadContext.getContext();

    // the 2 items should be added to context now.
    assertThat(context).containsExactly(MapEntry.entry(METRIC_LABEL_PREFIX + "verificationTaskId", "myTask"),
        MapEntry.entry(METRIC_LABEL_PREFIX + "accountId", "myAccount"));
    analysisContext.close();
    Map<String, String> closedContext = ThreadContext.getContext();

    assertThat(closedContext).isEmpty();
  }
}
