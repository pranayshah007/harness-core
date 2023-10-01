/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SplunkMetricDataCollectionInfo;
import io.harness.cvng.beans.splunk.SplunkMetricSampleDataCollectionRequest;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.SplunkMetricCVConfig;
import io.harness.cvng.core.entities.SplunkMetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.SplunkMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
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

public class SplunkMetricDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private SplunkMetricDataCollectionInfoMapper dataCollectionInfoMapper;
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
  public void testExecute_splunkDSLSLO() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    Instant instant = Instant.parse("2022-06-09T18:25:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.SPLUNK_METRIC);

    SplunkMetricCVConfig splunkMetricCVConfig =
        builderFactory.splunkMetricCVConfigBuilder()
            .metricInfos(Collections.singletonList(
                SplunkMetricInfo.builder()
                    .query(
                        "source=\"access_splunk.log\" host=\"splunk-dev\" sourcetype=\"access_logs_splunk\" status<400 | bucket _time span=1m | stats avg(response_time) as value by _time | rename _time as time | fields time value")
                    .identifier("response_time")
                    .metricName("Response time")
                    .build()))
            .build();
    splunkMetricCVConfig.setMetricPack(metricPacks.get(0));
    metricPackService.populateDataCollectionDsl(splunkMetricCVConfig.getType(), metricPacks.get(0));
    SplunkMetricDataCollectionInfo splunkMetricDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(splunkMetricCVConfig, VerificationTask.TaskType.SLI);
    /// 11:10:38.485 [testExecute_splunkDSLWithHostData] INFO  io.harness.antlr.visitor.StatementVisitor -
    /// {time=1654798800, value=1730.3}
    SplunkConnectorDTO splunkConnectorDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .build();
    Map<String, Object> params = splunkMetricDataCollectionInfo.getDslEnvVariables(splunkConnectorDTO);

    Map<String, String> headers = new HashMap<>();
    headers.putAll(splunkMetricDataCollectionInfo.collectionHeaders(splunkConnectorDTO));
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(5)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://splunk.dev.harness.io:8089/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(splunkMetricCVConfig.getDataCollectionDsl(),
            runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-splunk-dsl-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_splunkMetricSampleData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    SplunkConnectorDTO splunkConnectorDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .build();
    SplunkMetricSampleDataCollectionRequest splunkMetricSampleDataCollectionRequest =
        SplunkMetricSampleDataCollectionRequest.builder()
            .query(
                "source=\"access_splunk.log\" host=\"splunk-dev\" sourcetype=\"access_logs_splunk\" status<400 | bucket _time span=1m | stats avg(response_time) as value by _time | rename _time as time | fields time value")
            .connectorInfoDTO(ConnectorInfoDTO.builder().connectorConfig(splunkConnectorDTO).build())
            .build();
    Instant instant = Instant.parse("2022-06-09T18:25:00.000Z");

    /// 11:10:38.485 [testExecute_splunkDSLWithHostData] INFO  io.harness.antlr.visitor.StatementVisitor -
    /// {time=1654798800, value=1730.3}

    Map<String, Object> params = splunkMetricSampleDataCollectionRequest.fetchDslEnvVariables();

    Map<String, String> headers = new HashMap<>();
    headers.putAll(splunkMetricSampleDataCollectionRequest.collectionHeaders());
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(5)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://splunk.dev.harness.io:8089/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(splunkMetricSampleDataCollectionRequest.getDSL(),
            runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-splunk-sample-dsl-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(new File(getResourceFilePath("hoverfly/splunk/" + name)), StandardCharsets.UTF_8);
  }
}
