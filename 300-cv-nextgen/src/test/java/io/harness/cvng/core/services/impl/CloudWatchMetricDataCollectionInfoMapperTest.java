/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec.CloudWatchMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudWatchMetricDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject CloudWatchMetricDataCollectionInfoMapper mapper;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String region;
  String groupName1;
  String feature;
  String connectorIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  List<CloudWatchMetricDefinition> metricDefinitions;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    region = "us-east1";
    feature = "CloudWatch Metrics";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName1 = "g1";
    metricDefinitions = new ArrayList<>();
    CloudWatchMetricDefinition metricDefinition1 = createCloudWatchMetricDefinition(groupName1);
    metricDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    metricDefinition1.setAnalysis(
        HealthSourceMetricDefinition.AnalysisDTO.builder()
            .deploymentVerification(
                HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    metricDefinitions.add(metricDefinition1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withoutHostCollection() {
    metricDefinitions.get(0).setResponseMapping(null);
    CloudWatchMetricCVConfig cvConfig = (CloudWatchMetricCVConfig) createCVConfig(groupName1);
    cvConfig.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig);
    CloudWatchMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricInfos().get(0).getResponseMapping()).isNull();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withHostCollection() {
    metricDefinitions.get(0).setResponseMapping(
        MetricResponseMapping.builder().serviceInstanceJsonPath("path").build());
    CloudWatchMetricCVConfig cvConfig = (CloudWatchMetricCVConfig) createCVConfig(groupName1);
    cvConfig.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig);
    CloudWatchMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricInfos().get(0).getResponseMapping()).isNotNull();
    assertThat(dataCollectionInfo.getMetricInfos().get(0).getResponseMapping().getServiceInstanceJsonPath())
        .isEqualTo("path");
  }

  private void assertCommons(CloudWatchMetricDataCollectionInfo info) {
    assertThat(info.getRegion()).isEqualTo(region);
    assertThat(info.getGroupName()).isEqualTo(groupName1);
    assertThat(info.getMetricInfos().get(0).getMetricName()).isEqualTo(metricDefinitions.get(0).getMetricName());
    assertThat(info.getMetricInfos().get(0).getMetricIdentifier()).isEqualTo(metricDefinitions.get(0).getIdentifier());
    assertThat(info.getMetricInfos().get(0).getExpression()).isEqualTo(metricDefinitions.get(0).getExpression());
    assertThat(info.getMetricInfos().get(0).getFinalExpression()).isEqualTo(metricDefinitions.get(0).getExpression());
    assertThat(info.getMetricPack().getAccountId()).isEqualTo(accountId);
    assertThat(info.getMetricPack().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(info.getMetricPack().getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(info.getMetricPack().getCategory()).isEqualTo(metricDefinitions.get(0).getRiskProfile().getCategory());
    assertThat(info.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.CLOUDWATCH_METRICS);
    assertThat(info.getMetricPack().getMetrics().size()).isEqualTo(1);
  }

  private CVConfig createCVConfig(String groupName) {
    return builderFactory.cloudWatchMetricCVConfigBuilder()
        .region(region)
        .groupName(groupName)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }

  private CloudWatchMetricDefinition createCloudWatchMetricDefinition(String group) {
    String identifier = generateUuid();
    return CloudWatchMetricDefinition.builder()
        .expression(identifier)
        .metricName(identifier)
        .identifier(identifier)
        .groupName(group)
        .build();
  }

  private void populateBasicDetails(CVConfig cvConfig) {
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setProjectIdentifier(projectIdentifier);
  }
}
