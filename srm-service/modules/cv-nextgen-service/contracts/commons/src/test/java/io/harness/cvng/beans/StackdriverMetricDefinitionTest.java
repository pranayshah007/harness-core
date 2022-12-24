/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StackdriverMetricDefinitionTest extends CategoryTest {
  ClassLoader classLoader = getClass().getClassLoader();
  @Before
  public void setup() throws IOException {}

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExtractFromJson() throws Exception {
    String textLoad = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("metricDefinition.json")), StandardCharsets.UTF_8);

    StackDriverMetricDefinition metricDefinition = StackDriverMetricDefinition.extractFromJson(textLoad);

    assertThat(metricDefinition).isNotNull();
    assertThat(metricDefinition.getFilter())
        .isEqualTo("metric.type=\"kubernetes.io/container/restart_count\" resource.type=\"k8s_container\"");
    assertThat(metricDefinition.getAggregation()).isNotNull();
    assertThat(metricDefinition.getAggregation().getPerSeriesAligner()).isEqualTo("ALIGN_RATE");
    assertThat(metricDefinition.getAggregation().getCrossSeriesReducer()).isEqualTo("REDUCE_SUM");
    assertThat(metricDefinition.getAggregation().getAlignmentPeriod()).isEqualTo("60s");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExtractFromJson_manualFormat() throws Exception {
    String textLoad =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("stackdriver-metricDefinition-manual.json")),
            StandardCharsets.UTF_8);

    StackDriverMetricDefinition metricDefinition = StackDriverMetricDefinition.extractFromJson(textLoad);

    assertThat(metricDefinition).isNotNull();
    assertThat(metricDefinition.getFilter())
        .isEqualTo("metric.type=\"kubernetes.io/container/memory/used_bytes\" resource.type=\"k8s_container\"");
    assertThat(metricDefinition.getAggregation()).isNotNull();
    assertThat(metricDefinition.getAggregation().getPerSeriesAligner()).isEqualTo("ALIGN_MEAN");
    assertThat(metricDefinition.getAggregation().getCrossSeriesReducer()).isEqualTo("REDUCE_MEAN");
    assertThat(metricDefinition.getAggregation().getAlignmentPeriod()).isEqualTo("60s");
  }
}
