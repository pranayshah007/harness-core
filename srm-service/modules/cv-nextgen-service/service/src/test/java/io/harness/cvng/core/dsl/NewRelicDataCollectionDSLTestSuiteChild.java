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
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricResponseMappingDTO;
import io.harness.cvng.beans.NewRelicDataCollectionInfo;
import io.harness.cvng.beans.NewRelicDataCollectionInfo.NewRelicMetricInfoDTO;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NewRelicDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  @Inject private MetricPackService metricPackService;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private ExecutorService executorService;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testExecute_newRelicCustomPackForDeployment() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("custom-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1654494751000L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.NEW_RELIC);

    NewRelicDataCollectionInfo newRelicDataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .applicationName("My Application")
            .applicationId(107019083)
            .groupName("G1")
            .metricInfoList(Arrays.asList(
                NewRelicMetricInfoDTO.builder()
                    .metricName("New Relic Metric")
                    .metricIdentifier("new_relic_metric")
                    .nrql(
                        "SELECT count(apm.service.instance.count) FROM Metric WHERE appName LIKE 'My Application' TIMESERIES")
                    .responseMapping(MetricResponseMappingDTO.builder()
                                         .metricValueJsonPath("$.timeSeries.[*].results.[*].count")
                                         .timestampJsonPath("$.timeSeries.[*].beginTimeSeconds")
                                         .build())
                    .build()))
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Custom"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customQuery(true)
            .build();

    newRelicDataCollectionInfo.setCollectHostData(true);
    Map<String, Object> params = newRelicDataCollectionInfo.getDslEnvVariables(NewRelicConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://insights-api.newrelic.com/v1/accounts/1805869/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("custom-deployment-expectation.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_newRelicCustomPack_withoutHostDataCollection() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("custom-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1654494751000L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.NEW_RELIC);

    NewRelicDataCollectionInfo newRelicDataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .applicationName("My Application")
            .applicationId(107019083)
            .groupName("G1")
            .metricInfoList(Arrays.asList(
                NewRelicMetricInfoDTO.builder()
                    .metricName("New Relic Metric")
                    .metricIdentifier("new_relic_metric")
                    .nrql(
                        "SELECT count(apm.service.instance.count) FROM Metric WHERE appName LIKE 'My Application' TIMESERIES")
                    .responseMapping(MetricResponseMappingDTO.builder()
                                         .metricValueJsonPath("$.timeSeries.[*].results.[*].count")
                                         .timestampJsonPath("$.timeSeries.[*].beginTimeSeconds")
                                         .build())
                    .build()))
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Custom"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customQuery(true)
            .build();

    newRelicDataCollectionInfo.setCollectHostData(false);
    Map<String, Object> params = newRelicDataCollectionInfo.getDslEnvVariables(NewRelicConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Query-Key",
        "***"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://insights-api.newrelic.com/v1/accounts/1805869/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords.size()).isEqualTo(1);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("G1");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("new_relic_metric");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("New Relic Metric");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(15.0);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1654494660000L);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_newRelicPerformancePack_withoutHostDataCollection() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("performance-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1654494751000L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.NEW_RELIC);

    NewRelicDataCollectionInfo newRelicDataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .applicationName("My Application")
            .applicationId(107019083)
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Performance"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customQuery(false)
            .build();

    newRelicDataCollectionInfo.setCollectHostData(false);
    Map<String, Object> params = newRelicDataCollectionInfo.getDslEnvVariables(NewRelicConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Query-Key",
        "***"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://insights-api.newrelic.com/v1/accounts/1805869/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords.size()).isEqualTo(4);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_newRelicPerformancePack_withHostDataCollection() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("performance-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1654494751000L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.NEW_RELIC);

    NewRelicDataCollectionInfo newRelicDataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .applicationName("My Application")
            .applicationId(107019083)
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Performance"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customQuery(false)
            .build();

    newRelicDataCollectionInfo.setCollectHostData(true);
    Map<String, Object> params = newRelicDataCollectionInfo.getDslEnvVariables(NewRelicConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Query-Key",
        "***"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://insights-api.newrelic.com/v1/accounts/1805869/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords.size()).isEqualTo(6);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(NewRelicCVConfig.class, "/newrelic/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/newrelic/" + name)), StandardCharsets.UTF_8);
  }
}
