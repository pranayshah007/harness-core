/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

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
import io.harness.cvng.core.beans.healthsource.QueryParams;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NextGenHealthSourceSpecTest extends CvNextGenTestBase {
  public static final String SERVICE_INSTANCE_LABEL_EMPTY_ERROR_MESSAGE =
      "Service instance label/key/path shouldn't be empty for Deployment Verification";
  NextGenHealthSourceSpec nextGenHealthSourceSpec;
  @Inject MetricPackService metricPackService;
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
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
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    List<TimeSeriesMetricPackDTO> metricThresholds = Collections.singletonList(
        TimeSeriesMetricPackDTO.builder().identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER).build());
    List<QueryDefinition> queryDefinitions = new ArrayList<>();
    QueryDefinition queryDefinition1 = createNextGenMetricDefinition("g1");
    queryDefinition1.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    queryDefinition1.setContinuousVerificationEnabled(false);
    QueryDefinition queryDefinition2 = createNextGenMetricDefinition("g1");
    queryDefinition2.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.ERRORS).build());
    queryDefinition2.setContinuousVerificationEnabled(false);
    QueryDefinition queryDefinition3 = createNextGenMetricDefinition("g2");
    queryDefinition3.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build());
    queryDefinition3.setContinuousVerificationEnabled(false);
    QueryDefinition queryDefinition4 = createNextGenMetricDefinition("g2");
    queryDefinition4.setRiskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build());
    queryDefinition4.setContinuousVerificationEnabled(false);
    queryDefinitions.add(queryDefinition1);
    queryDefinitions.add(queryDefinition2);
    queryDefinitions.add(queryDefinition3);
    queryDefinitions.add(queryDefinition4);
    nextGenHealthSourceSpec = NextGenHealthSourceSpec.builder()
                                  .connectorRef(connectorIdentifier)
                                  .dataSourceType(DataSourceType.SUMOLOGIC_METRICS)
                                  .queryDefinitions(queryDefinitions)
                                  .metricPacks(new HashSet<>(metricThresholds))
                                  .build();
    // TODO add the tests for logs also.
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_noExistingConfigs() {
    HealthSource.CVConfigUpdateResult result = nextGenHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier, identifier, name,
        Collections.emptyList(), metricPackService);
    Map<CVMonitoringCategory, NextGenMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, NextGenMetricCVConfig.class ::cast, (u, v) -> v));
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
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withDifferentExistingConfigs() {
    QueryDefinition metricDefinition1 =
        nextGenHealthSourceSpec.getQueryDefinitions()
            .stream()
            .filter(
                metricDefinition -> metricDefinition.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .orElseThrow();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricPack.MetricDefinition.builder()
                                                                       .identifier(metricDefinition1.getIdentifier())
                                                                       .name(metricDefinition1.getName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, metricDefinition1.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g3", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    HealthSource.CVConfigUpdateResult result =
        nextGenHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    Map<CVMonitoringCategory, NextGenMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, NextGenMetricCVConfig.class ::cast, (u, v) -> v));
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
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withSimilarExistingConfigs() {
    QueryDefinition queryDefinitionWithErrorCategory =
        nextGenHealthSourceSpec.getQueryDefinitions()
            .stream()
            .filter(queryDefinitionWithError
                -> queryDefinitionWithError.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .orElseThrow();
    List<CVConfig> existingCVConfigs = new ArrayList<>();
    MetricPack metricPack =
        createMetricPack(Collections.singleton(MetricPack.MetricDefinition.builder()
                                                   .identifier(queryDefinitionWithErrorCategory.getIdentifier())
                                                   .name(queryDefinitionWithErrorCategory.getName())
                                                   .included(true)
                                                   .build()),
            CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, queryDefinitionWithErrorCategory.getRiskProfile().getCategory());
    existingCVConfigs.add(createCVConfig("g1", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    HealthSource.CVConfigUpdateResult result =
        nextGenHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, existingCVConfigs, metricPackService);
    Map<CVMonitoringCategory, NextGenMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, NextGenMetricCVConfig.class ::cast, (u, v) -> v));
    // Key and updated relation.TODO
    assertThat(result.getUpdated()).hasSize(1);
    assertThat(result.getDeleted()).hasSize(0);
    assertThat(map).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon((NextGenMetricCVConfig) result.getUpdated().get(0));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNewConfigs_withSimilarAndDifferentExistingConfigs() {
    QueryDefinition queryDefinition =
        nextGenHealthSourceSpec.getQueryDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .orElseThrow();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricPack.MetricDefinition.builder()
                                                                       .identifier(queryDefinition.getIdentifier())
                                                                       .name(queryDefinition.getName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, queryDefinition.getRiskProfile().getCategory());
    cvConfigs.add(createCVConfig("g1", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    cvConfigs.add(createCVConfig("g3", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    HealthSource.CVConfigUpdateResult result =
        nextGenHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    Map<CVMonitoringCategory, NextGenMetricCVConfig> map = result.getAdded().stream().collect(
        Collectors.toMap(CVConfig::getCategory, NextGenMetricCVConfig.class ::cast, (u, v) -> v));
    assertThat(result.getUpdated()).hasSize(1);
    assertThat(result.getDeleted()).hasSize(1);
    assertThat(map).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.PERFORMANCE).getGroupName()).isEqualTo("g1");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getGroupName()).isEqualTo("g2");
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricInfos()).hasSize(2);
    assertThat(map.get(CVMonitoringCategory.INFRASTRUCTURE).getMetricPack().getMetrics()).hasSize(2);
    assertCommon((NextGenMetricCVConfig) result.getUpdated().get(0));
    assertCommon((NextGenMetricCVConfig) result.getDeleted().get(0));
    assertCommon((NextGenMetricCVConfig) result.getAdded().get(0));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_addingNoConfigs_withExistingConfigs() {
    QueryDefinition queryDefinition =
        nextGenHealthSourceSpec.getQueryDefinitions()
            .stream()
            .filter(md -> md.getRiskProfile().getCategory().equals(CVMonitoringCategory.ERRORS))
            .findFirst()
            .orElseThrow();
    List<CVConfig> cvConfigs = new ArrayList<>();
    MetricPack metricPack = createMetricPack(Collections.singleton(MetricPack.MetricDefinition.builder()
                                                                       .identifier(queryDefinition.getIdentifier())
                                                                       .name(queryDefinition.getName())
                                                                       .included(true)
                                                                       .build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, queryDefinition.getRiskProfile().getCategory());
    nextGenHealthSourceSpec.setQueryDefinitions(Collections.emptyList());
    cvConfigs.add(createCVConfig("g1", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    cvConfigs.add(createCVConfig("g3", metricPack, DataSourceType.SUMOLOGIC_METRICS));
    HealthSource.CVConfigUpdateResult result =
        nextGenHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, cvConfigs, metricPackService);
    assertThat(result.getUpdated()).hasSize(0);
    assertThat(result.getDeleted()).hasSize(2);
    assertThat(result.getAdded()).hasSize(0);
    assertCommon((NextGenMetricCVConfig) result.getDeleted().get(0));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testValidate_serviceJsonInstancePathIsEmpty() {
    QueryDefinition queryDefinition = nextGenHealthSourceSpec.getQueryDefinitions().get(0);
    queryDefinition.setContinuousVerificationEnabled(true);
    queryDefinition.setQueryParams(QueryParams.builder().serviceInstanceField("").build());
    assertThatThrownBy(() -> nextGenHealthSourceSpec.validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SERVICE_INSTANCE_LABEL_EMPTY_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testValidate_serviceJsonInstancePathIsNull() {
    QueryDefinition queryDefinition = nextGenHealthSourceSpec.getQueryDefinitions().get(0);
    queryDefinition.setContinuousVerificationEnabled(true);
    queryDefinition.setQueryParams(QueryParams.builder().serviceInstanceField(null).build());
    assertThatThrownBy(() -> nextGenHealthSourceSpec.validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(SERVICE_INSTANCE_LABEL_EMPTY_ERROR_MESSAGE);
  }

  /*  @Test
    @Owner(developers = ANSUMAN)
    @Category(UnitTests.class)
    public void testValidate_metricResponseMappingIsNull() {
      QueryDefinition metricDefinition = nextGenHealthSourceSpec.getQueryDefinitions().get(0);
      metricDefinition.setContinuousVerificationEnabled(true);
      metricDefinition.setQueryParams(null);
      assertThatThrownBy(() -> nextGenHealthSourceSpec.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(SERVICE_INSTANCE_LABEL_EMPTY_ERROR_MESSAGE);
    }*/

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testValidate_serviceJsonInstancePathIsPresent() {
    QueryDefinition metricDefinition = nextGenHealthSourceSpec.getQueryDefinitions().get(0);
    metricDefinition.setContinuousVerificationEnabled(true);
    metricDefinition.setQueryParams(QueryParams.builder().serviceInstanceField("path").build());
    nextGenHealthSourceSpec.validate();
    assertThat(true).isTrue();
  }

  /*  @Test
    @Owner(developers = ANSUMAN)
    @Category(UnitTests.class)
    public void testValidate_metricIdentifierDoesNotMatchExpectedPattern() {
      QueryDefinition queryDefinition = nextGenHealthSourceSpec.getQueryDefinitions().get(0);
      queryDefinition.setIdentifier("Sumologic Metric-1");
      // TODO fix the regex.
      assertThatThrownBy(() -> nextGenHealthSourceSpec.validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Metric identifier does not match the expected pattern: " +
    CloudWatchUtils.METRIC_QUERY_IDENTIFIER_REGEX);
    }*/

  private void assertCommon(NextGenMetricCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(cvConfig.getIdentifier()).isEqualTo(identifier);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(name);
    assertThat(cvConfig.getMetricPack().getAccountId()).isEqualTo(accountId);
    assertThat(cvConfig.getMetricPack().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(cvConfig.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.SUMOLOGIC_METRICS);
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  private CVConfig createCVConfig(String groupName, MetricPack metricPack, DataSourceType dataSourceType) {
    return builderFactory.nextGenMetricCVConfigBuilder(dataSourceType)
        .groupName(groupName)
        .metricPack(metricPack)
        .category(metricPack.getCategory())
        .connectorIdentifier(connectorIdentifier)
        .monitoringSourceName(name)
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .identifier(identifier)
        .build();
  }
  private MetricPack createMetricPack(
      Set<MetricPack.MetricDefinition> metricDefinitions, String identifier, CVMonitoringCategory category) {
    return MetricPack.builder()
        .accountId(accountId)
        .category(category)
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(DataSourceType.SUMOLOGIC_METRICS)
        .metrics(metricDefinitions)
        .build();
  }
  private QueryDefinition createNextGenMetricDefinition(String group) {
    String identifier = generateUuid();
    return QueryDefinition.builder()
        .query(identifier)
        .name(identifier)
        .identifier("identifier")
        .groupName(group)
        .build();
  }
}
