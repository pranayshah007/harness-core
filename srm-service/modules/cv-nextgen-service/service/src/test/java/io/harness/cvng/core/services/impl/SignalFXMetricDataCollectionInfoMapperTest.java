/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SignalFXMetricDataCollectionInfo;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SignalFXMetricDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private SignalFXMetricDataCollectionInfoMapper mapper;
  private String orgIdentifier;
  private String projectIdentifier;
  private String accountId;
  private String groupName1;
  private String feature;
  private String connectorIdentifier;
  private String identifier;
  private String name;
  private String monitoredServiceIdentifier;
  private List<QueryDefinition> queryDefinitions;
  BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    feature = "SignalFX Metrics";
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName1 = "g1";
    queryDefinitions = new ArrayList<>();
    QueryDefinition queryDefinition1 = createSignalFXQueryDefinition(groupName1);
    queryDefinitions.add(queryDefinition1);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withoutHostCollection() {
    NextGenMetricCVConfig cvConfig =
        (NextGenMetricCVConfig) createCVConfig(groupName1, DataSourceType.SPLUNK_SIGNALFX_METRICS);
    cvConfig.addMetricPackAndInfo(queryDefinitions);
    populateBasicDetails(cvConfig);
    SignalFXMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag()).isNull();
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo_withHostCollection() {
    queryDefinitions.get(0).setQueryParams(QueryParamsDTO.builder().serviceInstanceField("container.id").build());
    NextGenMetricCVConfig cvConfig =
        (NextGenMetricCVConfig) createCVConfig(groupName1, DataSourceType.SPLUNK_SIGNALFX_METRICS);
    cvConfig.addMetricPackAndInfo(queryDefinitions);
    populateBasicDetails(cvConfig);
    SignalFXMetricDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig);
    assertCommons(dataCollectionInfo);
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag()).isNotNull();
    assertThat(dataCollectionInfo.getMetricDefinitions().get(0).getServiceInstanceIdentifierTag())
        .isEqualTo("container.id");
  }

  private void assertCommons(SignalFXMetricDataCollectionInfo info) {
    assertThat(info.getGroupName()).isEqualTo(groupName1);
    assertThat(info.getMetricDefinitions().get(0).getMetricName()).isEqualTo(queryDefinitions.get(0).getName());
    assertThat(info.getMetricDefinitions().get(0).getMetricIdentifier())
        .isEqualTo(queryDefinitions.get(0).getIdentifier());
    assertThat(info.getMetricDefinitions().get(0).getQuery()).isEqualTo(queryDefinitions.get(0).getQuery());
  }

  private CVConfig createCVConfig(String groupName, DataSourceType dataSourceType) {
    return builderFactory.nextGenMetricCVConfigBuilder(dataSourceType)
        .groupName(groupName)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .productName(feature)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }

  private QueryDefinition createSignalFXQueryDefinition(String group) {
    String metricDefinitionIdentifier = generateUuid();
    return QueryDefinition.builder()
        .query("query")
        .continuousVerificationEnabled(false)
        .name("metric_name")
        .identifier(metricDefinitionIdentifier)
        .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
        .groupName(group)
        .build();
  }

  private void populateBasicDetails(CVConfig cvConfig) {
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setProjectIdentifier(projectIdentifier);
  }
}
