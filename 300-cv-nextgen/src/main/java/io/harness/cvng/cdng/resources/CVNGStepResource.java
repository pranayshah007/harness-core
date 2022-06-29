/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.activity.beans.DeploymentActivitySummaryDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListWithCountDTO;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.cdng.beans.InputSetTemplateRequest;
import io.harness.cvng.cdng.beans.InputSetTemplateResponse;
import io.harness.cvng.cdng.services.api.CVNGStepService;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.DeploymentLogsFilter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@Api("verify-step")
@Path("/verify-step")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class CVNGStepResource {
  @Inject private CVNGStepService cvngStepService;
  @Inject private CVNGStepTaskService stepTaskService;

  @POST
  @Path("input-set-template")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Given a template Add verify step to it if required", nickname = "inputSetTemplate")
  public ResponseDTO<InputSetTemplateResponse> updateInputSetTemplate(
      @NotNull @QueryParam("accountId") String accountId, InputSetTemplateRequest inputSetTemplateRequest) {
    return ResponseDTO.newResponse(
        InputSetTemplateResponse.builder()
            .inputSetTemplateYaml(cvngStepService.getUpdatedInputSetTemplate(inputSetTemplateRequest.getPipelineYaml()))
            .build());
  }

  @GET
  @Path("/{verifyStepExecutionId}/deployment-activity-summary")
  @ApiOperation(value = "get summary of deployment activity", nickname = "getVerifyStepDeploymentActivitySummary")
  public RestResponse<DeploymentActivitySummaryDTO> getDeploymentSummary(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @PathParam("verifyStepExecutionId") String callBackId) {
    return new RestResponse(stepTaskService.getDeploymentSummary(callBackId));
  }

  @GET
  @Path("/{verifyStepExecutionId}/deployment-timeseries-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metrics for given activity", nickname = "getVerifyStepDeploymentMetrics")
  public RestResponse<TransactionMetricInfoSummaryPageDTO> getMetrics(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotEmpty @NotNull @PathParam("verifyStepExecutionId") String callBackId,
      @BeanParam DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter,
      @BeanParam PageParams pageParams) {
    return new RestResponse(stepTaskService.getDeploymentActivityTimeSeriesData(
        accountId, callBackId, deploymentTimeSeriesAnalysisFilter, pageParams));
  }

  @GET
  @Path("/{verifyStepExecutionId}/healthSources")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get health sources  for an activity", nickname = "getVerifyStepHealthSources")
  public RestResponse<Set<HealthSourceDTO>> getHealthSources(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @NotEmpty @PathParam("verifyStepExecutionId") String callBackId) {
    return new RestResponse(stepTaskService.healthSources(accountId, callBackId));
  }

  @GET
  @Path("/{verifyStepExecutionId}/clusters")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get logs for given activity", nickname = "getVerifyStepDeploymentLogAnalysisClusters")
  public RestResponse<List<LogAnalysisClusterChartDTO>> getDeploymentLogAnalysisClusters(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @NotEmpty @PathParam("verifyStepExecutionId") String callBackId,
      @BeanParam DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    return new RestResponse(
        stepTaskService.getDeploymentActivityLogAnalysisClusters(accountId, callBackId, deploymentLogAnalysisFilter));
  }

  @GET
  @Path("/{verifyStepExecutionId}/deployment-log-analysis-radar-chart-clusters")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get radar chart logs clusters for given verify step",
      nickname = "getVerifyStepDeploymentRadarChartLogAnalysisClusters")
  public RestResponse<List<LogAnalysisRadarChartClusterDTO>>
  getDeploymentLogAnalysisRadarChartClusters(@NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotNull @NotEmpty @PathParam("verifyStepExecutionId") String callBackId,
      @BeanParam DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    return new RestResponse(stepTaskService.getDeploymentActivityRadarCartLogAnalysisClusters(
        accountId, callBackId, deploymentLogAnalysisFilter));
  }

  @Path("/{verifyStepExecutionId}/deployment-log-analysis-radar-chart-data")
  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get radar chart logs list for given verify step",
      nickname = "getVerifyStepDeploymentLogAnalysisRadarChartResult")
  public RestResponse<LogAnalysisRadarChartListWithCountDTO>
  getDeploymentLogAnalysisRadarChartResult(@NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @PathParam("verifyStepExecutionId") String callBackId,
      @BeanParam DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, @BeanParam PageParams pageParams) {
    return new RestResponse(stepTaskService.getDeploymentActivityRadarChartLogAnalysisResult(
        accountId, callBackId, deploymentLogAnalysisFilter, pageParams));
  }

  @Path("/{verifyStepExecutionId}/deployment-log-analysis-data")
  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get logs for given activity", nickname = "getVerifyStepDeploymentLogAnalysisResult")
  public RestResponse<PageResponse<LogAnalysisClusterDTO>> getDeploymentLogAnalysisResult(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @PathParam("verifyStepExecutionId") String callBackId, @QueryParam("label") Integer label,
      @BeanParam DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, @BeanParam PageParams pageParams) {
    return new RestResponse(stepTaskService.getDeploymentActivityLogAnalysisResult(
        accountId, callBackId, label, deploymentLogAnalysisFilter, pageParams));
  }

  @Path("/{verifyStepExecutionId}/deployment-log-analysis-data-v2")
  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get logs for given activity", nickname = "getVerifyStepDeploymentLogAnalysisResultV2")
  public RestResponse<LogAnalysisClusterWithCountDTO> getDeploymentLogAnalysisResultV2(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @PathParam("verifyStepExecutionId") String callBackId, @QueryParam("label") Integer label,
      @BeanParam DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, @BeanParam PageParams pageParams) {
    return new RestResponse(stepTaskService.getDeploymentActivityLogAnalysisResultV2(
        accountId, callBackId, label, deploymentLogAnalysisFilter, pageParams));
  }

  @GET
  @Path("/{verifyStepExecutionId}/all-transaction-names")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the transaction names", nickname = "getVerifyStepTransactionNames")
  public RestResponse<List<String>> getTransactionNames(@NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotEmpty @NotNull @PathParam("verifyStepExecutionId") String callBackId) {
    return new RestResponse(stepTaskService.getTransactionNames(accountId, callBackId));
  }

  @GET
  @Path("/{verifyStepExecutionId}/all-node-names")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all the Node names", nickname = "getVerifyStepNodeNames")
  public RestResponse<Set<String>> getNodeNames(@NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotEmpty @NotNull @PathParam("verifyStepExecutionId") String callBackId) {
    return new RestResponse(stepTaskService.getNodeNames(accountId, callBackId));
  }

  @GET
  @Path("/{verifyStepExecutionId}/logs")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get verify step logs", nickname = "getVerifyStepLogs")
  public RestResponse<PageResponse<CVNGLogDTO>> getLogs(@NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotEmpty @NotNull @PathParam("verifyStepExecutionId") String callBackId,
      @BeanParam DeploymentLogsFilter deploymentLogsFilter, @BeanParam PageParams pageParams) {
    return new RestResponse(stepTaskService.getCVNGLogs(accountId, callBackId, deploymentLogsFilter, pageParams));
  }

  /**
  This API is only for debugging. We have to have proper API once we show the logs in the UI.
  */
  @GET
  @Path("/{verifyStepExecutionId}/execution-logs-debugging")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get execution logs debug", nickname = "getVerifyStepExecutionLogs")
  public RestResponse<List<VerificationJobInstance.ProgressLog>> getVerifyStepExecutionLogs(
      @NotEmpty @NotNull @QueryParam("accountId") String accountId,
      @NotEmpty @NotNull @PathParam("verifyStepExecutionId") String callBackId) {
    return new RestResponse(stepTaskService.getExecutionLogs(accountId, callBackId));
  }
}
