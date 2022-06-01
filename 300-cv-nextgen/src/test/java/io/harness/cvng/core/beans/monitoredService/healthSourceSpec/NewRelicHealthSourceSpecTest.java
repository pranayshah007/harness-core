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
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.MetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NewRelicCVConfig;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewRelicHealthSourceSpecTest extends CvNextGenTestBase {
  NewRelicHealthSourceSpec newRelicHealthSourceSpec;
  @Inject MetricPackService metricPackService;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String applicationName;
  String applicationId;
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
    applicationId = "1234";
    feature = "apm";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    metricPackDTOS =
        Arrays.asList(MetricPackDTO.builder().identifier(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER).build());
    newRelicHealthSourceSpec = NewRelicHealthSourceSpec.builder()
                                   .applicationId(applicationId)
                                   .applicationName(applicationName)
                                   .connectorRef(connectorIdentifier)
                                   .feature(feature)
                                   .metricPacks(metricPackDTOS.stream().collect(Collectors.toSet()))
                                   .build();

    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenNoConfigExist() {
    CVConfigUpdateResult cvConfigUpdateResult = newRelicHealthSourceSpec.getCVConfigUpdateResult(accountId,
        orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier,
        name, Collections.emptyList(), metricPackService);
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();
    List<CVConfig> added = cvConfigUpdateResult.getAdded();

    List<NewRelicCVConfig> newRelicCVConfigs = (List<NewRelicCVConfig>) (List<?>) added;
    assertThat(newRelicCVConfigs).hasSize(1);
    NewRelicCVConfig newRelicCVConfig = newRelicCVConfigs.get(0);
    assertCommon(newRelicCVConfig);
    assertThat(newRelicCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
    assertThat(newRelicCVConfig.getMetricPack().getMetrics().size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkDeleted() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(
        createCVConfig(MetricPack.builder().accountId(accountId).category(CVMonitoringCategory.ERRORS).build()));
    CVConfigUpdateResult result =
        newRelicHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getDeleted()).hasSize(1);
    NewRelicCVConfig newRelicCVConfig = (NewRelicCVConfig) result.getDeleted().get(0);
    assertThat(newRelicCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkAdded() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(
        createCVConfig(MetricPack.builder().accountId(accountId).category(CVMonitoringCategory.ERRORS).build()));
    CVConfigUpdateResult result =
        newRelicHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getAdded()).hasSize(1);
    NewRelicCVConfig newRelicCVConfig = (NewRelicCVConfig) result.getAdded().get(0);
    assertCommon(newRelicCVConfig);
    assertThat(newRelicCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkUpdated() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(metricPackService.getMetricPack(accountId, orgIdentifier, projectIdentifier,
        DataSourceType.NEW_RELIC, CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER)));
    CVConfigUpdateResult result =
        newRelicHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getUpdated()).hasSize(1);
    NewRelicCVConfig newRelicCVConfig = (NewRelicCVConfig) result.getUpdated().get(0);
    assertCommon(newRelicCVConfig);
    assertThat(newRelicCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  private void assertCommon(NewRelicCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getApplicationName()).isEqualTo(applicationName);
    assertThat(cvConfig.getApplicationId()).isEqualTo(Long.valueOf(applicationId));
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getMetricPack().getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getMetricPack().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.NEW_RELIC);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  private CVConfig createCVConfig(MetricPack metricPack) {
    return builderFactory.newRelicCVConfigBuilder()
        .applicationId(Long.valueOf(applicationId))
        .applicationName(applicationName)
        .metricPack(metricPack)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }
}
