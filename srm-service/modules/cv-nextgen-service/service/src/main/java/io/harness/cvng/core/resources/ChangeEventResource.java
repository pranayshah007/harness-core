/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.CHANGE_EVENT_RESOURCE;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("change-event")
@Path(CHANGE_EVENT_RESOURCE)
@Produces("application/json")
@ExposeInternalException
@OwnedBy(HarnessTeam.CV)
public class ChangeEventResource {
  @Inject ChangeEventService changeEventService;

  @POST
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @Path("/register-change")
  @ApiOperation(value = "register a ChangeEvent", nickname = "registerChangeEventFromDelegate")
  public RestResponse<Boolean> registerFromDelegate(
      @ApiParam(required = true) @NotNull @QueryParam("accountId") String accountId,
      @NotNull @Valid @Body ChangeEventDTO changeEventDTO) {
    return new RestResponse<>(changeEventService.register(changeEventDTO));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/monitored-service-summary")
  @ExceptionMetered
  @ApiOperation(
      value = "get ChangeEvent summary for monitored service", nickname = "getMonitoredServiceChangeEventSummary")
  public RestResponse<ChangeSummaryDTO>
  getSummary(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @QueryParam("monitoredServiceIdentifiers") List<String> monitoredServiceIdentifiers,
      @QueryParam("isMonitoredServiceIdentifierScoped") boolean isMonitoredServiceIdentifierScoped,
      @QueryParam("changeCategories") List<ChangeCategory> changeCategories,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime,
      @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime) {
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams.getProjectParams(),
        monitoredServiceIdentifier, monitoredServiceIdentifiers, isMonitoredServiceIdentifierScoped, changeCategories,
        changeSourceTypes, Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @Path("/monitored-service-timeline")
  @ExceptionMetered
  @ApiOperation(
      value = "get monitored service timeline with durationDTO", nickname = "getMonitoredServiceChangeTimeline")
  public RestResponse<ChangeTimeline>
  getMonitoredServiceChangeTimeline(@NotNull @BeanParam ProjectScopedProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @QueryParam("changeSourceTypes") List<ChangeSourceType> changeSourceTypes,
      @QueryParam("searchText") String searchText, @NotNull @QueryParam("duration") DurationDTO durationDTO,
      @NotNull @QueryParam("endTime") Long endTime) {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(projectParams.getAccountIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build();

    return new RestResponse<>(changeEventService.getMonitoredServiceChangeTimeline(
        monitoredServiceParams, changeSourceTypes, durationDTO, Instant.ofEpochMilli(endTime)));
  }
}