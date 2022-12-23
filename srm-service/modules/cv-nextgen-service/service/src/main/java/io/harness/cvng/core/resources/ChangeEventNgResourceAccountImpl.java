/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.CHANGE_EVENT_NG_ACCOUNT_PATH;

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
import com.cronutils.utils.Preconditions;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import io.swagger.annotations.Api;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path(CHANGE_EVENT_NG_ACCOUNT_PATH)
@Api("change-event")
@OwnedBy(HarnessTeam.CV)
@Produces("application/json")
@ExposeInternalException
public class ChangeEventNgResourceAccountImpl implements ChangeEventNgResource {
  @Inject ChangeEventService changeEventService;

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<PageResponse<ChangeEventDTO>> get(@Valid ProjectPathParams projectPathParams,
      List<String> serviceIdentifiers, List<String> envIdentifiers, List<String> monitoredServiceIdentifiers,
      List<String> scopedMonitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, String searchText, @NotNull long startTime, @NotNull long endTime,
      PageRequest pageRequest) {
    Preconditions.checkArgument(!Lists.isNullOrEmpty(scopedMonitoredServiceIdentifiers),
        "Scoped Monitored Service Identifiers should be present for account");
    Preconditions.checkArgument(Lists.isNullOrEmpty(monitoredServiceIdentifiers),
        "Only Scoped Monitored Service Identifiers need to be sent for account");
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeEvents(projectParams, serviceIdentifiers, envIdentifiers,
        scopedMonitoredServiceIdentifiers, true, searchText, changeCategories, changeSourceTypes,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pageRequest));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<ChangeTimeline> get(@Valid ProjectPathParams projectPathParams, List<String> serviceIdentifiers,
      List<String> envIdentifiers, List<String> monitoredServiceIdentifiers,
      List<String> scopedMonitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, String searchText, @NotNull long startTime, @NotNull long endTime,
      Integer pointCount) {
    Preconditions.checkArgument(!Lists.isNullOrEmpty(scopedMonitoredServiceIdentifiers),
        "Scoped Monitored Service Identifiers should be present for account");
    Preconditions.checkArgument(Lists.isNullOrEmpty(monitoredServiceIdentifiers),
        "Only Scoped Monitored Service Identifiers need to be sent for account");
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getTimeline(projectParams, serviceIdentifiers, envIdentifiers,
        scopedMonitoredServiceIdentifiers, true, searchText, changeCategories, changeSourceTypes,
        Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime), pointCount));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  public RestResponse<ChangeSummaryDTO> getSummary(@Valid ProjectPathParams projectPathParams,
      String monitoredServiceIdentifier, List<String> monitoredServiceIdentifiers,
      List<String> scopedMonitoredServiceIdentifiers, List<ChangeCategory> changeCategories,
      List<ChangeSourceType> changeSourceTypes, @NotNull long startTime, @NotNull long endTime) {
    Preconditions.checkArgument(!Lists.isNullOrEmpty(scopedMonitoredServiceIdentifiers),
        "Scoped Monitored Service Identifiers should be present for account");
    Preconditions.checkArgument(Lists.isNullOrEmpty(monitoredServiceIdentifiers),
        "Only Scoped Monitored Service Identifiers need to be sent for account");
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(projectPathParams.getAccountIdentifier())
                                      .orgIdentifier(projectPathParams.getOrgIdentifier())
                                      .projectIdentifier(projectPathParams.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(changeEventService.getChangeSummary(projectParams, monitoredServiceIdentifier,
        scopedMonitoredServiceIdentifiers, true, changeCategories, changeSourceTypes, Instant.ofEpochMilli(startTime),
        Instant.ofEpochMilli(endTime)));
  }
}
