/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("/metric-pack")
@Path("/metric-pack")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class MetricPackResource {
  @Inject private MetricPackService metricPackService;

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all metric packs for a ConnectorDisconnectHandler type", nickname = "getMetricPacks")
  public RestResponse<List<MetricPackDTO>> getMetricPacks(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("dataSourceType") @NotNull DataSourceType dataSourceType) {
    return new RestResponse<>(metricPackService.getMetricPacks(dataSourceType, projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves a metric pack for a ConnectorDisconnectHandler type", nickname = "saveMetricPacks")
  public RestResponse<Boolean> saveMetricPacks(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("dataSourceType") @NotNull DataSourceType dataSourceType,
      @NotNull @Valid @Body List<MetricPack> metricPacks) {
    return new RestResponse<>(metricPackService.saveMetricPacks(projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), dataSourceType, metricPacks));
  }
}
