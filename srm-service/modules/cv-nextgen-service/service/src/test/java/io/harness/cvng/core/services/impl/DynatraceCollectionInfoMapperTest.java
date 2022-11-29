/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DynatraceDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig.DynatraceMetricInfo;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DynatraceCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final String MOCKED_SERVICE_ENTITY_ID = "MOCKED_SERVICE_ENTITY_ID";
  private static final String MOCKED_SERVICE_ENTITY_NAME = "MOCKED_SERVICE_ENTITY_NAME";
  private static final String MOCKED_METRIC_NAME = "testMetricName";
  private static final String MOCKED_METRIC_IDENTIFIER = "testMetricIdentifier";
  private static final String MOCKED_METRIC_SELECTOR = "builtin:service.response.time";
  private static final String MOCKED_METRIC_PACK_DSL = "metric-pack-dsl";

  @Inject private DynatraceDataCollectionInfoMapper classUnderTest;
  private final BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl(MOCKED_METRIC_PACK_DSL).build();
    DynatraceMetricInfo metricInfo = DynatraceMetricInfo.builder()
                                         .metricName(MOCKED_METRIC_NAME)
                                         .identifier(MOCKED_METRIC_IDENTIFIER)
                                         .metricSelector(MOCKED_METRIC_SELECTOR)
                                         .metricType(TimeSeriesMetricType.INFRA)
                                         .build();

    @SuppressWarnings("unchecked")
    DynatraceCVConfig dynatraceCVConfig = builderFactory.dynatraceCVConfigBuilder()
                                              .metricInfos(Collections.singletonList(metricInfo))
                                              .dynatraceServiceId(MOCKED_SERVICE_ENTITY_ID)
                                              .dynatraceServiceName(MOCKED_SERVICE_ENTITY_NAME)
                                              .build();
    dynatraceCVConfig.setMetricPack(metricPack);
    DynatraceDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(dynatraceCVConfig, TaskType.DEPLOYMENT);

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getMetricPack()).isNotNull();
    assertThat(collectionInfoResult.getServiceId()).isEqualTo(MOCKED_SERVICE_ENTITY_ID);

    collectionInfoResult.getCustomMetrics().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getMetricSelector()).isEqualTo(MOCKED_METRIC_SELECTOR);
    });
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoWithTaskTypeFilter() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl(MOCKED_METRIC_PACK_DSL).build();
    DynatraceMetricInfo customMetricDefinition1 = DynatraceMetricInfo.builder()
                                                      .metricSelector(MOCKED_METRIC_SELECTOR)
                                                      .metricName(MOCKED_METRIC_NAME)
                                                      .identifier(MOCKED_METRIC_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.INFRA)
                                                      .liveMonitoring(LiveMonitoring.builder().enabled(true).build())
                                                      .build();
    DynatraceMetricInfo customMetricDefinition2 = DynatraceMetricInfo.builder()
                                                      .metricSelector(MOCKED_METRIC_SELECTOR)
                                                      .metricName(MOCKED_METRIC_NAME + "2")
                                                      .identifier(MOCKED_METRIC_IDENTIFIER + "2")
                                                      .metricType(TimeSeriesMetricType.INFRA)
                                                      .liveMonitoring(LiveMonitoring.builder().enabled(false).build())
                                                      .build();

    DynatraceCVConfig dynatraceCVConfig =
        builderFactory.dynatraceCVConfigBuilder()
            .metricInfos(Arrays.asList(customMetricDefinition1, customMetricDefinition2))
            .dynatraceServiceId(MOCKED_SERVICE_ENTITY_ID)
            .build();
    dynatraceCVConfig.setMetricPack(metricPack);

    DynatraceDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(dynatraceCVConfig, TaskType.LIVE_MONITORING);

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getCustomMetrics().size()).isEqualTo(1);
    collectionInfoResult.getCustomMetrics().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getMetricSelector()).isEqualTo(MOCKED_METRIC_SELECTOR);
    });
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLI() {
    DynatraceDataCollectionInfo collectionInfoResult = getSLIDynatraceCollectionResult(MOCKED_METRIC_IDENTIFIER);
    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getCustomMetrics().size()).isEqualTo(1);
    collectionInfoResult.getCustomMetrics().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getMetricSelector()).isEqualTo(MOCKED_METRIC_SELECTOR);
    });
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLIWithDifferentMetricName() {
    DynatraceDataCollectionInfo collectionInfoResult = getSLIDynatraceCollectionResult("diff_metric_name");

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getCustomMetrics().size()).isEqualTo(0);
  }

  private DynatraceDataCollectionInfo getSLIDynatraceCollectionResult(String metricIdentifier) {
    MetricPack metricPack = MetricPack.builder()
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .dataCollectionDsl(MOCKED_METRIC_PACK_DSL)
                                .build();
    DynatraceMetricInfo metricInfo = DynatraceMetricInfo.builder()
                                         .metricName(MOCKED_METRIC_NAME)
                                         .identifier(MOCKED_METRIC_IDENTIFIER)
                                         .metricSelector(MOCKED_METRIC_SELECTOR)
                                         .metricType(TimeSeriesMetricType.INFRA)
                                         .build();
    ServiceLevelIndicator serviceLevelIndicator =
        ThresholdServiceLevelIndicator.builder().metric1(metricIdentifier).build();

    DynatraceCVConfig dynatraceCVConfig = builderFactory.dynatraceCVConfigBuilder()
                                              .metricInfos(Collections.singletonList(metricInfo))
                                              .dynatraceServiceId(MOCKED_SERVICE_ENTITY_NAME)
                                              .dynatraceServiceName(MOCKED_SERVICE_ENTITY_NAME)
                                              .build();
    dynatraceCVConfig.setMetricPack(metricPack);
    return classUnderTest.toDataCollectionInfo(Collections.singletonList(dynatraceCVConfig), serviceLevelIndicator);
  }
}
