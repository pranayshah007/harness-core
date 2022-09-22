/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec.CloudWatchMetricDefinition;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudWatchMetricsHealthSourceSpecTest extends CvNextGenTestBase {
  CloudWatchMetricsHealthSourceSpec cloudWatchMetricsHealthSourceSpec;
  @Inject MetricPackService metricPackService;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String region;
  String feature;
  String connectorIdentifier;
  String serviceIdentifier;
  String envIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    region = "us-east1";
    feature = "CloudWatch Metrics";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    List<TimeSeriesMetricPackDTO> metricThresholds = Collections.singletonList(
        TimeSeriesMetricPackDTO.builder().identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER).build());
    List<CloudWatchMetricDefinition> metricDefinitions = new ArrayList<>();
    CloudWatchMetricDefinition metricDefinition1 = createCloudWatchMetricDefinition("g1");
    metricDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    metricDefinition1.setAnalysis(
        AnalysisDTO.builder()
            .deploymentVerification(DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    CloudWatchMetricDefinition metricDefinition2 = createCloudWatchMetricDefinition("g1");
    metricDefinition2.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.ERRORS).build());
    metricDefinition2.setAnalysis(
        AnalysisDTO.builder()
            .deploymentVerification(DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    CloudWatchMetricDefinition metricDefinition3 = createCloudWatchMetricDefinition("g2");
    metricDefinition3.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build());
    metricDefinition3.setAnalysis(
        AnalysisDTO.builder()
            .deploymentVerification(DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    CloudWatchMetricDefinition metricDefinition4 = createCloudWatchMetricDefinition("g2");
    metricDefinition4.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build());
    metricDefinition4.setAnalysis(
        AnalysisDTO.builder()
            .deploymentVerification(DeploymentVerificationDTO.builder().enabled(false).build())
            .build());
    metricDefinitions.add(metricDefinition1);
    metricDefinitions.add(metricDefinition2);
    metricDefinitions.add(metricDefinition3);
    metricDefinitions.add(metricDefinition4);
    cloudWatchMetricsHealthSourceSpec = CloudWatchMetricsHealthSourceSpec.builder()
                                            .region(region)
                                            .connectorRef(connectorIdentifier)
                                            .feature(feature)
                                            .metricDefinitions(metricDefinitions)
                                            .metricThresholds(new HashSet<>(metricThresholds))
                                            .build();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_noExistingConfigs() {
    CVConfigUpdateResult result = cloudWatchMetricsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name,
        Collections.emptyList(), metricPackService);
    Map<CVMonitoringCategory, CloudWatchMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, CloudWatchMetricCVConfig.class ::cast, (u, v) -> v));
    assertThat(result.getUpdated()).hasSize(0);
    assertThat(result.getDeleted()).hasSize(0);
    assertThat(map).hasSize(3);
    assertThat(map.get(CVMonitoringCategory.ERRORS).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon(map.get(CVMonitoringCategory.ERRORS));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withDifferentExistingConfigs() {
    CloudWatchMetricDefinition metricDefinition =
        cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .get();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricDefinition.builder()
                                                                       .identifier(metricDefinition.getIdentifier())
                                                                       .name(metricDefinition.getMetricName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, metricDefinition.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g3", metricPack));
    CVConfigUpdateResult result = cloudWatchMetricsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    Map<CVMonitoringCategory, CloudWatchMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, CloudWatchMetricCVConfig.class ::cast, (u, v) -> v));
    assertThat(result.getUpdated()).hasSize(0);
    assertThat(result.getDeleted()).hasSize(1);
    assertThat(map).hasSize(3);
    assertThat(map.get(CVMonitoringCategory.ERRORS).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon(map.get(CVMonitoringCategory.ERRORS));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withSimilarExistingConfigs() {
    CloudWatchMetricDefinition metricDefinition =
        cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .get();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricDefinition.builder()
                                                                       .identifier(metricDefinition.getIdentifier())
                                                                       .name(metricDefinition.getMetricName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, metricDefinition.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g1", metricPack));
    CVConfigUpdateResult result = cloudWatchMetricsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    Map<CVMonitoringCategory, CloudWatchMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, CloudWatchMetricCVConfig.class ::cast, (u, v) -> v));
    assertThat(result.getUpdated()).hasSize(1);
    assertThat(result.getDeleted()).hasSize(0);
    assertThat(map).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon((CloudWatchMetricCVConfig) result.getUpdated().get(0));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withSimilarAndDifferentExistingConfigs() {
    CloudWatchMetricDefinition metricDefinition =
        cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .get();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricDefinition.builder()
                                                                       .identifier(metricDefinition.getIdentifier())
                                                                       .name(metricDefinition.getMetricName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, metricDefinition.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g1", metricPack));
    cvConfigs.add(createCVConfig("g3", metricPack));
    CVConfigUpdateResult result = cloudWatchMetricsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    Map<CVMonitoringCategory, CloudWatchMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, CloudWatchMetricCVConfig.class ::cast, (u, v) -> v));
    assertThat(result.getUpdated()).hasSize(1);
    assertThat(result.getDeleted()).hasSize(1);
    assertThat(map).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon((CloudWatchMetricCVConfig) result.getUpdated().get(0));
    assertCommon((CloudWatchMetricCVConfig) result.getDeleted().get(0));
    assertCommon((CloudWatchMetricCVConfig) result.getAdded().get(0));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNoConfigs_withExistingConfigs() {
    CloudWatchMetricDefinition metricDefinition =
        cloudWatchMetricsHealthSourceSpec.getMetricDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .get();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricDefinition.builder()
                                                                       .identifier(metricDefinition.getIdentifier())
                                                                       .name(metricDefinition.getMetricName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, metricDefinition.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g1", metricPack));
    cvConfigs.add(createCVConfig("g3", metricPack));
    cloudWatchMetricsHealthSourceSpec.setMetricDefinitions(null);
    CVConfigUpdateResult result = cloudWatchMetricsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    assertThat(result.getUpdated()).hasSize(0);
    assertThat(result.getDeleted()).hasSize(2);
    assertThat(result.getAdded()).hasSize(0);
    assertCommon((CloudWatchMetricCVConfig) result.getDeleted().get(0));
  }

  private void assertCommon(CloudWatchMetricCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getRegion()).isEqualTo(region);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getMetricPack().getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getMetricPack().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.CLOUDWATCH_METRICS);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  private CVConfig createCVConfig(String groupName, MetricPack metricPack) {
    return builderFactory.cloudWatchMetricCVConfigBuilder()
        .region(region)
        .groupName(groupName)
        .metricPack(metricPack)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }
  private MetricPack createMetricPack(
      Set<MetricDefinition> metricDefinitions, String identifier, CVMonitoringCategory category) {
    return MetricPack.builder()
        .accountId(accountId)
        .category(category)
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(DataSourceType.CLOUDWATCH_METRICS)
        .metrics(metricDefinitions)
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
}
