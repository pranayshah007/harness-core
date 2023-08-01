/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.DatadogMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogMetricDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  private static final String BASE_URL = "https://app.datadoghq.com/api/";

  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private DatadogMetricDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;

  @Before
  public void setup() throws IOException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_datadogDSLWithHostData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-02-14T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.DATADOG_METRICS);

    DatadogMetricCVConfig datadogMetricCVConfig =
        createAndpopulateCVConfig("docker.cpu.usage{cluster-name:chi-play}.rollup(avg, 60)",
            "docker.cpu.usage{cluster-name:chi-play} by {pod_name}.rollup(avg, 60)");

    datadogMetricCVConfig.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfig, TaskType.LIVE_MONITORING);
    datadogMetricsDataCollectionInfo.setCollectHostData(false);
    DatadogConnectorDTO datadogConnectorDTO = getDatadogConnectorDTO();
    Map<String, Object> params = datadogMetricsDataCollectionInfo.getDslEnvVariables(datadogConnectorDTO);

    Map<String, String> headers = datadogMetricsDataCollectionInfo.collectionHeaders(datadogConnectorDTO);
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(5)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(BASE_URL)
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, System.out::println);
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-datadog-dsl-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
    DatadogMetricCVConfig datadogMetricCVConfigWithNull =
        createAndpopulateCVConfig("docker.cpu.usage{cluster-null:null}.rollup(avg, 60)",
            "docker.cpu.usage{cluster-null:null} by {pod_name}.rollup(avg, 60)");
    datadogMetricCVConfigWithNull.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfoNull =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfigWithNull, TaskType.LIVE_MONITORING);
    datadogMetricsDataCollectionInfoNull.setCollectHostData(false);
    params = datadogMetricsDataCollectionInfoNull.getDslEnvVariables(datadogConnectorDTO);
    headers = datadogMetricsDataCollectionInfoNull.collectionHeaders(datadogConnectorDTO);
    runtimeParameters = RuntimeParameters.builder()
                            .startTime(instant.minus(Duration.ofMinutes(5)))
                            .endTime(instant)
                            .commonHeaders(headers)
                            .otherEnvVariables(params)
                            .baseUrl(BASE_URL)
                            .build();
    List<TimeSeriesRecord> timeSeriesRecordsWithNull =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, System.out::println);
    assertThat(timeSeriesRecordsWithNull.size()).isEqualTo(8);
    System.out.println();
  }

  private DatadogMetricCVConfig createAndpopulateCVConfig(String query, String groupingQuery) {
    DatadogMetricCVConfig datadogMetricCVConfig =
        builderFactory.datadogMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(DatadogMetricCVConfig.MetricInfo.builder()
                                              .query(query)
                                              .groupingQuery(groupingQuery)
                                              .metric("docker.cpu.usage")
                                              .metricType(TimeSeriesMetricType.RESP_TIME)
                                              .identifier("cpu")
                                              .metricName("cpu")
                                              .isManualQuery(true)
                                              .serviceInstanceIdentifierTag("pod_name")
                                              .build(),
                DatadogMetricCVConfig.MetricInfo.builder()
                    .query("kubernetes.memory.usage{cluster-name:chi-play}.rollup(avg, 60)")
                    .groupingQuery("kubernetes.memory.usage{cluster-name:chi-play} by {pod_name}.rollup(avg, 60)")
                    .metric("kubernetes.memory.usage")
                    .metricType(TimeSeriesMetricType.RESP_TIME)
                    .identifier("memory")
                    .metricName("memory")
                    .isManualQuery(true)
                    .serviceInstanceIdentifierTag("pod_name")
                    .build()))
            .build();
    return datadogMetricCVConfig;
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogDSL_forSLI() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-07-23T08:12:40.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.DATADOG_METRICS);

    DatadogMetricCVConfig datadogMetricCVConfig =
        builderFactory.datadogMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(DatadogMetricCVConfig.MetricInfo.builder()
                                              .query("system.cpu.user{*}.rollup(avg, 60)")
                                              .metric("system.cpu.user")
                                              .identifier("my-dashboard-1")
                                              .metricName("my-dashboard-1")
                                              .isManualQuery(true)
                                              .build()))
            .build();
    datadogMetricCVConfig.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfig, TaskType.SLI);
    datadogMetricsDataCollectionInfo.setCollectHostData(false);
    DatadogConnectorDTO datadogConnectorDTO = getDatadogConnectorDTO();
    Map<String, Object> params = datadogMetricsDataCollectionInfo.getDslEnvVariables(datadogConnectorDTO);

    Map<String, String> headers = datadogMetricsDataCollectionInfo.collectionHeaders(datadogConnectorDTO);
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(60)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(BASE_URL)
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(timeSeriesRecords.size()).isEqualTo(60);
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("system.cpu.user");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("my-dashboard-1");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(10.20684609003365);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("dashboardName");
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1658560380000L);
    assertThat(timeSeriesRecords.get(59).getTimestamp()).isEqualTo(1658563920000L);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogDSL_forServiceHealth() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-07-23T08:12:40.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.DATADOG_METRICS);

    DatadogMetricCVConfig datadogMetricCVConfig =
        builderFactory.datadogMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(DatadogMetricCVConfig.MetricInfo.builder()
                                              .query("system.cpu.user{*}.rollup(avg, 60)")
                                              .metric("system.cpu.user")
                                              .identifier("my-dashboard-1")
                                              .metricName("my-dashboard-1")
                                              .metricType(TimeSeriesMetricType.INFRA)
                                              .isManualQuery(true)
                                              .build()))
            .build();
    datadogMetricCVConfig.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfig, TaskType.LIVE_MONITORING);
    datadogMetricsDataCollectionInfo.setCollectHostData(false);
    DatadogConnectorDTO datadogConnectorDTO = getDatadogConnectorDTO();
    Map<String, Object> params = datadogMetricsDataCollectionInfo.getDslEnvVariables(datadogConnectorDTO);

    Map<String, String> headers = datadogMetricsDataCollectionInfo.collectionHeaders(datadogConnectorDTO);
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(60)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(BASE_URL)
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(timeSeriesRecords.size()).isEqualTo(60);
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("system.cpu.user");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("my-dashboard-1");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(10.20684609003365);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("dashboardName");
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1658560380000L);
    assertThat(timeSeriesRecords.get(59).getTimestamp()).isEqualTo(1658563920000L);
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/datadog/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/datadog/" + name)), StandardCharsets.UTF_8);
  }

  private DatadogConnectorDTO getDatadogConnectorDTO() {
    return DatadogConnectorDTO.builder()
        .url(BASE_URL)
        .apiKeyRef(SecretRefData.builder().decryptedValue("****".toCharArray()).build())
        .applicationKeyRef(SecretRefData.builder().decryptedValue("****".toCharArray()).build())
        .build();
  }
}
