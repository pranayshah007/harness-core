/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.sumologic.SumologicMetricSampleDataRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;

public class SumologicMetricNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<SumoLogicConnectorDTO> request;
    request = SumologicMetricSampleDataRequest.builder()
                  .from(healthSourceRecordsRequest.getStartTime())
                  .to(healthSourceRecordsRequest.getEndTime())
                  .dsl(MetricPackServiceImpl.SUMOLOGIC_METRIC_SAMPLE_DSL)
                  .query(healthSourceRecordsRequest.getQuery().trim())
                  .type(DataCollectionRequestType.SUMOLOGIC_METRIC_SAMPLE_DATA)
                  .build();
    return request;
  }
}
