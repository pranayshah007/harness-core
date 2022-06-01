/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.MetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec.AppDMetricDefinitions;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppDynamicsHealthSourceSpecTest extends CvNextGenTestBase {
  AppDynamicsHealthSourceSpec appDynamicsHealthSourceSpec;
  @Inject MetricPackService metricPackService;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String applicationName;
  String tierName;
  String feature;
  String connectorIdentifier;
  String serviceIdentifier;
  String envIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  List<MetricPackDTO> metricPackDTOS;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    applicationName = "appName";
    tierName = "tierName";
    feature = "Application Monitoring";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    metricPackDTOS =
        Arrays.asList(MetricPackDTO.builder().identifier(CVNextGenConstants.ERRORS_PACK_IDENTIFIER).build());
    appDynamicsHealthSourceSpec =
        AppDynamicsHealthSourceSpec.builder()
            .applicationName(applicationName)
            .tierName(tierName)
            .connectorRef(connectorIdentifier)
            .feature(feature)
            .metricPacks(metricPackDTOS.stream().collect(Collectors.toSet()))
            .metricDefinitions(Arrays.<AppDMetricDefinitions>asList(
                AppDMetricDefinitions.builder()
                    .metricName("metric1")
                    .groupName("group1")
                    .metricPath("path2")
                    .baseFolder("baseFolder2")
                    .sli(SLIDTO.builder().enabled(true).build())
                    .analysis(AnalysisDTO.builder()
                                  .riskProfile(RiskProfile.builder()
                                                   .category(CVMonitoringCategory.ERRORS)
                                                   .metricType(TimeSeriesMetricType.INFRA)
                                                   .build())
                                  .deploymentVerification(DeploymentVerificationDTO.builder()
                                                              .enabled(true)
                                                              .serviceInstanceMetricPath("Individual Nodes|*|path")
                                                              .build())
                                  .liveMonitoring(LiveMonitoringDTO.builder().enabled(true).build())
                                  .build())
                    .build(),
                AppDMetricDefinitions.builder()
                    .metricName("metric2")
                    .groupName("group1")
                    .metricPath("path2")
                    .baseFolder("baseFolder2")
                    .sli(SLIDTO.builder().enabled(true).build())
                    .analysis(AnalysisDTO.builder()
                                  .riskProfile(RiskProfile.builder()
                                                   .category(CVMonitoringCategory.ERRORS)
                                                   .metricType(TimeSeriesMetricType.INFRA)
                                                   .build())
                                  .deploymentVerification(DeploymentVerificationDTO.builder()
                                                              .enabled(true)
                                                              .serviceInstanceMetricPath("Individual Nodes|*|path")
                                                              .build())
                                  .liveMonitoring(LiveMonitoringDTO.builder().enabled(true).build())
                                  .build())
                    .build(),
                AppDMetricDefinitions.builder()
                    .metricName("metric3")
                    .groupName("group3")
                    .metricPath("path2")
                    .baseFolder("baseFolder2")
                    .sli(SLIDTO.builder().enabled(true).build())
                    .analysis(AnalysisDTO.builder()
                                  .riskProfile(RiskProfile.builder()
                                                   .category(CVMonitoringCategory.ERRORS)
                                                   .metricType(TimeSeriesMetricType.INFRA)
                                                   .build())
                                  .deploymentVerification(DeploymentVerificationDTO.builder()
                                                              .enabled(true)
                                                              .serviceInstanceMetricPath("Individual Nodes|*|path")
                                                              .build())
                                  .liveMonitoring(LiveMonitoringDTO.builder().enabled(true).build())
                                  .build())
                    .build()))
            .build();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenNoConfigExist() {
    CVConfigUpdateResult cvConfigUpdateResult = appDynamicsHealthSourceSpec.getCVConfigUpdateResult(accountId,
        orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier,
        name, Collections.emptyList(), metricPackService);
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();
    List<CVConfig> added = cvConfigUpdateResult.getAdded();

    List<AppDynamicsCVConfig> appDynamicsCVConfigs = (List<AppDynamicsCVConfig>) (List<?>) added;
    assertThat(appDynamicsCVConfigs).hasSize(3);
    AppDynamicsCVConfig appDynamicsCVConfig = cvConfigUpdateResult.getAdded()
                                                  .stream()
                                                  .map(cvConfig -> (AppDynamicsCVConfig) cvConfig)
                                                  .filter(cvConfig -> StringUtils.isEmpty(cvConfig.getGroupName()))
                                                  .findAny()
                                                  .get();
    assertCommon(appDynamicsCVConfig);
    assertThat(appDynamicsCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(appDynamicsCVConfig.getMetricPack().getMetrics().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkDeleted() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(
        createCVConfig(MetricPack.builder().accountId(accountId).category(CVMonitoringCategory.PERFORMANCE).build()));
    CVConfigUpdateResult result =
        appDynamicsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getDeleted()).hasSize(1);
    AppDynamicsCVConfig appDynamicsCVConfig = (AppDynamicsCVConfig) result.getDeleted().get(0);
    assertThat(appDynamicsCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkAdded() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(
        createCVConfig(MetricPack.builder().accountId(accountId).category(CVMonitoringCategory.PERFORMANCE).build()));
    CVConfigUpdateResult result =
        appDynamicsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getAdded()).hasSize(3);
    result.getAdded().stream().map(cvConfig -> (AppDynamicsCVConfig) cvConfig).forEach(this::assertCommon);
    assertThat(result.getAdded()
                   .stream()
                   .map(cvConfig -> (AppDynamicsCVConfig) cvConfig)
                   .filter(cvConfig -> "group1".equals(cvConfig.getGroupName()))
                   .count())
        .isEqualTo(1);
    assertThat(result.getAdded()
                   .stream()
                   .map(cvConfig -> (AppDynamicsCVConfig) cvConfig)
                   .filter(cvConfig -> "group3".equals(cvConfig.getGroupName()))
                   .count())
        .isEqualTo(1);
    AppDynamicsCVConfig group1CVConfig = result.getAdded()
                                             .stream()
                                             .map(cvConfig -> (AppDynamicsCVConfig) cvConfig)
                                             .filter(cvConfig -> "group1".equals(cvConfig.getGroupName()))
                                             .findAny()
                                             .get();
    assertThat(group1CVConfig.getMetricInfos().size()).isEqualTo(2);
    assertThat(group1CVConfig.getMetricInfos().get(0).getDeploymentVerification().isEnabled()).isTrue();
    assertThat(group1CVConfig.getMetricInfos().get(0).getDeploymentVerification().getServiceInstanceMetricPath())
        .isEqualTo("Individual Nodes|*|path");
    assertThat(group1CVConfig.getMetricInfos().get(0).getSli().isEnabled()).isTrue();
    AppDynamicsCVConfig appDynamicsCVConfig = (AppDynamicsCVConfig) result.getAdded().get(0);
    assertThat(appDynamicsCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkUpdated() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(metricPackService.getMetricPack(accountId, orgIdentifier, projectIdentifier,
        DataSourceType.APP_DYNAMICS, CVNextGenConstants.ERRORS_PACK_IDENTIFIER)));
    CVConfigUpdateResult result =
        appDynamicsHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getUpdated()).hasSize(1);
    AppDynamicsCVConfig appDynamicsCVConfig = (AppDynamicsCVConfig) result.getUpdated().get(0);
    assertCommon(appDynamicsCVConfig);
    assertThat(appDynamicsCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  private void assertCommon(AppDynamicsCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getApplicationName()).isEqualTo(applicationName);
    assertThat(cvConfig.getTierName()).isEqualTo(tierName);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getMetricPack().getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getMetricPack().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.APP_DYNAMICS);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  private CVConfig createCVConfig(MetricPack metricPack) {
    return builderFactory.appDynamicsCVConfigBuilder()
        .tierName(tierName)
        .applicationName(applicationName)
        .metricPack(metricPack)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .identifier(identifier)
        .category(metricPack.getCategory())
        .build();
  }
}
