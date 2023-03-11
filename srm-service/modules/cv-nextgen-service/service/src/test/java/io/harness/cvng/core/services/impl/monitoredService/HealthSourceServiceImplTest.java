/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.encryption.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HealthSourceServiceImplTest extends CvNextGenTestBase {
  @Inject HealthSourceService healthSourceService;
  @Inject MetricPackService metricPackService;
  @Inject CVConfigService cvConfigService;
  @Inject MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;

  String identifier;
  String name;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String environmentIdentifier;
  String serviceIdentifier;
  String feature;
  String connectorIdentifier;
  String appTierName;
  String applicationName;
  String nameSpaceIdentifier;
  String monitoredServiceIdentifier;

  @Before
  public void setup() {
    identifier = "health-source-identifier";
    name = "health-source-name";
    accountId = generateUuid();
    orgIdentifier = "org";
    projectIdentifier = "project";
    environmentIdentifier = "env";
    serviceIdentifier = "service";
    feature = "Application Monitoring";
    connectorIdentifier = "connectorIdentifier";
    applicationName = "applicationName";
    appTierName = "appTierName";
    nameSpaceIdentifier = "monitoredServiceIdentifier";
    monitoredServiceIdentifier = generateUuid();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCheckIfAlreadyPresent_ExistingConfigsWithIdentifier() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    assertThatThrownBy(()
                           -> healthSourceService.checkIfAlreadyPresent(accountId, orgIdentifier, projectIdentifier,
                               nameSpaceIdentifier, Sets.newHashSet(healthSource)))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format(
            "Already Existing configs for Monitored Service  with identifier %s and orgIdentifier %s and projectIdentifier %s",
            HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()),
            orgIdentifier, projectIdentifier));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testCreate_CVConfigsCreation() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig cvConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    commonCVConfigAssert(cvConfig);
    assertThat(cvConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(cvConfig.isEnabled()).isTrue();
    assertThat(cvConfig.getMetricPack())
        .isEqualTo(metricPackService.getMetricPack(accountId, orgIdentifier, projectIdentifier,
            DataSourceType.APP_DYNAMICS, CVNextGenConstants.ERRORS_PACK_IDENTIFIER));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreate_MonitoringSourcePTCreation() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    String workerId = monitoringSourcePerpetualTaskService.getDeploymentWorkerId(accountId, orgIdentifier,
        projectIdentifier, connectorIdentifier, nameSpaceIdentifier + "/" + healthSource.getIdentifier());
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
        monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
            accountId, orgIdentifier, projectIdentifier, connectorIdentifier, Scope.PROJECT);
    assertThat(monitoringSourcePerpetualTasks).hasSize(2);

    // Delete HealthSource and create new one and make sure it is created with new connector id
    healthSourceService.delete(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, Arrays.asList(healthSource.getIdentifier()));
    connectorIdentifier = "connector2";
    healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    workerId = monitoringSourcePerpetualTaskService.getDeploymentWorkerId(accountId, orgIdentifier, projectIdentifier,
        connectorIdentifier, nameSpaceIdentifier + "/" + healthSource.getIdentifier());
    monitoringSourcePerpetualTasks = monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, Scope.PROJECT);
    assertThat(monitoringSourcePerpetualTasks).hasSize(2);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGet() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), false);
    Set<HealthSource> savedHealthSources = healthSourceService.get(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, Arrays.asList(identifier));
    assertThat(savedHealthSources.size()).isEqualTo(1);
    HealthSource saveHealthSource = savedHealthSources.iterator().next();
    assertThat(saveHealthSource).isEqualTo(healthSource);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testDelete() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    healthSourceService.delete(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, Arrays.asList(identifier));
    List<CVConfig> cvConfigs =
        cvConfigService.list(accountId, orgIdentifier, projectIdentifier, healthSource.getIdentifier());
    assertThat(cvConfigs.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testFetchCVConfig() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    List<CVConfig> cvConfigs = healthSourceService.getCVConfigs(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, healthSource.getIdentifier());
    assertThat(cvConfigs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_updateName() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    healthSource.setName("new-name");
    healthSourceService.update(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), false);
    Set<HealthSource> savedHealthSource = healthSourceService.get(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, Arrays.asList(identifier));
    assertThat(savedHealthSource.size()).isEqualTo(1);
    assertThat(savedHealthSource.iterator().next().getName()).isEqualTo("new-name");
    List<CVConfig> cvConfigs = cvConfigService.getCVConfigs(MonitoredServiceParams.builder()
                                                                .accountIdentifier(accountId)
                                                                .orgIdentifier(orgIdentifier)
                                                                .projectIdentifier(projectIdentifier)
                                                                .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                                .build());
    assertThat(cvConfigs.size()).isNotZero();
    for (CVConfig cvConfig : cvConfigs) {
      assertThat(cvConfig.isEnabled()).isFalse();
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_deleteAndAddCVConfigs() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig cvConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    commonCVConfigAssert(cvConfig);
    assertThat(cvConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);

    HealthSource updatedHealthSource = createHealthSource(CVMonitoringCategory.PERFORMANCE);
    healthSourceService.update(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(updatedHealthSource), true);
    cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
    assertThat(cvConfigs.size()).isEqualTo(1);
    cvConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    commonCVConfigAssert(cvConfig);
    assertThat(cvConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_updateCVConfigs() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    healthSource.setIdentifier("new-identifier");
    healthSource.setName("new-name");
    healthSourceService.update(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
    assertThat(cvConfigs.size()).isEqualTo(1);
    AppDynamicsCVConfig cvConfig = (AppDynamicsCVConfig) cvConfigs.get(0);
    assertThat(cvConfig.getIdentifier())
        .isEqualTo(HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, "new-identifier"));
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo("new-name");
    assertThat(cvConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_updateCVConfigsWithMetricPacks() {
    HealthSource healthSource = createHealthSource(CVMonitoringCategory.ERRORS);
    healthSourceService.create(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);
    healthSource.setIdentifier("new-identifier");
    healthSource.setName("new-name");
    AppDynamicsHealthSourceSpec appDynamicsHealthSourceSpec = (AppDynamicsHealthSourceSpec) healthSource.getSpec();
    appDynamicsHealthSourceSpec.setMetricPacks(
        Arrays
            .asList(TimeSeriesMetricPackDTO.builder().identifier(CVNextGenConstants.ERRORS_PACK_IDENTIFIER).build(),
                TimeSeriesMetricPackDTO.builder().identifier(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER).build())
            .stream()
            .collect(Collectors.toSet()));
    appDynamicsHealthSourceSpec.setFeature("new-feature");
    appDynamicsHealthSourceSpec.setTierName("new-tier-name");
    appDynamicsHealthSourceSpec.setApplicationName("new-application-name");
    healthSourceService.update(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier,
        monitoredServiceIdentifier, nameSpaceIdentifier, Sets.newHashSet(healthSource), true);

    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
    assertThat(cvConfigs.size()).isEqualTo(2);

    for (CVConfig cvConfig : cvConfigs) {
      AppDynamicsCVConfig appdCVConfig = (AppDynamicsCVConfig) cvConfig;
      assertThat(appdCVConfig.getIdentifier())
          .isEqualTo(HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, "new-identifier"));
      assertThat(appdCVConfig.getMonitoringSourceName()).isEqualTo("new-name");
      assertThat(appdCVConfig.getProductName()).isEqualTo("new-feature");
      assertThat(appdCVConfig.getTierName()).isEqualTo("new-tier-name");
      assertThat(appdCVConfig.getApplicationName()).isEqualTo("new-application-name");
      assertThat(appdCVConfig.getMetricPack().getCategory())
          .isIn(CVMonitoringCategory.ERRORS, CVMonitoringCategory.PERFORMANCE);
    }

    Set<HealthSource> saveHealthSources = healthSourceService.get(
        accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, Arrays.asList("new-identifier"));
    assertThat(saveHealthSources.size()).isEqualTo(1);
    assertThat(saveHealthSources.iterator().next().getName()).isEqualTo("new-name");
    assertThat(saveHealthSources.iterator().next().getIdentifier()).isEqualTo("new-identifier");
    AppDynamicsHealthSourceSpec savedAppdHealthSourceSpec =
        (AppDynamicsHealthSourceSpec) saveHealthSources.iterator().next().getSpec();
    assertThat(savedAppdHealthSourceSpec.getTierName()).isEqualTo("new-tier-name");
    assertThat(savedAppdHealthSourceSpec.getApplicationName()).isEqualTo("new-application-name");
    assertThat(savedAppdHealthSourceSpec.getFeature()).isEqualTo("new-feature");
    assertThat(savedAppdHealthSourceSpec.getMetricPacks().size()).isEqualTo(2);
  }

  HealthSource createHealthSource(CVMonitoringCategory cvMonitoringCategory) {
    HealthSourceSpec healthSourceSpec =
        AppDynamicsHealthSourceSpec.builder()
            .applicationName(applicationName)
            .tierName(appTierName)
            .connectorRef(connectorIdentifier)
            .feature(feature)
            .metricPacks(
                Arrays
                    .asList(TimeSeriesMetricPackDTO.builder().identifier(cvMonitoringCategory.getDisplayName()).build())
                    .stream()
                    .collect(Collectors.toSet()))
            .metricDefinitions(Collections.EMPTY_LIST)
            .build();
    return HealthSource.builder()
        .identifier(identifier)
        .name(name)
        .type(MonitoredServiceDataSourceType.APP_DYNAMICS)
        .spec(healthSourceSpec)
        .build();
  }

  private void commonCVConfigAssert(AppDynamicsCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getTierName()).isEqualTo(appTierName);
    assertThat(cvConfig.getApplicationName()).isEqualTo(applicationName);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getIdentifier())
        .isEqualTo(HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, identifier));
  }
}
