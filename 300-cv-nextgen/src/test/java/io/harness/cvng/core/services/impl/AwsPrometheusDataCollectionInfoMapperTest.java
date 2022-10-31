/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition.PrometheusFilter;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsPrometheusDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private AwsPrometheusDataCollectionInfoMapper mapper;
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    AwsPrometheusCVConfig cvConfig =
        (AwsPrometheusCVConfig) builderFactory.awsPrometheusCVConfigBuilder().groupName("mygroupName").build();

    cvConfig.setMetricPack(metricPack);

    MetricInfo metricInfo =
        MetricInfo.builder()
            .metricName("myMetric")
            .identifier("metricIdentifier")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .envFilter(Collections.singletonList(
                PrometheusFilter.builder().labelName("namespace").labelValue("cv-demo").build()))
            .serviceFilter(Collections.singletonList(
                PrometheusFilter.builder().labelName("app").labelValue("cv-demo-app").build()))
            .additionalFilters(Arrays.asList(PrometheusFilter.builder().labelName("filter2").labelValue("cv-2").build(),
                PrometheusFilter.builder().labelName("filter3").labelValue("cv-3").build()))
            .build();

    cvConfig.setMetricInfoList(Collections.singletonList(metricInfo));
    AwsPrometheusDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig, TaskType.DEPLOYMENT);
    assertThat(dataCollectionInfo.getGroupName()).isEqualTo("mygroupName");
    assertThat(dataCollectionInfo.getRegion()).isEqualTo("us-east-1");
    assertThat(dataCollectionInfo.getWorkspaceId()).isEqualTo("ws-bd297196-b5ca-48c5-9857-972fe759354f");
    assertThat(dataCollectionInfo.getMetricCollectionInfoList()).isNotEmpty();
    assertThat(dataCollectionInfo.getDataCollectionDsl()).isEqualTo("metric-pack-dsl");

    List<MetricCollectionInfo> metricCollectionInfoList = dataCollectionInfo.getMetricCollectionInfoList();
    metricCollectionInfoList.forEach(metricCollectionInfo -> {
      assertThat(metricCollectionInfo.getMetricName()).isEqualTo("myMetric");
      assertThat(metricCollectionInfo.getMetricIdentifier()).isEqualTo("metricIdentifier");
      assertThat(metricCollectionInfo.getQuery())
          .isEqualTo("cpu_usage_total{app=\"cv-demo-app\",namespace=\"cv-demo\",filter2=\"cv-2\",filter3=\"cv-3\"}");
      assertThat(metricCollectionInfo.getFilters())
          .isEqualTo("app=\"cv-demo-app\",namespace=\"cv-demo\",filter2=\"cv-2\",filter3=\"cv-3\"");
    });
  }
}
