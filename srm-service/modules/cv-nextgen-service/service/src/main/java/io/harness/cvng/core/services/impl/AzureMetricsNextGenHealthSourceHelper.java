/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.azure.AzureMetricsSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureMetricsNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    return AzureMetricsSampleDataRequest.builder()
        .dsl(MetricPackServiceImpl.AZURE_METRICS_SAMPLE_DATA_DSL)
        .from(Instant.ofEpochMilli(healthSourceRecordsRequest.getStartTime()))
        .to(Instant.ofEpochMilli(healthSourceRecordsRequest.getEndTime()))
        .metricName(healthSourceRecordsRequest.getQuery().trim())
        .resourceId(healthSourceRecordsRequest.getHealthSourceQueryParams().getIndex())
        .type(DataCollectionRequestType.AZURE_METRICS_SAMPLE_DATA)
        .build();
  }
}
