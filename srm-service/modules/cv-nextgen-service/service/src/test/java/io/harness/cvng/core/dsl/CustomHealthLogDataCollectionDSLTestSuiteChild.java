/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CustomHealthLogDataCollectionInfo;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.beans.customhealthlog.CustomHealthLogInfo;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.delegate.beans.cvng.customhealth.CustomHealthConnectorValidationInfo;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthLogDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  private DataCollectionDSLService dataCollectionDSLService;
  private String code;

  @Before
  public void setup() {
    dataCollectionDSLService = new DataCollectionServiceImpl();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testExecute_customLogDSL() throws IOException {
    code = readDSL("custom-log-fetch-data.datacollection");

    final RuntimeParameters runtimeParameters = getRuntimeParameters(Instant.parse("2022-02-14T10:21:00.000Z"));
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull().hasSize(5);
    assertThat(logDataRecords.get(0).getHostname())
        .isEqualTo("gke-chi-play-delegate-non-preemptible-5a4eed4e-6phx.c.chi-play.internal");
    assertThat(logDataRecords.get(0).getLog()).containsIgnoringCase("exception");
    assertThat(logDataRecords.get(0).getTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testExecute_customLogDSLEmptyResponse() throws IOException {
    code = readDSL("custom-log-fetch-data.datacollection");
    final RuntimeParameters runtimeParameters = getRuntimeParameters(Instant.parse("2022-02-14T10:21:00.000Z"));
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isEmpty();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testExecute_customLogConnectionValidationValidSettings() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    CustomHealthConnectorValidationInfo customHealthConnectorValidationInfo =
        CustomHealthConnectorValidationInfo.builder().build();
    customHealthConnectorValidationInfo.setConnectorConfigDTO(CustomHealthConnectorDTO.builder()
                                                                  .baseURL("https://app.datadoghq.com/api/v1/")
                                                                  .method(CustomHealthMethod.GET)
                                                                  .headers(new ArrayList<>())
                                                                  .params(new ArrayList<>())
                                                                  .validationPath("metrics?from=1527102292")
                                                                  .build());
    customHealthConnectorValidationInfo.getConnectorConfigDTO().getParams().add(
        CustomHealthKeyAndValue.builder().key("application_key").isValueEncrypted(false).value("app_key").build());
    customHealthConnectorValidationInfo.getConnectorConfigDTO().getParams().add(
        CustomHealthKeyAndValue.builder().key("api_key").isValueEncrypted(false).value("api_key").build());

    String code = customHealthConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(customHealthConnectorValidationInfo.getStartTime(instant))
            .endTime(customHealthConnectorValidationInfo.getEndTime(instant))
            .commonHeaders(customHealthConnectorValidationInfo.collectionHeaders())
            .otherEnvVariables(customHealthConnectorValidationInfo.getDslEnvVariables())
            .commonOptions(customHealthConnectorValidationInfo.collectionParams())
            .baseUrl(customHealthConnectorValidationInfo.getBaseUrl())
            .build();
    String isValid = (String) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(isValid).isEqualTo("true");
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testExecute_customLogConnectionValidationInvalidSettings() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    CustomHealthConnectorValidationInfo customHealthConnectorValidationInfo =
        CustomHealthConnectorValidationInfo.builder().build();
    customHealthConnectorValidationInfo.setConnectorConfigDTO(CustomHealthConnectorDTO.builder()
                                                                  .baseURL("https://app.datadoghq.com/api/v1/")
                                                                  .method(CustomHealthMethod.GET)
                                                                  .validationPath("metrics?from=1527102292")
                                                                  .headers(new ArrayList<>())
                                                                  .params(new ArrayList<>())
                                                                  .validationPath("metrics?from=1527102292")
                                                                  .build());
    customHealthConnectorValidationInfo.getConnectorConfigDTO().getParams().add(
        CustomHealthKeyAndValue.builder()
            .key("application_key")
            .isValueEncrypted(true)
            .encryptedValueRef(SecretRefData.builder().decryptedValue("app_key".toCharArray()).build())
            .build());
    customHealthConnectorValidationInfo.getConnectorConfigDTO().getParams().add(
        CustomHealthKeyAndValue.builder()
            .key("api_key")
            .isValueEncrypted(true)
            .encryptedValueRef(SecretRefData.builder().decryptedValue("api_key".toCharArray()).build())
            .build());

    String code = customHealthConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(customHealthConnectorValidationInfo.getStartTime(instant))
            .endTime(customHealthConnectorValidationInfo.getEndTime(instant))
            .commonHeaders(customHealthConnectorValidationInfo.collectionHeaders())
            .otherEnvVariables(customHealthConnectorValidationInfo.getDslEnvVariables())
            .commonOptions(customHealthConnectorValidationInfo.collectionParams())
            .baseUrl(customHealthConnectorValidationInfo.getBaseUrl())
            .build();
    dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
  }

  private String readDSL(String fileName) throws IOException {
    return Resources.toString(SplunkCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
  }

  private RuntimeParameters getRuntimeParameters(Instant instant) {
    CustomHealthLogDataCollectionInfo dataCollectionInfo =
        CustomHealthLogDataCollectionInfo.builder()
            .customHealthLogInfo(
                CustomHealthLogInfo.builder()
                    .urlPath("logs-queries/list")
                    .method(CustomHealthMethod.POST)
                    .body("{\"query\":\"*exception*\",\"time\":{\"from\":\"start_time\",\"to\":\"end_time\"}}")
                    .logMessageJsonPath("$.logs.[*].content.message")
                    .timestampJsonPath("$.logs.[*].content.timestamp")
                    .serviceInstanceJsonPath("$.logs.[*].content.host")
                    .endTimeInfo(TimestampInfo.builder()
                                     .placeholder("end_time")
                                     .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                     .build())
                    .startTimeInfo(TimestampInfo.builder()
                                       .placeholder("start_time")
                                       .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                       .build())
                    .queryName("perfQuery")
                    .build())
            .build();

    dataCollectionInfo.setHostCollectionDSL(code);
    dataCollectionInfo.setCollectHostData(true);
    CustomHealthConnectorDTO customHealthConnectorDTO = CustomHealthConnectorDTO.builder()
                                                            .baseURL("https://app.datadoghq.com/api/v1/")
                                                            .method(CustomHealthMethod.GET)
                                                            .params(new ArrayList<>())
                                                            .headers(new ArrayList<>())
                                                            .build();

    customHealthConnectorDTO.getParams().add(
        CustomHealthKeyAndValue.builder().key("application_key").isValueEncrypted(false).value("app_key").build());
    customHealthConnectorDTO.getParams().add(
        CustomHealthKeyAndValue.builder().key("api_key").isValueEncrypted(false).value("api_key").build());

    return RuntimeParameters.builder()
        .baseUrl(dataCollectionInfo.getBaseUrl(customHealthConnectorDTO))
        .commonHeaders(dataCollectionInfo.collectionHeaders(customHealthConnectorDTO))
        .commonOptions(dataCollectionInfo.collectionParams(customHealthConnectorDTO))
        .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO))
        .endTime(instant)
        .startTime(instant.minus(Duration.ofMinutes(1)))
        .build();
  }
}
