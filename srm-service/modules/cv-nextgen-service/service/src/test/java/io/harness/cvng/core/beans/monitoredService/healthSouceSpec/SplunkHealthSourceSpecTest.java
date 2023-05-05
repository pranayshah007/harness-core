/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class SplunkHealthSourceSpecTest extends CvNextGenTestBase {
  SplunkHealthSourceSpec splunkHealthSourceSpec;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String feature;
  String connectorIdentifier;
  String serviceIdentifier;
  String envIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  List<SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO> queryDTOS;
  BuilderFactory builderFactory;

  @Inject MetricPackService metricPackService;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    feature = "Application Monitoring";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    queryDTOS = Lists.newArrayList(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                       .name(randomAlphabetic(10))
                                       .query(randomAlphabetic(10))
                                       .build());
    splunkHealthSourceSpec =
        SplunkHealthSourceSpec.builder().connectorRef(connectorIdentifier).feature(feature).queries(queryDTOS).build();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetType() {
    assertThat(splunkHealthSourceSpec.getType()).isEqualTo(DataSourceType.SPLUNK);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_forNoExistingConfigs() {
    HealthSource.CVConfigUpdateResult cvConfigUpdateResult =
        splunkHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, new ArrayList<>(), metricPackService);
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();
    List<CVConfig> added = cvConfigUpdateResult.getAdded();

    List<SplunkCVConfig> splunkCVConfigs = (List<SplunkCVConfig>) (List<?>) added;
    assertThat(splunkCVConfigs).hasSize(1);
    SplunkCVConfig splunkCVConfig = splunkCVConfigs.get(0);
    assertCommon(splunkCVConfig);
    assertThat(splunkCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkDeleted() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                     .name(randomAlphabetic(10))
                                     .query(randomAlphabetic(10))
                                     .build()));
    HealthSource.CVConfigUpdateResult result =
        splunkHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getDeleted()).hasSize(1);
    SplunkCVConfig splunkCVConfig = (SplunkCVConfig) result.getDeleted().get(0);
    assertThat(splunkCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkAdded() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                     .name(randomAlphabetic(10))
                                     .query(randomAlphabetic(10))
                                     .build()));
    HealthSource.CVConfigUpdateResult result =
        splunkHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getAdded()).hasSize(1);
    SplunkCVConfig splunkCVConfig = (SplunkCVConfig) result.getAdded().get(0);
    assertCommon(splunkCVConfig);
    assertThat(splunkCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkUpdated() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                     .name(queryDTOS.get(0).getName())
                                     .query(randomAlphabetic(10))
                                     .build()));
    HealthSource.CVConfigUpdateResult result =
        splunkHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getUpdated()).hasSize(1);
    SplunkCVConfig splunkCVConfig = (SplunkCVConfig) result.getUpdated().get(0);
    assertCommon(splunkCVConfig);
    assertThat(splunkCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testValidate() {
    queryDTOS.add(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                      .name(queryDTOS.get(0).getName())
                      .query(randomAlphabetic(10))
                      .build());
    assertThatThrownBy(() -> splunkHealthSourceSpec.validate())
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Duplicate query name present");
  }

  private void assertCommon(SplunkCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getQueryName()).isEqualTo(queryDTOS.get(0).getName());
    assertThat(cvConfig.getQuery()).isEqualTo(queryDTOS.get(0).getQuery());
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  private CVConfig createCVConfig(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO queryDTO) {
    return builderFactory.splunkCVConfigBuilder()
        .queryName(queryDTO.getName())
        .query(queryDTO.getQuery())
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .identifier(identifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountId(accountId)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .build();
  }
}
