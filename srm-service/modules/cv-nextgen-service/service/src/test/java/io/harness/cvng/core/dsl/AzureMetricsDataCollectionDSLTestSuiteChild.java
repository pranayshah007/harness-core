/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.KARAN_SARASWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.AzureMetricsDataCollectionInfo;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.azure.AzureMetricsSampleDataRequest;
import io.harness.cvng.beans.azure.AzureServiceInstanceFieldDataRequest;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.AzureMetricsDataCollectionInfoMapper;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.utils.AggregationType;
import io.harness.cvng.utils.AzureUtils;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureMetricsDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  private static final int THREADS = 10;
  private final String metricName = "node_cpu_usage_millicores";
  private final String metricNamespace = "microsoft.containerservice/managedclusters";
  private final Instant startTime = Instant.ofEpochMilli(1689279600000L);
  private final Instant endTime = Instant.ofEpochMilli(1689280200000L);
  private final String resourceId =
      "/subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319/resourceGroups/srm-test/providers/Microsoft.ContainerService/managedClusters/srm-test";

  BuilderFactory builderFactory;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;

  String name;
  String identifier;
  String groupName;
  AzureMetricsDataCollectionInfo dataCollectionInfo;
  MetricPack metricPack;
  String serviceInstancePath;
  String healthSourceMetricNamespace;
  String healthSourceMetricName;
  AzureConnectorDTO azureConnectorDTO;

  ConnectorInfoDTO connectorInfoDTO =
      ConnectorInfoDTO.builder()
          .connectorConfig(
              AzureConnectorDTO.builder()
                  .credential(
                      AzureCredentialDTO.builder()
                          .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                          .config(AzureManualDetailsDTO.builder()
                                      .clientId("***")
                                      .tenantId("b229b2bb-5f33-4d22-bce0-730f6474e906")
                                      .authDTO(AzureAuthDTO.builder()
                                                   .azureSecretType(AzureSecretType.SECRET_KEY)
                                                   .credentials(AzureClientSecretKeyDTO.builder()
                                                                    .secretKey(SecretRefData.builder()
                                                                                   .decryptedValue("***".toCharArray())
                                                                                   .build())
                                                                    .build())
                                                   .build())
                                      .build())
                          .build())
                  .build())
          .build();

  @Inject MetricPackService metricPackService;
  @Inject AzureMetricsDataCollectionInfoMapper dataCollectionInfoMapper;
  DataCollectionDSLService dataCollectionDSLService;

  @Before
  public void setup() throws IOException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());

    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    azureConnectorDTO = (AzureConnectorDTO) connectorInfoDTO.getConnectorConfig();
    name = "metric-1";
    groupName = "g1";
    identifier = "m1";
    serviceInstancePath = "node";
    healthSourceMetricNamespace = "microsoft.containerservice/managedclusters";
    healthSourceMetricName = "node_cpu_usage_millicores";
    List<AzureMetricsDataCollectionInfo.MetricCollectionInfo> metricInfoDTOs = new ArrayList<>();
    AzureMetricsDataCollectionInfo.MetricCollectionInfo infoDTO1 =
        AzureMetricsDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName(name)
            .metricIdentifier(identifier)
            .resourceId(resourceId)
            .serviceInstanceIdentifierTag(serviceInstancePath)
            .aggregationType("average")
            .healthSourceMetricNamespace(healthSourceMetricNamespace)
            .healthSourceMetricName(healthSourceMetricName)
            .build();
    AzureMetricsDataCollectionInfo.MetricCollectionInfo infoDTO2 =
        AzureMetricsDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName(name + "2")
            .metricIdentifier(identifier + "2")
            .resourceId(resourceId)
            .serviceInstanceIdentifierTag(serviceInstancePath)
            .aggregationType("maximum")
            .healthSourceMetricNamespace(healthSourceMetricNamespace)
            .healthSourceMetricName(healthSourceMetricName)
            .build();
    metricInfoDTOs.add(infoDTO1);
    metricInfoDTOs.add(infoDTO2);

    dataCollectionInfo =
        AzureMetricsDataCollectionInfo.builder().groupName(groupName).metricDefinitions(metricInfoDTOs).build();
    metricPack = createMetricPack(Collections.singleton(
        MetricPack.MetricDefinition.builder().identifier(identifier).name(name).included(true).build()));
    metricPackService.populateDataCollectionDsl(DataSourceType.AZURE_METRICS, metricPack);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testExecute_AzureMetrics_DSL_getSampleData() {
    String sampleDataRequestDSL = MetricPackServiceImpl.AZURE_METRICS_SAMPLE_DATA_DSL;
    AzureMetricsSampleDataRequest azureMetricsSampleDataRequest = AzureMetricsSampleDataRequest.builder()
                                                                      .metricNamespace(metricNamespace)
                                                                      .metricName(metricName)
                                                                      .aggregationType("average")
                                                                      .from(startTime)
                                                                      .to(endTime)
                                                                      .dsl(sampleDataRequestDSL)
                                                                      .resourceId(resourceId)
                                                                      .connectorInfoDTO(connectorInfoDTO)
                                                                      .build();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(azureMetricsSampleDataRequest.getFrom())
                                              .endTime(azureMetricsSampleDataRequest.getTo())
                                              .otherEnvVariables(azureMetricsSampleDataRequest.fetchDslEnvVariables())
                                              .commonHeaders(azureMetricsSampleDataRequest.collectionHeaders())
                                              .baseUrl(azureMetricsSampleDataRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(
        sampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(10);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testExecute_AzureMetrics_DSL_getServiceInstanceFieldData() {
    String serviceInstanceFieldDsl = MetricPackServiceImpl.AZURE_SERVICE_INSTANCE_FIELD_DSL;
    AzureServiceInstanceFieldDataRequest azureServiceInstanceFieldDataRequest =
        AzureServiceInstanceFieldDataRequest.builder()
            .connectorInfoDTO(connectorInfoDTO)
            .resourceId(resourceId)
            .metricNamespace(metricNamespace)
            .metricName(metricName)
            .dsl(serviceInstanceFieldDsl)
            .build();
    Instant instant = Instant.parse("2023-07-15T10:45:48.164Z");
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(azureServiceInstanceFieldDataRequest.getStartTime(instant))
            .endTime(azureServiceInstanceFieldDataRequest.getEndTime(instant))
            .commonHeaders(azureServiceInstanceFieldDataRequest.collectionHeaders())
            .otherEnvVariables(azureServiceInstanceFieldDataRequest.fetchDslEnvVariables())
            .baseUrl(azureServiceInstanceFieldDataRequest.getBaseUrl())
            .build();
    List<?> result =
        (List<?>) dataCollectionDSLService.execute(serviceInstanceFieldDsl, runtimeParameters, callDetails -> {});
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testExecute_AzureMetrics_DSL() {
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.AZURE_METRICS);

    NextGenMetricCVConfig nextGenMetricCVConfig =
        builderFactory.nextGenMetricCVConfigBuilder(DataSourceType.AZURE_METRICS)
            .metricInfos(
                Collections.singletonList(NextGenMetricInfo.builder()
                                              .queryParams(QueryParams.builder()
                                                               .index(resourceId)
                                                               .serviceInstanceField(serviceInstancePath)
                                                               .aggregationType(AggregationType.AVERAGE)
                                                               .healthSourceMetricNamespace(healthSourceMetricNamespace)
                                                               .healthSourceMetricName(healthSourceMetricName)
                                                               .build())
                                              .identifier("cpu_usage")
                                              .metricName("cpu_usage")
                                              .build()))
            .build();
    nextGenMetricCVConfig.setMetricPack(metricPacks.get(0));
    nextGenMetricCVConfig.setGroupName("default");
    metricPackService.populateDataCollectionDsl(nextGenMetricCVConfig.getType(), metricPacks.get(0));
    AzureMetricsDataCollectionInfo azureMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(nextGenMetricCVConfig, VerificationTask.TaskType.SLI);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(startTime)
            .endTime(endTime)
            .commonHeaders(AzureUtils.collectionHeaders())
            .otherEnvVariables(azureMetricsDataCollectionInfo.getDslEnvVariables(azureConnectorDTO))
            .baseUrl(AzureUtils.getBaseUrl(VerificationType.TIME_SERIES))
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        nextGenMetricCVConfig.getDataCollectionDsl(), runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(timeSeriesRecords).hasSize(10);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCollectData_withoutHostData() {
    dataCollectionInfo.setCollectHostData(false);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(startTime)
            .endTime(endTime.minus(Duration.ofMinutes(5)))
            .commonHeaders(AzureUtils.collectionHeaders())
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(azureConnectorDTO))
            .baseUrl(AzureUtils.getBaseUrl(VerificationType.TIME_SERIES))
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(10);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo(groupName);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(startTime.toEpochMilli());
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo(name);
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCollectData_withHostData() {
    dataCollectionInfo.setCollectHostData(true);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(startTime)
            .endTime(endTime.minus(Duration.ofMinutes(5)))
            .commonHeaders(AzureUtils.collectionHeaders())
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(azureConnectorDTO))
            .baseUrl(AzureUtils.getBaseUrl(VerificationType.TIME_SERIES))
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(30);
    Map<String, List<TimeSeriesRecord>> hostRecords =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(hostRecords.keySet()).hasSize(3);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo(groupName);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(startTime.toEpochMilli());
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo(name);
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo(identifier);
  }

  private MetricPack createMetricPack(Set<MetricPack.MetricDefinition> metricDefinitions) {
    return MetricPack.builder()
        .accountId(accountId)
        .category(CVMonitoringCategory.ERRORS)
        .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(DataSourceType.AZURE_METRICS)
        .metrics(metricDefinitions)
        .build();
  }
}
