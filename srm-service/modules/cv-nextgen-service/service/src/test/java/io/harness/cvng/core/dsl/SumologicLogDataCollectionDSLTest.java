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
import io.harness.cvng.HoverflyTestBase;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
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

public class SumologicLogDataCollectionDSLTest extends HoverflyTestBase {
  private static final String SECRET_REF_DATA = "Dummy_Secret_Ref";
  private static final int THREADS = 10;
  private static final int LOG_RECORDS_COUNT = 50;

  DataCollectionDSLService dataCollectionDSLService;
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
                            .url("https://api.in.sumologic.com/")
                            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
                            .accessKeyRef(SecretRefData.builder()
                                              .decryptedValue(SECRET_REF_DATA.toCharArray())
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
    List<?> result = (List<?>) dataCollectionDSLService.execute(
        sampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
    assertThat(result).hasSize(LOG_RECORDS_COUNT);
  }
}