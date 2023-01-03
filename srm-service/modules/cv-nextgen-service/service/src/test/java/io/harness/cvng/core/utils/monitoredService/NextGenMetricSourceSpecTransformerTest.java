/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NextGenMetricSourceSpecTransformerTest extends CvNextGenTestBase {
  String connectorIdentifier;
  String projectIdentifier;
  String accountId;
  String identifier;
  String orgIdentifier;
  String groupName;
  String query;
  String name;
  String monitoredServiceIdentifier;
  BuilderFactory builderFactory;
  List<QueryDefinition> queryDefinitions;

  @Inject NextGenMetricSourceSpecTransformer nextGenMetricSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    groupName = "g1";
    query = "expression";
    queryDefinitions = new ArrayList<>();
    QueryDefinition queryDefinition1 = createSumologicQueryDefinition(groupName);
    queryDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    queryDefinition1.setContinuousVerificationEnabled(true);
    queryDefinitions.add(queryDefinition1);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_preconditionDifferentConnector() {
    List<NextGenMetricCVConfig> cvConfigs = new ArrayList<>();
    NextGenMetricCVConfig cvConfig1 =
        (NextGenMetricCVConfig) createCVConfig(connectorIdentifier, DataSourceType.SUMOLOGIC_METRICS);
    NextGenMetricCVConfig cvConfig2 =
        (NextGenMetricCVConfig) createCVConfig(connectorIdentifier + "1", DataSourceType.SUMOLOGIC_METRICS);
    cvConfigs.add(cvConfig1);
    cvConfigs.add(cvConfig2);
    assertThatThrownBy(() -> nextGenMetricSourceSpecTransformer.transform(cvConfigs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ConnectorRef should be same for List of all configs.");
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    queryDefinitions.get(0).setQueryParams(QueryParamsDTO.builder().serviceInstanceField("path").build());
    List<NextGenMetricCVConfig> cvConfigs = new ArrayList<>();
    NextGenMetricCVConfig cvConfig1 =
        (NextGenMetricCVConfig) createCVConfig(connectorIdentifier, DataSourceType.SUMOLOGIC_METRICS);
    cvConfig1.addMetricPackAndInfo(queryDefinitions);
    populateBasicDetails(cvConfig1);
    cvConfigs.add(cvConfig1);

    NextGenHealthSourceSpec NextGenHealthSourceSpec = nextGenMetricSourceSpecTransformer.transform(cvConfigs);

    assertThat(NextGenHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().size()).isEqualTo(1);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getName()).isEqualTo(name);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getIdentifier()).isEqualTo(identifier);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getGroupName()).isEqualTo(groupName);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getQuery()).isEqualTo(query);
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getQueryParams().getServiceInstanceField())
        .isEqualTo("path");
    assertThat(NextGenHealthSourceSpec.getQueryDefinitions().get(0).getContinuousVerificationEnabled()).isTrue();
  }

  private CVConfig createCVConfig(String connectorIdentifier, DataSourceType dataSourceType) {
    return builderFactory.nextGenMetricCVConfigBuilder(dataSourceType)
        .groupName(groupName)
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }

  private QueryDefinition createSumologicQueryDefinition(String group) {
    return QueryDefinition.builder().query(query).name(name).identifier(identifier).groupName(group).build();
  }

  private void populateBasicDetails(CVConfig cvConfig) {
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setProjectIdentifier(projectIdentifier);
  }
}
