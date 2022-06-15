/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.dashboardaggregateservice.impl;

import static io.harness.dashboards.SortBy.DEPLOYMENTS;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dashboards.DeploymentStatsSummary;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.LandingDashboardRequestCD;
import io.harness.dashboards.PipelineExecutionDashboardInfo;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectDashBoardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServiceDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.dashboards.TimeBasedDeploymentInfo;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.overviewdashboard.bean.OverviewDashboardRequestType;
import io.harness.overviewdashboard.bean.RestCallRequest;
import io.harness.overviewdashboard.bean.RestCallResponse;
import io.harness.overviewdashboard.dashboardaggregateservice.service.OverviewDashboardService;
import io.harness.overviewdashboard.dtos.AccountInfo;
import io.harness.overviewdashboard.dtos.ActiveServiceInfo;
import io.harness.overviewdashboard.dtos.CountChangeAndCountChangeRateInfo;
import io.harness.overviewdashboard.dtos.CountChangeDetails;
import io.harness.overviewdashboard.dtos.CountInfo;
import io.harness.overviewdashboard.dtos.CountOverview;
import io.harness.overviewdashboard.dtos.CountWithSuccessFailureDetails;
import io.harness.overviewdashboard.dtos.DeploymentsOverview;
import io.harness.overviewdashboard.dtos.DeploymentsStatsOverview;
import io.harness.overviewdashboard.dtos.DeploymentsStatsSummary;
import io.harness.overviewdashboard.dtos.ExecutionResponse;
import io.harness.overviewdashboard.dtos.ExecutionStatus;
import io.harness.overviewdashboard.dtos.MostActiveServicesList;
import io.harness.overviewdashboard.dtos.OrgInfo;
import io.harness.overviewdashboard.dtos.PipelineExecutionInfo;
import io.harness.overviewdashboard.dtos.PipelineInfo;
import io.harness.overviewdashboard.dtos.ProjectInfo;
import io.harness.overviewdashboard.dtos.RateAndRateChangeInfo;
import io.harness.overviewdashboard.dtos.ServiceInfo;
import io.harness.overviewdashboard.dtos.TimeBasedStats;
import io.harness.overviewdashboard.dtos.TopProjectsDashboardInfo;
import io.harness.overviewdashboard.dtos.TopProjectsPanel;
import io.harness.overviewdashboard.rbac.service.DashboardRBACService;
import io.harness.overviewdashboard.remote.ParallelRestCallExecutor;
import io.harness.pipeline.dashboards.PMSLandingDashboardResourceClient;
import io.harness.pms.dashboards.GroupBy;
import io.harness.pms.dashboards.LandingDashboardRequestPMS;
import io.harness.pms.dashboards.PipelinesCount;
import io.harness.project.remote.ProjectClient;

