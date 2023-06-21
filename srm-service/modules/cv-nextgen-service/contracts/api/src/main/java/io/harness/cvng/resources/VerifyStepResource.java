/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.VERIFICATIONS_RESOURCE_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.cdng.beans.v2.Baseline;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries;
import io.harness.cvng.cdng.beans.v2.VerificationOverview;
import io.harness.cvng.cdng.beans.v2.VerifyStepPathParams;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path(VERIFICATIONS_RESOURCE_PATH)
@Api(VERIFICATIONS_RESOURCE_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public interface VerifyStepResource {
  @GET
  @Path("/transaction-groups")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the transaction groups", nickname = "getTransactionGroupsForVerifyStepExecutionId")
  List<String> getTransactionGroupsForVerifyStepExecutionId(
      @BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @GET
  @Path("/health-sources")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the health sources", nickname = "getHealthSourcesForVerifyStepExecutionId")
  List<HealthSource> getHealthSourcesForVerifyStepExecutionId(
      @BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @GET
  @Path("/overview")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get verification overview for given verifyStepExecutionId",
      nickname = "getVerificationOverviewForVerifyStepExecutionId")
  VerificationOverview
  getVerificationOverviewForVerifyStepExecutionId(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @POST
  @Path("/baseline")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "use the verification as a baseline", nickname = "updateBaseline")
  Baseline updateBaseline(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams, Baseline baseline);

  @GET
  @Path("/analysis/metrics")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metrics analysis for given verifyStepExecutionId",
      nickname = "getMetricsAnalysisForVerifyStepExecutionId")
  PageResponse<MetricsAnalysis>
  getMetricsAnalysisForVerifyStepExecutionId(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams,
      @QueryParam("anomalousMetricsOnly") @DefaultValue("false") boolean anomalousMetricsOnly,
      @QueryParam("healthSource") List<String> healthSources,
      @QueryParam("transactionGroup") List<String> transactionGroups, @QueryParam("node") List<String> nodes,
      @BeanParam @Valid PageRequest pageRequest);

  @GET
  @Path("/debug/metrics/time-series")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metrics time-series for given verifyStepExecutionId",
      nickname = "getMetricsTimeSeriesForVerifyStepExecutionId")
  VerificationMetricsTimeSeries
  getMetricsTimeSeriesForVerifyStepExecutionId(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams);
}
