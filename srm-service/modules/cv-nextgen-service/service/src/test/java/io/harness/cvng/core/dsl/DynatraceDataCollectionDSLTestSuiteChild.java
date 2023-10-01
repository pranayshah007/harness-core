/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.DynatraceDataCollectionInfo;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DynatraceDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  @Inject private MetricPackService metricPackService;
  DataCollectionDSLService dataCollectionDSLService;
  private List<MetricPack> metricPacks;
  private Instant instant;
  private String code;
  private static final String BASE_URL = "https://qva35651.live.dynatrace.com/";

  @Before
  public void setup() throws IOException {
    BuilderFactory builderFactory = BuilderFactory.getDefault();
    String accountId = builderFactory.getContext().getAccountId();
    String orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    String projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    ExecutorService executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL("metric-pack.datacollection");
    instant = Instant.parse("2022-07-26T00:00:00.00Z");
    metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.DYNATRACE);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_PerformancePack_withoutHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo dataCollectionInfo =
        DynatraceDataCollectionInfo.builder()
            .serviceId("SERVICE-D739201C4CBBA618")
            .serviceMethodIds(Collections.singletonList("SERVICE_METHOD-F3988BEE84FF7388"))
            .metricPack(getMetricPack("Performance"))
            .build();
    dataCollectionInfo.setCollectHostData(false);
    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(3);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("Performance");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_PerformancePack_withHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo dataCollectionInfo =
        DynatraceDataCollectionInfo.builder()
            .serviceId("SERVICE-D739201C4CBBA618")
            .serviceMethodIds(Collections.singletonList("SERVICE_METHOD-F3988BEE84FF7388"))
            .metricPack(getMetricPack("Performance"))
            .build();
    dataCollectionInfo.setCollectHostData(true);

    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(3);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("Performance");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_infraPack_withoutHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo dataCollectionInfo =
        DynatraceDataCollectionInfo.builder()
            .serviceId("SERVICE-D739201C4CBBA618")
            .serviceMethodIds(Collections.singletonList("SERVICE_METHOD-F3988BEE84FF7388"))
            .metricPack(getMetricPack("Infrastructure"))
            .build();
    dataCollectionInfo.setCollectHostData(false);
    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(3);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("Infrastructure");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_infraPack_withHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo dataCollectionInfo =
        DynatraceDataCollectionInfo.builder()
            .serviceId("SERVICE-D739201C4CBBA618")
            .serviceMethodIds(Collections.singletonList("SERVICE_METHOD-F3988BEE84FF7388"))
            .metricPack(getMetricPack("Infrastructure"))
            .build();
    dataCollectionInfo.setCollectHostData(true);

    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(3);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("Infrastructure");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_customPack_withHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo.MetricCollectionInfo customInfo =
        DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName("IOtime")
            .metricSelector("builtin:service.ioTime")
            .identifier("IOtime")
            .build();
    DynatraceDataCollectionInfo dataCollectionInfo = DynatraceDataCollectionInfo.builder()
                                                         .serviceId("SERVICE-D739201C4CBBA618")
                                                         .metricPack(getMetricPack("Custom"))
                                                         .groupName("G1")
                                                         .customMetrics(Collections.singletonList(customInfo))
                                                         .build();
    dataCollectionInfo.setCollectHostData(true);

    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(1);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNotBlank();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("G1");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_customPack_withoutHostData() {
    DynatraceConnectorDTO dynatraceConnectorDTO = getConnectorDTO();
    DynatraceDataCollectionInfo.MetricCollectionInfo customInfo =
        DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
            .metricName("IOtime")
            .metricSelector("builtin:service.ioTime")
            .identifier("IOtime")
            .build();
    DynatraceDataCollectionInfo dataCollectionInfo = DynatraceDataCollectionInfo.builder()
                                                         .serviceId("SERVICE-D739201C4CBBA618")
                                                         .metricPack(getMetricPack("Custom"))
                                                         .groupName("G1")
                                                         .customMetrics(Collections.singletonList(customInfo))
                                                         .build();
    dataCollectionInfo.setCollectHostData(false);

    RuntimeParameters runtimeParameters = getRuntimeParams(dataCollectionInfo, dynatraceConnectorDTO);
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});

    assertThat(timeSeriesRecords).isNotNull();
    assertThat(timeSeriesRecords).hasSize(1);
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("G1");
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(DynatraceCVConfig.class, "/dynatrace/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private MetricPackDTO getMetricPack(String name) {
    return metricPacks.stream()
        .filter(pack -> pack.getIdentifier().equals(name))
        .findFirst()
        .orElse(MetricPack.builder().build())
        .toDTO();
  }

  private RuntimeParameters getRuntimeParams(
      DynatraceDataCollectionInfo dataCollectionInfo, DynatraceConnectorDTO dynatraceConnectorDTO) {
    Map<String, Object> params = dataCollectionInfo.getDslEnvVariables(dynatraceConnectorDTO);
    Map<String, String> headers = dataCollectionInfo.collectionHeaders(dynatraceConnectorDTO);
    return RuntimeParameters.builder()
        .startTime(instant.minusSeconds(60))
        .endTime(instant)
        .commonHeaders(headers)
        .otherEnvVariables(params)
        .baseUrl(BASE_URL)
        .build();
  }

  private DynatraceConnectorDTO getConnectorDTO() {
    return DynatraceConnectorDTO.builder()
        .url(BASE_URL)
        .apiTokenRef(SecretRefData.builder().decryptedValue("***".toCharArray()).build())
        .build();
  }
}
