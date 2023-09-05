/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DatadogLogDataCollectionInfo;
import io.harness.cvng.beans.datadog.DatadogLogSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

public class DatadogLogNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    return DatadogLogSampleDataRequest.builder()
        .type(DataCollectionRequestType.DATADOG_LOG_SAMPLE_DATA)
        .from(healthSourceRecordsRequest.getStartTime())
        .to(healthSourceRecordsRequest.getEndTime())
        .limit(DatadogLogDataCollectionInfo.LOG_MAX_LIMIT)
        .query(healthSourceRecordsRequest.getQuery().trim())
        .build();
  }
}
