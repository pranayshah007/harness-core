/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_CLUSTER_RESOURCE;

import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(LOG_CLUSTER_RESOURCE)
@Path(LOG_CLUSTER_RESOURCE)
@Produces("application/json")
public class LogClusterResource {
  @Inject private LogClusterService logClusterService;

  @GET
  @Path("/test-data")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get test log data for a clustering", nickname = "getTestLogDataForClustering")
  public RestResponse<List<LogClusterDTO>> getTestData(@QueryParam("verificationTaskId") String verificationTaskId,
      @QueryParam("clusterLevel") LogClusterLevel logClusterLevel, @QueryParam("startTime") Long startTime,
      @QueryParam("endTime") Long endTime, @QueryParam("host") String host) {
    return new RestResponse<>(logClusterService.getDataForLogCluster(
        verificationTaskId, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), host, logClusterLevel));
  }
  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/l1-test-verification-test-data")
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get test log data of L1 clustering", nickname = "getL1LogClusteringDataForTestVerification")
  public RestResponse<List<LogClusterDTO>> getL1TestVerificationTestData(
      @QueryParam("baselineVerificationTaskId") String baselineVerificationTaskId,
      @NotNull @QueryParam("verificationTaskId") String verificationTaskId,
      @NotNull @QueryParam("startTime") Long startTime, @NotNull @QueryParam("endTime") Long endTime) {
    return new RestResponse<>(logClusterService.getL1TestVerificationTestData(baselineVerificationTaskId,
        verificationTaskId, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/save-clustered-logs")
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "saves clustered log data", nickname = "saveClusteredData")
  public RestResponse<Boolean> saveClusteredData(@QueryParam("taskId") String taskId,
      @QueryParam("verificationTaskId") String verificationTaskId, @QueryParam("timestamp") String timestamp,
      @QueryParam("clusterLevel") LogClusterLevel clusterLevel, List<LogClusterDTO> clusterDTO) {
    logClusterService.saveClusteredData(clusterDTO, verificationTaskId, Instant.parse(timestamp), taskId, clusterLevel);
    return new RestResponse<>(true);
  }
}
