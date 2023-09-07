/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.beans.CVMonitoringCategory.PERFORMANCE;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig.MetricInfo;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogMetricDataCollectionInfoMapperTest extends CvNextGenTestBase {
  private static final String MOCKED_DASHBOARD_NAME = "MockedDashboardName";
  private static final String MOCKED_METRIC_NAME = "testMetricName";
  private static final String MOCKED_METRIC_IDENTIFIER = "testMetricIdentifier";
  private static final String MOCKED_METRIC_QUERY = "system.user.cpu{*}";

  @Inject private DatadogMetricDataCollectionInfoMapper classUnderTest;
  private final BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    DatadogMetricCVConfig.MetricInfo metricInfo = DatadogMetricCVConfig.MetricInfo.builder()
                                                      .query(MOCKED_METRIC_QUERY)
                                                      .metricName(MOCKED_METRIC_NAME)
                                                      .identifier(MOCKED_METRIC_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.INFRA)
                                                      .build();

    DatadogMetricCVConfig datadogMetricCVConfig = builderFactory.datadogMetricCVConfigBuilder()
                                                      .metricInfoList(Arrays.asList(metricInfo))
                                                      .dashboardName(MOCKED_DASHBOARD_NAME)
                                                      .build();
    datadogMetricCVConfig.setMetricPack(metricPack);

    DatadogMetricsDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(datadogMetricCVConfig, TaskType.DEPLOYMENT);

    assertThat(collectionInfoResult).isNotNull();
    collectionInfoResult.getMetricDefinitions().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getMetricIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getQuery()).isEqualTo(MOCKED_METRIC_QUERY);
    });
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testNextGenMetricCVConfigToDataCollectionInfo() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    NextGenMetricInfo metricInfo = NextGenMetricInfo.builder()
                                       .query(MOCKED_METRIC_QUERY)
                                       .queryParams(QueryParams.builder().serviceInstanceField("container").build())
                                       .metricName(MOCKED_METRIC_NAME)
                                       .identifier(MOCKED_METRIC_IDENTIFIER)
                                       .metricType(TimeSeriesMetricType.INFRA)
                                       .build();

    NextGenMetricCVConfig nextGenMetricCVConfig = NextGenMetricCVConfig.builder()
                                                      .groupName("onboarding_default_group")
                                                      .metricInfos(List.of(metricInfo))
                                                      .dataSourceType(DataSourceType.DATADOG_METRICS)
                                                      .connectorIdentifier("account.Datadog_metrics")
                                                      .monitoredServiceIdentifier("onboarding_monitored_service")
                                                      .category(PERFORMANCE)
                                                      .build();
    nextGenMetricCVConfig.setMetricPack(metricPack);

    DatadogMetricsDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(nextGenMetricCVConfig, TaskType.DEPLOYMENT);

    assertThat(collectionInfoResult).isNotNull();
    collectionInfoResult.getMetricDefinitions().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getMetricIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getQuery()).isEqualTo(MOCKED_METRIC_QUERY);
    });
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_taskTypeFilter() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    DatadogMetricCVConfig.MetricInfo metricInfo1 = MetricInfo.builder()
                                                       .query(MOCKED_METRIC_QUERY)
                                                       .metricName(MOCKED_METRIC_NAME)
                                                       .identifier(MOCKED_METRIC_IDENTIFIER)
                                                       .metricType(TimeSeriesMetricType.INFRA)
                                                       .liveMonitoring(LiveMonitoring.builder().enabled(true).build())
                                                       .build();
    DatadogMetricCVConfig.MetricInfo metricInfo2 = MetricInfo.builder()
                                                       .query(MOCKED_METRIC_QUERY)
                                                       .metricName(MOCKED_METRIC_NAME + "2")
                                                       .identifier(MOCKED_METRIC_IDENTIFIER + "2")
                                                       .metricType(TimeSeriesMetricType.INFRA)
                                                       .liveMonitoring(LiveMonitoring.builder().enabled(false).build())
                                                       .build();

    DatadogMetricCVConfig datadogMetricCVConfig = builderFactory.datadogMetricCVConfigBuilder()
                                                      .metricInfoList(Arrays.asList(metricInfo1, metricInfo2))
                                                      .dashboardName(MOCKED_DASHBOARD_NAME)
                                                      .build();
    datadogMetricCVConfig.setMetricPack(metricPack);

    DatadogMetricsDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(datadogMetricCVConfig, TaskType.LIVE_MONITORING);

    assertThat(collectionInfoResult).isNotNull();
    collectionInfoResult.getMetricDefinitions().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getMetricIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getQuery()).isEqualTo(MOCKED_METRIC_QUERY);
    });
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLI() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    DatadogMetricCVConfig.MetricInfo metricInfo = DatadogMetricCVConfig.MetricInfo.builder()
                                                      .query(MOCKED_METRIC_QUERY)
                                                      .metricName(MOCKED_METRIC_NAME)
                                                      .identifier(MOCKED_METRIC_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.INFRA)
                                                      .build();
    ServiceLevelIndicator serviceLevelIndicator =
        ThresholdServiceLevelIndicator.builder().metric1(MOCKED_METRIC_IDENTIFIER).build();

    DatadogMetricCVConfig datadogMetricCVConfig = builderFactory.datadogMetricCVConfigBuilder()
                                                      .metricInfoList(Arrays.asList(metricInfo))
                                                      .dashboardName(MOCKED_DASHBOARD_NAME)
                                                      .build();
    datadogMetricCVConfig.setMetricPack(metricPack);

    DatadogMetricsDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(Collections.singletonList(datadogMetricCVConfig), serviceLevelIndicator);

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getMetricDefinitions().size()).isEqualTo(1);
    collectionInfoResult.getMetricDefinitions().forEach(metricInfoToCheck -> {
      assertThat(metricInfoToCheck.getMetricName()).isEqualTo(MOCKED_METRIC_NAME);
      assertThat(metricInfoToCheck.getMetricIdentifier()).isEqualTo(MOCKED_METRIC_IDENTIFIER);
      assertThat(metricInfoToCheck.getQuery()).isEqualTo(MOCKED_METRIC_QUERY);
    });
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLIWithDifferentMetricName() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    DatadogMetricCVConfig.MetricInfo metricInfo = DatadogMetricCVConfig.MetricInfo.builder()
                                                      .query(MOCKED_METRIC_QUERY)
                                                      .metricName(MOCKED_METRIC_NAME)
                                                      .identifier(MOCKED_METRIC_IDENTIFIER)
                                                      .metricType(TimeSeriesMetricType.INFRA)
                                                      .build();
    ServiceLevelIndicator serviceLevelIndicator = ThresholdServiceLevelIndicator.builder().metric1("metric1").build();

    DatadogMetricCVConfig datadogMetricCVConfig = builderFactory.datadogMetricCVConfigBuilder()
                                                      .metricInfoList(Arrays.asList(metricInfo))
                                                      .dashboardName(MOCKED_DASHBOARD_NAME)
                                                      .build();
    datadogMetricCVConfig.setMetricPack(metricPack);

    DatadogMetricsDataCollectionInfo collectionInfoResult =
        classUnderTest.toDataCollectionInfo(Collections.singletonList(datadogMetricCVConfig), serviceLevelIndicator);

    assertThat(collectionInfoResult).isNotNull();
    assertThat(collectionInfoResult.getMetricDefinitions().size()).isEqualTo(0);
  }
}
