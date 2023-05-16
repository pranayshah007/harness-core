/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.MetricInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig.DynatraceMetricInfo;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicMetricInfo;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.models.VerificationType;
import io.harness.encryption.Scope;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVConfigServiceImplTest extends CvNextGenTestBase {
  @Inject private CVConfigService cvConfigService;

  private String accountId;
  private String connectorIdentifier;
  private String productName;
  private String groupId;
  private String serviceInstanceIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private String monitoringSourceIdentifier;
  private String monitoringSourceName;
  private String environmentIdentifier;
  BuilderFactory builderFactory;
  ServiceEnvironmentParams serviceEnvironmentParams;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    connectorIdentifier = generateUuid();
    productName = generateUuid();
    groupId = generateUuid();
    serviceInstanceIdentifier = builderFactory.getContext().getServiceIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    environmentIdentifier = builderFactory.getContext().getEnvIdentifier();
    monitoringSourceIdentifier = generateUuid() + "/" + generateUuid();
    monitoringSourceName = generateUuid();
    serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier(projectIdentifier)
                                   .orgIdentifier(orgIdentifier)
                                   .serviceIdentifier(serviceInstanceIdentifier)
                                   .environmentIdentifier(environmentIdentifier)
                                   .build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSave() {
    CVConfig cvConfig = createCVConfig();
    CVConfig updated = save(cvConfig);
    CVConfig saved = cvConfigService.get(updated.getUuid());
    assertCommons(saved, cvConfig);
  }

  private CVConfig save(CVConfig cvConfig) {
    return cvConfigService.save(cvConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSave_batchAPI() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    save(cvConfigs);
    cvConfigs.forEach(cvConfig -> assertCommons(cvConfigService.get(cvConfig.getUuid()), cvConfig));
  }

  private List<CVConfig> save(List<CVConfig> cvConfigs) {
    return cvConfigService.save(cvConfigs);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSave_batchAPIIfUUIDIsDefined() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    cvConfigs.forEach(cvConfig -> cvConfig.setUuid(generateUuid()));
    assertThatThrownBy(() -> save(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UUID should be null when creating CVConfig");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGet() {
    CVConfig cvConfig = createCVConfig();
    CVConfig updated = save(cvConfig);
    CVConfig saved = cvConfigService.get(updated.getUuid());
    assertCommons(saved, cvConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDelete() {
    CVConfig cvConfig = createCVConfig();
    CVConfig updated = save(cvConfig);
    cvConfigService.delete(updated.getUuid());
    assertThat(cvConfigService.get(cvConfig.getUuid())).isEqualTo(null);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdate_AppDCVConfig() {
    AppDynamicsCVConfig appDynamicsCVConfig = createAppDCVConfig();
    save(appDynamicsCVConfig);
    AppDynamicsCVConfig updated = (AppDynamicsCVConfig) cvConfigService.get(appDynamicsCVConfig.getUuid());
    updated.setTierName("updated-tier-name");
    updated.setApplicationName("updated-application-name");
    MetricInfo metricInfo = MetricInfo.builder()
                                .baseFolder("baseFolder")
                                .metricPath("metricPath")
                                .metricType(TimeSeriesMetricType.ERROR)
                                .build();
    updated.setMetricInfos(Arrays.asList(metricInfo));
    cvConfigService.update(updated);
    AppDynamicsCVConfig updateStored = (AppDynamicsCVConfig) cvConfigService.get(updated.getUuid());
    assertCommons(updated, updateStored);
    assertThat(updateStored.getApplicationName()).isEqualTo("updated-application-name");
    assertThat(updateStored.getTierName()).isEqualTo("updated-tier-name");
    assertThat(updateStored.getMetricInfos()).isEqualTo(Arrays.asList(metricInfo));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdate_NewRelicCVConfig() {
    NewRelicCVConfig newRelicCVConfig = createNewRelicCVConfig();
    save(newRelicCVConfig);
    NewRelicCVConfig updated = (NewRelicCVConfig) cvConfigService.get(newRelicCVConfig.getUuid());
    updated.setApplicationId(54321);
    updated.setApplicationName("updated-application-name");
    NewRelicMetricInfo metricInfo = NewRelicMetricInfo.builder()
                                        .nrql("nrql")
                                        .metricType(TimeSeriesMetricType.ERROR)
                                        .responseMapping(MetricResponseMapping.builder()
                                                             .metricValueJsonPath("metricValueJsonPath")
                                                             .timestampJsonPath("timestampJsonPath")
                                                             .build())
                                        .build();
    updated.setMetricInfos(Arrays.asList(metricInfo));
    cvConfigService.update(updated);
    NewRelicCVConfig updateStored = (NewRelicCVConfig) cvConfigService.get(updated.getUuid());
    assertCommons(updated, updateStored);
    assertThat(updateStored.getApplicationName()).isEqualTo("updated-application-name");
    assertThat(updateStored.getApplicationId()).isEqualTo(54321);
    assertThat(updateStored.getMetricInfos()).isEqualTo(Arrays.asList(metricInfo));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdate_DynatraceCVConfig() {
    DynatraceCVConfig dynatraceCVConfig = createDynatraceCVConfig();
    save(dynatraceCVConfig);
    DynatraceCVConfig updated = (DynatraceCVConfig) cvConfigService.get(dynatraceCVConfig.getUuid());
    updated.setDynatraceServiceName("updated-dynatrace-service-name");
    updated.setDynatraceServiceId("54321");
    DynatraceMetricInfo metricInfo = DynatraceMetricInfo.builder()
                                         .metricType(TimeSeriesMetricType.ERROR)
                                         .metricSelector("metricSelector")
                                         .isManualQuery(true)
                                         .build();
    updated.setMetricInfos(Arrays.asList(metricInfo));
    cvConfigService.update(updated);
    DynatraceCVConfig updateStored = (DynatraceCVConfig) cvConfigService.get(updated.getUuid());
    assertCommons(updated, updateStored);
    assertThat(updateStored.getDynatraceServiceName()).isEqualTo("updated-dynatrace-service-name");
    assertThat(updateStored.getDynatraceServiceId()).isEqualTo("54321");
    assertThat(updateStored.getMetricInfos()).isEqualTo(Arrays.asList(metricInfo));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdate_withEmptyCVConfigId() {
    CVConfig cvConfig = createCVConfig();
    assertThatThrownBy(() -> cvConfigService.update(Lists.newArrayList(cvConfig)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Trying to update a CVConfig with empty UUID.");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_findSingleCVConfig() {
    CVConfig cvConfig = createCVConfig();
    save(cvConfig);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, cvConfig.getConnectorIdentifier());
    assertCommons(cvConfigs.get(0), cvConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_zeroMatch() {
    CVConfig cvConfig = createCVConfig();
    save(cvConfig);
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, generateUuid());
    assertThat(cvConfigs).hasSize(0);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_multipleMatchMultipleConnectorIdentifiers() {
    List<CVConfig> cvConfigs1 = createCVConfigs(5);
    String connectorIdentifier1 = generateUuid();
    cvConfigs1.forEach(cvConfig -> {
      cvConfig.setConnectorIdentifier(connectorIdentifier1);
      save(cvConfig);
    });

    List<CVConfig> cvConfigs2 = createCVConfigs(7);
    String connectorIdentifier2 = generateUuid();
    cvConfigs2.forEach(cvConfig -> {
      cvConfig.setConnectorIdentifier(connectorIdentifier2);
      save(cvConfig);
    });

    assertThat(cvConfigService.list(accountId, connectorIdentifier1)).hasSize(5);
    assertThat(cvConfigService.list(accountId, connectorIdentifier2)).hasSize(7);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_withConnectorAndProductName() {
    List<CVConfig> cvConfigs = createCVConfigs(4);
    String connectorIdentifier1 = generateUuid();
    cvConfigs.forEach(cvConfig -> {
      cvConfig.setConnectorIdentifier(connectorIdentifier1);
      cvConfig.setProductName("product1");
    });
    cvConfigs.get(0).setProductName("product2");
    save(cvConfigs);
    assertThat(cvConfigService.list(accountId, connectorIdentifier1, "product1")).hasSize(3);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_withAccountOrgAndProjectId() {
    List<CVConfig> cvConfigs = createCVConfigs(4);
    cvConfigs.forEach(cvConfig -> {
      cvConfig.setOrgIdentifier(orgIdentifier);
      cvConfig.setProjectIdentifier(projectIdentifier);
      cvConfig.setIdentifier("group/1");
      cvConfig.setMonitoringSourceName("group1");
    });
    cvConfigs.get(0).setProjectIdentifier("project2");
    save(cvConfigs);
    assertThat(cvConfigService.list(accountId, orgIdentifier, projectIdentifier, "group/1")).hasSize(3);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetProjectsNames_whenNoConfigsPresent() {
    assertThat(cvConfigService.getProductNames(accountId, generateUuid())).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetProjectsNames_withMultipleDuplicateProjectNames() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    List<String> projectNames = Arrays.asList("p2", "p1", "p2", "p3", "p3");
    IntStream.range(0, 5).forEach(index -> cvConfigs.get(index).setProductName(projectNames.get(index)));
    save(cvConfigs);
    assertThat(cvConfigService.getProductNames(accountId, connectorIdentifier))
        .isEqualTo(Lists.newArrayList("p1", "p2", "p3"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDeleteByGroupId() {
    String groupName = "appdynamics/app/name";
    List<CVConfig> cvConfigs = createCVConfigs(5);
    cvConfigs.forEach(cvConfig -> cvConfig.setIdentifier(groupName));
    save(cvConfigs);
    cvConfigService.deleteByIdentifier(accountId, orgIdentifier, projectIdentifier, groupName);
    cvConfigs.forEach(cvConfig -> assertThat(cvConfigService.get(cvConfig.getUuid())).isNull());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGetMonitoringSourceIds() {
    String MONITORING_SOURCE_SUFFIX = "Monitoring Source Id ";
    List<CVConfig> cvConfigs = createCVConfigs(50);
    for (int i = 0; i < 50; i++) {
      cvConfigs.get(i).setIdentifier(MONITORING_SOURCE_SUFFIX + "/" + i);
    }
    save(cvConfigs);

    // Testing no filter
    List<String> monitoringSourceIds =
        cvConfigService.getMonitoringSourceIds(accountId, orgIdentifier, projectIdentifier, null);
    assertThat(monitoringSourceIds.size()).isEqualTo(50);

    // Testing filter
    monitoringSourceIds =
        cvConfigService.getMonitoringSourceIds(accountId, orgIdentifier, projectIdentifier, "Ce iD   ");
    assertThat(monitoringSourceIds.size()).isEqualTo(50);

    monitoringSourceIds =
        cvConfigService.getMonitoringSourceIds(accountId, orgIdentifier, projectIdentifier, generateUuid());
    assertThat(monitoringSourceIds).isEmpty();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testlistByMonitoringSources() {
    String MONITORING_SOURCE_SUFFIX = "Monitoring Source Id ";
    List<CVConfig> cvConfigs = createCVConfigs(10);
    for (int i = 0; i < 3; i++) {
      cvConfigs.get(i).setIdentifier(MONITORING_SOURCE_SUFFIX + "/0");
    }
    for (int i = 0; i < 3; i++) {
      cvConfigs.get(i + 3).setIdentifier(MONITORING_SOURCE_SUFFIX + "/1");
    }
    save(cvConfigs);
    List<CVConfig> cvConfigsList = cvConfigService.listByMonitoringSources(accountId, orgIdentifier, projectIdentifier,
        Arrays.asList(MONITORING_SOURCE_SUFFIX + "/0", MONITORING_SOURCE_SUFFIX + "/1"));
    assertThat(cvConfigsList.size()).isEqualTo(6);
  }

  private void assertCommons(CVConfig actual, CVConfig expected) {
    assertThat(actual.getVerificationType()).isEqualTo(expected.getVerificationType());
    assertThat(actual.getAccountId()).isEqualTo(expected.getAccountId());
    assertThat(actual.getConnectorIdentifier()).isEqualTo(expected.getConnectorIdentifier());
    assertThat(actual.getMonitoredServiceIdentifier()).isEqualTo(expected.getMonitoredServiceIdentifier());
    assertThat(actual.getProjectIdentifier()).isEqualTo(expected.getProjectIdentifier());
    assertThat(actual.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(actual.getCategory()).isEqualTo(expected.getCategory());
    assertThat(actual.getProductName()).isEqualTo(expected.getProductName());
    assertThat(actual.getType()).isEqualTo(expected.getType());
    assertThat(actual.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(actual.getMonitoringSourceName()).isEqualTo(expected.getMonitoringSourceName());
    assertThat(actual.getDataSourceName()).isEqualTo(expected.getType());
  }

  public List<CVConfig> createCVConfigs(int n) {
    return IntStream.range(0, n).mapToObj(index -> createCVConfig()).collect(Collectors.toList());
  }

  private CVConfig createCVConfig() {
    return builderFactory.splunkCVConfigBuilder().connectorIdentifier(connectorIdentifier).build();
  }

  private AppDynamicsCVConfig createAppDCVConfig() {
    AppDynamicsCVConfig appDynamicsCVConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    fillCommon(appDynamicsCVConfig);
    appDynamicsCVConfig.setApplicationName("application-name");
    appDynamicsCVConfig.setTierName("tier-name");
    appDynamicsCVConfig.setMetricPack(MetricPack.builder().build());
    appDynamicsCVConfig.setMetricInfos(Arrays.asList(MetricInfo.builder().build()));
    return appDynamicsCVConfig;
  }

  private NewRelicCVConfig createNewRelicCVConfig() {
    NewRelicCVConfig newRelicCVConfig = builderFactory.newRelicCVConfigBuilder().build();
    fillCommon(newRelicCVConfig);
    newRelicCVConfig.setApplicationName("application-name");
    newRelicCVConfig.setApplicationId(12345);
    newRelicCVConfig.setMetricPack(MetricPack.builder().build());
    newRelicCVConfig.setMetricInfos(Arrays.asList(NewRelicMetricInfo.builder().build()));
    return newRelicCVConfig;
  }

  private DynatraceCVConfig createDynatraceCVConfig() {
    DynatraceCVConfig dynatraceCVConfig = builderFactory.dynatraceCVConfigBuilder().build();
    fillCommon(dynatraceCVConfig);
    dynatraceCVConfig.setDynatraceServiceName("dynatrace-service-name");
    dynatraceCVConfig.setDynatraceServiceId("12345");
    dynatraceCVConfig.setMetricPack(MetricPack.builder().build());
    dynatraceCVConfig.setMetricInfos(Arrays.asList(DynatraceMetricInfo.builder().build()));
    return dynatraceCVConfig;
  }

  private void fillCommon(CVConfig cvConfig) {
    cvConfig.setVerificationType(VerificationType.LOG);
    cvConfig.setAccountId(accountId);
    cvConfig.setConnectorIdentifier(connectorIdentifier);
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setIdentifier(groupId);
    cvConfig.setMonitoringSourceName(generateUuid());
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(productName);
    cvConfig.setIdentifier(monitoringSourceIdentifier);
    cvConfig.setMonitoringSourceName(monitoringSourceName);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testDoesAnyCVConfigExistsInProjectWhenNoCVConfigExists() {
    boolean doesAnyCVConfigExists =
        cvConfigService.doesAnyCVConfigExistsInProject(accountId, orgIdentifier, projectIdentifier);
    assertThat(doesAnyCVConfigExists).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testDoesAnyCVConfigExistsInProjectWhenCVConfigExists() {
    List<CVConfig> cvConfigs = createCVConfigs(4);
    save(cvConfigs);
    boolean doesAnyCVConfigExists =
        cvConfigService.doesAnyCVConfigExistsInProject(accountId, orgIdentifier, projectIdentifier);
    assertThat(doesAnyCVConfigExists).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDeleteConfigsForProject() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    cvConfigs.get(0).setProjectIdentifier("newProject");
    save(cvConfigs);
    cvConfigService.deleteByProjectIdentifier(CVConfig.class, accountId, orgIdentifier, projectIdentifier);
    assertThat(cvConfigService.get(cvConfigs.get(0).getUuid())).isEqualTo(cvConfigs.get(0));
    for (int i = 1; i < 5; i++) {
      assertThat(cvConfigService.get(cvConfigs.get(i).getUuid())).isNull();
    }
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDeleteConfigsForOrganisation() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    cvConfigs.get(0).setOrgIdentifier("newOrganisation");
    save(cvConfigs);
    cvConfigService.deleteByOrgIdentifier(CVConfig.class, accountId, orgIdentifier);
    assertThat(cvConfigService.get(cvConfigs.get(0).getUuid())).isEqualTo(cvConfigs.get(0));
    for (int i = 1; i < 5; i++) {
      assertThat(cvConfigService.get(cvConfigs.get(i).getUuid())).isNull();
    }
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDeleteConfigsForAccount() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    cvConfigs.get(0).setAccountId("newAccount");
    save(cvConfigs);
    cvConfigService.deleteByAccountIdentifier(CVConfig.class, accountId);
    assertThat(cvConfigService.get(cvConfigs.get(0).getUuid())).isEqualTo(cvConfigs.get(0));
    for (int i = 1; i < 5; i++) {
      assertThat(cvConfigService.get(cvConfigs.get(i).getUuid())).isNull();
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_projectScope() {
    List<CVConfig> cvConfigs = createCVConfigs(5);
    save(cvConfigs);
    List<CVConfig> result = cvConfigService.findByConnectorIdentifier(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, Scope.PROJECT);
    Collections.sort(cvConfigs, Comparator.comparing(CVConfig::getUuid));
    Collections.sort(result, Comparator.comparing(CVConfig::getUuid));
    assertThat(result).isEqualTo(cvConfigs);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_accountScope() {
    List<CVConfig> projectScoped = createCVConfigs(5);
    save(projectScoped);
    String projectScopedConnectorIdentifier = connectorIdentifier;
    connectorIdentifier = "account.connectorId";
    List<CVConfig> cvConfigs = createCVConfigs(5);
    save(cvConfigs);
    List<CVConfig> result = cvConfigService.findByConnectorIdentifier(accountId, "", "", "connectorId", Scope.ACCOUNT);
    Collections.sort(cvConfigs, Comparator.comparing(CVConfig::getUuid));
    Collections.sort(result, Comparator.comparing(CVConfig::getUuid));
    assertThat(result).isEqualTo(cvConfigs);
    result = cvConfigService.findByConnectorIdentifier(
        accountId, orgIdentifier, projectIdentifier, projectScopedConnectorIdentifier, Scope.PROJECT);
    Collections.sort(projectScoped, Comparator.comparing(CVConfig::getUuid));
    Collections.sort(result, Comparator.comparing(CVConfig::getUuid));
    assertThat(result).isEqualTo(projectScoped);

    result =
        cvConfigService.findByConnectorIdentifier(accountId, orgIdentifier, projectIdentifier, "random", Scope.ACCOUNT);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindByConnectorIdentifier_OrgIdentifierScope() {
    List<CVConfig> projectScoped = createCVConfigs(5);
    save(projectScoped);
    String projectScopedConnectorIdentifier = connectorIdentifier;
    connectorIdentifier = "org.connectorId";
    List<CVConfig> cvConfigs = createCVConfigs(5);
    save(cvConfigs);
    List<CVConfig> result =
        cvConfigService.findByConnectorIdentifier(accountId, orgIdentifier, "", "connectorId", Scope.ORG);
    Collections.sort(cvConfigs, Comparator.comparing(CVConfig::getUuid));
    Collections.sort(result, Comparator.comparing(CVConfig::getUuid));
    assertThat(result).isEqualTo(cvConfigs);
    result = cvConfigService.findByConnectorIdentifier(
        accountId, orgIdentifier, projectIdentifier, projectScopedConnectorIdentifier, Scope.PROJECT);
    Collections.sort(projectScoped, Comparator.comparing(CVConfig::getUuid));
    Collections.sort(result, Comparator.comparing(CVConfig::getUuid));
    assertThat(result).isEqualTo(projectScoped);

    result =
        cvConfigService.findByConnectorIdentifier(accountId, orgIdentifier, projectIdentifier, "random", Scope.ACCOUNT);
    assertThat(result).isEmpty();
  }
}
