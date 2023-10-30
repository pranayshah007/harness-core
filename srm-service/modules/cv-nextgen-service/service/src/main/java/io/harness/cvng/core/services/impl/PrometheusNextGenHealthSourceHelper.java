/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.prometheus.PrometheusFetchSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import java.time.Instant;

public class PrometheusNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    String query = healthSourceRecordsRequest.getQuery();
    return PrometheusFetchSampleDataRequest.builder()
        .type(DataCollectionRequestType.PROMETHEUS_SAMPLE_DATA)
        .query(query.trim())
        .startTime(Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime()))
        .endTime(Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()))
        .build();
  }
}
