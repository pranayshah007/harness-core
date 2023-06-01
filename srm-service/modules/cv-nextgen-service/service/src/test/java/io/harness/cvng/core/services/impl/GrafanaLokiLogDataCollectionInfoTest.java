/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.GrafanaLokiLogDataCollectionInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GrafanaLokiLogDataCollectionInfoTest extends CvNextGenTestBase {
  private static final String LOKI_BASE_URL = "http://127.0.0.1:3100";
  private GrafanaLokiLogDataCollectionInfo grafanaLokiLogDataCollectionInfo;
  private CustomHealthConnectorDTO customHealthConnectorDTO;

  @Before
  public void setup() throws IOException {
    grafanaLokiLogDataCollectionInfo = GrafanaLokiLogDataCollectionInfo.builder()
                                           .urlEncodedQuery("urlEncodedQuery")
                                           .serviceInstanceIdentifier("serviceInstanceIdentifier")
                                           .build();
    customHealthConnectorDTO = CustomHealthConnectorDTO.builder().baseURL(LOKI_BASE_URL).build();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables() {
    String finalRequestUrl = String.format("%s%s%s%s%d%s%s%s%s", customHealthConnectorDTO.getBaseURL(),
        "loki/api/v1/query_range?query=", "urlEncodedQuery", "&limit=", 5000,
        "&direction=forward&start=", "START_TIME_PLACEHOLDER", "&end=", "END_TIME_PLACEHOLDER");
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("requestUrl"))
        .isEqualTo(finalRequestUrl);
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO)
                   .get("serviceInstanceIdentifierPath"))
        .isEqualTo(String.format("%s%s", "$.stream.", "serviceInstanceIdentifier"));
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("logsListPath"))
        .isEqualTo("$.values");
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("logMessagePath"))
        .isEqualTo("$.[1]");
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("timestampPath"))
        .isEqualTo("$.[0]");
    assertThat(
        grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("startTimePlaceholder"))
        .isEqualTo("START_TIME_PLACEHOLDER");
    assertThat(grafanaLokiLogDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO).get("endTimePlaceholder"))
        .isEqualTo("END_TIME_PLACEHOLDER");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    assertThat(grafanaLokiLogDataCollectionInfo.getBaseUrl(customHealthConnectorDTO)).isEqualTo(LOKI_BASE_URL + "/");
  }
}