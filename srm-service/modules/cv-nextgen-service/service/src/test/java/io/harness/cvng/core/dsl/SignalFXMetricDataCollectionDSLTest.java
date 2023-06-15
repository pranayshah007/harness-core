/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SignalFXMetricDataCollectionInfo;
import io.harness.cvng.beans.signalfx.SignalFXMetricSampleDataRequest;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.cvng.core.services.impl.SignalFXMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SignalFXMetricDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  private static final int THREADS = 10;
  private static final long FROM_EPOCH_TIME = 1681699563000L;
  private static final long TO_EPOCH_TIME = 1681702563000L;
  private static final String TOKEN_REF_DATA = "dummyToken";
  private SignalFXMetricDataCollectionInfo dataCollectionInfo;
  private static final String SIGNALFX_BASE_URL = "https://stream.us1.signalfx.com/";
  private static final int RESPONSE_SIZE_METRIC_SAMPLE = 20;
  BuilderFactory builderFactory;

  MetricPack metricPack;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;

  ExecutorService executorService;
  @Inject MetricPackService metricPackService;

  @Inject SignalFXMetricDataCollectionInfoMapper dataCollectionInfoMapper;
  DataCollectionDSLService dataCollectionDSLService;
  SignalFXConnectorDTO signalFXConnectorDTO;
  String name;
  String identifier;
  @Before
  public void setup() throws IOException {
    builderFactory = BuilderFactory.getDefault();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());

    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    name = "metric-1";
    identifier = "m1";
    String query = "data(\"k8s.container.memory_request\")";
    String groupName = "g1";
    signalFXConnectorDTO =
        SignalFXConnectorDTO.builder()
            .url(SIGNALFX_BASE_URL)
            .apiTokenRef(SecretRefData.builder().decryptedValue(TOKEN_REF_DATA.toCharArray()).build())
            .build();
    String serviceInstancePath = "container.id";
    List<SignalFXMetricDataCollectionInfo.MetricCollectionInfo> metricInfoDTOs = new ArrayList<>();
    SignalFXMetricDataCollectionInfo.MetricCollectionInfo infoDTO1 =
        SignalFXMetricDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName(name)
            .metricIdentifier(identifier)
            .query(query)
            .serviceInstanceIdentifierTag(serviceInstancePath)
            .build();
    SignalFXMetricDataCollectionInfo.MetricCollectionInfo infoDTO2 =
        SignalFXMetricDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName(name + "2")
            .metricIdentifier(identifier + "2")
            .query(query)
            .serviceInstanceIdentifierTag(serviceInstancePath)
            .build();
    metricInfoDTOs.add(infoDTO1);
    metricInfoDTOs.add(infoDTO2);
    dataCollectionInfo =
        SignalFXMetricDataCollectionInfo.builder().groupName(groupName).metricDefinitions(metricInfoDTOs).build();
    metricPack = createMetricPack(
        Collections.singleton(
            MetricPack.MetricDefinition.builder().identifier(identifier).name(name).included(true).build()),
        CVNextGenConstants.CUSTOM_PACK_IDENTIFIER, CVMonitoringCategory.ERRORS);
    metricPackService.populateDataCollectionDsl(DataSourceType.SPLUNK_SIGNALFX_METRICS, metricPack);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_signalfxDSLSLO() throws IOException {
    Instant instant = Instant.ofEpochMilli(1681702563000L);
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.SPLUNK_SIGNALFX_METRICS);

    NextGenMetricCVConfig nextGenMetricCVConfig =
        builderFactory.nextGenMetricCVConfigBuilder(DataSourceType.SPLUNK_SIGNALFX_METRICS)
            .metricInfos(Collections.singletonList(NextGenMetricInfo.builder()
                                                       .query("data(\"k8s.container.memory_request\")")
                                                       .identifier("memory_request")
                                                       .metricName("memory_request")
                                                       .build()))
            .build();
    nextGenMetricCVConfig.setMetricPack(metricPacks.get(0));
    nextGenMetricCVConfig.setGroupName("default");
    metricPackService.populateDataCollectionDsl(nextGenMetricCVConfig.getType(), metricPacks.get(0));
    SignalFXMetricDataCollectionInfo signalFXMetricDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(nextGenMetricCVConfig, VerificationTask.TaskType.SLI);
    Map<String, Object> params = signalFXMetricDataCollectionInfo.getDslEnvVariables(signalFXConnectorDTO);

    Map<String, String> headers = new HashMap<>();
    headers.putAll(signalFXMetricDataCollectionInfo.collectionHeaders(signalFXConnectorDTO));
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(Instant.ofEpochMilli(1681699563000L))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(SIGNALFX_BASE_URL)
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        nextGenMetricCVConfig.getDataCollectionDsl(), runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("expected-signalfx-metric-sample-dsl-output.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecuteSignalFXMetricSampleData() throws IOException {
    String metricSampleDataRequestDSL = MetricPackServiceImpl.SIGNALFX_METRIC_SAMPLE_DSL;
    SignalFXMetricSampleDataRequest signalFXMetricSampleDataRequest =
        SignalFXMetricSampleDataRequest.builder()
            .query("data(\"k8s.container.memory_request\")")
            .from(FROM_EPOCH_TIME)
            .to(TO_EPOCH_TIME)
            .dsl(metricSampleDataRequestDSL)
            .connectorInfoDTO(ConnectorInfoDTO.builder().connectorConfig(signalFXConnectorDTO).build())
            .build();
    Map<String, Object> params = signalFXMetricSampleDataRequest.fetchDslEnvVariables();

    Map<String, String> headers = new HashMap<>(signalFXMetricSampleDataRequest.collectionHeaders());
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(Instant.ofEpochMilli(FROM_EPOCH_TIME))
                                              .endTime(Instant.ofEpochMilli(TO_EPOCH_TIME))
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(SIGNALFX_BASE_URL)
                                              .build();
    ArrayList response = (ArrayList) dataCollectionDSLService.execute(
        metricSampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(response).hasSize(RESPONSE_SIZE_METRIC_SAMPLE);
    JsonNode obj1 = (JsonNode) response.get(0);
    JsonNode jsonNode = JsonUtils.readTree(readJson("expected-raw-first-object-sample-dsl.json"));
    assertThat(obj1.get(0)).isEqualTo(jsonNode.get(0));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCollectDataWithoutHostData() {
    dataCollectionInfo.setCollectHostData(false);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(Instant.ofEpochMilli(1681699563000L))
            .endTime(Instant.ofEpochMilli(1681702563000L))
            .commonHeaders(dataCollectionInfo.collectionHeaders(signalFXConnectorDTO))
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(signalFXConnectorDTO))
            .baseUrl(dataCollectionInfo.getBaseUrl(signalFXConnectorDTO))
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(86);
    TimeSeriesRecord firstTimeSeriesRecord = timeSeriesRecords.get(0);
    assertThat(firstTimeSeriesRecord.getTxnName()).isEqualTo("g1");
    assertThat(firstTimeSeriesRecord.getHostname()).isNull();
    assertThat(firstTimeSeriesRecord.getMetricValue()).isEqualTo(2.3068672E8, offset(0.0001d));
    assertThat(firstTimeSeriesRecord.getTimestamp()).isEqualTo(1681699920000L);
    assertThat(firstTimeSeriesRecord.getMetricName()).isEqualTo(name);
    assertThat(firstTimeSeriesRecord.getMetricIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCollectDataWithHostData() {
    dataCollectionInfo.setCollectHostData(true);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(Instant.ofEpochMilli(1681699563000L))
            .endTime(Instant.ofEpochMilli(1681702563000L))
            .commonHeaders(dataCollectionInfo.collectionHeaders(signalFXConnectorDTO))
            .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(signalFXConnectorDTO))
            .baseUrl(dataCollectionInfo.getBaseUrl(signalFXConnectorDTO))
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        metricPack.getDataCollectionDsl(), runtimeParameters, callDetails -> {});
    assertThat(timeSeriesRecords).hasSize(590);
    Map<String, List<TimeSeriesRecord>> hostRecords =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(hostRecords.keySet()).hasSize(16);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isNotNull();
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo(name);
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo(identifier);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(new File(getResourceFilePath("signalfx/" + name)), StandardCharsets.UTF_8);
  }

  private MetricPack createMetricPack(
      Set<MetricPack.MetricDefinition> metricDefinitions, String identifier, CVMonitoringCategory category) {
    return MetricPack.builder()
        .accountId(accountId)
        .category(category)
        .identifier(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .dataSourceType(DataSourceType.SPLUNK_SIGNALFX_METRICS)
        .metrics(metricDefinitions)
        .build();
  }
}
