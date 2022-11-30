/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.resources;

import static io.harness.cvng.analysis.CVAnalysisConstants.DEPLOYMENT_LOG_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.PREVIOUS_ANALYSIS_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.PREVIOUS_LOG_ANALYSIS_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TEST_DATA_PATH;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
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
import lombok.extern.slf4j.Slf4j;

@Api(LOG_ANALYSIS_RESOURCE)
@Path(LOG_ANALYSIS_RESOURCE)
@Produces("application/json")
@Slf4j
public class LogAnalysisResource {
  @Inject LogAnalysisService logAnalysisService;

  @GET
  @Path("/" + TEST_DATA_PATH)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get test log data for a verification job", nickname = "getTestLogData")
  public RestResponse<List<LogClusterDTO>> getTestData(@QueryParam("verificationTaskId") String verificationTaskId,
      @NotNull @QueryParam("analysisStartTime") Long analysisStartTime,
      @NotNull @QueryParam("analysisEndTime") Long analysisEndTime) {
    return new RestResponse<>(logAnalysisService.getTestData(
        verificationTaskId, Instant.ofEpochMilli(analysisStartTime), Instant.ofEpochMilli(analysisEndTime)));
  }

  @GET
  @Path("/" + PREVIOUS_LOG_ANALYSIS_PATH)
  @Timed
  @ExceptionMetered
  @LearningEngineAuth
  @ApiOperation(value = "get previous log analysis for a data source config", nickname = "getPreviousLogAnalysis")
  public RestResponse<List<LogAnalysisCluster>> getPreviousAnalysis(
      @QueryParam("verificationTaskId") String verificationTaskId,
      @QueryParam("analysisStartTime") String analysisStartTime,
      @QueryParam("analysisEndTime") String analysisEndTime) {
    return new RestResponse<>(logAnalysisService.getPreviousAnalysis(
        verificationTaskId, Instant.parse(analysisStartTime), Instant.parse(analysisEndTime)));
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/" + LOG_ANALYSIS_SAVE_PATH)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "saves log risk analysis for a data source config", nickname = "saveServiceGuardLogAnalysis")
  public RestResponse<Boolean> saveServiceGuardAnalysis(
      @QueryParam("taskId") String taskId, LogAnalysisDTO analysisBody) {
    logAnalysisService.saveAnalysis(taskId, analysisBody);
    return new RestResponse<>(true);
  }

  @Produces({"application/json", "application/v1+json"})
  @POST
  @Path("/" + DEPLOYMENT_LOG_ANALYSIS_SAVE_PATH)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(value = "saves log risk analysis for a deployment verification", nickname = "saveDeploymentAnalysis")
  public RestResponse<Void> saveDeploymentAnalysis(
      @QueryParam("taskId") String taskId, DeploymentLogAnalysisDTO deploymentLogAnalysisDTO) {
    logAnalysisService.saveAnalysis(taskId, deploymentLogAnalysisDTO);
    return new RestResponse<>(null);
  }

  @Produces({"application/json", "application/v1+json"})
  @GET
  @Path("/" + PREVIOUS_ANALYSIS_URL)
  @Timed
  @LearningEngineAuth
  @ExceptionMetered
  @ApiOperation(
      value = "get previous deployment analysis result for next task", nickname = "getPreviousDeploymentAnalysis")
  public RestResponse<DeploymentLogAnalysisDTO>
  getPreviousDeploymentAnalysis(@QueryParam("verificationTaskId") String verificationTaskId,
      @QueryParam("analysisStartTime") String analysisStartTime,
      @QueryParam("analysisEndTime") String analysisEndTime) {
    return new RestResponse<>(logAnalysisService.getPreviousDeploymentAnalysis(verificationTaskId,
        Instant.ofEpochMilli(Long.parseLong(analysisStartTime)),
        Instant.ofEpochMilli(Long.parseLong(analysisEndTime))));
  }
}
