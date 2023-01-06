/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.cvng.beans.DynatraceDataCollectionInfo.ENTITY_ID_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.GROUP_NAME_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.METRICS_TO_VALIDATE_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.METRIC_NAME_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.QUERY_SELECTOR_PARAM;
import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DynatraceDataCollectionInfoTest extends CategoryTest {
  private static final String SERVICE_ID = "SERVICE_ID";
  private static final String GROUP_NAME = "GROUP_NAME";
  private static final String METRIC_NAME = "MOCK_METRIC_NAME";
  private static final String MOCK_PATH = "MOCK_PATH";

  private DynatraceDataCollectionInfo classUnderTest;

  @Before
  public void setup() throws IOException {
    classUnderTest = DynatraceDataCollectionInfo.builder()
                         .serviceMethodIds(Arrays.asList("SERVICE_METHOD_1", "SERVICE_METHOD_2"))
                         .serviceId(SERVICE_ID)
                         .groupName(GROUP_NAME)
                         .build();
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetEnvVariablesFromMetricPack() {
    MetricPackDTO metricPackDTO =
        MetricPackDTO.builder()
            .metrics(Collections.singleton(
                MetricPackDTO.MetricDefinitionDTO.builder().name(METRIC_NAME).path(MOCK_PATH).build()))
            .build();
    classUnderTest.setMetricPack(metricPackDTO);
    Map<String, Object> metricPackMetricsEnvVariables =
        classUnderTest.getDslEnvVariables(DynatraceConnectorDTO.builder().build());

    String serviceMethodsIdsParam = "\"SERVICE_METHOD_1\",\"SERVICE_METHOD_2\"";
    assertThat(metricPackMetricsEnvVariables.get(ENTITY_ID_PARAM))
        .isEqualTo("type(\"dt.entity.service_method\"),entityId(".concat(serviceMethodsIdsParam).concat(")"));
    assertThat(metricPackMetricsEnvVariables.get(GROUP_NAME_PARAM)).isEqualTo(GROUP_NAME);
    List<Map<String, String>> metricsToValidate =
        (List<Map<String, String>>) metricPackMetricsEnvVariables.get(METRICS_TO_VALIDATE_PARAM);

    assertThat(metricsToValidate.size()).isEqualTo(1);
    assertThat(metricsToValidate.get(0).get(METRIC_NAME_PARAM)).isEqualTo(METRIC_NAME);
    assertThat(metricsToValidate.get(0).get(QUERY_SELECTOR_PARAM)).isEqualTo(MOCK_PATH);
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetEnvVariablesFromCustomMetrics() {
    List<DynatraceDataCollectionInfo.MetricCollectionInfo> customMetrics =
        Arrays.asList(DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
                          .identifier("metric_1")
                          .metricName("Metric 1")
                          .metricSelector("mock_metric_selector_1")
                          .build(),
            DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
                .identifier("metric_2")
                .metricName("Metric 2")
                .metricSelector("mock_metric_selector_2")
                .build());

    MetricPackDTO customPackDTO =
        MetricPackDTO.builder()
            .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
            .metrics(Collections.singleton(
                MetricPackDTO.MetricDefinitionDTO.builder().name(METRIC_NAME).path(MOCK_PATH).build()))
            .build();
    classUnderTest.setCustomMetrics(customMetrics);
    classUnderTest.setMetricPack(customPackDTO);
    Map<String, Object> metricPackMetricsEnvVariables =
        classUnderTest.getDslEnvVariables(DynatraceConnectorDTO.builder().build());

    assertThat(metricPackMetricsEnvVariables.get(ENTITY_ID_PARAM))
        .isEqualTo("type(\"dt.entity.service\"),entityId(\"".concat(SERVICE_ID).concat("\")"));
    assertThat(metricPackMetricsEnvVariables.get(GROUP_NAME_PARAM)).isEqualTo(GROUP_NAME);
    List<Map<String, String>> metricsToValidate =
        (List<Map<String, String>>) metricPackMetricsEnvVariables.get(METRICS_TO_VALIDATE_PARAM);

    assertThat(metricsToValidate.size()).isEqualTo(2);
    assertThat(metricsToValidate.get(0).get(METRIC_NAME_PARAM)).isEqualTo("Metric 1");
    assertThat(metricsToValidate.get(0).get(QUERY_SELECTOR_PARAM)).isEqualTo("mock_metric_selector_1");
    assertThat(metricsToValidate.get(1).get(METRIC_NAME_PARAM)).isEqualTo("Metric 2");
    assertThat(metricsToValidate.get(1).get(QUERY_SELECTOR_PARAM)).isEqualTo("mock_metric_selector_2");
  }
}
