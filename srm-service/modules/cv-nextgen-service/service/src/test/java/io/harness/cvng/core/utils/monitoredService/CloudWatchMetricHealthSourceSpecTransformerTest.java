/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec;
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
public class CloudWatchMetricHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  String connectorIdentifier;
  String projectIdentifier;
  String accountId;
  String identifier;
  String orgIdentifier;
  String region;
  String groupName;
  String feature;
  String expression;
  String name;
  String monitoredServiceIdentifier;
  BuilderFactory builderFactory;
  List<CloudWatchMetricDefinition> metricDefinitions;

  @Inject CloudWatchMetricHealthSourceSpecTransformer cloudWatchMetricHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    region = "us-east-1";
    feature = "CloudWatch Metrics";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName = "g1";
    expression = "expression";
    metricDefinitions = new ArrayList<>();
    CloudWatchMetricDefinition metricDefinition1 = createCloudWatchMetricDefinition(groupName);
    metricDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    metricDefinition1.setAnalysis(
        HealthSourceMetricDefinition.AnalysisDTO.builder()
            .deploymentVerification(
                HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder().enabled(true).build())
            .build());
    metricDefinitions.add(metricDefinition1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentRegion() {
    List<CloudWatchMetricCVConfig> cvConfigs = new ArrayList<>();
    CloudWatchMetricCVConfig cvConfig1 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature, connectorIdentifier);
    CloudWatchMetricCVConfig cvConfig2 =
        (CloudWatchMetricCVConfig) createCVConfig(region + "1", feature, connectorIdentifier);
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> cloudWatchMetricHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Region should be same for List of all configs.");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentConnector() {
    List<CloudWatchMetricCVConfig> cvConfigs = new ArrayList<>();
    CloudWatchMetricCVConfig cvConfig1 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature, connectorIdentifier);
    CloudWatchMetricCVConfig cvConfig2 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature, connectorIdentifier + "1");
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> cloudWatchMetricHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentFeature() {
    List<CloudWatchMetricCVConfig> cvConfigs = new ArrayList<>();
    CloudWatchMetricCVConfig cvConfig1 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature, connectorIdentifier);
    CloudWatchMetricCVConfig cvConfig2 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature + "1", connectorIdentifier);
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> cloudWatchMetricHealthSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Feature name should be same for List of all configs.");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    metricDefinitions.get(0).setResponseMapping(
        MetricResponseMapping.builder().serviceInstanceJsonPath("path").build());
    List<CloudWatchMetricCVConfig> cvConfigs = new ArrayList<>();
    CloudWatchMetricCVConfig cvConfig1 =
        (CloudWatchMetricCVConfig) createCVConfig(region, feature, connectorIdentifier);
    cvConfig1.addMetricPackAndInfo(metricDefinitions);
    populateBasicDetails(cvConfig1);
    cvConfigs.add(cvConfig1);

    CloudWatchMetricsHealthSourceSpec cloudWatchMetricsHealthSourceSpec =
        cloudWatchMetricHealthSourceSpecTransformer.transform(cvConfigs);

    assertThat(cloudWatchMetricsHealthSourceSpec.getRegion()).isEqualTo(region);
    assertThat(cloudWatchMetricsHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(cloudWatchMetricsHealthSourceSpec.getFeature()).isEqualTo(feature);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().size()).isEqualTo(1);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getMetricName()).isEqualTo(name);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getIdentifier()).isEqualTo(identifier);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getGroupName()).isEqualTo(groupName);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions().get(0).getExpression()).isEqualTo(expression);
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
                   .get(0)
                   .getResponseMapping()
                   .getServiceInstanceJsonPath())
        .isEqualTo("path");
    assertThat(cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
                   .get(0)
                   .getAnalysis()
                   .getDeploymentVerification()
                   .getEnabled())
        .isTrue();
  }

  private CVConfig createCVConfig(String region, String feature, String connectorIdentifier) {
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
    return CloudWatchMetricDefinition.builder()
        .expression(expression)
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
