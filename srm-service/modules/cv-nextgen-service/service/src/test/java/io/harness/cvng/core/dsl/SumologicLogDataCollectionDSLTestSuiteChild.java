/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.SumologicLogDataCollectionInfo;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.cvng.sumologic.SumoLogicConnectorValidationInfo;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicLogDataCollectionDSLTestSuiteChild extends DSLHoverflyTestSuiteChildBase {
  private static final String SECRET_REF_DATA = "Dummy_Secret_Ref";
  private static final int THREADS = 10;
  private static final int LOG_RECORDS_COUNT = 50;
  private static final String SUMOLOGIC_BASE_COM = "https://api.in.sumologic.com/";
  private static final long LOG_FIRST_RECORD_TIMESTAMP = 1668137697295L;

  DataCollectionDSLService dataCollectionDSLService;
  String code;

  @Before
  public void setup() throws IOException {
    super.before();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_SumoLogic_DSL_getSampleData() {
    String sampleDataRequestDSL = MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL;
    SumologicLogSampleDataRequest sumologicLogSampleDataRequest =
        SumologicLogSampleDataRequest.builder()
            .query("_sourceCategory=windows/performance")
            .from("2022-11-11T09:00:00")
            .to("2022-11-11T09:05:00")
            .dsl(sampleDataRequestDSL)
            .connectorInfoDTO(
                ConnectorInfoDTO.builder()
                    .connectorConfig(
                        SumoLogicConnectorDTO.builder()
                            .url(SUMOLOGIC_BASE_COM)
                            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
                            .accessKeyRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
                            .build())
                    .build())
            .build();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(sumologicLogSampleDataRequest.getStartTime(instant))
                                              .endTime(sumologicLogSampleDataRequest.getEndTime(instant))
                                              .otherEnvVariables(sumologicLogSampleDataRequest.fetchDslEnvVariables())
                                              .commonHeaders(sumologicLogSampleDataRequest.collectionHeaders())
                                              .baseUrl(sumologicLogSampleDataRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(
        sampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(LOG_RECORDS_COUNT);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_sumologickDSL() throws IOException {
    code = readDSL("sumologic-log.datacollection");
    final RuntimeParameters runtimeParameters = getRuntimeParameters(Instant.parse("2020-08-28T11:06:44.711Z"));
    List<LogDataRecord> logDataRecords = (List<LogDataRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(LOG_RECORDS_COUNT);
    assertThat(logDataRecords.get(0).getHostname()).isEqualTo("DESKTOP-BS3Q623");
    assertThat(logDataRecords.get(0).getLog()).contains("instance of Win32_PerfFormattedData_PerfDisk_PhysicalDisk");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(LOG_FIRST_RECORD_TIMESTAMP);
  }
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_sumologicConnectionValidationValidSettings() {
    SumoLogicConnectorValidationInfo sumoLogicConnectorValidationInfo =
        SumoLogicConnectorValidationInfo.builder().build();
    sumoLogicConnectorValidationInfo.setConnectorConfigDTO(
        SumoLogicConnectorDTO.builder()
            .url(SUMOLOGIC_BASE_COM)
            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .accessKeyRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .build());
    code = sumoLogicConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(sumoLogicConnectorValidationInfo.getStartTime(instant))
                                              .endTime(sumoLogicConnectorValidationInfo.getEndTime(instant))
                                              .commonHeaders(sumoLogicConnectorValidationInfo.collectionHeaders())
                                              .baseUrl(sumoLogicConnectorValidationInfo.getBaseUrl())
                                              .build();
    String isValid =
        (String) dataCollectionDSLService.execute(code, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(isValid).isEqualTo("true");
  }
  private RuntimeParameters getRuntimeParameters(Instant instant) {
    SumologicLogDataCollectionInfo dataCollectionInfo = SumologicLogDataCollectionInfo.builder()
                                                            .query("_sourceCategory=windows/performance")
                                                            .serviceInstanceIdentifier("_sourcehost")
                                                            .build();
    dataCollectionInfo.setHostCollectionDSL(code);
    dataCollectionInfo.setCollectHostData(true);
    SumoLogicConnectorDTO sumoLogicConnectorDTO =
        SumoLogicConnectorDTO.builder()
            .url(SUMOLOGIC_BASE_COM)
            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .accessKeyRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .build();
    return RuntimeParameters.builder()
        .baseUrl(dataCollectionInfo.getBaseUrl(sumoLogicConnectorDTO))
        .commonHeaders(dataCollectionInfo.collectionHeaders(sumoLogicConnectorDTO))
        .commonOptions(dataCollectionInfo.collectionParams(sumoLogicConnectorDTO))
        .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(sumoLogicConnectorDTO))
        .endTime(instant)
        .startTime(instant.minus(Duration.ofMinutes(1)))
        .build();
  }

  private String readDSL(String fileName) throws IOException {
    return Resources.toString(NextGenLogCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
  }
}