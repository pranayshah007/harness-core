/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.AppdynamicsValidationResponse;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.CorrelationContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;
import retrofit2.http.Body;

@Api("appdynamics")
@Path("/appdynamics")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(CV)
public class AppDynamicsResource {
  @Inject private AppDynamicsService appDynamicsService;
  @POST
  @Path("/metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metric data for given metric packs", nickname = "getAppdynamicsMetricData")
  public ResponseDTO<Set<AppdynamicsValidationResponse>> getMetricData(
      @NotNull @Valid @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("appName") @NotNull String appName, @QueryParam("tierName") @NotNull String tierName,
      @QueryParam("requestGuid") @NotNull String requestGuid, @NotNull @Valid @Body List<MetricPackDTO> metricPacks) {
    return ResponseDTO.newResponse(appDynamicsService.getMetricPackData(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), appName, tierName,
        requestGuid, metricPacks));
  }

  @GET
  @Path("/applications")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all appdynamics applications", nickname = "getAppdynamicsApplications")
  public ResponseDTO<PageResponse<AppDynamicsApplication>> getAllApplications(
      @NotNull @Valid @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("offset") @NotNull Integer offset, @QueryParam("pageSize") @NotNull Integer pageSize,
      @QueryParam("filter") String filter) {
    return ResponseDTO.newResponse(
        appDynamicsService.getApplications(projectParams.getAccountIdentifier(), connectorIdentifier,
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), offset, pageSize, filter));
  }

  @GET
  @Path("/tiers")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all appdynamics tiers for an application", nickname = "getAppdynamicsTiers")
  public ResponseDTO<PageResponse<AppDynamicsTier>> getAllTiers(@NotNull @Valid @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("appName") String appName, @QueryParam("offset") @NotNull Integer offset,
      @QueryParam("pageSize") @NotNull Integer pageSize, @QueryParam("filter") String filter) {
    return ResponseDTO.newResponse(
        appDynamicsService.getTiers(projectParams.getAccountIdentifier(), connectorIdentifier,
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), appName, offset, pageSize, filter));
  }

  @GET
  @Path("/base-folders")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all appdynamics base folders for an application", nickname = "getAppdynamicsBaseFolders")
  public ResponseDTO<List<String>> getBaseFolders(@BeanParam @Valid ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("appName") String appName, @QueryParam("path") @DefaultValue("") String path,
      @QueryParam("routingId") String routingId) {
    if (StringUtils.isEmpty(routingId)) {
      routingId = CorrelationContext.getCorrelationId();
    }
    return ResponseDTO.newResponse(
        appDynamicsService.getBaseFolders(projectParams, connectorIdentifier, appName, path, routingId));
  }

  @GET
  @Path("/metric-structure")
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "get all appdynamics metric structure for an application", nickname = "getAppdynamicsMetricStructure")
  public ResponseDTO<List<AppDynamicsFileDefinition>>
  getMetricStructure(@BeanParam @Valid ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("appName") String appName, @NotNull @QueryParam("baseFolder") String baseFolder,
      @NotNull @QueryParam("tier") String tier, @NotNull @QueryParam("metricPath") @DefaultValue("") String metricPath,
      @QueryParam("routingId") String routingId) {
    if (StringUtils.isEmpty(routingId)) {
      routingId = CorrelationContext.getCorrelationId();
    }
    return ResponseDTO.newResponse(appDynamicsService.getMetricStructure(
        projectParams, connectorIdentifier, appName, baseFolder, tier, metricPath, routingId));
  }

  @GET
  @Path("/metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all appdynamics metric data for an application and a metric path",
      nickname = "getAppdynamicsMetricDataByPath")
  public ResponseDTO<AppdynamicsMetricDataResponse>
  getMetricData(@BeanParam @Valid ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("appName") String appName, @NotNull @QueryParam("baseFolder") String baseFolder,
      @NotNull @QueryParam("tier") String tier, @NotNull @QueryParam("metricPath") String metricPath,
      @QueryParam("routingId") String routingId) {
    if (StringUtils.isEmpty(routingId)) {
      routingId = CorrelationContext.getCorrelationId();
    }
    return ResponseDTO.newResponse(appDynamicsService.getMetricData(
        projectParams, connectorIdentifier, appName, baseFolder, tier, metricPath, routingId));
  }

  @GET
  @Path("/service-instance-metric-path")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get service instance metric path for an application and a metric path",
      nickname = "getServiceInstanceMetricPath")
  public ResponseDTO<String>
  getServiceInstanceMetricPath(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("appName") String appName, @NotNull @QueryParam("baseFolder") String baseFolder,
      @NotNull @QueryParam("tier") String tier, @NotNull @QueryParam("metricPath") String metricPath,
      @QueryParam("routingId") String routingId) {
    if (StringUtils.isEmpty(routingId)) {
      routingId = CorrelationContext.getCorrelationId();
    }
    return ResponseDTO.newResponse(appDynamicsService.getServiceInstanceMetricPath(
        projectParams, connectorIdentifier, appName, baseFolder, tier, metricPath, routingId));
  }
}
