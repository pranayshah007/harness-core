/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec.StackdriverLogHealthSourceQueryDTO;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StackdriverLogHealthSourceSpecTest extends CvNextGenTestBase {
  StackdriverLogHealthSourceSpec stackdriverLogHealthSourceSpec;
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
  List<StackdriverLogHealthSourceQueryDTO> queryDTOS;
  BuilderFactory builderFactory;

  @Inject MetricPackService metricPackService;

  @Before
  public void setup() {
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
    queryDTOS = Lists.newArrayList(StackdriverLogHealthSourceQueryDTO.builder()
                                       .name(randomAlphabetic(10))
                                       .query(randomAlphabetic(10))
                                       .messageIdentifier(randomAlphabetic(10))
                                       .serviceInstanceIdentifier(randomAlphabetic(10))
                                       .build());
    stackdriverLogHealthSourceSpec = StackdriverLogHealthSourceSpec.builder()
                                         .connectorRef(connectorIdentifier)
                                         .feature(feature)
                                         .queries(queryDTOS)
                                         .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenNoConfigExist() {
    CVConfigUpdateResult cvConfigUpdateResult = stackdriverLogHealthSourceSpec.getCVConfigUpdateResult(accountId,
        orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier,
        name, Collections.emptyList(), metricPackService);
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();
    List<CVConfig> added = cvConfigUpdateResult.getAdded();

    List<StackdriverLogCVConfig> stackdriverLogCVConfigs = (List<StackdriverLogCVConfig>) (List<?>) added;
    assertThat(stackdriverLogCVConfigs).hasSize(1);
    StackdriverLogCVConfig stackdriverLogCVConfig = stackdriverLogCVConfigs.get(0);
    assertCommon(stackdriverLogCVConfig);
    assertThat(stackdriverLogCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkDeleted() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(StackdriverLogHealthSourceQueryDTO.builder()
                                     .name(randomAlphabetic(10))
                                     .query(randomAlphabetic(10))
                                     .messageIdentifier(randomAlphabetic(10))
                                     .serviceInstanceIdentifier(randomAlphabetic(10))
                                     .build()));
    CVConfigUpdateResult result = stackdriverLogHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    assertThat(result.getDeleted()).hasSize(1);
    StackdriverLogCVConfig stackdriverLogCVConfig = (StackdriverLogCVConfig) result.getDeleted().get(0);
    assertThat(stackdriverLogCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkAdded() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(StackdriverLogHealthSourceQueryDTO.builder()
                                     .name(randomAlphabetic(10))
                                     .query(randomAlphabetic(10))
                                     .messageIdentifier(randomAlphabetic(10))
                                     .serviceInstanceIdentifier(randomAlphabetic(10))
                                     .build()));
    CVConfigUpdateResult result = stackdriverLogHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    assertThat(result.getAdded()).hasSize(1);
    StackdriverLogCVConfig stackdriverLogCVConfig = (StackdriverLogCVConfig) result.getAdded().get(0);
    assertCommon(stackdriverLogCVConfig);
    assertThat(stackdriverLogCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_checkUpdated() {
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(StackdriverLogHealthSourceQueryDTO.builder()
                                     .name(queryDTOS.get(0).getName())
                                     .query(randomAlphabetic(10))
                                     .messageIdentifier(queryDTOS.get(0).getMessageIdentifier())
                                     .serviceInstanceIdentifier(queryDTOS.get(0).getServiceInstanceIdentifier())
                                     .build()));
    CVConfigUpdateResult result = stackdriverLogHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs,
        metricPackService);
    assertThat(result.getUpdated()).hasSize(1);
    StackdriverLogCVConfig stackdriverLogCVConfig = (StackdriverLogCVConfig) result.getUpdated().get(0);
    assertCommon(stackdriverLogCVConfig);
    assertThat(stackdriverLogCVConfig.getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void validate() {
    queryDTOS.add(StackdriverLogHealthSourceQueryDTO.builder()
                      .name(queryDTOS.get(0).getName())
                      .query(randomAlphabetic(10))
                      .messageIdentifier(randomAlphabetic(10))
                      .serviceInstanceIdentifier(randomAlphabetic(10))
                      .build());
    assertThatThrownBy(() -> stackdriverLogHealthSourceSpec.validate())
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Duplicate query name present");
  }

  private void assertCommon(StackdriverLogCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getProductName()).isEqualTo(feature);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getQueryName()).isEqualTo(queryDTOS.get(0).getName());
    assertThat(cvConfig.getQuery()).isEqualTo(queryDTOS.get(0).getQuery());
    assertThat(cvConfig.getServiceInstanceIdentifier()).isEqualTo(queryDTOS.get(0).getServiceInstanceIdentifier());
    assertThat(cvConfig.getMessageIdentifier()).isEqualTo(queryDTOS.get(0).getMessageIdentifier());
  }

  private CVConfig createCVConfig(StackdriverLogHealthSourceQueryDTO queryDTO) {
    return builderFactory.stackdriverLogCVConfigBuilder()
        .serviceInstanceIdentifier(queryDTO.getServiceInstanceIdentifier())
        .messageIdentifier(queryDTO.getMessageIdentifier())
        .queryName(queryDTO.getName())
        .query(queryDTO.getQuery())
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .productName(feature)
        .identifier(identifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountId(accountId)
        .build();
  }
}
