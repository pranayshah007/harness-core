/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SumologicMetricHealthSourceSpec;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicMetricSourceSpecTransformerTest extends CvNextGenTestBase {
  String connectorIdentifier;
  String projectIdentifier;
  String accountId;
  String identifier;
  String orgIdentifier;
  String groupName;
  String query;
  String name;

  String feature = "Sumologic"; // TODO Fix remove
  String monitoredServiceIdentifier;
  BuilderFactory builderFactory;
  List<SumologicMetricHealthSourceSpec.SumologicMetricDefinition> metricDefinitions;

  @Inject SumologicMetricSourceSpecTransformer sumologicMetricSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName = "g1";
    query = "expression";
    metricDefinitions = new ArrayList<>();
    SumologicMetricHealthSourceSpec.SumologicMetricDefinition metricDefinition1 =
        createSumologicMetricDefinition(groupName);
    metricDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    metricDefinition1.setAnalysis(
        HealthSourceMetricDefinition.AnalysisDTO.builder()
            .deploymentVerification(
                HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder().enabled(true).build())
            .build());
    metricDefinitions.add(metricDefinition1);
  }

  /*    @Test
      @Owner(developers = ANSUMAN)
      @Category(UnitTests.class)
      public void testTransformToHealthSourceConfig_preconditionDifferentRegion() {
          List<SumologicMetricCVConfig> cvConfigs = new ArrayList<>();
          SumologicMetricCVConfig cvConfig1 =
                  (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier);
          SumologicMetricCVConfig cvConfig2 =
                  (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier);
          cvConfigs.add(cvConfig1);
          cvConfigs.add(cvConfig2);
          assertThatThrownBy(() -> sumologicMetricSourceSpecTransformer.transform(cvConfigs))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessage("Region should be same for List of all configs.");
      }*/

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentConnector() {
    List<SumologicMetricCVConfig> cvConfigs = new ArrayList<>();
    SumologicMetricCVConfig cvConfig1 = (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier);
    SumologicMetricCVConfig cvConfig2 = (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier + "1");
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> sumologicMetricSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for all the configs in the list.");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentFeature() {
    List<SumologicMetricCVConfig> cvConfigs = new ArrayList<>();
    SumologicMetricCVConfig cvConfig1 = (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier);
    SumologicMetricCVConfig cvConfig2 = (SumologicMetricCVConfig) createCVConfig(feature + "1", connectorIdentifier);
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> sumologicMetricSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Feature name should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    metricDefinitions.get(0).setResponseMapping(
        MetricResponseMapping.builder().serviceInstanceJsonPath("_sourcehost").build());
    List<SumologicMetricCVConfig> cvConfigs = new ArrayList<>();
    SumologicMetricCVConfig cvConfig1 = (SumologicMetricCVConfig) createCVConfig(feature, connectorIdentifier);
    cvConfig1.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig1);
    cvConfigs.add(cvConfig1);

    SumologicMetricHealthSourceSpec cloudWatchMetricsHealthSourceSpec =
        sumologicMetricSourceSpecTransformer.transform(cvConfigs);
    // Fix null pointer here. TODO
    assertThat(cloudWatchMetricsHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().size()).isEqualTo(1);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getMetricName()).isEqualTo(name);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getIdentifier()).isEqualTo(identifier);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getGroupName()).isEqualTo(groupName);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getQuery()).isEqualTo(query);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
                   .get(0)
                   .getResponseMapping()
                   .getServiceInstanceJsonPath())
        .isEqualTo("_sourcehost");
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
                   .get(0)
                   .getAnalysis()
                   .getDeploymentVerification()
                   .getEnabled())
        .isTrue();
  }

  private CVConfig createCVConfig(String feature, String connectorIdentifier) {
    return builderFactory.sumologicMetricCVConfigBuilder()
        .groupName(groupName)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }

  private SumologicMetricHealthSourceSpec.SumologicMetricDefinition createSumologicMetricDefinition(String group) {
    return SumologicMetricHealthSourceSpec.SumologicMetricDefinition.builder()
        .query(query)
        .metricName(name)
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