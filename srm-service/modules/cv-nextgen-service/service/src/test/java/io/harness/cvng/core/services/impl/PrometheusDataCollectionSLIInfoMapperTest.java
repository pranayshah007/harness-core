/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.PrometheusDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusDataCollectionSLIInfoMapperTest extends CvNextGenTestBase {
  @Inject private PrometheusDataCollectionInfoMapper mapper;
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLI() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();
    ServiceLevelIndicator serviceLevelIndicator = ThresholdServiceLevelIndicator.builder().metric1("myMetric").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .identifier("myMetric")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDataCollectionInfo(Collections.singletonList(cvConfig), serviceLevelIndicator);
    assertThat(dataCollectionInfo.getMetricCollectionInfoList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLI_withDifferentMetricName() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();
    ServiceLevelIndicator serviceLevelIndicator = ThresholdServiceLevelIndicator.builder().metric1("metric1").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDataCollectionInfo(Collections.singletonList(cvConfig), serviceLevelIndicator);
    assertThat(dataCollectionInfo.getMetricCollectionInfoList().size()).isEqualTo(0);
  }
}
