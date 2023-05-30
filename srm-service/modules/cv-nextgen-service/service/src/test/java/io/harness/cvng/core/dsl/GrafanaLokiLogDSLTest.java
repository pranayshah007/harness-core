/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.HoverflyTestBase;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.GrafanaLokiLogDataCollectionInfo;
import io.harness.cvng.beans.grafanaloki.GrafanaLokiLogSampleDataRequest;
import io.harness.cvng.core.services.impl.DataCollectionDSLFactory;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GrafanaLokiLogDSLTest extends HoverflyTestBase {
  private static final int THREADS = 10;
  private static final String LOKI_BASE_URL = "http://127.0.0.1:3100";
  DataCollectionDSLService dataCollectionDSLService;
  private final String encodedQuery = "%7Bjob%3D%7E%22.%2B%22%7D";
  private final Instant startTime = Instant.ofEpochSecond(1684423037L);
  private final Instant endTime = Instant.ofEpochSecond(1684432037L);

  @Before
  public void setup() throws IOException {
    super.before();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  // TODO: Check why hoverfly is not capturing complete data and remove @Ignore from tests.
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  @Ignore("Hoverfly is not capturing data and failing in SIMULATE mode.")
  public void testExecute_GrafanaLokiLogDsl_getSampleData() {
    String sampleDataRequestDSL = MetricPackServiceImpl.GRAFANA_LOKI_LOG_SAMPLE_DATA_DSL;
    GrafanaLokiLogSampleDataRequest grafanaLokiLogSampleDataRequest =
        GrafanaLokiLogSampleDataRequest.builder()
            .query(encodedQuery)
            .startTimeInSeconds(startTime.getEpochSecond())
            .endTimeInSeconds(endTime.getEpochSecond())
            .dsl(sampleDataRequestDSL)
            .connectorInfoDTO(ConnectorInfoDTO.builder()
                                  .connectorConfig(CustomHealthConnectorDTO.builder().baseURL(LOKI_BASE_URL).build())
                                  .build())
            .build();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(grafanaLokiLogSampleDataRequest.getStartTime(Instant.now()))
                                              .endTime(grafanaLokiLogSampleDataRequest.getEndTime(Instant.now()))
                                              .otherEnvVariables(grafanaLokiLogSampleDataRequest.fetchDslEnvVariables())
                                              .commonHeaders(grafanaLokiLogSampleDataRequest.collectionHeaders())
                                              .baseUrl(grafanaLokiLogSampleDataRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(
        sampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(6);
    result.forEach(currentResult -> assertThat((Map<String, Object>) currentResult).containsKeys("stream", "values"));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  @Ignore("Hoverfly is not capturing data and failing in SIMULATE mode.")
  public void testExecute_GrafanaLokiLogDsl_getLogRecords() {
    String dataCollectionDsl = DataCollectionDSLFactory.readLogDSL(DataSourceType.GRAFANA_LOKI_LOGS);
    GrafanaLokiLogDataCollectionInfo grafanaLokiLogDataCollectionInfo = GrafanaLokiLogDataCollectionInfo.builder()
                                                                            .urlEncodedQuery(encodedQuery)
                                                                            .serviceInstanceIdentifier("job")
                                                                            .build();
    CustomHealthConnectorDTO customHealthConnectorDTO =
        CustomHealthConnectorDTO.builder().baseURL(LOKI_BASE_URL).build();
    grafanaLokiLogDataCollectionInfo.setDataCollectionDsl(dataCollectionDsl);
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(startTime)
            .endTime(endTime)
            .otherEnvVariables(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO))
            .commonHeaders(grafanaLokiLogDataCollectionInfo.collectionHeaders(customHealthConnectorDTO))
            .baseUrl(grafanaLokiLogDataCollectionInfo.getBaseUrl(customHealthConnectorDTO))
            .build();
    List<LogDataRecord> result = (List<LogDataRecord>) dataCollectionDSLService.execute(
        dataCollectionDsl, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(152);
    result.forEach(currentResult -> {
      assertThat(currentResult.getLog()).isNotBlank();
      assertThat(currentResult.getHostname()).isNotBlank();
      assertThat(currentResult.getTimestamp()).isGreaterThanOrEqualTo(startTime.toEpochMilli());
      assertThat(currentResult.getTimestamp()).isLessThanOrEqualTo(endTime.toEpochMilli());
    });
  }
}