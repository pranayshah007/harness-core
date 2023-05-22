/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.grafanaloki.GrafanaLokiLogSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrafanaLokiLogNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    Long startTime = convertEpochMillisToSeconds(healthSourceRecordsRequest.getStartTime());
    Long endTime = convertEpochMillisToSeconds(healthSourceRecordsRequest.getEndTime());
    try {
      return GrafanaLokiLogSampleDataRequest.builder()
          .startTimeInSeconds(startTime)
          .endTimeInSeconds(endTime)
          .dsl(MetricPackServiceImpl.GRAFANA_LOKI_LOG_SAMPLE_DATA_DSL)
          .query(encodeValue(healthSourceRecordsRequest.getQuery().trim()))
          .type(DataCollectionRequestType.GRAFANA_LOKI_LOG_SAMPLE_DATA)
          .build();
    } catch (UnsupportedEncodingException e) {
      log.error("Unable to encide the query {}", healthSourceRecordsRequest.getQuery(), e);
      throw new IllegalArgumentException();
    }
  }
  private static long convertEpochMillisToSeconds(long millis) {
    return Instant.ofEpochMilli(millis).getEpochSecond();
  }
  private static String encodeValue(String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
