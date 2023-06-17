/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkHealthSourceSpec;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SplunkHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  List<SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO> queryDTOS;
  String envIdentifier;
  String connectorIdentifier;
  String productName;
  String projectIdentifier;
  String accountId;
  String identifier;
  String monitoringSourceName;
  String serviceIdentifier;
  String serviceInstanceIdentifier;
  private BuilderFactory builderFactory;

  @Inject SplunkHealthSourceSpecTransformer splunkHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    queryDTOS = Lists.newArrayList(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                       .name(randomAlphabetic(10))
                                       .query(randomAlphabetic(10))
                                       .build(),
        SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
            .name(randomAlphabetic(10))
            .query(randomAlphabetic(10))
            .build());
    envIdentifier = "env";
    connectorIdentifier = "connectorId";
    productName = "Application Monitoring";
    projectIdentifier = "projectId";
    accountId = generateUuid();
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "StackdriverLog";
    serviceIdentifier = "serviceId";
    serviceInstanceIdentifier = "serviceInstanceIdentifier";
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    SplunkHealthSourceSpec splunkHealthSourceSpec =
        splunkHealthSourceSpecTransformer.transformToHealthSourceConfig(createCVConfigs());
    assertThat(splunkHealthSourceSpec.getQueries()).hasSize(2);
    assertThat(splunkHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(splunkHealthSourceSpec.getType()).isEqualTo(DataSourceType.SPLUNK);
    assertThat(splunkHealthSourceSpec.getQueries().get(0).getName()).isEqualTo(queryDTOS.get(0).getName());
    assertThat(splunkHealthSourceSpec.getQueries().get(1).getName()).isEqualTo(queryDTOS.get(1).getName());
    assertThat(splunkHealthSourceSpec.getQueries().get(0).getQuery()).isEqualTo(queryDTOS.get(0).getQuery());
    assertThat(splunkHealthSourceSpec.getQueries().get(1).getQuery()).isEqualTo(queryDTOS.get(1).getQuery());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfig_onDistinctProductName() {
    List<SplunkCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setProductName("different_productName");
    Assertions.assertThatThrownBy(() -> splunkHealthSourceSpecTransformer.transformToHealthSourceConfig(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Application feature name should be same for List of all configs.");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void transformToHealthSourceConfig_onDistinctConnectorRef() {
    List<SplunkCVConfig> cvConfigs = createCVConfigs();
    cvConfigs.get(0).setConnectorIdentifier("different_ConnectorRef");
    Assertions.assertThatThrownBy(() -> splunkHealthSourceSpecTransformer.transformToHealthSourceConfig(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }

  private List<SplunkCVConfig> createCVConfigs() {
    return queryDTOS.stream()
        .map(query
            -> (SplunkCVConfig) builderFactory.splunkCVConfigBuilder()
                   .serviceInstanceIdentifier(serviceInstanceIdentifier)
                   .queryName(query.getName())
                   .query(query.getQuery())
                   .connectorIdentifier(connectorIdentifier)
                   .productName(productName)
                   .projectIdentifier(projectIdentifier)
                   .accountId(accountId)
                   .identifier(identifier)
                   .monitoringSourceName(monitoringSourceName)
                   .build())
        .collect(Collectors.toList());
  }
}
