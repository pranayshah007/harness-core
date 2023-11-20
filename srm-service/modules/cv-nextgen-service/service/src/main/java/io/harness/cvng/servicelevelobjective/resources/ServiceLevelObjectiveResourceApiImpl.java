/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.cvng.v1.ServiceLevelObjectiveApi;
import io.harness.spec.server.cvng.v1.model.DowntimeStatus;
import io.harness.spec.server.cvng.v1.model.DowntimeStatusDetails;
import io.harness.spec.server.cvng.v1.model.MetricGraph;
import io.harness.spec.server.cvng.v1.model.SLOError;
import io.harness.spec.server.cvng.v1.model.UserJourney;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.Max;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@NextGenManagerAuth
@Slf4j
@Tag(name = "SLOs", description = "This contains APIs related to CRUD operations of SLOs with OpenAPI.")
public class ServiceLevelObjectiveResourceApiImpl implements ServiceLevelObjectiveApi {
  public static final String SLO = "SLO";
  public static final String VIEW_PERMISSION = "chi_slo_view";

  @Inject SLODashboardService sloDashboardService;

  @Override
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public Response getMetricGraphForSLO(@OrgIdentifier String org, @ProjectIdentifier String project,
      String sloIdentifier, Long startTime, Long endTime, @AccountIdentifier String harnessAccount) {
    ProjectParams projectParams =
        ProjectParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).projectIdentifier(project).build();
    Map<String, MetricGraph> metricGraphMap =
        sloDashboardService.getMetricGraphs(projectParams, sloIdentifier, startTime, endTime);
    return Response.ok().entity(metricGraphMap).build();
  }

  @Override
  @Timed
  @ResponseMetered
  @ExceptionMetered
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public Response listSlo(@OrgIdentifier String org, @ProjectIdentifier String project,
      @AccountIdentifier String harnessAccount, Integer page, @Max(100L) Integer limit, String compositeSloIdentifier,
      String monitoredServiceIdentifier, List<String> userJourneyIdentifiers,
      @Pattern(regexp = "^[a-zA-Z_][0-9a-zA-Z-_ ]{0,127}$") String filter, @Size(max = 1024) String sloType,
      @Size(max = 128) List<String> envIdentifiers, List<String> targetTypes, List<String> errorBudgetRisks,
      String evaluationType, Boolean childResource) {
    ProjectParams projectParams =
        ProjectParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).projectIdentifier(project).build();
    PageParams pageParams = PageParams.builder().size(limit).page(page).build();
    SLODashboardApiFilter sloDashboardApiFilter =
        SLODashboardApiFilter.builder()
            .type(sloType != null ? ServiceLevelObjectiveType.fromString(sloType) : null)
            .searchFilter(filter)
            .childResource(childResource)
            .compositeSLOIdentifier(compositeSloIdentifier)
            .envIdentifiers(envIdentifiers)
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .userJourneyIdentifiers(userJourneyIdentifiers)
            .targetTypes(targetTypes.isEmpty()
                    ? null
                    : targetTypes.stream().map(SLOTargetType::fromString).collect(Collectors.toList()))
            .errorBudgetRisks(errorBudgetRisks.isEmpty()
                    ? null
                    : errorBudgetRisks.stream().map(ErrorBudgetRisk::fromString).collect(Collectors.toList()))
            .evaluationType(evaluationType != null ? SLIEvaluationType.fromString(evaluationType) : null)
            .build();
    ResponseBuilder responseBuilder = Response.ok();
    PageResponse<SLOHealthListView> sloHealthListViews =
        sloDashboardService.getSloHealthListView(projectParams, sloDashboardApiFilter, pageParams);
    List<io.harness.spec.server.cvng.v1.model.SLOHealthListView> sloHealthListViewResponses =
        getOASSloHealthListViewResponses(sloHealthListViews.getContent());
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, sloHealthListViews.getTotalItems(), page, limit);
    return responseBuilderWithLinks.entity(sloHealthListViewResponses).build();
  }

  List<io.harness.spec.server.cvng.v1.model.SLOHealthListView> getOASSloHealthListViewResponses(
      List<SLOHealthListView> sloHealthListViewList) {
    return sloHealthListViewList.stream()
        .map(sloHealthListView
            -> new io.harness.spec.server.cvng.v1.model.SLOHealthListView()
                   .sloIdentifier(sloHealthListView.getSloIdentifier())
                   .name(sloHealthListView.getName())
                   .orgName(sloHealthListView.getOrgName())
                   .projectName(sloHealthListView.getProjectName())
                   .projectParams(new io.harness.spec.server.cvng.v1.model.ProjectParams()
                                      .accountIdentifier(sloHealthListView.getProjectParams().getAccountIdentifier())
                                      .orgIdentifier(sloHealthListView.getProjectParams().getOrgIdentifier())
                                      .projectIdentifier(sloHealthListView.getProjectParams().getProjectIdentifier()))
                   .description(sloHealthListView.getDescription())
                   .tags(!sloHealthListView.getTags().isEmpty() ? new ArrayList<>(sloHealthListView.getTags().keySet())
                                                                : null)
                   .userJourneyName(sloHealthListView.getUserJourneyName())
                   .monitoredServiceIdentifier(sloHealthListView.getMonitoredServiceIdentifier())
                   .monitoredServiceName(sloHealthListView.getMonitoredServiceName())
                   .healthSourceIdentifier(sloHealthListView.getHealthSourceIdentifier())
                   .healthSourceName(sloHealthListView.getHealthSourceName())
                   .serviceName(sloHealthListView.getServiceName())
                   .serviceIdentifier(sloHealthListView.getServiceIdentifier())
                   .errorBudgetRisk(sloHealthListView.getErrorBudgetRisk().getDisplayName())
                   .environmentIdentifier(sloHealthListView.getEnvironmentIdentifier())
                   .environmentName(sloHealthListView.getEnvironmentName())
                   .burnRate(sloHealthListView.getBurnRate())
                   .errorBudgetRemainingPercentage(sloHealthListView.getErrorBudgetRemainingPercentage())
                   .errorBudgetRemaining(sloHealthListView.getErrorBudgetRemaining())
                   .totalErrorBudget(sloHealthListView.getTotalErrorBudget())
                   .sloTargetType(new io.harness.spec.server.cvng.v1.model.SLOTargetType().type(
                       sloHealthListView.getSloTargetType().getIdentifier()))
                   .sloType(new io.harness.spec.server.cvng.v1.model.ServiceLevelObjectiveType().type(
                       sloHealthListView.getSloType().getIdentifier()))
                   .evaluationType(new io.harness.spec.server.cvng.v1.model.SLIEvaluationType().type(
                       sloHealthListView.getEvaluationType().getIdentifier()))
                   .noOfActiveAlerts(sloHealthListView.getNoOfActiveAlerts())
                   .downtimeStatusDetails(sloHealthListView.getDowntimeStatusDetails() != null
                           ? new DowntimeStatusDetails()
                                 .status(new DowntimeStatus().status(
                                     sloHealthListView.getDowntimeStatusDetails().getStatus().getIdentifier()))
                                 .startTime(sloHealthListView.getDowntimeStatusDetails().getStartTime())
                                 .endTime(sloHealthListView.getDowntimeStatusDetails().getEndTime())
                                 .endDateTime(sloHealthListView.getDowntimeStatusDetails().getEndDateTime())
                           : null)
                   .sloError(sloHealthListView.getSloError() != null
                           ? new SLOError()
                                 .failedState(sloHealthListView.getSloError().isFailedState())
                                 .errorMessage(sloHealthListView.getSloError().getErrorMessage())
                                 .sloErrorType(sloHealthListView.getSloError().getSloErrorType() != null
                                         ? sloHealthListView.getSloError().getSloErrorType().getIdentifier()
                                         : null)
                           : null)
                   .userJourneys(sloHealthListView.getUserJourneys()
                                     .stream()
                                     .map(userJourneyDTO
                                         -> new UserJourney()
                                                .name(userJourneyDTO.getName())
                                                .identifier(userJourneyDTO.getIdentifier()))
                                     .collect(Collectors.toList()))
                   .sloTargetPercentage(sloHealthListView.getSloTargetPercentage()))
        .collect(Collectors.toList());
  }
}
