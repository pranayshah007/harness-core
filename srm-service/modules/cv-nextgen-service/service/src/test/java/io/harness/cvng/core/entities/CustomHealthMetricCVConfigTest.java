/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthMetricCVConfigTest extends CategoryTest {
  List<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition> metricDefinitions;
  CustomHealthMetricCVConfig customHealthCVConfig;
  MetricResponseMapping responseMapping;

  @Before
  public void setup() {
    metricDefinitions = new ArrayList<>();
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath("metricValuePath")
                          .timestampJsonPath("timeStringPath")
                          .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)
                                   .urlPath("https://dd.com")
                                   .startTimeInfo(TimestampInfo.builder().build())
                                   .endTimeInfo(TimestampInfo.builder().build())
                                   .build())
            .metricResponseMapping(responseMapping)
            .metricName("metric_1")
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .build();

    metricDefinitions.add(metricDefinition);
    customHealthCVConfig = CustomHealthMetricCVConfig.builder()
                               .groupName("group1")
                               .queryType(HealthSourceQueryType.SERVICE_BASED)
                               .metricDefinitions(metricDefinitions)
                               .build();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenMetricNameIsNull() {
    metricDefinitions.get(0).setMetricName(null);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("metricName should not be null for index 0");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenLiveMonitoringIsTrueForHostBasedQuery() {
    customHealthCVConfig.setQueryType(HealthSourceQueryType.HOST_BASED);
    metricDefinitions.get(0).setDeploymentVerification(
        AnalysisInfo.DeploymentVerification.builder().enabled(true).build());
    metricDefinitions.get(0).setLiveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build());
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Host based queries can only be used for continuous verification.");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenDeploymentVerificationIsTrueForServiceBasedQuery() {
    metricDefinitions.get(0).setDeploymentVerification(
        AnalysisInfo.DeploymentVerification.builder().enabled(true).build());
    customHealthCVConfig.setQueryType(HealthSourceQueryType.SERVICE_BASED);
    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service based queries can only be used for live monitoring and service level indicators.");
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testValidateParams_whenThereAreDuplicateMetricDefinitions() {
    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)
                                   .urlPath("https://dd.com")
                                   .startTimeInfo(TimestampInfo.builder().build())
                                   .endTimeInfo(TimestampInfo.builder().build())
                                   .build())
            .metricResponseMapping(responseMapping)
            .metricName("metric_1")
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(false).build())
            .build();
    metricDefinitions.add(metricDefinition);
    customHealthCVConfig.setQueryType(HealthSourceQueryType.SERVICE_BASED);

    assertThatThrownBy(customHealthCVConfig::validateParams)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Duplicate group name (group1) and metric name (metric_1) combination present.");
  }
}
