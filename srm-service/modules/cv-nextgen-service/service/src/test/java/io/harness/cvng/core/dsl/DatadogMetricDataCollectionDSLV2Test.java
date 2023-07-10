/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DatadogMetricsDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.DatadogMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogMetricDataCollectionDSLV2Test extends HoverflyCVNextGenTestBase {
  private static final String BASE_URL = "https://api.datadoghq.com/api/";
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private DatadogMetricDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;

  FeatureFlagService featureFlagService;

  @Before
  public void setup() throws IOException, IllegalAccessException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(builderFactory.getContext().getAccountId()),
             eq(FeatureName.SRM_DATADOG_METRICS_FORMULA_SUPPORT.name())))
        .thenReturn(true);
    FieldUtils.writeField(dataCollectionInfoMapper, "featureFlagService", featureFlagService, true);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_datadogDSLWithHostData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("datadog-v2-dsl-metric.datacollection");
    Instant instant = Instant.parse("2023-06-26T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.DATADOG_METRICS);

    DatadogMetricCVConfig datadogMetricCVConfig = createAndpopulateCVConfig(
        "kubernetes.memory.usage{cluster-name:chi-play}.rollup(avg, 60); kubernetes.memory.usage{cluster-name:chi-play}.rollup(avg, 60); a/b * 100",
        "docker.cpu.usage{cluster-name:chi-play} by {pod_name}.rollup(avg, 60)");

    datadogMetricCVConfig.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    datadogMetricsDataCollectionInfo.setCollectHostData(true);
    dataCollectionInfoMapper.postProcessDataCollectionInfo(
        datadogMetricsDataCollectionInfo, datadogMetricCVConfig, VerificationTask.TaskType.DEPLOYMENT);
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
    assertThat(timeSeriesRecords.size()).isEqualTo(1080);
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("cpu");
    assertThat(timeSeriesRecords.get(0).getHostname()).isEqualTo("datadog-agent-vlrsk");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("cpu");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(100.0);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("dashboardName");
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1687774560000L);
  }

  private DatadogMetricCVConfig createAndpopulateCVConfig(String query, String groupingQuery) {
    DatadogMetricCVConfig datadogMetricCVConfig =
        builderFactory.datadogMetricCVConfigBuilder()
            .metricInfoList(Collections.singletonList(DatadogMetricCVConfig.MetricInfo.builder()
                                                          .query(query)
                                                          .groupingQuery(groupingQuery)
                                                          .metric("docker.cpu.usage")
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("cpu")
                                                          .metricName("cpu")
                                                          .isManualQuery(true)
                                                          .serviceInstanceIdentifierTag("pod_name")
                                                          .build()))
            .build();
    return datadogMetricCVConfig;
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_datadogDSL_forSLI() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("datadog-v2-dsl-metric.datacollection");
    Instant instant = Instant.parse("2023-06-27T08:12:40.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.DATADOG_METRICS);

    DatadogMetricCVConfig datadogMetricCVConfig =
        builderFactory.datadogMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(DatadogMetricCVConfig.MetricInfo.builder()
                                              .query("system.cpu.user{*}.rollup(avg, 60)")
                                              .metric("system.cpu.user")
                                              .identifier("system.cpu.user")
                                              .metricName("system.cpu.user")
                                              .isManualQuery(true)
                                              .build()))
            .build();
    datadogMetricCVConfig.setMetricPack(metricPacks.get(0));
    DatadogMetricsDataCollectionInfo datadogMetricsDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(datadogMetricCVConfig, VerificationTask.TaskType.SLI);
    datadogMetricsDataCollectionInfo.setCollectHostData(false);
    dataCollectionInfoMapper.postProcessDataCollectionInfo(
        datadogMetricsDataCollectionInfo, datadogMetricCVConfig, VerificationTask.TaskType.SLI);
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
    HOVERFLY_RULE.printSimulationData();
    assertThat(timeSeriesRecords.size()).isEqualTo(60);
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("system.cpu.user");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("system.cpu.user");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(20.823993);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("dashboardName");
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1687849980000L);
    assertThat(timeSeriesRecords.get(59).getTimestamp()).isEqualTo(1687853520000L);
    assertThat(timeSeriesRecords.get(59).getMetricValue()).isEqualTo(19.857091);
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(PrometheusCVConfig.class, "/io/harness/cvng/core/entities/" + name)),
        StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/datadog/" + name)), StandardCharsets.UTF_8);
  }

  private DatadogConnectorDTO getDatadogConnectorDTO() {
    return DatadogConnectorDTO.builder()
        .url(BASE_URL)
        .apiKeyRef(SecretRefData.builder().decryptedValue("**".toCharArray()).build()) // rollback TODO
        .applicationKeyRef(SecretRefData.builder().decryptedValue("**".toCharArray()).build())
        .build();
  }
}
