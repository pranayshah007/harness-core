/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.cvng.HoverflyTestBase;
import io.harness.cvng.beans.SumologicLogDataCollectionInfo;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.core.entities.SumologicLogCVConfig;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicLogDataCollectionDSLTest extends HoverflyTestBase {
  private DataCollectionDSLService dataCollectionDSLService;

  private ExecutorService executorService;

  private String code;

  @Before
  public void setup() throws IOException {
    dataCollectionDSLService = new DataCollectionServiceImpl();
    executorService = Executors.newFixedThreadPool(10);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL("sumologic-log.datacollection");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_SumoLogic_DSL_getSampleData() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
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
                            .url("https://api.in.sumologic.com/")
                            .accessIdRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
                            .accessKeyRef(SecretRefData.builder()
                                              .decryptedValue("Harness@123".toCharArray())
                                              .build()) // TODO Use encrypted
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
    List<?> result =
        (List<?>) dataCollectionDSLService.execute(sampleDataRequestDSL, runtimeParameters, callDetails -> {});
    assertThat(result.size()).isEqualTo(50);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_sumologickDSL() {
    // TODO Fix the dates
    final RuntimeParameters runtimeParameters = getRuntimeParameters(Instant.parse("2020-08-28T11:06:44.711Z"));
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(50);
    assertThat(logDataRecords.get(0).getHostname()).isEqualTo("DESKTOP-BS3Q623");
    assertThat(logDataRecords.get(0).getLog()).contains("instance of Win32_PerfFormattedData_PerfDisk_PhysicalDisk");
    // TODO Fix the timestamp exact matching.
    //      assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(668137697295L);
  }

  /*
@Test
      @Owner(developers = ANSUMAN)
      @Category(UnitTests.class)
      public void testExecute_sumologicConnectionValidationValidSettings() {
          DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
          SplunkConnectorValidationInfo splunkConnectorValidationInfo = SplunkConnectorValidationInfo.builder().build();
          splunkConnectorValidationInfo.setConnectorConfigDTO(
                  SplunkConnectorDTO.builder()
                          .splunkUrl("https://splunk.dev.harness.io:8089/")
                          .username("harnessadmin")
                          .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
                          .build());
          String code = splunkConnectorValidationInfo.getConnectionValidationDSL();
          Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
          RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                  .startTime(splunkConnectorValidationInfo.getStartTime(instant))
                  .endTime(splunkConnectorValidationInfo.getEndTime(instant))
                  .commonHeaders(splunkConnectorValidationInfo.collectionHeaders())
                  .baseUrl(splunkConnectorValidationInfo.getBaseUrl())
                  .build();
          String isValid = (String) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
          assertThat(isValid).isEqualTo("true");
      }
  */
  /*
        @Test
        @Owner(developers = ANSUMAN)
        @Category(UnitTests.class)
        public void testExecute_sumologicConnectionValidationInValidSettings() {
            DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
            SumoLogicConnectorValidationInfo sumoLogicConnectorValidationInfo =
     SumoLogicConnectorValidationInfo.builder().build(); sumoLogicConnectorValidationInfo.setConnectorConfigDTO(
                    SumoLogicConnectorDTO.builder()
                            .url("https://api.in.sumologic.com")
                            .accessIdRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
                            .accessKeyRef(
                                    SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build()) // TODO
     Use encrypted .build()); String code = sumoLogicConnectorValidationInfo.getConnectionValidationDSL(); Instant
     instant = Instant.parse("2020-10-30T10:44:48.164Z"); RuntimeParameters runtimeParameters =
     RuntimeParameters.builder() .startTime(sumoLogicConnectorValidationInfo.getStartTime(instant))
                    .endTime(sumoLogicConnectorValidationInfo.getEndTime(instant))
                    .commonHeaders(sumoLogicConnectorValidationInfo.collectionHeaders())
                    .baseUrl(sumoLogicConnectorValidationInfo.getBaseUrl())
                    .build();
            assertThatThrownBy(() -> dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {}))
                    .hasMessageContaining("Response code: 405");
        }
  */

  private RuntimeParameters getRuntimeParameters(Instant instant) {
    SumologicLogDataCollectionInfo dataCollectionInfo = SumologicLogDataCollectionInfo.builder()
                                                            .query("_sourceCategory=windows/performance")
                                                            .serviceInstanceIdentifier("host")
                                                            .build();
    dataCollectionInfo.setHostCollectionDSL(code);
    dataCollectionInfo.setCollectHostData(true);
    SumoLogicConnectorDTO sumoLogicConnectorDTO =
        SumoLogicConnectorDTO.builder()
            .url("https://api.in.sumologic.com/")
            .accessIdRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .accessKeyRef(
                SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build()) // TODO Use encrypted
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
    return Resources.toString(SumologicLogCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("/hoverfly/sumologic/" + name)), StandardCharsets.UTF_8);
  }
}