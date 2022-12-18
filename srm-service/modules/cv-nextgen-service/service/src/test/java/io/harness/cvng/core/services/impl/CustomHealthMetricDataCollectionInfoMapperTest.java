/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CustomHealthDataCollectionInfo;
import io.harness.cvng.beans.MetricResponseMappingDTO;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthMetricDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private CustomHealthMetricDataCollectionInfoMapper customHealthMapper;
  CustomHealthMetricCVConfig customHealthCVConfig;

  String groupName = "group";
  String metricName = "metric";
  String urlPath = "https://dd.com";
  String metricValueJSONPath = "metricValuePath";
  String timestampJSONPath = "timeStringPath";

  String metricName1 = "metricName_1";
  String urlPath1 = "https://appd.com";

  String metricName2 = "metricName_2";
  String urlPath2 = "https://prometheus-mock.com";

  @Before
  public void setup() {
    List<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition> metricDefinitions = new ArrayList<>();
    MetricResponseMapping responseMapping = MetricResponseMapping.builder()
                                                .metricValueJsonPath(metricValueJSONPath)
                                                .timestampJsonPath(timestampJSONPath)
                                                .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition cvMetricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .requestDefinition(
                CustomHealthRequestDefinition.builder().method(CustomHealthMethod.GET).urlPath(urlPath).build())
            .metricResponseMapping(responseMapping)
            .metricName(metricName)
            .sli(AnalysisInfo.SLI.builder().enabled(false).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(true).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(false).build())
            .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition sliMetricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .requestDefinition(
                CustomHealthRequestDefinition.builder().method(CustomHealthMethod.GET).urlPath(urlPath1).build())
            .metricResponseMapping(responseMapping)
            .metricName(metricName1)
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(false).build())
            .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition liveMonitoringMetricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .requestDefinition(
                CustomHealthRequestDefinition.builder().method(CustomHealthMethod.GET).urlPath(urlPath2).build())
            .metricResponseMapping(responseMapping)
            .metricName(metricName2)
            .sli(AnalysisInfo.SLI.builder().enabled(false).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .build();

    metricDefinitions.add(cvMetricDefinition);
    metricDefinitions.add(sliMetricDefinition);
    metricDefinitions.add(liveMonitoringMetricDefinition);

    customHealthCVConfig = CustomHealthMetricCVConfig.builder()
                               .groupName(groupName)
                               .queryType(HealthSourceQueryType.HOST_BASED)
                               .metricDefinitions(metricDefinitions)
                               .metricPack(MetricPack.builder().build())
                               .build();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_forDeploymentVerification() {
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        customHealthMapper.toDataCollectionInfo(customHealthCVConfig, TaskType.DEPLOYMENT);
    assertThat(customHealthDataCollectionInfo.getGroupName()).isEqualTo(groupName);
    List<CustomHealthDataCollectionInfo.CustomHealthMetricInfo> customHealthMetricInfo =
        customHealthDataCollectionInfo.getMetricInfoList();
    assertThat(customHealthMetricInfo.size()).isEqualTo(1);

    CustomHealthDataCollectionInfo.CustomHealthMetricInfo customHealthMetricInfo1 = customHealthMetricInfo.get(0);
    assertThat(customHealthMetricInfo1.getMetricName()).isEqualTo(metricName);
    assertThat(customHealthMetricInfo1.getUrlPath()).isEqualTo(urlPath);
    assertThat(customHealthMetricInfo1.getResponseMapping())
        .isEqualTo(MetricResponseMappingDTO.builder()
                       .metricValueJsonPath(metricValueJSONPath)
                       .timestampJsonPath(timestampJSONPath)
                       .build());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_forSLI() {
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        customHealthMapper.toDataCollectionInfo(customHealthCVConfig, TaskType.SLI);
    assertThat(customHealthDataCollectionInfo.getGroupName()).isEqualTo(groupName);
    List<CustomHealthDataCollectionInfo.CustomHealthMetricInfo> customHealthMetricInfo =
        customHealthDataCollectionInfo.getMetricInfoList();
    assertThat(customHealthMetricInfo.size()).isEqualTo(1);

    CustomHealthDataCollectionInfo.CustomHealthMetricInfo customHealthMetricInfo1 = customHealthMetricInfo.get(0);
    assertThat(customHealthMetricInfo1.getMetricName()).isEqualTo(metricName1);
    assertThat(customHealthMetricInfo1.getUrlPath()).isEqualTo(urlPath1);
    assertThat(customHealthMetricInfo1.getResponseMapping())
        .isEqualTo(MetricResponseMappingDTO.builder()
                       .metricValueJsonPath(metricValueJSONPath)
                       .timestampJsonPath(timestampJSONPath)
                       .build());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_forLiveMonitoring() {
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        customHealthMapper.toDataCollectionInfo(customHealthCVConfig, TaskType.LIVE_MONITORING);
    assertThat(customHealthDataCollectionInfo.getGroupName()).isEqualTo(groupName);
    List<CustomHealthDataCollectionInfo.CustomHealthMetricInfo> customHealthMetricInfo =
        customHealthDataCollectionInfo.getMetricInfoList();
    assertThat(customHealthMetricInfo.size()).isEqualTo(1);

    CustomHealthDataCollectionInfo.CustomHealthMetricInfo customHealthMetricInfo1 = customHealthMetricInfo.get(0);
    assertThat(customHealthMetricInfo1.getMetricName()).isEqualTo(metricName2);
    assertThat(customHealthMetricInfo1.getUrlPath()).isEqualTo(urlPath2);
    assertThat(customHealthMetricInfo1.getResponseMapping())
        .isEqualTo(MetricResponseMappingDTO.builder()
                       .metricValueJsonPath(metricValueJSONPath)
                       .timestampJsonPath(timestampJSONPath)
                       .build());
  }
}
