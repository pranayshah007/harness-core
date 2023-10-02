/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.ELKDataCollectionInfo;
import io.harness.cvng.beans.elk.ELKIndexCollectionRequest;
import io.harness.cvng.beans.elk.ELKSampleDataCollectionRequest;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.exception.DataCollectionDSLException;
import io.harness.datacollection.exception.DataCollectionException;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.cvng.elk.ELKConnectorValidationInfo;
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

public class ELKDataCollectionDSLTestSuiteChild extends DSLHoverflyTestSuiteChildBase {
  private DataCollectionDSLService dataCollectionDSLService;
  private String code;
  private ExecutorService executorService;

  @Before
  public void setup() throws IOException {
    executorService = Executors.newFixedThreadPool(10);
    dataCollectionDSLService = new DataCollectionServiceImpl();
    code = readDSL("elk-log-fetch-data.datacollection");
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("error", "['_source'].['hostname']",
        "integration-test", "['_source'].['@timestamp']", "['_source'].['message']", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords.size()).isEqualTo(26);
    assertThat(logDataRecords.get(0).getHostname()).isEqualTo("qa-multiple-appd-deployment-5fff6c6c58-mqjq5");
    assertThat(logDataRecords.get(0).getLog()).contains("Error");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(1673378985885L);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_OldFormat() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("error", "_source.hostname", "integration-test",
        "_source.@timestamp", "_source.message", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords.size()).isEqualTo(26);
    assertThat(logDataRecords.get(0).getHostname()).isEqualTo("qa-multiple-appd-deployment-5fff6c6c58-mqjq5");
    assertThat(logDataRecords.get(0).getLog()).contains("Error");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(1673378985885L);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_noDataQuery() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters(
        "message: unmatchableString", "hostname", "*", "@timestamp", "message", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_noIndex() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters(
        "message: error", "hostname", null, "@timestamp", "message", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    assertThatThrownBy(() -> dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {}))
        .isInstanceOf(DataCollectionException.class);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_validationWithValidSettings() {
    ELKConnectorValidationInfo elkConnectorValidationInfo = ELKConnectorValidationInfo.builder().build();
    elkConnectorValidationInfo.setConnectorConfigDTO(
        ELKConnectorDTO.builder().url("http://elk6.dev.harness.io:9200/").authType(ELKAuthType.NONE).build());
    String validationDSL = elkConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkConnectorValidationInfo.getStartTime(instant))
                                              .endTime(elkConnectorValidationInfo.getEndTime(instant))
                                              .commonHeaders(elkConnectorValidationInfo.collectionHeaders())
                                              .baseUrl(elkConnectorValidationInfo.getBaseUrl())
                                              .build();
    String isValid = (String) dataCollectionDSLService.execute(validationDSL, runtimeParameters, callDetails -> {});
    assertThat(isValid).isEqualTo("true");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_validationWithInValidSettings() {
    // TODO: change url to one with have authentication enabled.
    ELKConnectorValidationInfo elkConnectorValidationInfo = ELKConnectorValidationInfo.builder().build();
    elkConnectorValidationInfo.setConnectorConfigDTO(
        ELKConnectorDTO.builder()
            .url("http://elk6.dev.harness.io:9200/")
            .authType(ELKAuthType.USERNAME_PASSWORD)
            .username("Invalid_user")
            .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .build());
    String validationDSL = elkConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkConnectorValidationInfo.getStartTime(instant))
                                              .endTime(elkConnectorValidationInfo.getEndTime(instant))
                                              .commonHeaders(elkConnectorValidationInfo.collectionHeaders())
                                              .baseUrl(elkConnectorValidationInfo.getBaseUrl())
                                              .build();
    String isValid = (String) dataCollectionDSLService.execute(validationDSL, runtimeParameters, callDetails -> {});
    assertThat(isValid).isEqualTo("true");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_validationWithInValidUrl() {
    ELKConnectorValidationInfo elkConnectorValidationInfo = ELKConnectorValidationInfo.builder().build();
    elkConnectorValidationInfo.setConnectorConfigDTO(
        ELKConnectorDTO.builder().url("http://elk6.dev.wrongurl.io:9200/").authType(ELKAuthType.NONE).build());
    String validationDSL = elkConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkConnectorValidationInfo.getStartTime(instant))
                                              .endTime(elkConnectorValidationInfo.getEndTime(instant))
                                              .commonHeaders(elkConnectorValidationInfo.collectionHeaders())
                                              .baseUrl(elkConnectorValidationInfo.getBaseUrl())
                                              .build();
    assertThatThrownBy(() -> dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {}))
        .isInstanceOf(DataCollectionDSLException.class);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_getIndex() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    ELKIndexCollectionRequest elkIndexCollectionRequest =
        ELKIndexCollectionRequest.builder()
            .connectorInfoDTO(ConnectorInfoDTO.builder()
                                  .connectorConfig(ELKConnectorDTO.builder()
                                                       .url("http://elk6.dev.harness.io:9200/")
                                                       .authType(ELKAuthType.NONE)
                                                       .build())
                                  .build())
            .build();

    String indexDSL = elkIndexCollectionRequest.getDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkIndexCollectionRequest.getStartTime(instant))
                                              .endTime(elkIndexCollectionRequest.getEndTime(instant))
                                              .commonHeaders(elkIndexCollectionRequest.collectionHeaders())
                                              .baseUrl(elkIndexCollectionRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(indexDSL, runtimeParameters, callDetails -> {});
    assertThat(result.size()).isEqualTo(31);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_getSampleData() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    ELKSampleDataCollectionRequest elkSampleDataCollectionRequest =
        ELKSampleDataCollectionRequest.builder()
            .index("*")
            .query("message: error")
            .connectorInfoDTO(ConnectorInfoDTO.builder()
                                  .connectorConfig(ELKConnectorDTO.builder()
                                                       .url("http://elk6.dev.harness.io:9200/")
                                                       .authType(ELKAuthType.NONE)
                                                       .build())
                                  .build())
            .build();

    String indexDSL = elkSampleDataCollectionRequest.getDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkSampleDataCollectionRequest.getStartTime(instant))
                                              .endTime(elkSampleDataCollectionRequest.getEndTime(instant))
                                              .otherEnvVariables(elkSampleDataCollectionRequest.fetchDslEnvVariables())
                                              .commonHeaders(elkSampleDataCollectionRequest.collectionHeaders())
                                              .baseUrl(elkSampleDataCollectionRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(indexDSL, runtimeParameters, callDetails -> {});
    assertThat(result.size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_ELK_DSL_getSampleDataSingleQuery() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    ELKSampleDataCollectionRequest elkSampleDataCollectionRequest =
        ELKSampleDataCollectionRequest.builder()
            .index("*")
            .query("error")
            .connectorInfoDTO(ConnectorInfoDTO.builder()
                                  .connectorConfig(ELKConnectorDTO.builder()
                                                       .url("http://elk6.dev.harness.io:9200/")
                                                       .authType(ELKAuthType.NONE)
                                                       .build())
                                  .build())
            .build();

    String indexDSL = elkSampleDataCollectionRequest.getDSL();
    Instant instant = Instant.parse("2020-10-30T10:44:48.164Z");
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(elkSampleDataCollectionRequest.getStartTime(instant))
                                              .endTime(elkSampleDataCollectionRequest.getEndTime(instant))
                                              .otherEnvVariables(elkSampleDataCollectionRequest.fetchDslEnvVariables())
                                              .commonHeaders(elkSampleDataCollectionRequest.collectionHeaders())
                                              .baseUrl(elkSampleDataCollectionRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(indexDSL, runtimeParameters, callDetails -> {});
    assertThat(result.size()).isEqualTo(10);
  }

  private RuntimeParameters getRuntimeParameters(String query, String serviceInstanceIdentifier, String index,
      String timeStampIdentifier, String messageIdentifier, String timeStampFormat) {
    Instant instant = Instant.parse("2023-01-10T19:30:38.498Z");
    ELKDataCollectionInfo dataCollectionInfo = ELKDataCollectionInfo.builder()
                                                   .query(query)
                                                   .index(index)
                                                   .serviceInstanceIdentifier(serviceInstanceIdentifier)
                                                   .messageIdentifier(messageIdentifier)
                                                   .timeStampIdentifier(timeStampIdentifier)
                                                   .timeStampFormat(timeStampFormat)
                                                   .build();
    dataCollectionInfo.setHostCollectionDSL(code);
    dataCollectionInfo.setCollectHostData(true);
    ELKConnectorDTO elkConnectorDTO =
        ELKConnectorDTO.builder().url("http://elk6.dev.harness.io:9200/").authType(ELKAuthType.NONE).build();
    return RuntimeParameters.builder()
        .baseUrl(dataCollectionInfo.getBaseUrl(elkConnectorDTO))
        .commonHeaders(dataCollectionInfo.collectionHeaders(elkConnectorDTO))
        .commonOptions(dataCollectionInfo.collectionParams(elkConnectorDTO))
        .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(elkConnectorDTO))
        .endTime(instant)
        .startTime(instant.minus(Duration.ofMinutes(1)))
        .build();
  }

  private String readDSL(String fileName) throws IOException {
    return Resources.toString(ELKCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
  }
}
