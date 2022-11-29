/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition.PrometheusFilter;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusCVConfigTest extends CategoryTest {
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetQuery() {
    MetricInfo metricInfo =
        MetricInfo.builder()
            .metricName("myMetric")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .envFilter(Arrays.asList(PrometheusFilter.builder().labelName("namespace").labelValue("cv-demo").build()))
            .serviceFilter(Arrays.asList(PrometheusFilter.builder().labelName("app").labelValue("cv-demo-app").build()))
            .build();

    String query = metricInfo.getQuery();
    assertThat(query).isEqualTo("cpu_usage_total{app=\"cv-demo-app\",namespace=\"cv-demo\"}");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetQuery_withAdditionalFilters() {
    MetricInfo metricInfo =
        MetricInfo.builder()
            .metricName("myMetric")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .envFilter(Arrays.asList(PrometheusFilter.builder().labelName("namespace").labelValue("cv-demo").build()))
            .serviceFilter(Arrays.asList(PrometheusFilter.builder().labelName("app").labelValue("cv-demo-app").build()))
            .additionalFilters(Arrays.asList(PrometheusFilter.builder().labelName("filter2").labelValue("cv-2").build(),
                PrometheusFilter.builder().labelName("filter3").labelValue("cv-3").build()))
            .build();

    String query = metricInfo.getQuery();
    assertThat(query).isEqualTo(
        "cpu_usage_total{app=\"cv-demo-app\",namespace=\"cv-demo\",filter2=\"cv-2\",filter3=\"cv-3\"}");
  }
}
