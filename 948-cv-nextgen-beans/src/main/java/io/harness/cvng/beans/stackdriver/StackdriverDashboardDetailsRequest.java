/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.utils.StackdriverUtils.Scope.METRIC_SCOPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.utils.StackdriverUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("STACKDRIVER_DASHBOARD_GET")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class StackdriverDashboardDetailsRequest extends StackdriverRequest {
  public static final String DSL = StackdriverDashboardRequest.readDSL(
      "stackdriver-dashboards-details.datacollection", StackdriverDashboardRequest.class);

  private String path;

  @Override
  public String getBaseUrl() {
    return "https://monitoring.googleapis.com/v1/";
  }

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> envVariables = StackdriverUtils.getCommonEnvVariables(getConnectorConfigDTO(), METRIC_SCOPE);
    envVariables.put("path", path);
    return envVariables;
  }
}
