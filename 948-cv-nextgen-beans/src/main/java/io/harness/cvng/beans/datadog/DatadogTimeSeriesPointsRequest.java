/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.datadog;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DATADOG_TIME_SERIES_POINTS")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
@FieldNameConstants(innerTypeName = "DatadogTimeSeriesPointsRequestKeys")
@EqualsAndHashCode(callSuper = true)
public class DatadogTimeSeriesPointsRequest extends DatadogRequest {
  private static final String DSL =
      DataCollectionRequest.readDSL("datadog-time-series-points.datacollection", DatadogTimeSeriesPointsRequest.class);

  private Long from;
  private Long to;
  private String query;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonVariables = super.fetchDslEnvVariables();
    commonVariables.put(DatadogTimeSeriesPointsRequestKeys.from, from);
    commonVariables.put(DatadogTimeSeriesPointsRequestKeys.to, to);
    commonVariables.put(DatadogTimeSeriesPointsRequestKeys.query, query);
    return commonVariables;
  }
}
