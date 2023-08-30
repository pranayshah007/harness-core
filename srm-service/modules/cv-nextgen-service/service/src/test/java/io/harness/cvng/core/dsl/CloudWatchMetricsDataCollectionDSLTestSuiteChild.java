/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricResponseMappingDTO;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
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

public class CloudWatchMetricsDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  @Inject private MetricPackService metricPackService;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private ExecutorService executorService;
  private BuilderFactory builderFactory;

  String name;
  String region;
  String groupName;
  String expression;
  String identifier;
  String serviceInstancePath;
  String accessKey;
  String secretKey;
  CloudWatchMetricDataCollectionInfo dataCollectionInfo;
  AwsConnectorDTO testAwsConnector;
  MetricPack metricPack;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    name = "metric-1";
    region = "us-east-1";
    groupName = "g1";
    expression = "SELECT AVG(CPUUtilization) FROM SCHEMA(\"AWS/EC2\", InstanceId)";
    identifier = "m1";
    serviceInstancePath = "InstanceId";
    accessKey = "***";
    secretKey = "***";
    List<CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO> metricInfoDTOs = new ArrayList<>();
    CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO infoDTO1 =
        CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO.builder()
            .metricName(name)
            .metricIdentifier(identifier)
            .expression(expression)
            .finalExpression(expression)
            .responseMapping(MetricResponseMappingDTO.builder().serviceInstanceJsonPath(serviceInstancePath).build())
            .build();
    CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO infoDTO2 =
        CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO.builder()
            .metricName(name + "2")
            .metricIdentifier(identifier + "2")
            .expression(expression)
            .finalExpression(expression)
            .responseMapping(MetricResponseMappingDTO.builder().serviceInstanceJsonPath(serviceInstancePath).build())
            .build();
    metricInfoDTOs.add(infoDTO1);
    metricInfoDTOs.add(infoDTO2);
    dataCollectionInfo = CloudWatchMetricDataCollectionInfo.builder()
                             .region(region)
                             .groupName(groupName)
                             .metricInfos(metricInfoDTOs)
                             .metricPack(null)
                             .build();
    testAwsConnector =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey(accessKey)
                                .secretKeyRef(SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                                .build())
                    .build())
            .build();
    metricPack = createMetricPack(
        Collections.singleton(
            MetricPack.MetricDefinition.builder().identifier(identifier).name(name).included(true).build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, CVMonitoringCategory.ERRORS);
    metricPackService.populateDataCollectionDsl(DataSourceType.CLOUDWATCH_METRICS, metricPack);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectData_withoutHostData() {
    dataCollectionInfo.setCollectHostData(false);
    Instant instant = Instant.ofEpochMilli(1663333097219L);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minusSeconds(3600))
            .endTime(instant)
            .commonHeaders(dataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl(dataCollectionInfo.getBaseUrl(testAwsConnector))
            .build();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(72);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo(name);
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectData_withHostData() {
    dataCollectionInfo.setCollectHostData(true);
    Instant instant = Instant.ofEpochMilli(1663333097219L);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minusSeconds(3600))
            .endTime(instant)
            .commonHeaders(dataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl(dataCollectionInfo.getBaseUrl(testAwsConnector))
            .build();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(120);
    Map<String, List<TimeSeriesRecord>> hostRecords =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(hostRecords.keySet()).hasSize(5);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo(name);
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectData_withNoRequestBodies() {
    dataCollectionInfo.setCollectHostData(true);
    Instant instant = Instant.ofEpochMilli(1663333097219L);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minusSeconds(3600))
            .endTime(instant)
            .commonHeaders(dataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl(dataCollectionInfo.getBaseUrl(testAwsConnector))
            .build();
    runtimeParameters.getOtherEnvVariables().put("bodies", null);
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(0);
  }

  private MetricPack createMetricPack(
      Set<MetricPack.MetricDefinition> metricDefinitions, String identifier, CVMonitoringCategory category) {
    return MetricPack.builder()
        .accountId(accountId)
        .category(category)
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(DataSourceType.CLOUDWATCH_METRICS)
        .metrics(metricDefinitions)
        .build();
  }
}