import com.google.inject.Inject;
import dashboards.CDLandingDashboardResourceClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class OverviewDashboardServiceImpl implements OverviewDashboardService {
  private final String SUCCESS_MESSAGE = "Successfully fetched data";
  private final String FAILURE_MESSAGE = "Failed to fetch data";

  DashboardRBACService dashboardRBACService;
  CDLandingDashboardResourceClient cdLandingDashboardResourceClient;
  PMSLandingDashboardResourceClient pmsLandingDashboardResourceClient;
  ParallelRestCallExecutor parallelRestCallExecutor;
  ProjectClient projectClient;

  @Override
  public ExecutionResponse<TopProjectsPanel> getTopProjectsPanel(
      String accountIdentifier, String userId, long startInterval, long endInterval) {
    List<ProjectDTO> listOfAccessibleProject = dashboardRBACService.listAccessibleProject(accountIdentifier, userId);
    List<String> orgIdentifiers = getOrgIdentifiers(listOfAccessibleProject);
    Map<String, String> mapOfOrganizationIdentifierAndOrganizationName =
        dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(accountIdentifier, orgIdentifiers);
    List<OrgProjectIdentifier> orgProjectIdentifierList = getOrgProjectIdentifier(listOfAccessibleProject);
    LandingDashboardRequestCD landingDashboardRequestCD =
        LandingDashboardRequestCD.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();
    Map<String, String> mapOfProjectIdentifierAndProjectName =
        getMapOfProjectIdentifierAndProjectName(listOfAccessibleProject);
    List<RestCallRequest> restCallRequestList = getRestCallRequestListForTopProjectsPanel(
        accountIdentifier, startInterval, endInterval, landingDashboardRequestCD);
    List<RestCallResponse> restCallResponses = parallelRestCallExecutor.executeRestCalls(restCallRequestList);

    Optional<RestCallResponse> cdProjectsDashBoardInfoOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_CD_TOP_PROJECT_LIST);

    ExecutionResponse<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>
        executionResponseCDTopProjectsInfoList =
            getExecutionResponseCDTopProjectsInfoList(cdProjectsDashBoardInfoOptional,
                mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName);
    ExecutionResponse<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>
        executionResponseCITopProjectsInfoList = getExecutionResponseCITopProjectsInfoList();
    ExecutionResponse<List<TopProjectsDashboardInfo<CountInfo>>> executionResponseCFTopProjectsInfoList =
        getExecutionResponseCFTopProjectsInfoList();

    if (executionResponseCDTopProjectsInfoList.getExecutionStatus() == ExecutionStatus.SUCCESS
        || executionResponseCITopProjectsInfoList.getExecutionStatus() == ExecutionStatus.SUCCESS
        || executionResponseCFTopProjectsInfoList.getExecutionStatus() == ExecutionStatus.SUCCESS) {
      return ExecutionResponse.<TopProjectsPanel>builder()
          .response(TopProjectsPanel.builder()
                        .CDTopProjectsInfo(executionResponseCDTopProjectsInfoList)
                        .CITopProjectsInfo(executionResponseCITopProjectsInfoList)
                        .CFTopProjectsInfo(executionResponseCFTopProjectsInfoList)
                        .build())
          .executionStatus(ExecutionStatus.SUCCESS)
          .executionMessage(SUCCESS_MESSAGE)
          .build();
    } else {
      return ExecutionResponse.<TopProjectsPanel>builder()
          .response(TopProjectsPanel.builder()
                        .CDTopProjectsInfo(executionResponseCDTopProjectsInfoList)
                        .CITopProjectsInfo(executionResponseCITopProjectsInfoList)
                        .CFTopProjectsInfo(executionResponseCFTopProjectsInfoList)
                        .build())
          .executionStatus(ExecutionStatus.FAILURE)
          .executionMessage(FAILURE_MESSAGE)
          .build();
    }
  }

  @Override
  public ExecutionResponse<DeploymentsStatsOverview> getDeploymentStatsOverview(
      String accountIdentifier, String userId, long startInterval, long endInterval, GroupBy groupBy, SortBy sortBy) {
    List<ProjectDTO> listOfAccessibleProject = dashboardRBACService.listAccessibleProject(accountIdentifier, userId);
    List<String> orgIdentifiers = getOrgIdentifiers(listOfAccessibleProject);
    Map<String, String> mapOfOrganizationIdentifierAndOrganizationName =
        dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(accountIdentifier, orgIdentifiers);
    List<OrgProjectIdentifier> orgProjectIdentifierList = getOrgProjectIdentifier(listOfAccessibleProject);
    LandingDashboardRequestCD landingDashboardRequestCD =
        LandingDashboardRequestCD.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();
    Map<String, String> mapOfProjectIdentifierAndProjectName =
        getMapOfProjectIdentifierAndProjectName(listOfAccessibleProject);
    List<RestCallRequest> restCallRequestList = getRestCallRequestListForDeploymentStatsOverview(
        accountIdentifier, startInterval, endInterval, landingDashboardRequestCD, groupBy, sortBy);
    List<RestCallResponse> restCallResponses = parallelRestCallExecutor.executeRestCalls(restCallRequestList);

    Optional<RestCallResponse> activeDeploymentsInfoOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_ACTIVE_DEPLOYMENTS_INFO);
    Optional<RestCallResponse> deploymentStatsInfoOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_DEPLOYMENT_STATS_SUMMARY);
    Optional<RestCallResponse> mostActiveServicesOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_MOST_ACTIVE_SERVICES);

    if (activeDeploymentsInfoOptional.isPresent() && deploymentStatsInfoOptional.isPresent()
        && mostActiveServicesOptional.isPresent()) {
      if (activeDeploymentsInfoOptional.get().isCallFailed() || deploymentStatsInfoOptional.get().isCallFailed()
          || mostActiveServicesOptional.get().isCallFailed()) {
        return ExecutionResponse.<DeploymentsStatsOverview>builder()
            .executionStatus(ExecutionStatus.FAILURE)
            .executionMessage(FAILURE_MESSAGE)
            .build();
      } else {
        PipelinesExecutionDashboardInfo activeDeploymentsInfo =
            (PipelinesExecutionDashboardInfo) activeDeploymentsInfoOptional.get().getResponse();
        DeploymentStatsSummary deploymentStatsInfo =
            (DeploymentStatsSummary) deploymentStatsInfoOptional.get().getResponse();
        ServicesDashboardInfo servicesDashboardInfo =
            (ServicesDashboardInfo) mostActiveServicesOptional.get().getResponse();
        return ExecutionResponse.<DeploymentsStatsOverview>builder()
            .response(DeploymentsStatsOverview.builder()
                          .deploymentsOverview(getDeploymentsOverview(activeDeploymentsInfo,
                              mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
                          .deploymentsStatsSummary(getDeploymentStatsSummary(deploymentStatsInfo))
                          .mostActiveServicesList(getMostActiveServicesList(sortBy, servicesDashboardInfo,
                              mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
                          .build())
            .executionStatus(ExecutionStatus.SUCCESS)
            .executionMessage(SUCCESS_MESSAGE)
            .build();
      }
    }
    return ExecutionResponse.<DeploymentsStatsOverview>builder()
        .executionStatus(ExecutionStatus.FAILURE)
        .executionMessage(FAILURE_MESSAGE)
        .build();
  }

  @Override
  public ExecutionResponse<CountOverview> getCountOverview(
      String accountIdentifier, String userId, long startInterval, long endInterval) {
    List<ProjectDTO> listOfAccessibleProject = dashboardRBACService.listAccessibleProject(accountIdentifier, userId);
    List<OrgProjectIdentifier> orgProjectIdentifierList = getOrgProjectIdentifier(listOfAccessibleProject);
    LandingDashboardRequestCD landingDashboardRequestCD =
        LandingDashboardRequestCD.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();
    LandingDashboardRequestPMS landingDashboardRequestPMS =
        LandingDashboardRequestPMS.builder().orgProjectIdentifiers(orgProjectIdentifierList).build();
    List<RestCallRequest> restCallRequestList = getRestCallRequestListForCountOverview(
        accountIdentifier, userId, startInterval, endInterval, landingDashboardRequestCD, landingDashboardRequestPMS);
    List<RestCallResponse> restCallResponses = parallelRestCallExecutor.executeRestCalls(restCallRequestList);

    Optional<RestCallResponse> servicesCountOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_SERVICES_COUNT);
    Optional<RestCallResponse> envCountOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_ENV_COUNT);
    Optional<RestCallResponse> pipelinesCountOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_PIPELINES_COUNT);
    Optional<RestCallResponse> projectsCountOptional =
        getResponseOptional(restCallResponses, OverviewDashboardRequestType.GET_PROJECTS_COUNT);

    if (servicesCountOptional.isPresent() && envCountOptional.isPresent() && pipelinesCountOptional.isPresent()
        && projectsCountOptional.isPresent()) {
      if (servicesCountOptional.get().isCallFailed() || envCountOptional.get().isCallFailed()
          || pipelinesCountOptional.get().isCallFailed() || projectsCountOptional.get().isCallFailed()) {
        return ExecutionResponse.<CountOverview>builder()
            .executionStatus(ExecutionStatus.FAILURE)
            .executionMessage(FAILURE_MESSAGE)
            .build();
      } else {
        ServicesCount servicesCount = (ServicesCount) servicesCountOptional.get().getResponse();
        EnvCount envCount = (EnvCount) envCountOptional.get().getResponse();
        PipelinesCount pipelinesCount = (PipelinesCount) pipelinesCountOptional.get().getResponse();
        ActiveProjectsCountDTO projectsNewCount = (ActiveProjectsCountDTO) projectsCountOptional.get().getResponse();
        return ExecutionResponse.<CountOverview>builder()
            .response(CountOverview.builder()
                          .servicesCountDetail(getServicesCount(servicesCount))
                          .envCountDetail(getEnvCount(envCount))
                          .pipelinesCountDetail(getPipelinesCount(pipelinesCount))
                          .projectsCountDetail(getProjectsCount(listOfAccessibleProject.size(), projectsNewCount))
                          .build())
            .executionStatus(ExecutionStatus.SUCCESS)
            .executionMessage(SUCCESS_MESSAGE)
            .build();
      }
    }
    return ExecutionResponse.<CountOverview>builder()
        .executionStatus(ExecutionStatus.FAILURE)
        .executionMessage(FAILURE_MESSAGE)
        .build();
  }

  private DeploymentsOverview getDeploymentsOverview(PipelinesExecutionDashboardInfo activeDeploymentsInfo,
      Map<String, String> mapOfProjectIdentifierAndProjectName,
      Map<String, String> mapOfOrganizationIdentifierAndOrganizationName) {
    return DeploymentsOverview.builder()
        .failed24HrsExecutions(getExecutions(activeDeploymentsInfo.getFailed24HrsExecutions(),
            mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
        .pendingApprovalExecutions(getExecutions(activeDeploymentsInfo.getPendingApprovalExecutions(),
            mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
        .pendingManualInterventionExecutions(
            getExecutions(activeDeploymentsInfo.getPendingManualInterventionExecutions(),
                mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
        .runningExecutions(getExecutions(activeDeploymentsInfo.getRunningExecutions(),
            mapOfProjectIdentifierAndProjectName, mapOfOrganizationIdentifierAndOrganizationName))
        .build();
  }

  private List<PipelineExecutionInfo> getExecutions(List<PipelineExecutionDashboardInfo> executions,
      Map<String, String> mapOfProjectIdentifierAndProjectName,
      Map<String, String> mapOfOrganizationIdentifierAndOrganizationName) {
    List<PipelineExecutionInfo> executionsList = new ArrayList<>();
    for (PipelineExecutionDashboardInfo pipelineExecutionDashboardInfo : emptyIfNull(executions)) {
      executionsList.add(PipelineExecutionInfo.builder()
                             .pipelineInfo(PipelineInfo.builder()
                                               .pipelineIdentifier(pipelineExecutionDashboardInfo.getIdentifier())
                                               .pipelineName(pipelineExecutionDashboardInfo.getName())
                                               .build())
                             .accountInfo(AccountInfo.builder()
                                              .accountIdentifier(pipelineExecutionDashboardInfo.getAccountIdentifier())
                                              .build())
                             .orgInfo(OrgInfo.builder()
                                          .orgIdentifier(pipelineExecutionDashboardInfo.getOrgIdentifier())
                                          .orgName(mapOfOrganizationIdentifierAndOrganizationName.get(
                                              pipelineExecutionDashboardInfo.getOrgIdentifier()))
                                          .build())
                             .projectInfo(ProjectInfo.builder()
                                              .projectIdentifier(pipelineExecutionDashboardInfo.getProjectIdentifier())
                                              .projectName(mapOfProjectIdentifierAndProjectName.get(
                                                  pipelineExecutionDashboardInfo.getProjectIdentifier()))
                                              .build())
                             .planExecutionId(pipelineExecutionDashboardInfo.getPlanExecutionId())
                             .startTs(pipelineExecutionDashboardInfo.getStartTs())
                             .build());
    }
    return executionsList;
  }

  private DeploymentsStatsSummary getDeploymentStatsSummary(DeploymentStatsSummary deploymentStatsSummary) {
    return DeploymentsStatsSummary.builder()
        .countAndChangeRate(CountChangeDetails.builder()
                                .count(deploymentStatsSummary.getTotalCount())
                                .countChangeAndCountChangeRateInfo(
                                    CountChangeAndCountChangeRateInfo.builder()
                                        .countChangeRate(deploymentStatsSummary.getTotalCountChangeRate())
                                        .build())
                                .build())
        .failureRateAndChangeRate(RateAndRateChangeInfo.builder()
                                      .rate(deploymentStatsSummary.getFailureRate())
                                      .rateChangeRate(deploymentStatsSummary.getFailureRateChangeRate())
                                      .build())
        .deploymentRateAndChangeRate(RateAndRateChangeInfo.builder()
                                         .rate(deploymentStatsSummary.getDeploymentRate())
                                         .rateChangeRate(deploymentStatsSummary.getDeploymentRateChangeRate())
                                         .build())
        .deploymentStats(getTimeWiseDeploymentInfo(deploymentStatsSummary.getTimeBasedDeploymentInfoList()))
        .build();
  }

  private List<TimeBasedStats> getTimeWiseDeploymentInfo(List<TimeBasedDeploymentInfo> timeBasedDeploymentInfoList) {
    List<TimeBasedStats> timeBasedStatsList = new ArrayList<>();
    for (TimeBasedDeploymentInfo timeBasedDeploymentInfo : emptyIfNull(timeBasedDeploymentInfoList)) {
      TimeBasedStats timeBasedStats =
          TimeBasedStats.builder()
              .time(timeBasedDeploymentInfo.getEpochTime())
              .countWithSuccessFailureDetails(CountWithSuccessFailureDetails.builder()
                                                  .count(timeBasedDeploymentInfo.getTotalCount())
                                                  .failureCount(timeBasedDeploymentInfo.getFailedCount())
                                                  .successCount(timeBasedDeploymentInfo.getSuccessCount())
                                                  .build())
              .build();
      timeBasedStatsList.add(timeBasedStats);
    }
    return timeBasedStatsList;
  }

  private MostActiveServicesList getMostActiveServicesList(SortBy sortBy, ServicesDashboardInfo servicesDashboardInfo,
      Map<String, String> mapOfProjectIdentifierAndProjectName,
      Map<String, String> mapOfOrganizationIdentifierAndOrganizationName) {
    List<ActiveServiceInfo> activeServiceInfoList = new ArrayList<>();
    for (ServiceDashboardInfo serviceDashboardInfo : emptyIfNull(servicesDashboardInfo.getServiceDashboardInfoList())) {
      CountWithSuccessFailureDetails countWithSuccessFailureDetails =
          CountWithSuccessFailureDetails.builder()
              .failureCount(serviceDashboardInfo.getFailureDeploymentsCount())
              .successCount(serviceDashboardInfo.getSuccessDeploymentsCount())
              .countChangeAndCountChangeRateInfo(
                  CountChangeAndCountChangeRateInfo.builder()
                      .countChangeRate((sortBy == DEPLOYMENTS) ? serviceDashboardInfo.getTotalDeploymentsChangeRate()
                                                               : serviceDashboardInfo.getInstancesCountChangeRate())
                      .build())
              .count((sortBy == DEPLOYMENTS) ? serviceDashboardInfo.getTotalDeploymentsCount()
                                             : serviceDashboardInfo.getInstancesCount())
              .build();

      ActiveServiceInfo activeServiceInfo =
          ActiveServiceInfo.builder()
              .accountInfo(AccountInfo.builder().accountIdentifier(serviceDashboardInfo.getAccountIdentifier()).build())
              .orgInfo(OrgInfo.builder()
                           .orgIdentifier(serviceDashboardInfo.getOrgIdentifier())
                           .orgName(mapOfOrganizationIdentifierAndOrganizationName.get(
                               serviceDashboardInfo.getOrgIdentifier()))
                           .build())
              .projectInfo(ProjectInfo.builder()
                               .projectIdentifier(serviceDashboardInfo.getProjectIdentifier())
                               .projectName(mapOfProjectIdentifierAndProjectName.get(
                                   serviceDashboardInfo.getProjectIdentifier()))
                               .build())
              .serviceInfo(ServiceInfo.builder()
                               .serviceIdentifier(serviceDashboardInfo.getIdentifier())
                               .serviceName(serviceDashboardInfo.getName())
                               .build())
              .countWithSuccessFailureDetails(countWithSuccessFailureDetails)
              .build();
      activeServiceInfoList.add(activeServiceInfo);
    }
    return MostActiveServicesList.builder().activeServices(activeServiceInfoList).build();
  }

  private CountChangeDetails getServicesCount(ServicesCount servicesCount) {
    return CountChangeDetails.builder()
        .countChangeAndCountChangeRateInfo(
            CountChangeAndCountChangeRateInfo.builder().countChange(servicesCount.getNewCount()).build())
        .count(servicesCount.getTotalCount())
        .build();
  }

  private CountChangeDetails getEnvCount(EnvCount envCount) {
    return CountChangeDetails.builder()
        .countChangeAndCountChangeRateInfo(
            CountChangeAndCountChangeRateInfo.builder().countChange(envCount.getNewCount()).build())
        .count(envCount.getTotalCount())
        .build();
  }

  private CountChangeDetails getPipelinesCount(PipelinesCount pipelinesCount) {
    return CountChangeDetails.builder()
        .countChangeAndCountChangeRateInfo(
            CountChangeAndCountChangeRateInfo.builder().countChange(pipelinesCount.getNewCount()).build())
        .count(pipelinesCount.getTotalCount())
        .build();
  }

  private CountChangeDetails getProjectsCount(long totalCount, ActiveProjectsCountDTO newCount) {
    return CountChangeDetails.builder()
        .countChangeAndCountChangeRateInfo(
            CountChangeAndCountChangeRateInfo.builder().countChange(newCount.getCount()).build())
        .count(totalCount)
        .build();
  }

  private List<OrgProjectIdentifier> getOrgProjectIdentifier(List<ProjectDTO> listOfAccessibleProject) {
    return emptyIfNull(listOfAccessibleProject)
        .stream()
        .map(projectDTO
            -> OrgProjectIdentifier.builder()
                   .orgIdentifier(projectDTO.getOrgIdentifier())
                   .projectIdentifier(projectDTO.getIdentifier())
                   .build())
        .collect(Collectors.toList());
  }

  private Map<String, String> getMapOfProjectIdentifierAndProjectName(List<ProjectDTO> listOfAccessibleProject) {
    Map<String, String> mapOfProjectIdentifierAndProjectName = new HashMap<>();
    for (ProjectDTO projectDTO : listOfAccessibleProject) {
      mapOfProjectIdentifierAndProjectName.put(projectDTO.getIdentifier(), projectDTO.getName());
    }
    return mapOfProjectIdentifierAndProjectName;
  }

  private Optional<RestCallResponse> getResponseOptional(
      List<RestCallResponse> restCallResponses, OverviewDashboardRequestType overviewDashboardRequestType) {
    return emptyIfNull(restCallResponses)
        .stream()
        .filter(k -> k.getRequestType() == overviewDashboardRequestType)
        .findAny();
  }

  private List<RestCallRequest> getRestCallRequestListForTopProjectsPanel(String accountIdentifier, long startInterval,
      long endInterval, LandingDashboardRequestCD landingDashboardRequestCD) {
    List<RestCallRequest> restCallRequestList = new ArrayList<>();
    restCallRequestList.add(RestCallRequest.<ProjectsDashboardInfo>builder()
                                .request(cdLandingDashboardResourceClient.getTopProjects(
                                    accountIdentifier, startInterval, endInterval, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_CD_TOP_PROJECT_LIST)
                                .build());
    return restCallRequestList;
  }

  private List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>> getCDTopProjectsInfoList(
      ProjectsDashboardInfo cdProjectsDashBoardInfo, Map<String, String> mapOfProjectIdentifierAndProjectName,
      Map<String, String> mapOfOrganizationIdentifierAndOrganizationName) {
    List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>> cdTopProjectsInfoList = new ArrayList<>();
    for (ProjectDashBoardInfo projectDashBoardInfo :
        emptyIfNull(cdProjectsDashBoardInfo.getProjectDashBoardInfoList())) {
      cdTopProjectsInfoList.add(
          TopProjectsDashboardInfo.<CountWithSuccessFailureDetails>builder()
              .accountInfo(AccountInfo.builder().accountIdentifier(projectDashBoardInfo.getAccountId()).build())
              .orgInfo(OrgInfo.builder()
                           .orgIdentifier(projectDashBoardInfo.getOrgIdentifier())
                           .orgName(mapOfOrganizationIdentifierAndOrganizationName.get(
                               projectDashBoardInfo.getOrgIdentifier()))
                           .build())
              .projectInfo(ProjectInfo.builder()
                               .projectIdentifier(projectDashBoardInfo.getProjectIdentifier())
                               .projectName(mapOfProjectIdentifierAndProjectName.get(
                                   projectDashBoardInfo.getProjectIdentifier()))
                               .build())
              .countDetails(CountWithSuccessFailureDetails.builder()
                                .count(projectDashBoardInfo.getDeploymentsCount())
                                .failureCount(projectDashBoardInfo.getFailedDeploymentsCount())
                                .successCount(projectDashBoardInfo.getSuccessDeploymentsCount())
                                .countChangeAndCountChangeRateInfo(
                                    CountChangeAndCountChangeRateInfo.builder()
                                        .countChangeRate(projectDashBoardInfo.getDeploymentsCountChangeRate())
                                        .build())
                                .build())
              .build());
    }
    return cdTopProjectsInfoList;
  }

  private List<RestCallRequest> getRestCallRequestListForDeploymentStatsOverview(String accountIdentifier,
      long startInterval, long endInterval, LandingDashboardRequestCD landingDashboardRequestCD, GroupBy groupBy,
      SortBy sortBy) {
    List<RestCallRequest> restCallRequestList = new ArrayList<>();
    restCallRequestList.add(RestCallRequest.<PipelinesExecutionDashboardInfo>builder()
                                .request(cdLandingDashboardResourceClient.getActiveDeploymentStats(
                                    accountIdentifier, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_ACTIVE_DEPLOYMENTS_INFO)
                                .build());
    restCallRequestList.add(RestCallRequest.<DeploymentStatsSummary>builder()
                                .request(cdLandingDashboardResourceClient.getDeploymentStatsSummary(
                                    accountIdentifier, startInterval, endInterval, groupBy, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_DEPLOYMENT_STATS_SUMMARY)
                                .build());
    restCallRequestList.add(RestCallRequest.<ServicesDashboardInfo>builder()
                                .request(cdLandingDashboardResourceClient.get(
                                    accountIdentifier, startInterval, endInterval, sortBy, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_MOST_ACTIVE_SERVICES)
                                .build());
    return restCallRequestList;
  }

  private List<RestCallRequest> getRestCallRequestListForCountOverview(String accountIdentifier, String userId,
      long startInterval, long endInterval, LandingDashboardRequestCD landingDashboardRequestCD,
      LandingDashboardRequestPMS landingDashboardRequestPMS) {
    List<RestCallRequest> restCallRequestList = new ArrayList<>();
    restCallRequestList.add(RestCallRequest.<ServicesCount>builder()
                                .request(cdLandingDashboardResourceClient.getServicesCount(
                                    accountIdentifier, startInterval, endInterval, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_SERVICES_COUNT)
                                .build());
    restCallRequestList.add(RestCallRequest.<EnvCount>builder()
                                .request(cdLandingDashboardResourceClient.getEnvCount(
                                    accountIdentifier, startInterval, endInterval, landingDashboardRequestCD))
                                .requestType(OverviewDashboardRequestType.GET_ENV_COUNT)
                                .build());
    restCallRequestList.add(RestCallRequest.<PipelinesCount>builder()
                                .request(pmsLandingDashboardResourceClient.getPipelinesCount(
                                    accountIdentifier, startInterval, endInterval, landingDashboardRequestPMS))
                                .requestType(OverviewDashboardRequestType.GET_PIPELINES_COUNT)
                                .build());
    restCallRequestList.add(
        RestCallRequest.<ActiveProjectsCountDTO>builder()
            .request(projectClient.getAccessibleProjectsCount(accountIdentifier, startInterval, endInterval))
            .requestType(OverviewDashboardRequestType.GET_PROJECTS_COUNT)
            .build());
    return restCallRequestList;
  }

  private ExecutionResponse<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>
  getExecutionResponseCDTopProjectsInfoList(Optional<RestCallResponse> cdProjectsDashBoardInfoOptional,
      Map<String, String> mapOfProjectIdentifierAndProjectName,
      Map<String, String> mapOfOrganizationIdentifierAndOrganizationName) {
    ExecutionResponse<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>
        executionResponseCDTopProjectsInfoList;
    if (cdProjectsDashBoardInfoOptional.isPresent()) {
      if (cdProjectsDashBoardInfoOptional.get().isCallFailed()) {
        executionResponseCDTopProjectsInfoList =
            ExecutionResponse.<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>builder()
                .executionStatus(ExecutionStatus.FAILURE)
                .executionMessage(FAILURE_MESSAGE)
                .build();
      } else {
        ProjectsDashboardInfo cdProjectsDashBoardInfo =
            (ProjectsDashboardInfo) cdProjectsDashBoardInfoOptional.get().getResponse();
        executionResponseCDTopProjectsInfoList =
            ExecutionResponse.<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>builder()
                .response(getCDTopProjectsInfoList(cdProjectsDashBoardInfo, mapOfProjectIdentifierAndProjectName,
                    mapOfOrganizationIdentifierAndOrganizationName))
                .executionStatus(ExecutionStatus.SUCCESS)
                .executionMessage(SUCCESS_MESSAGE)
                .build();
      }
    } else {
      executionResponseCDTopProjectsInfoList =
          ExecutionResponse.<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>builder()
              .executionStatus(ExecutionStatus.FAILURE)
              .executionMessage(FAILURE_MESSAGE)
              .build();
    }
    return executionResponseCDTopProjectsInfoList;
  }

  private ExecutionResponse<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>
  getExecutionResponseCITopProjectsInfoList() {
    return ExecutionResponse.<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>builder()
        .executionStatus(ExecutionStatus.FAILURE)
        .executionMessage(FAILURE_MESSAGE)
        .build();
  }

  private ExecutionResponse<List<TopProjectsDashboardInfo<CountInfo>>> getExecutionResponseCFTopProjectsInfoList() {
    return ExecutionResponse.<List<TopProjectsDashboardInfo<CountInfo>>>builder()
        .executionStatus(ExecutionStatus.FAILURE)
        .executionMessage(FAILURE_MESSAGE)
        .build();
  }

  private List<String> getOrgIdentifiers(List<ProjectDTO> listOfAccessibleProject) {
    return emptyIfNull(listOfAccessibleProject).stream().map(ProjectDTO::getOrgIdentifier).collect(Collectors.toList());
  }
}
