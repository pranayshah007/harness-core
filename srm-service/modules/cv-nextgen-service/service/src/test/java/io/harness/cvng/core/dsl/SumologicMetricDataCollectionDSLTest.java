/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.MetricPackServiceImpl;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SumologicMetricDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  private static final int THREADS = 10;
  private static final long FROM_EPOCH_TIME = 1668431100000L;
  private static final long TO_EPOCH_TIME = 1668431400000L;
  private static final int FIVE_MINUTES = 5;
  private static final String SECRET_REF_DATA = "Dummy_Secret_Ref";
  BuilderFactory builderFactory;
  @Inject MetricPackService metricPackService;
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
  public void testExecute_SumologicMetricSampleData() {
    String metricSampleDataRequestDSL = MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL;
    SumoLogicConnectorDTO SumologiconnectorDTO =
        SumoLogicConnectorDTO.builder()
            .url("https://api.in.sumologic.com/")
            .accessIdRef(SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build())
            .accessKeyRef(
                SecretRefData.builder().decryptedValue(SECRET_REF_DATA.toCharArray()).build()) // TODO Use encrypted
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
            .startTime(instant.minus(Duration.ofMinutes(FIVE_MINUTES))) // TODO find use of these
            .endTime(instant)
            .commonHeaders(headers)
            .otherEnvVariables(params)
            .baseUrl("https://api.in.sumologic.com/")
            .build();
    dataCollectionDSLService.execute(metricSampleDataRequestDSL, runtimeParameters, (CallDetails callDetails) -> {});
  }
}