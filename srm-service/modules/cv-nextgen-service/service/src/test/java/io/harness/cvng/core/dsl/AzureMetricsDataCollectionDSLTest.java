/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.KARAN_SARASWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.HoverflyTestBase;
import io.harness.cvng.beans.azure.AzureMetricsSampleDataRequest;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureMetricsDataCollectionDSLTest extends HoverflyTestBase {
  private static final int THREADS = 3;
  private final String query = "node_cpu_usage_millicores";
  private final Instant startTime = Instant.ofEpochMilli(1689279600000L);
  private final Instant endTime = Instant.ofEpochMilli(1689280200000L);
  private final String resourceId =
      "/subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319/resourceGroups/srm-test/providers/Microsoft.ContainerService/managedClusters/srm-test";

  ConnectorInfoDTO connectorInfoDTO =
      ConnectorInfoDTO.builder()
          .connectorConfig(
              AzureConnectorDTO.builder()
                  .credential(
                      AzureCredentialDTO.builder()
                          .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                          .config(AzureManualDetailsDTO.builder()
                                      .clientId("***")
                                      .tenantId("b229b2bb-5f33-4d22-bce0-730f6474e906")
                                      .authDTO(AzureAuthDTO.builder()
                                                   .azureSecretType(AzureSecretType.SECRET_KEY)
                                                   .credentials(AzureClientSecretKeyDTO.builder()
                                                                    .secretKey(SecretRefData.builder()
                                                                                   .decryptedValue("***".toCharArray())
                                                                                   .build())
                                                                    .build())
                                                   .build())
                                      .build())
                          .build())
                  .build())
          .build();

  DataCollectionDSLService dataCollectionDSLService;

  @Before
  public void setup() throws IOException {
    super.before();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testExecute_AzureMetrics_DSL_getSampleData() {
    String sampleDataRequestDSL = MetricPackServiceImpl.AZURE_METRICS_SAMPLE_DATA_DSL;
    AzureMetricsSampleDataRequest azureMetricsSampleDataRequest = AzureMetricsSampleDataRequest.builder()
                                                                      .metricName(query)
                                                                      .from(startTime)
                                                                      .to(endTime)
                                                                      .dsl(sampleDataRequestDSL)
                                                                      .resourceId(resourceId)
                                                                      .connectorInfoDTO(connectorInfoDTO)
                                                                      .build();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(azureMetricsSampleDataRequest.getFrom())
                                              .endTime(azureMetricsSampleDataRequest.getTo())
                                              .otherEnvVariables(azureMetricsSampleDataRequest.fetchDslEnvVariables())
                                              .commonHeaders(azureMetricsSampleDataRequest.collectionHeaders())
                                              .baseUrl(azureMetricsSampleDataRequest.getBaseUrl())
                                              .build();
    List<?> result = (List<?>) dataCollectionDSLService.execute(
        sampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(10);
  }
}
