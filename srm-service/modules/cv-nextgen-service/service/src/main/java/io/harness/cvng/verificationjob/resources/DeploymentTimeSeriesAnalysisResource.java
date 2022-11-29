/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("deployment-time-series-analysis")
@Path("deployment-time-series-analysis")
@Produces("application/json")
@ExposeInternalException
public class DeploymentTimeSeriesAnalysisResource {
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/{verificationJobInstanceId}")
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get metrics for given verificationJob", nickname = "getMetrics")
  public RestResponse<TransactionMetricInfoSummaryPageDTO> getMetrics(
      @PathParam("verificationJobInstanceId") String verificationJobInstanceId,
      @QueryParam("accountId") String accountId,
      @BeanParam DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter,
      @BeanParam PageParams pageParams) {
    return new RestResponse(deploymentTimeSeriesAnalysisService.getMetrics(
        accountId, verificationJobInstanceId, deploymentTimeSeriesAnalysisFilter, pageParams));
  }
}
