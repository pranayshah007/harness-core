/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.CHANGE_EVENT_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.CVNextGenConstants;
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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.NonNull;
import retrofit2.http.Body;

@Api("change-event")
@Path(CHANGE_EVENT_PATH)
@Produces("application/json")
@ExposeInternalException
@OwnedBy(HarnessTeam.CV)
public class ChangeEventNgResource {
  @Inject ChangeEventService changeEventService;

  @POST
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("/register")
  @ApiOperation(value = "register a ChangeEvent", nickname = "registerChangeEvent")
  public RestResponse<Boolean> register(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @NotNull @Valid @Body ChangeEventDTO changeEventDTO) {
    return new RestResponse<>(changeEventService.register(changeEventDTO));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @Path("")
  @ApiOperation(value = "get ChangeEvent List", nickname = "changeEventList")
  public RestResponse<PageResponse<ChangeEventDTO>> get(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @QueryParam("monitoredServiceIdentifiers") List<String> monitoredServiceIdentifiers,
      @QueryParam("changeCategories") List<ChangeCategory> changeCategories,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @QueryParam("searchText") String searchText,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime, @BeanParam PageRequest pageRequest) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return new RestResponse<>(changeEventService.getChangeEvents(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime),
        Instant.ofEpochMilli(endTime), pageRequest));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/summary")
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent summary", nickname = "changeEventSummary")
  public RestResponse<ChangeSummaryDTO> get(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @QueryParam("changeCategories") List<ChangeCategory> changeCategories,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams, serviceIdentifiers, envIdentifiers,
        changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/{activityId}")
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent detail", nickname = "getChangeEventDetail")
  public RestResponse<ChangeEventDTO> getChangeEventDetail(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @PathParam("activityId") String activityId) {
    return new RestResponse<>(changeEventService.get(activityId));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/timeline")
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent timeline", nickname = "changeEventTimeline")
  public RestResponse<ChangeTimeline> get(
      @PathParam(CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY) @NonNull String accountIdentifier,
      @PathParam(CVNextGenConstants.ORG_IDENTIFIER_KEY) @NonNull String orgIdentifier,
      @PathParam(CVNextGenConstants.PROJECT_IDENTIFIER_KEY) @NonNull String projectIdentifier,
      @QueryParam("serviceIdentifiers") List<String> serviceIdentifiers,
      @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @QueryParam("monitoredServiceIdentifiers") List<String> monitoredServiceIdentifiers,
      @QueryParam("changeCategories") List<ChangeCategory> changeCategories,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @QueryParam("searchText") String searchText,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime,
      @ApiParam @QueryParam("pointCount") @DefaultValue("48") Integer pointCount) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return new RestResponse<>(changeEventService.getTimeline(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, searchText, changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime),
        Instant.ofEpochMilli(endTime), pointCount));
  }
}