/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.StackdriverDataCollectionInfo;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.StackdriverServiceImplTest;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StackdriverDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private StackdriverDataCollectionInfoMapper mapper;
  private MetricPack metricPack;
  private String metricDef;
  @Before
  public void setup() throws IOException {
    metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    metricDef = Resources.toString(
        Objects.requireNonNull(StackdriverServiceImplTest.class.getResource("/stackdriver/metric-definition.json")),
        Charsets.UTF_8);
  }
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() throws Exception {
    StackdriverCVConfig stackdriverCVConfig = StackdriverCVConfig.builder().dashboardName("dashboard").build();
    MetricInfo metricInfo = MetricInfo.builder().jsonMetricDefinition(metricDef).identifier("identifier").build();
    stackdriverCVConfig.setMetricPack(metricPack);
    stackdriverCVConfig.setMetricInfoList(Arrays.asList(metricInfo));
    StackDriverMetricDefinition stackDriverMetricDefinition = StackDriverMetricDefinition.extractFromJson(metricDef);
    stackDriverMetricDefinition.setMetricIdentifier("identifier");
    StackdriverDataCollectionInfo info = mapper.toDataCollectionInfo(stackdriverCVConfig, TaskType.DEPLOYMENT);

    assertThat(info).isNotNull();
    assertThat(info.getMetricDefinitions()).containsAll(Arrays.asList(stackDriverMetricDefinition));
    assertThat(info.getDataCollectionDsl()).isEqualTo("metric-pack-dsl");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_taskTypeFilter() throws Exception {
    StackdriverCVConfig stackdriverCVConfig = StackdriverCVConfig.builder().dashboardName("dashboard").build();
    MetricInfo metricInfo1 = MetricInfo.builder().jsonMetricDefinition(metricDef).identifier("identifier").build();
    MetricInfo metricInfo2 = MetricInfo.builder()
                                 .jsonMetricDefinition(metricDef)
                                 .identifier("identifier2")
                                 .deploymentVerification(DeploymentVerification.builder().enabled(false).build())
                                 .build();
    stackdriverCVConfig.setMetricPack(metricPack);
    stackdriverCVConfig.setMetricInfoList(Arrays.asList(metricInfo1, metricInfo2));
    StackDriverMetricDefinition stackDriverMetricDefinition = StackDriverMetricDefinition.extractFromJson(metricDef);
    stackDriverMetricDefinition.setMetricIdentifier("identifier");
    StackdriverDataCollectionInfo info = mapper.toDataCollectionInfo(stackdriverCVConfig, TaskType.DEPLOYMENT);

    assertThat(info).isNotNull();
    assertThat(info.getMetricDefinitions()).containsAll(Arrays.asList(stackDriverMetricDefinition));
    assertThat(info.getDataCollectionDsl()).isEqualTo("metric-pack-dsl");
  }
}
