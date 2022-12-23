/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.PROJECT_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.VerificationOverview;
import io.harness.cvng.cdng.beans.v2.VerifyStepPathParams;
import io.harness.ng.beans.PageResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path(PROJECT_PATH + "/verifications/{verifyStepExecutionId}")
@Api(PROJECT_PATH + "/verifications/{verifyStepExecutionId}")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public interface VerifyStepResource {
  @GET
  @Path("/transaction-groups")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the transaction groups", nickname = "getTransactionGroups")
  List<String> getTransactionGroups(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @GET
  @Path("/health-sources")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the health sources", nickname = "getHealthSources")
  List<HealthSource> getHealthSources(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @GET
  @Path("/overview")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get verification overview for given verifyStepExecutionId",
      nickname = "getVerificationOverviewForVerifyStepExecutionId")
  List<VerificationOverview>
  getVerificationOverviewForVerifyStepExecutionId(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams);

  @GET
  @Path("/analysis/metrics")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metrics analysis for given verifyStepExecutionId",
      nickname = "getMetricsAnalysisForVerifyStepExecutionId")
  PageResponse<MetricsAnalysis>
  getMetricsAnalysisForVerifyStepExecutionId(@BeanParam @Valid VerifyStepPathParams verifyStepPathParams,
      @QueryParam("anomalousMetricsOnly") @DefaultValue("false") boolean anomalousMetricsOnly,
      @QueryParam("healthSource") List<String> healthSource, @QueryParam("node") List<String> node,
      @QueryParam("limit") @DefaultValue("30") @Min(1) int limit,
      @QueryParam("page") @DefaultValue("1") @Min(1) int page);
}
