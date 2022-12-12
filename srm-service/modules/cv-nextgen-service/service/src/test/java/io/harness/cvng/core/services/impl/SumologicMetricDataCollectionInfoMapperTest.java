/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.SumologicMetricDataCollectionInfo;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicMetricDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private SumologicMetricDataCollectionInfoMapper mapper;
  private String orgIdentifier;
  private String projectIdentifier;
  private String accountId;
  private String groupName1;
  private String feature;
  private String connectorIdentifier;
  private String identifier;
  private String name;
  private String monitoredServiceIdentifier;
  private List<MetricDefinition> metricDefinitions;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    feature = "Sumologic Metrics";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName1 = "g1";
    metricDefinitions = new ArrayList<>();
    MetricDefinition metricDefinition1 = createSumologicMetricDefinition(groupName1);
    metricDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    metricDefinition1.setAnalysis(
        HealthSourceMetricDefinition.AnalysisDTO.builder()
            .deploymentVerification(
                HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    metricDefinitions.add(metricDefinition1);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withoutHostCollection() {
    SumologicMetricCVConfig cvConfig = (SumologicMetricCVConfig) createCVConfig(groupName1);
    cvConfig.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig);
    SumologicMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag()).isNull();
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withHostCollection() {
    metricDefinitions.get(0).setResponseMapping(
        MetricResponseMapping.builder().serviceInstanceJsonPath("_sourcehost").build());
    SumologicMetricCVConfig cvConfig = (SumologicMetricCVConfig) createCVConfig(groupName1);
    cvConfig.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig);
    SumologicMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag()).isNotNull();
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag())
        .isEqualTo("_sourcehost");
  }

  private void assertCommons(SumologicMetricDataCollectionInfo info) {
    assertThat(info.getGroupName()).isEqualTo(groupName1);
    assertThat(info.getMetricDefinitions().get(0).getMetricName()).isEqualTo(metricDefinitions.get(0).getMetricName());
    assertThat(info.getMetricDefinitions().get(0).getMetricIdentifier())
        .isEqualTo(metricDefinitions.get(0).getIdentifier());
    assertThat(info.getMetricDefinitions().get(0).getQuery()).isEqualTo(metricDefinitions.get(0).getQuery());
  }

  private CVConfig createCVConfig(String groupName) {
    return builderFactory.sumologicMetricCVConfigBuilder()
        .groupName(groupName)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }

  private MetricDefinition createSumologicMetricDefinition(String group) {
    String metricDefinitionIdentifier = generateUuid();
    return MetricDefinition.builder()
        .query("query")
        .metricName("metric_name")
        .identifier(metricDefinitionIdentifier)
        .groupName(group)
        .build();
  }

  private void populateBasicDetails(CVConfig cvConfig) {
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setProjectIdentifier(projectIdentifier);
  }
}
