/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.appd;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.delegate.beans.cvng.appd.AppDynamicsUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonTypeName("APPDYNAMICS_GET_SINGLE_METRIC_DATA")
@OwnedBy(CV)
public class AppDynamicSingleMetricDataRequest extends AppDynamicsDataCollectionRequest {
  public static final String DSL = AppDynamicsDataCollectionRequest.readDSL(
      "appd-single-metric-data.datacollection", AppDynamicsDataCollectionRequest.class);

  public AppDynamicSingleMetricDataRequest() {
    setType(DataCollectionRequestType.APPDYNAMICS_GET_SINGLE_METRIC_DATA);
  }

  @NonNull private String applicationName;
  @NonNull private Instant startTime;
  @NonNull private Instant endTime;
  private String metricPath;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = AppDynamicsUtils.getCommonEnvVariables(getConnectorConfigDTO());
    dslEnvVariables.put("applicationName", getApplicationName());
    dslEnvVariables.put("startTimeInMilliSeconds", startTime.toEpochMilli());
    dslEnvVariables.put("endTimeInMilliSeconds", endTime.toEpochMilli());
    dslEnvVariables.put("metricPath", metricPath);
    return dslEnvVariables;
  }
}
