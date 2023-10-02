/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.stackdriver.StackdriverLogSampleDataRequest;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.LogDataRecord;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StackdriverLogDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  private DataCollectionDSLService dataCollectionDSLService;

  @Before
  public void setup() {
    dataCollectionDSLService = new DataCollectionServiceImpl();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @Ignore("Only for local testing as hoverfly capture model does not work.")
  public void testEExecute_stackdriverDSL() {
    String filePath = "splunk/splunk-response.json";
    Instant now = Instant.now();
    // HOVERFLY_RULE.simulate(SimulationSource.file(Paths.get(getResourceFilePath("hoverfly/" + filePath))));
    DSLSuiteTest.HOVERFLY_RULE.capture(filePath);
    StackdriverLogSampleDataRequest dataCollectionRequest =
        StackdriverLogSampleDataRequest.builder()
            .type(DataCollectionRequestType.STACKDRIVER_LOG_SAMPLE_DATA)
            .query("query")
            .connectorInfoDTO(
                ConnectorInfoDTO.builder()
                    .connectorConfig(
                        GcpConnectorDTO.builder()
                            .credential(
                                GcpConnectorCredentialDTO.builder()
                                    .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                    .config(
                                        GcpManualDetailsDTO.builder()
                                            .secretKeyRef(
                                                SecretRefData.builder()
                                                    .decryptedValue(
                                                        "<put service json here. This fails with cert error in bazel.>"
                                                            .toCharArray())
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .startTime(now.minus(Duration.ofMinutes(60)))
            .endTime(now)
            .build();
    Map<String, Object> env = new HashMap<>();
    env.put("accessToken", "run command - gcloud beta auth application-default print-access-token");
    env.putAll(dataCollectionRequest.fetchDslEnvVariables());
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .baseUrl(dataCollectionRequest.getBaseUrl())
                                              .commonHeaders(dataCollectionRequest.collectionHeaders())
                                              .commonOptions(dataCollectionRequest.collectionParams())
                                              .otherEnvVariables(env)
                                              .endTime(dataCollectionRequest.getEndTime(now))
                                              .startTime(dataCollectionRequest.getStartTime(now))
                                              .build();

    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(dataCollectionRequest.getDSL(), runtimeParameters);
    assertThat(logDataRecords).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_stackdriverDSL() {
    Instant now = Instant.parse("2022-07-26T00:00:00.00Z");
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder().build();
    ConnectorInfoDTO gcpConnectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(gcpConnectorDTO).build();
    StackdriverLogSampleDataRequest dataCollectionRequest =
        StackdriverLogSampleDataRequest.builder()
            .type(DataCollectionRequestType.STACKDRIVER_LOG_SAMPLE_DATA)
            .query(
                "resource.type=\"k8s_container\"\nresource.labels.project_id=\"chi-play\"\nresource.labels.location=\"us-central1-c\"")
            .connectorInfoDTO(gcpConnectorInfoDTO)
            .startTime(now.minus(Duration.ofMinutes(60)))
            .endTime(now)
            .build();
    Map<String, Object> env = new HashMap<>();
    env.put("project", "chi-play");
    env.put("accessToken", accessToken);
    env.putAll(dataCollectionRequest.fetchDslEnvVariables());
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .baseUrl(dataCollectionRequest.getBaseUrl())
                                              .commonHeaders(dataCollectionRequest.collectionHeaders())
                                              .commonOptions(dataCollectionRequest.collectionParams())
                                              .otherEnvVariables(env)
                                              .endTime(dataCollectionRequest.getEndTime(now))
                                              .startTime(dataCollectionRequest.getStartTime(now))
                                              .build();

    List<LogDataRecord> logDataRecords =
        (List<LogDataRecord>) dataCollectionDSLService.execute(dataCollectionRequest.getDSL(), runtimeParameters);
    assertThat(logDataRecords).isNotNull();
  }
}
