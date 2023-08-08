/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.pagerduty.PagerDutyServiceDetail;
import io.harness.cvng.beans.pagerduty.PagerDutyServicesRequest;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyConnectorDTO;
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

public class PagerDutyDataCollectionDSLTestSuiteChild extends DSLHoverflyTestSuiteChildBase {
  private DataCollectionDSLService dataCollectionDSLService;
  private String code;
  private ExecutorService executorService;

  @Before
  public void setup() throws IOException {
    executorService = Executors.newFixedThreadPool(10);
    dataCollectionDSLService = new DataCollectionServiceImpl();
    code = readDSL("pagerduty-services.datacollection");
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testPagerDutyServices_withoutQuery() {
    RuntimeParameters runtimeParameters = getRuntimeParameters(null);
    List<PagerDutyServiceDetail> pagerDutyServiceDetails =
        (List<PagerDutyServiceDetail>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(pagerDutyServiceDetails.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testPagerDutyServices_withQuery() {
    RuntimeParameters runtimeParameters = getRuntimeParameters("cv");
    List<PagerDutyServiceDetail> pagerDutyServiceDetails =
        (List<PagerDutyServiceDetail>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(pagerDutyServiceDetails.size()).isEqualTo(2);
  }

  private RuntimeParameters getRuntimeParameters(String query) {
    Instant instant = Instant.parse("2022-09-18T12:13:58.167Z");

    PagerDutyServicesRequest pagerDutyServicesRequest =
        PagerDutyServicesRequest.builder()
            .query(query)
            .connectorInfoDTO(
                ConnectorInfoDTO.builder()
                    .connectorConfig(
                        PagerDutyConnectorDTO.builder()
                            .apiTokenRef(SecretRefData.builder().decryptedValue("key".toCharArray()).build())
                            .build())
                    .build())
            .build();

    return RuntimeParameters.builder()
        .baseUrl(pagerDutyServicesRequest.getBaseUrl())
        .commonHeaders(pagerDutyServicesRequest.collectionHeaders())
        .otherEnvVariables(pagerDutyServicesRequest.fetchDslEnvVariables())
        .endTime(instant)
        .startTime(instant.minus(Duration.ofMinutes(1)))
        .build();
  }

  private String readDSL(String fileName) throws IOException {
    return Resources.toString(PagerDutyServicesRequest.class.getResource(fileName), StandardCharsets.UTF_8);
  }
}
