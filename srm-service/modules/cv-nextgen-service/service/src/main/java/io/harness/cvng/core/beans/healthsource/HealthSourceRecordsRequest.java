/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.healthsource;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthSourceRecordsRequest {
  @NotNull String connectorIdentifier;
  @NotNull String query;
  @NotNull Long startTime;
  @NotNull Long endTime;
  MonitoredServiceDataSourceType healthSourceType; // we need to add validation here once UI is merged.
  DataSourceType providerType;
  QueryParamsDTO healthSourceQueryParams = QueryParamsDTO.builder().build();
  HealthSourceParamsDTO healthSourceParams = HealthSourceParamsDTO.builder().build();

  public void validate() {
    if (providerType == null && healthSourceType == null) {
      throw new BadRequestException("datasourceType cannot be inferred from request:" + healthSourceType);
    }
  }
}
