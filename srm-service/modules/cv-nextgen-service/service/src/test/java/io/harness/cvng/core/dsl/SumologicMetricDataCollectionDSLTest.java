/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SumologicMetricDataCollectionInfo;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;
import io.harness.cvng.core.entities.SumologicMetricInfo;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.cvng.core.services.impl.SumologicMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
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

public class SumologicMetricDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  private static final int THREADS = 10;
  private static final long FROM_EPOCH_TIME = 1668431100000L;
  private static final long TO_EPOCH_TIME = 1668431400000L;
  private static final int DATA_COLLECTION_WINDOW = 5;
  private static final String SECRET_REF_DATA = "Dummy_Secret_Ref";
  private static final String SUMOLOGIC_BASE_URL = "https://api.in.sumologic.com/";
  private static final int RESPONSE_SIZE_METRIC_SAMPLE = 7;
  BuilderFactory builderFactory;
  @Inject MetricPackService metricPackService;
  @Inject SumologicMetricDataCollectionInfoMapper dataCollectionInfoMapper;
  DataCollectionDSLService dataCollectionDSLService;

  @Before
  public void setup() throws IOException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    ExecutorService executorService;
    executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_sumologicDSLSLO() throws IOException {
    Instant instant = Instant.ofEpochMilli(TO_EPOCH_TIME);
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.SUMOLOGIC_METRICS);

    SumologicMetricCVConfig sumologicMetricCVConfig =
        builderFactory.sumologicMetricCVConfigBuilder()
            .metricInfos(Collections.singletonList(SumologicMetricInfo.builder()
                                                       .query("metric=Mem_UsedPercent")
                                                       .identifier("Mem_UsedPercent")
                                                       .metricName("Mem_UsedPercent")
                                                       .build()))
            .build();
    sumologicMetricCVConfig.setMetricPack(metricPacks.get(0));
    sumologicMetricCVConfig.setGroupName("default");
    metricPackService.populateDataCollectionDsl(sumologicMetricCVConfig.getType(), metricPacks.get(0));
    SumologicMetricDataCollectionInfo sumologicMetricDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(sumologicMetricCVConfig, VerificationTask.TaskType.SLI);
    SumoLogicConnectorDTO sumoLogicConnectorDTO =
        SumoLogicConnectorDTO.builder()
            .url(SUMOLOGIC_BASE_URL)
            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .accessKeyRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .build();
    Map<String, Object> params = sumologicMetricDataCollectionInfo.getDslEnvVariables(sumoLogicConnectorDTO);

    Map<String, String> headers = new HashMap<>();
    headers.putAll(sumologicMetricDataCollectionInfo.collectionHeaders(sumoLogicConnectorDTO));
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(DATA_COLLECTION_WINDOW)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl(SUMOLOGIC_BASE_URL)
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        sumologicMetricCVConfig.getDataCollectionDsl(), runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("expected-sumologic-metric-sample-dsl-output.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_SumologicMetricSampleData() {
    String metricSampleDataRequestDSL = MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL;
    SumoLogicConnectorDTO SumologiconnectorDTO =
        SumoLogicConnectorDTO.builder()
            .url(SUMOLOGIC_BASE_URL)
            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .accessKeyRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .build();
    SumologicMetricSampleDataRequest sumologicMetricSampleDataRequest =
        SumologicMetricSampleDataRequest.builder()
            .query("metric=Mem_UsedPercent")
            .from(FROM_EPOCH_TIME)
            .to(TO_EPOCH_TIME)
            .dsl(metricSampleDataRequestDSL)
            .connectorInfoDTO(ConnectorInfoDTO.builder().connectorConfig(SumologiconnectorDTO).build())
            .build();
    Instant instant = Instant.parse("2022-06-09T18:25:00.000Z");

    Map<String, Object> params = sumologicMetricSampleDataRequest.fetchDslEnvVariables();

    Map<String, String> headers = new HashMap<>(sumologicMetricSampleDataRequest.collectionHeaders());
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(DATA_COLLECTION_WINDOW))) // TODO find use of these
            .endTime(instant)
            .commonHeaders(headers)
            .otherEnvVariables(params)
            .baseUrl(SUMOLOGIC_BASE_URL)
            .build();
    LinkedTreeMap response = (LinkedTreeMap) dataCollectionDSLService.execute(
        metricSampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(response).hasSize(RESPONSE_SIZE_METRIC_SAMPLE);
  }
  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(new File(getResourceFilePath("sumologic/" + name)), StandardCharsets.UTF_8);
  }
}