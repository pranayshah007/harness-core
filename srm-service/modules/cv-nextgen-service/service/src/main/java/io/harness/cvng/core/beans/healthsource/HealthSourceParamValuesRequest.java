/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.healthsource;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthSourceParamValuesRequest {
  @NotNull String paramName;
  QueryParamsDTO healthSourceQueryParams = QueryParamsDTO.builder().build();
  HealthSourceParamsDTO healthSourceParams = HealthSourceParamsDTO.builder().build();
  @NotNull MonitoredServiceDataSourceType providerType;
  String connectorIdentifier;

  public void validate() {
    if (providerType == MonitoredServiceDataSourceType.ELASTICSEARCH) {
      if (QueryParamsDTO.QueryParamKeys.index.equals(paramName)) {
        Preconditions.checkNotNull(connectorIdentifier, "The connector Identifer cannot be null");
      }
    }
  }
}
