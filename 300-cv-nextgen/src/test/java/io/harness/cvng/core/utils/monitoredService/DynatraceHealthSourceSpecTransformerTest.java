/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DynatraceHealthSourceSpec;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DynatraceHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  private static final int METRIC_DEFINITIONS_COUNT = 1;
  private static final int METRIC_PACK_COUNT = 1;
  private static final String MOCKED_METRIC_NAME = "MOCKED_METRIC_NAME";
  private static final String MOCKED_QUERY_SELECTOR = "builtin:service.response.time";
  private static final String CONNECTOR_IDENTIFIER = "connectorId";
  private static final String METRIC_PACK_IDENTIFIER = "mock_metric_pack_identifier";

  private BuilderFactory builderFactory;
  @Inject DynatraceHealthSourceSpecTransformer classUnderTest;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    DynatraceHealthSourceSpec dynatraceHealthSourceSpec = classUnderTest.transform(createCVConfigs());

    assertThat(dynatraceHealthSourceSpec).isNotNull();
    assertThat(dynatraceHealthSourceSpec.getConnectorRef()).isEqualTo(CONNECTOR_IDENTIFIER);

    assertThat(dynatraceHealthSourceSpec.getMetricPacks().size()).isEqualTo(METRIC_PACK_COUNT);
    TimeSeriesMetricPackDTO metricPack = dynatraceHealthSourceSpec.getMetricPacks().stream().findFirst().orElse(null);
    assertThat(metricPack).isNotNull();
    assertThat(metricPack.getIdentifier()).isEqualTo(METRIC_PACK_IDENTIFIER);

    assertThat(dynatraceHealthSourceSpec.getMetricDefinitions().size()).isEqualTo(METRIC_DEFINITIONS_COUNT);
    DynatraceHealthSourceSpec.DynatraceMetricDefinition dynatraceMetricDefinition =
        dynatraceHealthSourceSpec.getMetricDefinitions().stream().findFirst().orElse(null);
    assertThat(dynatraceMetricDefinition).isNotNull();
    assertThat(dynatraceMetricDefinition.getIdentifier()).isEqualTo(MOCKED_METRIC_NAME);
    assertThat(dynatraceMetricDefinition.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
    assertThat(dynatraceMetricDefinition.getMetricSelector()).isEqualTo(MOCKED_QUERY_SELECTOR);
    assertThat(dynatraceMetricDefinition.isManualQuery()).isEqualTo(true);
    assertThat(dynatraceMetricDefinition.getRiskProfile().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(dynatraceMetricDefinition.getRiskProfile().getMetricType()).isEqualTo(TimeSeriesMetricType.RESP_TIME);
  }

  private List<DynatraceCVConfig> createCVConfigs() {
    List<DynatraceCVConfig> cvConfigs = new ArrayList<>();
    DynatraceCVConfig cvConfigWithPerformancePack = builderFactory.dynatraceCVConfigBuilder().build();
    cvConfigWithPerformancePack.setConnectorIdentifier(CONNECTOR_IDENTIFIER);
    cvConfigWithPerformancePack.setMetricPack(
        MetricPack.builder().identifier(METRIC_PACK_IDENTIFIER).category(CVMonitoringCategory.PERFORMANCE).build());

    DynatraceCVConfig cvConfigWithCustomMetrics = builderFactory.dynatraceCVConfigBuilder().build();
    cvConfigWithCustomMetrics.setConnectorIdentifier(CONNECTOR_IDENTIFIER);
    cvConfigWithCustomMetrics.setMetricPack(MetricPack.builder().category(CVMonitoringCategory.ERRORS).build());
    cvConfigWithCustomMetrics.setMetricInfos(IntStream.range(0, METRIC_DEFINITIONS_COUNT)
                                                 .mapToObj(index
                                                     -> DynatraceCVConfig.DynatraceMetricInfo.builder()
                                                            .metricName(MOCKED_METRIC_NAME)
                                                            .identifier(MOCKED_METRIC_NAME)
                                                            .metricType(TimeSeriesMetricType.RESP_TIME)
                                                            .metricSelector(MOCKED_QUERY_SELECTOR)
                                                            .isManualQuery(true)
                                                            .build())
                                                 .collect(Collectors.toList()));
    cvConfigs.add(cvConfigWithPerformancePack);
    cvConfigs.add(cvConfigWithCustomMetrics);
    return cvConfigs;
  }
}
