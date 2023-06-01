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
import io.harness.cvng.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
  @NextGenManagerAuth
  @Path("/{activityId}")
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent detail", nickname = "getChangeEventDetail")
  public RestResponse<ChangeEventDTO> getChangeEventDetail(
      @Valid @BeanParam ProjectPathParams projectPathParams, @PathParam("activityId") String activityId) {
    return new RestResponse<>(changeEventService.get(activityId));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent List For Project", nickname = "changeEventList")
  public RestResponse<PageResponse<ChangeEventDTO>> get(@Valid @BeanParam ProjectPathParams projectPathParams,
      List<String> serviceIdentifiers, List<String> envIdentifiers, List<String> monitoredServiceIdentifiers,
      List<String> scopedMonitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, String searchText, @NotNull long startTime, @NotNull long endTime,
      PageRequest pageRequest) {
    validate(scopedMonitoredServiceIdentifiers, projectPathParams, startTime, endTime);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeEvents(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, false, searchText, changeCategories, changeSourceTypes,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pageRequest));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent timeline For Project", nickname = "changeEventTimeline")
  public RestResponse<ChangeTimeline> get(@Valid @BeanParam ProjectPathParams projectPathParams,
      List<String> serviceIdentifiers, List<String> envIdentifiers, List<String> monitoredServiceIdentifiers,
      List<String> scopedMonitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, String searchText, @NotNull long startTime, @NotNull long endTime,
      Integer pointCount) {
    validate(scopedMonitoredServiceIdentifiers, projectPathParams, startTime, endTime);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getTimeline(projectParams, serviceIdentifiers, envIdentifiers,
        monitoredServiceIdentifiers, false, searchText, changeCategories, changeSourceTypes,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pointCount));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get ChangeEvent summary for monitored service For Project",
      nickname = "getMonitoredServiceChangeEventSummary")
  public RestResponse<ChangeSummaryDTO>
  getSummary(@Valid @BeanParam ProjectPathParams projectPathParams, String monitoredServiceIdentifier,
      List<String> monitoredServiceIdentifiers, List<String> scopedMonitoredServiceIdentifiers,
      List<ChangeCategory> changeCategories, List<ChangeSourceType> changeSourceTypes, @NotNull long startTime,
      @NotNull long endTime) {
    validate(scopedMonitoredServiceIdentifiers, projectPathParams, startTime, endTime);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams, monitoredServiceIdentifier,
        monitoredServiceIdentifiers, false, changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime),
        Instant.ofEpochMilli(endTime)));
  }

  private void validate(List<String> scopedMonitoredServiceIdentifiers, ProjectPathParams projectPathParams,
      long startTime, long endTime) {
    if (StringUtils.isEmpty(projectPathParams.getAccountIdentifier())) {
      throw new InvalidArgumentsException(Pair.of("accountId", "should not be null or empty"));
    }
    if (projectPathParams.getOrgIdentifier().isEmpty() || projectPathParams.getOrgIdentifier().equals(null)) {
      throw new InvalidArgumentsException(Pair.of("orgIdentifier", "should not be null or empty"));
    }
    if (projectPathParams.getProjectIdentifier().isEmpty() || projectPathParams.getProjectIdentifier().equals(null)) {
      throw new InvalidArgumentsException(Pair.of("projectIdentifier", "should not be null or empty"));
    }
    if (startTime == 0) {
      throw new InvalidArgumentsException(Pair.of("startTime", "should not be null or empty"));
    }
    if (endTime == 0) {
      throw new InvalidArgumentsException(Pair.of("endTime", "should not be null or empty"));
    }
    if (!Lists.isNullOrEmpty(scopedMonitoredServiceIdentifiers)) {
      throw new InvalidArgumentsException(
          Pair.of("scopedMonitoredServiceIdentifiers", "should not be present for project"));
    }
  }
}
