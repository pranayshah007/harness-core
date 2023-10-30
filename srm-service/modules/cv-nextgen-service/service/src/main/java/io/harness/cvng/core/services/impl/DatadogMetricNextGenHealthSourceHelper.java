/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.datadog.DatadogTimeSeriesPointsRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.services.api.NextGenHealthSourceHelper;
import io.harness.cvng.utils.DatadogQueryUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class DatadogMetricNextGenHealthSourceHelper implements NextGenHealthSourceHelper {
  @Override
  public DataCollectionRequest<? extends ConnectorConfigDTO> getDataCollectionRequest(
      HealthSourceRecordsRequest healthSourceRecordsRequest) {
    DataCollectionRequest<DatadogConnectorDTO> request;
    String serviceInstanceField = healthSourceRecordsRequest.getHealthSourceQueryParams().getServiceInstanceField();
    String query = healthSourceRecordsRequest.getQuery();
    Pair<String, List<String>> formulaQueriesPair =
        DatadogQueryUtils.processCompositeQuery(query, serviceInstanceField, false);
    String formula = formulaQueriesPair.getLeft();
    List<String> formulaQueries = formulaQueriesPair.getRight();
    try {
      request = DatadogTimeSeriesPointsRequest.builder()
                    .from(healthSourceRecordsRequest.getStartTime())
                    .to(healthSourceRecordsRequest.getEndTime())
                    .query(query.trim())
                    .type(DataCollectionRequestType.DATADOG_TIME_SERIES_POINTS)
                    .DSL(Resources.toString(DatadogServiceImpl.DATADOG_SAMPLE_V2_DSL_PATH, Charsets.UTF_8))
                    .formula(formula)
                    .formulaQueriesList(formulaQueries)
                    .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return request;
  }
}
