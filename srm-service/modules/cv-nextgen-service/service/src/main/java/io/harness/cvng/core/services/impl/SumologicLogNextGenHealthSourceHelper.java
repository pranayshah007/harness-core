/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.sumologic.SumologicLogSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SumologicLogNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  private static final String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss";

  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<SumoLogicConnectorDTO> request;
    LocalDateTime startTime = Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime())
                                  .atZone(ZoneId.systemDefault())
                                  .toLocalDateTime();
    LocalDateTime endTime =
        Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_STRING);
    request = SumologicLogSampleDataRequest.builder()
                  .from(startTime.format(formatter))
                  .to(endTime.format(formatter))
                  .dsl(MetricPackServiceImpl.SUMOLOGIC_LOG_SAMPLE_DSL)
                  .query(healthSourceRecordsRequest.getQuery().trim())
                  .type(DataCollectionRequestType.SUMOLOGIC_LOG_SAMPLE_DATA)
                  .build();
    return request;
  }
}
