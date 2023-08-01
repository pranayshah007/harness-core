/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DatadogLogDataCollectionInfo;
import io.harness.cvng.beans.datadog.DatadogLogDefinition;
import io.harness.cvng.core.entities.DatadogLogCVConfig;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
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

public class DatadogLogDataCollectionDSLTestSuiteChild extends DSLHoverflyTestSuiteChildBase {
  private DataCollectionDSLService dataCollectionDSLService;
  private String code;
  private ExecutorService executorService;

  @Before
  public void setup() throws IOException {
    executorService = Executors.newFixedThreadPool(10);
    dataCollectionDSLService = new DataCollectionServiceImpl();
    code = readDSL("datadog-log-fetch-data.datacollection");
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogLogDSL_withoutIndex() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("service:todolist*", "host", null);
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(500);
    assertThat(logDataRecords.get(0).getHostname())
        .isEqualTo("gke-chi-play-general-preemptible-opti-986a2f33-rhnv.c.chi-play.internal");
    assertThat(logDataRecords.get(0).getLog()).contains(">");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(1658401604705L);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogLogDSL_withServiceInstanceDefinitionFromTags() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("service:todolist*", "container_name", null);
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(500);
    assertThat(logDataRecords.get(0).getHostname())
        .isEqualTo(
            "k8s_pqa-san-appd-appd_pqa-san-appd-appd-deployment-84dc67585f-2s24x_default_4c7f33af-d5ae-4e2e-8901-0876d2d869aa_0");
    assertThat(logDataRecords.get(0).getLog()).contains(">");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(1658401604705L);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogLogDSL_withNoServiceInstanceDefinition() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("service:todolist*", null, null);
    assertThatThrownBy(() -> dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {}))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogLogDSL_withNoMatchingServiceInstanceDefinition() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("service:todolist*", "0000000000000000", null);
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(500);
    assertThat(logDataRecords.get(0).getHostname())
        .isEqualTo("gke-chi-play-general-preemptible-opti-986a2f33-rhnv.c.chi-play.internal");
    assertThat(logDataRecords.get(0).getLog()).contains(">");
    assertThat(logDataRecords.get(0).getTimestamp()).isEqualTo(1658401604705L);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_datadogLogDSL_withQueryWithNoData() {
    final RuntimeParameters runtimeParameters = getRuntimeParameters("service:shopping*", "host", null);
    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(logDataRecords).isNotNull();
    assertThat(logDataRecords).hasSize(0);
  }

  private String readDSL(String fileName) throws IOException {
    return Resources.toString(DatadogLogCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
  }

  private RuntimeParameters getRuntimeParameters(String query, String serviceInstanceIdentifier, List<String> indexes) {
    Instant instant = Instant.parse("2022-07-21T11:06:44.711Z");
    DatadogLogDefinition datadogLogDefinition = DatadogLogDefinition.builder()
                                                    .query(query)
                                                    .serviceInstanceIdentifier(serviceInstanceIdentifier)
                                                    .name("Datadog Logs Query")
                                                    .indexes(indexes)
                                                    .build();
    DatadogLogDataCollectionInfo dataCollectionInfo =
        DatadogLogDataCollectionInfo.builder().logDefinition(datadogLogDefinition).build();
    dataCollectionInfo.setHostCollectionDSL(code);
    dataCollectionInfo.setCollectHostData(true);
    DatadogConnectorDTO datadogConnectorDTO =
        DatadogConnectorDTO.builder()
            .url("https://app.datadoghq.com/api/")
            .apiKeyRef(SecretRefData.builder().decryptedValue("add key".toCharArray()).build())
            .applicationKeyRef(SecretRefData.builder().decryptedValue("add key".toCharArray()).build())
            .build();
    return RuntimeParameters.builder()
        .baseUrl(dataCollectionInfo.getBaseUrl(datadogConnectorDTO))
        .commonHeaders(dataCollectionInfo.collectionHeaders(datadogConnectorDTO))
        .commonOptions(dataCollectionInfo.collectionParams(datadogConnectorDTO))
        .otherEnvVariables(dataCollectionInfo.getDslEnvVariables(datadogConnectorDTO))
        .endTime(instant)
        .startTime(instant.minus(Duration.ofMinutes(1)))
        .build();
  }
}
