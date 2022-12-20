/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.CHANGE_EVENT_NG_PROJECT_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Path(CHANGE_EVENT_NG_PROJECT_PATH)
@Api("change-event")
@OwnedBy(HarnessTeam.CV)
@Produces("application/json")
@ExposeInternalException
public class ChangeEventNgResourceProjectImpl implements ChangeEventNgResource {
  @Inject ChangeEventService changeEventService;

  @POST
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("/register")
  @ApiOperation(value = "register a ChangeEvent", nickname = "registerChangeEvent")
  public RestResponse<Boolean> register(
      @Valid @BeanParam ProjectPathParams projectPathParams, @NotNull @Valid @Body ChangeEventDTO changeEventDTO) {
    return new RestResponse<>(changeEventService.register(changeEventDTO));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/summary")
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent summary", nickname = "changeEventSummary")
  public RestResponse<ChangeSummaryDTO> get(@Valid @BeanParam ProjectPathParams projectPathParams,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @QueryParam("changeCategories") List<ChangeCategory> changeCategories,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams, serviceIdentifiers, envIdentifiers,
        changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)));
  }

  @GET
  @Timed
  @Path("/{activityId}")
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent detail", nickname = "getChangeEventDetail")
  public RestResponse<ChangeEventDTO> getChangeEventDetail(
      @Valid ProjectPathParams projectPathParams, @PathParam("activityId") String activityId) {
    return new RestResponse<>(changeEventService.get(activityId));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<PageResponse<ChangeEventDTO>> get(@Valid ProjectPathParams projectPathParams,
      List<String> serviceIdentifiers, List<String> envIdentifiers, List<String> monitoredServiceIdentifiers,
      boolean isMonitoredServiceIdentifierScoped, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, String searchText, @NotNull long startTime, @NotNull long endTime,
      PageRequest pageRequest) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeEvents(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped, searchText, changeCategories,
        changeSourceTypes, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pageRequest));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<ChangeTimeline> get(@Valid ProjectPathParams projectPathParams, List<String> serviceIdentifiers,
      List<String> envIdentifiers, List<String> monitoredServiceIdentifiers, boolean isMonitoredServiceIdentifierScoped,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, String searchText,
      @NotNull long startTime, @NotNull long endTime, Integer pointCount) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getTimeline(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped, searchText, changeCategories,
        changeSourceTypes, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pointCount));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<ChangeSummaryDTO> getSummary(@Valid ProjectPathParams projectPathParams,
      String monitoredServiceIdentifier, List<String> monitoredServiceIdentifiers,
      boolean isMonitoredServiceIdentifierScoped, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, @NotNull long startTime, @NotNull long endTime) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams, monitoredServiceIdentifier,
        monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped, changeCategories, changeSourceTypes,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)));
  }
}
