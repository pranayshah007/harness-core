/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.HealthMonitoringFlagResponse;
import io.harness.cvng.core.beans.monitoredService.AnomaliesSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.CountServiceDTO;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HealthScoreDTO;
import io.harness.cvng.core.beans.monitoredService.HistoricalTrend;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceChangeDetailSLO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceListItemDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServicePlatformResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceWithHealthSources;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.beans.params.logsFilterParams.LiveMonitoringLogsFilter;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.utils.NGAccessControlClientCheck;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api("monitored-service")
@Path("monitored-service")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@Tag(name = "Monitored Services", description = "This contains APIs related to CRUD operations of Monitored Services")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.CV)
public class MonitoredServiceResource {
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject AccessControlClient accessControlClient;

  public static final String MONITORED_SERVICE = "MONITOREDSERVICE";
  public static final String EDIT_PERMISSION = "chi_monitoredservice_edit";
  public static final String VIEW_PERMISSION = "chi_monitoredservice_view";
  public static final String TOGGLE_PERMISSION = "chi_monitoredservice_toggle";
  public static final String DELETE_PERMISSION = "chi_monitoredservice_delete";

  @POST
  @Path("/yaml")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves monitored service from yaml or template", nickname = "saveMonitoredServiceFromYaml")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<MonitoredServiceResponse> saveMonitoredServiceFromYaml(
      @ApiParam(required = true) @NotNull @BeanParam ProjectScopedProjectParams projectParam,
      @NotNull @Valid @Body String yaml) {
    return new RestResponse<>(monitoredServiceService.createFromYaml(projectParam.getProjectParams(), yaml));
  }

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves monitored service data", nickname = "saveMonitoredService")
  @Operation(operationId = "saveMonitoredService", summary = "Saves monitored service data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Saves monitored service data")
      })
  @NGAccessControlClientCheck
  public RestResponse<MonitoredServiceResponse>
  saveMonitoredService(@ApiParam(required = true) @NotNull @QueryParam("accountId") String accountId,
      @NotNull @Valid @Body MonitoredServiceDTO monitoredServiceDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, monitoredServiceDTO.getOrgIdentifier(), monitoredServiceDTO.getProjectIdentifier()),
        Resource.of(MONITORED_SERVICE, null), EDIT_PERMISSION);
    return new RestResponse<>(monitoredServiceService.create(accountId, monitoredServiceDTO));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("/create-default")
  @ApiOperation(value = "created default monitored service", nickname = "createDefaultMonitoredService")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<MonitoredServiceResponse> createDefaultMonitoredService(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotEmpty @QueryParam("environmentIdentifier") String environmentIdentifier,
      @ApiParam(required = true) @NotEmpty @QueryParam("serviceIdentifier") String serviceIdentifier) {
    return new RestResponse<>(monitoredServiceService.createDefault(
        projectParams.getProjectParams(), serviceIdentifier, environmentIdentifier));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "updates monitored service data", nickname = "updateMonitoredService")
  @Operation(operationId = "updateMonitoredService", summary = "Updates monitored service data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Updates monitored service data")
      })
  @NGAccessControlClientCheck
  public RestResponse<MonitoredServiceResponse>
  updateMonitoredService(@Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @ApiParam(
                             required = true) @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @ApiParam(required = true) @NotNull
      @QueryParam("accountId") String accountId, @NotNull @Valid @Body MonitoredServiceDTO monitoredServiceDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, monitoredServiceDTO.getOrgIdentifier(), monitoredServiceDTO.getProjectIdentifier()),
        Resource.of(MONITORED_SERVICE, identifier), EDIT_PERMISSION);
    Preconditions.checkArgument(identifier.equals(monitoredServiceDTO.getIdentifier()),
        String.format(
            "Identifier %s does not match with path identifier %s", monitoredServiceDTO.getIdentifier(), identifier));
    return new RestResponse<>(monitoredServiceService.update(accountId, monitoredServiceDTO));
  }

  @PUT
  @Path("/{identifier}/yaml")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "update monitored service from yaml or template", nickname = "updateMonitoredServiceFromYaml")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<MonitoredServiceResponse> updateMonitoredServiceFromYaml(
      @ApiParam(required = true) @NotNull @BeanParam ProjectScopedProjectParams projectParam,
      @ApiParam(required = true) @NotNull @PathParam("identifier") String identifier,
      @NotNull @Valid @Body String yaml) {
    return new RestResponse<>(
        monitoredServiceService.updateFromYaml(projectParam.getProjectParams(), identifier, yaml));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path("{identifier}/health-monitoring-flag")
  @ApiOperation(value = "updates monitored service data", nickname = "setHealthMonitoringFlag")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = TOGGLE_PERMISSION)
  public RestResponse<HealthMonitoringFlagResponse> setHealthMonitoringFlag(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @QueryParam("enable") Boolean enable) {
    return new RestResponse<>(
        monitoredServiceService.setHealthMonitoringFlag(projectParams.getProjectParams(), identifier, enable));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/overall-health-score")
  @ApiOperation(
      value = "get monitored service overall health score data ", nickname = "getMonitoredServiceOverAllHealthScore")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<HistoricalTrend>
  getOverAllHealthScore(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @NotEmpty @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @QueryParam("duration") DurationDTO durationDTO, @NotNull @QueryParam("endTime") Long endTime) {
    return ResponseDTO.newResponse(monitoredServiceService.getOverAllHealthScore(
        projectParams.getProjectParams(), identifier, durationDTO, Instant.ofEpochMilli(endTime)));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "list monitored service data", nickname = "listMonitoredService")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<MonitoredServiceListItemDTO>> list(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("environmentIdentifier") String environmentIdentifier,
      @QueryParam("environmentIdentifiers") List<String> environmentIdentifiers,
      @QueryParam("offset") @NotNull Integer offset, @QueryParam("pageSize") @NotNull Integer pageSize,
      @QueryParam("filter") String filter,
      @QueryParam("monitoredServiceType") MonitoredServiceType monitoredServiceType,
      @NotNull @QueryParam("servicesAtRiskFilter") @ApiParam(defaultValue = "false") boolean servicesAtRiskFilter) {
    if (isNotEmpty(environmentIdentifier)) {
      environmentIdentifiers = Collections.singletonList(environmentIdentifier);
    }
    return ResponseDTO.newResponse(monitoredServiceService.list(projectParams.getProjectParams(),
        environmentIdentifiers, offset, pageSize, filter, monitoredServiceType, servicesAtRiskFilter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "get monitored service data ", nickname = "getMonitoredService")
  @Operation(operationId = "getMonitoredService", summary = "Get monitored service data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get monitored service data")
      })
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<MonitoredServiceResponse>
  get(@NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams) {
    return ResponseDTO.newResponse(monitoredServiceService.get(projectParams.getProjectParams(), identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/list")
  @ApiOperation(value = "get list of monitored service data ", nickname = "getMonitoredServiceList")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<MonitoredServiceResponse>> getList(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("environmentIdentifier") String environmentIdentifier,
      @QueryParam("environmentIdentifiers") List<String> environmentIdentifiers,
      @QueryParam("offset") @NotNull Integer offset, @QueryParam("pageSize") @NotNull Integer pageSize,
      @QueryParam("filter") String filter) {
    // for backward comparability. Need to remove this.
    if (isNotEmpty(environmentIdentifier)) {
      environmentIdentifiers = Collections.singletonList(environmentIdentifier);
    }
    return ResponseDTO.newResponse(monitoredServiceService.getList(
        projectParams.getProjectParams(), environmentIdentifiers, offset, pageSize, filter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/platform/list")
  @ApiOperation(value = "get list of monitored service data", nickname = "getMonitoredServicePlatformList")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<MonitoredServicePlatformResponse>> getListV2(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("environmentIdentifiers") List<String> environmentIdentifiers,
      @QueryParam("offset") @NotNull Integer offset, @QueryParam("pageSize") @NotNull Integer pageSize,
      @QueryParam("filter") String filter,
      @QueryParam("monitoredServiceType") MonitoredServiceType monitoredServiceType,
      @QueryParam("hideNotConfiguredServices") @ApiParam(defaultValue = "false") boolean hideNotConfiguredServices) {
    return ResponseDTO.newResponse(monitoredServiceService.getMSPlatformList(projectParams.getProjectParams(),
        environmentIdentifiers, offset, pageSize, filter, monitoredServiceType, hideNotConfiguredServices));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/all/time-series-health-sources")
  @ApiOperation(value = "get all of monitored service data with time series health sources",
      nickname = "getAllMonitoredServicesWithTimeSeriesHealthSources")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<List<MonitoredServiceWithHealthSources>>
  getAllMonitoredServicesWithHealthSources(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams) {
    return ResponseDTO.newResponse(
        monitoredServiceService.getAllWithTimeSeriesHealthSources(projectParams.getProjectParams()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/service-environment")
  @ApiOperation(value = "get monitored service data from service and env ref",
      nickname = "getMonitoredServiceFromServiceAndEnvironment")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<MonitoredServiceResponse>
  getMonitoredServiceFromServiceAndEnvironment(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @QueryParam("serviceIdentifier") String serviceIdentifier,
      @NotNull @QueryParam("environmentIdentifier") String environmentIdentifier) {
    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(environmentIdentifier)
                                                            .accountIdentifier(projectParams.getAccountIdentifier())
                                                            .orgIdentifier(projectParams.getOrgIdentifier())
                                                            .projectIdentifier(projectParams.getProjectIdentifier())
                                                            .build();
    return ResponseDTO.newResponse(
        monitoredServiceService.getApplicationMonitoredServiceResponse(serviceEnvironmentParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/scores")
  @ApiOperation(value = "get monitored service scores", nickname = "getMonitoredServiceScores")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<HealthScoreDTO> getMonitoredServiceScore(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    MonitoredServiceParams serviceEnvironmentParams = MonitoredServiceParams.builder()
                                                          .accountIdentifier(projectParams.getAccountIdentifier())
                                                          .orgIdentifier(projectParams.getOrgIdentifier())
                                                          .projectIdentifier(projectParams.getProjectIdentifier())
                                                          .monitoredServiceIdentifier(identifier)
                                                          .build();
    return ResponseDTO.newResponse(
        monitoredServiceService.getCurrentAndDependentServicesScore(serviceEnvironmentParams));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "delete monitored service data ", nickname = "deleteMonitoredService")
  @Operation(operationId = "deleteMonitoredService", summary = "Delete monitored service data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delete monitored service data")
      })
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = DELETE_PERMISSION)
  public RestResponse<Boolean>
  delete(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @ApiParam(
          required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(monitoredServiceService.delete(projectParams.getProjectParams(), identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/environments")
  @ApiOperation(
      value = "get monitored service list environments data ", nickname = "getMonitoredServiceListEnvironments")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<List<EnvironmentResponse>>
  getEnvironments(@NotNull @QueryParam("accountId") @AccountIdentifier String accountId,
      @NotNull @QueryParam("orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam("projectIdentifier") @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(
        monitoredServiceService.listEnvironments(accountId, orgIdentifier, projectIdentifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/yaml-template")
  @ApiOperation(value = "yaml template for monitored service", nickname = "getMonitoredServiceYamlTemplate")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<String> yamlTemplate(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam @QueryParam("type") MonitoredServiceType type) {
    return new RestResponse<>(monitoredServiceService.getYamlTemplate(projectParams.getProjectParams(), type));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/health-sources")
  @ApiOperation(value = "get all health sources for service and environment",
      nickname = "getAllHealthSourcesForServiceAndEnvironment")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<List<HealthSourceDTO>>
  getHealthSources(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @QueryParam("serviceIdentifier") String serviceIdentifier,
      @ApiParam(required = true) @NotNull @QueryParam("environmentIdentifier") String environmentIdentifier) {
    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(projectParams.getAccountIdentifier())
                                                            .orgIdentifier(projectParams.getOrgIdentifier())
                                                            .projectIdentifier(projectParams.getProjectIdentifier())
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(environmentIdentifier)
                                                            .build();

    return new RestResponse<>(monitoredServiceService.getHealthSources(serviceEnvironmentParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{monitoredServiceIdentifier}/health-sources")
  @ApiOperation(value = "get all health sources for service and environment",
      nickname = "getAllHealthSourcesForMonitoredServiceIdentifier")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<List<HealthSourceDTO>>
  getHealthSourcesForMonitoredServiceIdentifier(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @PathParam("monitoredServiceIdentifier") @ResourceIdentifier String monitoredServiceIdentifier) {
    return new RestResponse<>(
        monitoredServiceService.getHealthSources(projectParams.getProjectParams(), monitoredServiceIdentifier));
  }

  @GET
  @Timed
  @Path("{identifier}/anomaliesCount")
  @ExceptionMetered
  @ApiOperation(value = "get anomalies summary details", nickname = "getAnomaliesSummary")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<AnomaliesSummaryDTO> getAnomaliesSummary(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime) {
    TimeRangeParams timeRangeParams = TimeRangeParams.builder()
                                          .startTime(Instant.ofEpochMilli(startTime))
                                          .endTime(Instant.ofEpochMilli(endTime))
                                          .build();
    return new RestResponse<>(
        monitoredServiceService.getAnomaliesSummary(projectParams.getProjectParams(), identifier, timeRangeParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/count-of-services")
  @ApiOperation(value = "get count of types of services like Monitored Service, Services at Risk ",
      nickname = "getCountOfServices")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public CountServiceDTO
  getCountOfServices(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("environmentIdentifier") String environmentIdentifier, @QueryParam("filter") String filter) {
    return monitoredServiceService.getCountOfServices(projectParams.getProjectParams(), environmentIdentifier, filter);
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/{monitoredServiceIdentifier}/health-source/{healthSourceIdentifier}/slo-metrics")
  @ApiOperation(value = "get slo metrics in a healthSource ", nickname = "getSloMetrcs")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<List<MetricDTO>> getSloMetrics(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @PathParam("monitoredServiceIdentifier") @ResourceIdentifier String monitoredServiceIdentifier,
      @PathParam("healthSourceIdentifier") String healthSourceIdentifier) {
    return new RestResponse<>(monitoredServiceService.getSloMetrics(
        projectParams.getProjectParams(), monitoredServiceIdentifier, healthSourceIdentifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{monitoredServiceIdentifier}/service-details")
  @ApiOperation(value = "get details of a monitored service present in the Service Dependency Graph",
      nickname = "getMonitoredServiceDetailsWithServiceId")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public MonitoredServiceListItemDTO
  getMonitoredServiceDetails(@NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @PathParam("monitoredServiceIdentifier") @ResourceIdentifier String monitoredServiceIdentifier) {
    return monitoredServiceService.getMonitoredServiceDetails(
        MonitoredServiceParams.builderWithProjectParams(projectParams.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .build());
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/service-details")
  @ApiOperation(value = "get details of a monitored service present in the Service Dependency Graph",
      nickname = "getMonitoredServiceDetails")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  @Deprecated
  public MonitoredServiceListItemDTO
  getMonitoredServiceDetails(@NotNull @Valid @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @QueryParam("serviceIdentifier") String serviceIdentifier,
      @ApiParam(required = true) @NotNull @QueryParam("environmentIdentifier") String environmentIdentifier) {
    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(projectParams.getAccountIdentifier())
                                                            .orgIdentifier(projectParams.getOrgIdentifier())
                                                            .projectIdentifier(projectParams.getProjectIdentifier())
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(environmentIdentifier)
                                                            .build();
    return monitoredServiceService.getMonitoredServiceDetails(serviceEnvironmentParams);
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{monitoredServiceIdentifier}/logs")
  @ApiOperation(value = "get monitored service logs", nickname = "getMonitoredServiceLogs")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<PageResponse<CVNGLogDTO>> getMonitoredServiceLogs(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam(
          "monitoredServiceIdentifier") @ResourceIdentifier String monitoredServiceIdentifier,
      @BeanParam LiveMonitoringLogsFilter liveMonitoringLogsFilter, @BeanParam PageParams pageParams) {
    return new RestResponse<>(monitoredServiceService.getCVNGLogs(
        MonitoredServiceParams.builderWithProjectParams(projectParams.getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .build(),
        liveMonitoringLogsFilter, pageParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{monitoredServiceIdentifier}/change-details")
  @ApiOperation(value = "get monitored service change details", nickname = "getMonitoredServiceChangeDetails")
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public RestResponse<List<MonitoredServiceChangeDetailSLO>> getMonitoredServiceChangeDetails(
      @NotNull @Valid @BeanParam ProjectScopedProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("monitoredServiceIdentifier")
      @ResourceIdentifier String monitoredServiceIdentifier, @QueryParam("sloIdentifiers") List<String> sloIdentifiers,
      @QueryParam("startTime") Long startTime, @QueryParam("endTime") Long endTime) {
    return new RestResponse<>(monitoredServiceService.getMonitoredServiceChangeDetails(
        projectParams.getProjectParams(), monitoredServiceIdentifier, startTime, endTime));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/notification-rules")
  @ApiOperation(
      value = "get notification rules for MonitoredService", nickname = "getNotificationRulesForMonitoredService")
  @Operation(operationId = "getNotificationRulesForMonitoredService",
      summary = "Get notification rules for MonitoredService",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get notification rules for MonitoredService")
      })
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<NotificationRuleResponse>>
  getNotificationRulesForMonitoredService(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @ApiParam(
          required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String monitoredServiceIdentifier,
      @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(monitoredServiceService.getNotificationRules(
        projectParams.getProjectParams(), monitoredServiceIdentifier, pageParams));
  }

  @POST
  @Path("/template-input")
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "saves monitored service from template input", nickname = "saveMonitoredServiceFromTemplateInput")
  @Operation(operationId = "saveMonitoredServiceFromTemplateInput",
      summary = "Saves monitored service from template input",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Saves monitored service from template input")
      })
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<MonitoredServiceResponse>
  saveMonitoredServiceFromTemplateInput(
      @ApiParam(required = true) @NotNull @BeanParam ProjectScopedProjectParams projectParam,
      @Parameter(description = "Template input yaml for the monitored service creation from given template") @NotNull
      @Valid @Body String yaml) {
    return new RestResponse<>(monitoredServiceService.createFromYaml(projectParam.getProjectParams(), yaml));
  }

  @PUT
  @Path("/{identifier}/template-input")
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "update monitored service from yaml or template", nickname = "updateMonitoredServiceFromTemplateInput")
  @Operation(operationId = "updateMonitoredServiceFromTemplateInput",
      summary = "Update monitored service from yaml or template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Update monitored service from yaml or template")
      })
  @NGAccessControlCheck(resourceType = MONITORED_SERVICE, permission = EDIT_PERMISSION)
  public RestResponse<MonitoredServiceResponse>
  updateMonitoredServiceFromTemplateInput(
      @ApiParam(required = true) @NotNull @BeanParam ProjectScopedProjectParams projectParam,
      @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) @ApiParam(
          required = true) @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = "Template input yaml for the monitored service creation from given template") @NotNull
      @Valid @Body String yaml) {
    return new RestResponse<>(
        monitoredServiceService.updateFromYaml(projectParam.getProjectParams(), identifier, yaml));
  }
}
