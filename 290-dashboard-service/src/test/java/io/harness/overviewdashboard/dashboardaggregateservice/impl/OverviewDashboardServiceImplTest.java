/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.dashboardaggregateservice.impl;

import static io.harness.beans.FeatureName.LANDING_OVERVIEW_PAGE_V2;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_ACTIVE_DEPLOYMENTS_INFO;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_CD_TOP_PROJECT_LIST;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_DEPLOYMENT_STATS_SUMMARY;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_ENV_COUNT;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_EXECUTIONS_COUNT;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_MOST_ACTIVE_SERVICES;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_PIPELINES_COUNT;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_PROJECTS_COUNT;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_SERVICES_COUNT;
import static io.harness.overviewdashboard.bean.OverviewDashboardRequestType.GET_USER_COUNT;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.MEET;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.dashboards.DeploymentStatsSummary;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.PipelineExecutionDashboardInfo;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectDashBoardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServiceDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.dashboards.TimeBasedDeploymentInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UsersCountDTO;
import io.harness.overviewdashboard.bean.RestCallResponse;
import io.harness.overviewdashboard.dtos.AccountInfo;
import io.harness.overviewdashboard.dtos.ActiveServiceInfo;
import io.harness.overviewdashboard.dtos.CountChangeAndCountChangeRateInfo;
import io.harness.overviewdashboard.dtos.CountChangeDetails;
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
import io.harness.overviewdashboard.rbac.impl.DashboardRBACServiceImpl;
import io.harness.overviewdashboard.remote.ParallelRestCallExecutor;
import io.harness.pipeline.dashboards.PMSLandingDashboardResourceClient;
import io.harness.pms.dashboards.ExecutionsCount;
import io.harness.pms.dashboards.GroupBy;
import io.harness.pms.dashboards.PipelinesCount;
import io.harness.project.remote.ProjectClient;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.utils.NGFeatureFlagHelperService;

import dashboards.CDLandingDashboardResourceClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

public class OverviewDashboardServiceImplTest {
  String accountIdentifier1 = UUIDGenerator.generateUuid();
  String userId1 = UUIDGenerator.generateUuid();
  long startTime1 = 1628899200000L;
  long endTime1 = 1631491200000L;
  String serviceIdentifier1 = UUIDGenerator.generateUuid();
  String serviceName1 = UUIDGenerator.generateUuid();
  String projectIdentifier1 = UUIDGenerator.generateUuid();

  String projectIdentifier2 = UUIDGenerator.generateUuid();

  String projectName1 = UUIDGenerator.generateUuid();
  String orgIdentifier1 = UUIDGenerator.generateUuid();
  String orgName1 = UUIDGenerator.generateUuid();
  String planExecutionId1 = UUIDGenerator.generateUuid();
  String pipelineName1 = UUIDGenerator.generateUuid();
  String pipelineIdentifier1 = UUIDGenerator.generateUuid();
  List<ProjectDTO> listOfAccessibleProjects = new ArrayList<>();

  Map<String, String> mapOfOrganizationIdentifierAndOrganizationName = new HashMap<>();
  List<RestCallResponse> deploymentStatsOverviewRestCallResponseList1 = new ArrayList<>();
  List<RestCallResponse> deploymentStatsOverviewRestCallResponseList2 = new ArrayList<>();
  List<RestCallResponse> deploymentStatsOverviewRestCallResponseList3 = new ArrayList<>();
  List<RestCallResponse> restCallResponseListForCountOverview1 = new ArrayList<>();
  List<RestCallResponse> restCallResponseListForCountOverview2 = new ArrayList<>();

  DeploymentsStatsOverview expectedDeploymentsStatsOverview1;
  DeploymentsStatsOverview expectedDeploymentsStatsOverview2;
  DeploymentsStatsOverview expectedDeploymentsStatsOverview3;
  CountOverview expectedCountOverview1;

  CountOverview expectedCountOverview2;
  CountOverview expectedCountOverview3;

  @InjectMocks OverviewDashboardServiceImpl overviewDashboardService;
  @Mock ParallelRestCallExecutor parallelRestCallExecutor;
  @Mock DashboardRBACServiceImpl dashboardRBACService;
  @Mock ProjectClient projectClient;
  @Mock CDLandingDashboardResourceClient cdLandingDashboardResourceClient;
  @Mock PMSLandingDashboardResourceClient pmsLandingDashboardResourceClient;

  @Mock NGFeatureFlagHelperService featureFlagHelperService;

  @Mock UserClient userClient;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(featureFlagHelperService.isEnabled(accountIdentifier1, LANDING_OVERVIEW_PAGE_V2)).thenReturn(false);
    mapOfOrganizationIdentifierAndOrganizationName.put(orgIdentifier1, orgName1);
    listOfAccessibleProjects.add(
        ProjectDTO.builder().identifier(projectIdentifier1).orgIdentifier(orgIdentifier1).name(projectName1).build());
    Call<ResponseDTO<UsersCountDTO>> usersCountResponse = Mockito.mock(Call.class);
    when(userClient.getUsersCount(anyString(), anyLong(), anyLong())).thenReturn(usersCountResponse);

    List<TimeBasedDeploymentInfo> timeBasedDeploymentInfoList1 = new ArrayList<>();
    timeBasedDeploymentInfoList1.add(getTimeBasedDeploymentInfo(10L, 10L, 10L, 10L));
    RestCallResponse<DeploymentStatsSummary> deploymentStatsSummaryRestCallResponse1 =
        getDeploymentStatsSummary(10L, 0.24, 0.24, 0.24, 0.24, 0.24, timeBasedDeploymentInfoList1);

    List<PipelineExecutionDashboardInfo> failed24HrsExecutions1 = new ArrayList<>();
    failed24HrsExecutions1.add(getExecutions(accountIdentifier1, pipelineIdentifier1, orgIdentifier1,
        projectIdentifier1, pipelineName1, planExecutionId1, startTime1));
    List<PipelineExecutionDashboardInfo> pendingApprovalExecutions1 = new ArrayList<>();
    pendingApprovalExecutions1.add(getExecutions(accountIdentifier1, pipelineIdentifier1, orgIdentifier1,
        projectIdentifier1, pipelineName1, planExecutionId1, startTime1));
    List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions1 = new ArrayList<>();
    pendingManualInterventionExecutions1.add(getExecutions(accountIdentifier1, pipelineIdentifier1, orgIdentifier1,
        projectIdentifier1, pipelineName1, planExecutionId1, startTime1));
    List<PipelineExecutionDashboardInfo> runningExecutions1 = new ArrayList<>();
    runningExecutions1.add(getExecutions(accountIdentifier1, pipelineIdentifier1, orgIdentifier1, projectIdentifier1,
        pipelineName1, planExecutionId1, startTime1));
    RestCallResponse<PipelinesExecutionDashboardInfo> activeDeploymentsStatsInfo1 = getActiveDeploymentsInfo(
        failed24HrsExecutions1, pendingApprovalExecutions1, pendingManualInterventionExecutions1, runningExecutions1);

    List<ServiceDashboardInfo> serviceDashboardInfoList1 = new ArrayList<>();
    serviceDashboardInfoList1.add(getActiveService(
        accountIdentifier1, 10L, serviceIdentifier1, serviceName1, orgIdentifier1, projectIdentifier1, 10L, 0.24, 10L));
    RestCallResponse<ServicesDashboardInfo> mostActivesServices1 = getMostActiveServices(serviceDashboardInfoList1);

    deploymentStatsOverviewRestCallResponseList1.add(deploymentStatsSummaryRestCallResponse1);
    deploymentStatsOverviewRestCallResponseList1.add(activeDeploymentsStatsInfo1);
    deploymentStatsOverviewRestCallResponseList1.add(mostActivesServices1);

    deploymentStatsOverviewRestCallResponseList2.add(deploymentStatsSummaryRestCallResponse1);
    deploymentStatsOverviewRestCallResponseList2.add(mostActivesServices1);

    deploymentStatsOverviewRestCallResponseList3.add(activeDeploymentsStatsInfo1);

    List<TimeBasedStats> timeBasedStatsList1 = new ArrayList<>();
    timeBasedStatsList1.add(getTimeBasedStats(10L, 10L, 10L, 10L));
    DeploymentsStatsSummary expectedDeploymentsStatsSummary1 =
        getExpectedDeploymentsStatsSummary(0.24, 0.24, 0.24, 0.24, 10L, 0.24, timeBasedStatsList1);

    List<ActiveServiceInfo> activeServiceInfoList1 = new ArrayList<>();
    activeServiceInfoList1.add(getActiveServiceInfo(serviceName1, serviceIdentifier1, accountIdentifier1,
        orgIdentifier1, orgName1, projectIdentifier1, projectName1, 10L, 10L, 10L, 0.24));
    MostActiveServicesList expectedMostActiveServicesList = getMostActiveServiceList(activeServiceInfoList1);

    List<PipelineExecutionInfo> expectedFailed24HrsExecutions1 = new ArrayList<>();
    expectedFailed24HrsExecutions1.add(getExpectedExecutions(accountIdentifier1, pipelineName1, pipelineIdentifier1,
        orgIdentifier1, orgName1, projectIdentifier1, projectName1, planExecutionId1, startTime1));
    List<PipelineExecutionInfo> expectedPendingApprovalExecutions1 = new ArrayList<>();
    expectedPendingApprovalExecutions1.add(getExpectedExecutions(accountIdentifier1, pipelineName1, pipelineIdentifier1,
        orgIdentifier1, orgName1, projectIdentifier1, projectName1, planExecutionId1, startTime1));
    List<PipelineExecutionInfo> expectedPendingManualInterventionExecutions1 = new ArrayList<>();
    expectedPendingManualInterventionExecutions1.add(getExpectedExecutions(accountIdentifier1, pipelineName1,
        pipelineIdentifier1, orgIdentifier1, orgName1, projectIdentifier1, projectName1, planExecutionId1, startTime1));
    List<PipelineExecutionInfo> expectedRunningExecutions1 = new ArrayList<>();
    expectedRunningExecutions1.add(getExpectedExecutions(accountIdentifier1, pipelineName1, pipelineIdentifier1,
        orgIdentifier1, orgName1, projectIdentifier1, projectName1, planExecutionId1, startTime1));
    DeploymentsOverview expectedDeploymentsOverview1 = getExpectedDeploymentsOverview(expectedFailed24HrsExecutions1,
        expectedPendingApprovalExecutions1, expectedPendingManualInterventionExecutions1, expectedRunningExecutions1);

    expectedDeploymentsStatsOverview1 = getExpectedResponseForDeploymentStatsOverview(
        expectedDeploymentsStatsSummary1, expectedMostActiveServicesList, expectedDeploymentsOverview1);
    expectedDeploymentsStatsOverview2 = getExpectedResponseForDeploymentStatsOverview(
        expectedDeploymentsStatsSummary1, expectedMostActiveServicesList, null);
    expectedDeploymentsStatsOverview3 =
        getExpectedResponseForDeploymentStatsOverview(null, null, expectedDeploymentsOverview1);

    restCallResponseListForCountOverview1.add(getServicesCount(10L, 12L));
    restCallResponseListForCountOverview1.add(getEnvCount(10L, 12L));
    restCallResponseListForCountOverview1.add(getPipelinesCount(10L, 12L));
    restCallResponseListForCountOverview1.add(getExecutionsCount(10L, 12L));
    restCallResponseListForCountOverview1.add(getProjectsCount(12));
    restCallResponseListForCountOverview1.add(getUsersCount(10L, 12L));

    restCallResponseListForCountOverview2.add(getServicesCount(10L, 12L));
    restCallResponseListForCountOverview2.add(getEnvCount(10L, 12L));
    restCallResponseListForCountOverview2.add(getPipelinesCount(10L, 12L));
    restCallResponseListForCountOverview2.add(getExecutionsCount(10L, 12L));
    restCallResponseListForCountOverview2.add(getUsersCount(10L, 12L));

    CountChangeDetails expectedServiceCountDetails1 = getExpectedCountDetail(10L, 12L);
    CountChangeDetails expectedEnvCountDetails1 = getExpectedCountDetail(10L, 12L);
    CountChangeDetails expectedPipelinesCountDetails1 = getExpectedCountDetail(10L, 12L);
    CountChangeDetails expectedExecutionsCountDetails1 = getExpectedCountDetail(10L, 12L);
    CountChangeDetails expectedUsersCountDetails1 = getExpectedCountDetail(10L, 12L);
    CountChangeDetails expectedProjectsCountDetails1 = getExpectedCountDetail(1L, 12L);
    CountChangeDetails expectedProjectsCountDetails3 = getExpectedCountDetail(0L, 0L);

    expectedCountOverview1 = getExpectedResponseForGetCountOverview(expectedServiceCountDetails1,
        expectedEnvCountDetails1, expectedPipelinesCountDetails1, null, expectedProjectsCountDetails1, null);

    expectedCountOverview2 = getExpectedResponseForGetCountOverview(expectedServiceCountDetails1,
        expectedEnvCountDetails1, expectedPipelinesCountDetails1, expectedExecutionsCountDetails1,
        expectedProjectsCountDetails3, expectedUsersCountDetails1);

    expectedCountOverview3 = getExpectedResponseForGetCountOverview(expectedServiceCountDetails1,
        expectedEnvCountDetails1, expectedPipelinesCountDetails1, null, expectedProjectsCountDetails3, null);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetDeploymentStatsOverview() throws Exception {
    GroupBy groupBy = GroupBy.MONTH;
    SortBy sortBy = SortBy.DEPLOYMENTS;

    Call<ResponseDTO<DeploymentStatsSummary>> requestDeploymentStatsSummary = Mockito.mock(Call.class);
    Call<ResponseDTO<ServicesDashboardInfo>> requestServicesDashboardInfo = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesExecutionDashboardInfo>> requestActiveDeploymentStats = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(anyString(), anyList()))
        .thenReturn(mapOfOrganizationIdentifierAndOrganizationName);
    doReturn(requestActiveDeploymentStats)
        .when(cdLandingDashboardResourceClient)
        .getActiveDeploymentStats(anyString(), any());
    when(cdLandingDashboardResourceClient.getDeploymentStatsSummary(
             anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestDeploymentStatsSummary);
    when(cdLandingDashboardResourceClient.get(anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestServicesDashboardInfo);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList1);

    ExecutionResponse<DeploymentsStatsOverview> actualSuccessResponse =
        overviewDashboardService.getDeploymentStatsOverview(
            accountIdentifier1, null, null, userId1, anyLong(), anyLong(), groupBy, sortBy);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedDeploymentsStatsOverview1);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList2);
    ExecutionResponse<DeploymentsStatsOverview> actualFailureResponse =
        overviewDashboardService.getDeploymentStatsOverview(
            accountIdentifier1, null, null, userId1, anyLong(), anyLong(), groupBy, sortBy);
    assertThat(actualFailureResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testActiveDeploymentOverview() throws Exception {
    Call<ResponseDTO<DeploymentStatsSummary>> requestDeploymentStatsSummary = Mockito.mock(Call.class);
    Call<ResponseDTO<ServicesDashboardInfo>> requestServicesDashboardInfo = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesExecutionDashboardInfo>> requestActiveDeploymentStats = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(anyString(), anyList()))
        .thenReturn(mapOfOrganizationIdentifierAndOrganizationName);
    doReturn(requestActiveDeploymentStats)
        .when(cdLandingDashboardResourceClient)
        .getActiveDeploymentStats(anyString(), any());
    when(cdLandingDashboardResourceClient.getDeploymentStatsSummary(
             anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestDeploymentStatsSummary);
    when(cdLandingDashboardResourceClient.get(anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestServicesDashboardInfo);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList3);

    ExecutionResponse<DeploymentsStatsOverview> actualSuccessResponse =
        overviewDashboardService.getActiveDeploymentsOverview(accountIdentifier1, null, null, userId1);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedDeploymentsStatsOverview3);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList2);
    ExecutionResponse<DeploymentsStatsOverview> actualFailureResponse =
        overviewDashboardService.getActiveDeploymentsOverview(accountIdentifier1, null, null, userId1);
    assertThat(actualFailureResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetDeploymentStatsOverviewLandingPageV2() throws Exception {
    GroupBy groupBy = GroupBy.MONTH;
    SortBy sortBy = SortBy.DEPLOYMENTS;
    when(featureFlagHelperService.isEnabled(accountIdentifier1, LANDING_OVERVIEW_PAGE_V2)).thenReturn(true);
    Call<ResponseDTO<DeploymentStatsSummary>> requestDeploymentStatsSummary = Mockito.mock(Call.class);
    Call<ResponseDTO<ServicesDashboardInfo>> requestServicesDashboardInfo = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesExecutionDashboardInfo>> requestActiveDeploymentStats = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(anyString(), anyList()))
        .thenReturn(mapOfOrganizationIdentifierAndOrganizationName);
    doReturn(requestActiveDeploymentStats)
        .when(cdLandingDashboardResourceClient)
        .getActiveDeploymentStats(anyString(), any());
    when(cdLandingDashboardResourceClient.getDeploymentStatsSummary(
             anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestDeploymentStatsSummary);
    when(cdLandingDashboardResourceClient.get(anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestServicesDashboardInfo);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList1);

    ExecutionResponse<DeploymentsStatsOverview> actualSuccessResponse =
        overviewDashboardService.getDeploymentStatsOverview(
            accountIdentifier1, null, null, userId1, anyLong(), anyLong(), groupBy, sortBy);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedDeploymentsStatsOverview2);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList2);
    ExecutionResponse<DeploymentsStatsOverview> actualFailureResponse =
        overviewDashboardService.getDeploymentStatsOverview(
            accountIdentifier1, null, null, userId1, anyLong(), anyLong(), groupBy, sortBy);
    assertThat(actualFailureResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetDeploymentStatsOverviewProjectScope() throws Exception {
    GroupBy groupBy = GroupBy.MONTH;
    SortBy sortBy = SortBy.DEPLOYMENTS;

    Call<ResponseDTO<DeploymentStatsSummary>> requestDeploymentStatsSummary = Mockito.mock(Call.class);
    Call<ResponseDTO<ServicesDashboardInfo>> requestServicesDashboardInfo = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesExecutionDashboardInfo>> requestActiveDeploymentStats = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(anyString(), anyList()))
        .thenReturn(mapOfOrganizationIdentifierAndOrganizationName);
    doReturn(requestActiveDeploymentStats)
        .when(cdLandingDashboardResourceClient)
        .getActiveDeploymentStats(anyString(), any());
    when(cdLandingDashboardResourceClient.getDeploymentStatsSummary(
             anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestDeploymentStatsSummary);
    when(cdLandingDashboardResourceClient.get(anyString(), eq(startTime1), eq(endTime1), any(), any()))
        .thenReturn(requestServicesDashboardInfo);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(deploymentStatsOverviewRestCallResponseList1);

    ExecutionResponse<DeploymentsStatsOverview> actualSuccessResponse =
        overviewDashboardService.getDeploymentStatsOverview(
            accountIdentifier1, orgIdentifier1, projectIdentifier1, userId1, anyLong(), anyLong(), groupBy, sortBy);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedDeploymentsStatsOverview1);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview2);
    assertThatThrownBy(()
                           -> overviewDashboardService.getDeploymentStatsOverview(accountIdentifier1, orgIdentifier1,
                               projectIdentifier2, userId1, anyLong(), anyLong(), groupBy, sortBy))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            format("Project with identifier %s in organization with identifier %s in not accessible to the user",
                projectIdentifier2, orgIdentifier1));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetCountOverview() throws Exception {
    Call<ResponseDTO<ServicesCount>> requestServicesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<EnvCount>> requestEnvCount = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesCount>> requestPipelinesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ActiveProjectsCountDTO>> requestProjectsCount = Mockito.mock(Call.class);
    Call<ResponseDTO<UsersCountDTO>> requestUsersCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ExecutionsCount>> requestExecutionsCount = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(cdLandingDashboardResourceClient.getServicesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestServicesCount);
    when(cdLandingDashboardResourceClient.getEnvCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestEnvCount);
    when(pmsLandingDashboardResourceClient.getPipelinesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestPipelinesCount);
    when(pmsLandingDashboardResourceClient.getExecutionsCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestExecutionsCount);
    when(userClient.getUsersCount(anyString(), eq(startTime1), eq(endTime1))).thenReturn(requestUsersCount);
    when(projectClient.getAccessibleProjectsCount(anyString(), eq(startTime1), eq(endTime1)))
        .thenReturn(requestProjectsCount);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview1);

    ExecutionResponse<CountOverview> actualSuccessResponse =
        overviewDashboardService.getCountOverview(accountIdentifier1, null, null, userId1, startTime1, endTime1);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedCountOverview1);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview2);
    ExecutionResponse<CountOverview> actualFailureResponse =
        overviewDashboardService.getCountOverview(accountIdentifier1, null, null, userId1, startTime1, endTime1);
    assertThat(actualFailureResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetCountOverviewProjectScope() throws Exception {
    Call<ResponseDTO<ServicesCount>> requestServicesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<EnvCount>> requestEnvCount = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesCount>> requestPipelinesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ExecutionsCount>> requestExecutionsCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ActiveProjectsCountDTO>> requestProjectsCount = Mockito.mock(Call.class);
    Call<ResponseDTO<UsersCountDTO>> requestUsersCount = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(cdLandingDashboardResourceClient.getServicesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestServicesCount);
    when(cdLandingDashboardResourceClient.getEnvCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestEnvCount);
    when(pmsLandingDashboardResourceClient.getPipelinesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestPipelinesCount);
    when(pmsLandingDashboardResourceClient.getExecutionsCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestExecutionsCount);
    when(userClient.getUsersCount(anyString(), eq(startTime1), eq(endTime1))).thenReturn(requestUsersCount);
    when(projectClient.getAccessibleProjectsCount(anyString(), eq(startTime1), eq(endTime1)))
        .thenReturn(requestProjectsCount);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview1);

    ExecutionResponse<CountOverview> actualSuccessResponse = overviewDashboardService.getCountOverview(
        accountIdentifier1, orgIdentifier1, projectIdentifier1, userId1, startTime1, endTime1);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedCountOverview3);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview2);
    assertThatThrownBy(()
                           -> overviewDashboardService.getCountOverview(
                               accountIdentifier1, orgIdentifier1, projectIdentifier2, userId1, startTime1, endTime1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            format("Project with identifier %s in organization with identifier %s in not accessible to the user",
                projectIdentifier2, orgIdentifier1));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetCountOverviewProjectScopeLandingPageV2() throws Exception {
    Call<ResponseDTO<ServicesCount>> requestServicesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<EnvCount>> requestEnvCount = Mockito.mock(Call.class);
    Call<ResponseDTO<PipelinesCount>> requestPipelinesCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ExecutionsCount>> requestExecutionsCount = Mockito.mock(Call.class);
    Call<ResponseDTO<ActiveProjectsCountDTO>> requestProjectsCount = Mockito.mock(Call.class);
    Call<ResponseDTO<UsersCountDTO>> requestUsersCount = Mockito.mock(Call.class);
    when(featureFlagHelperService.isEnabled(accountIdentifier1, LANDING_OVERVIEW_PAGE_V2)).thenReturn(true);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(cdLandingDashboardResourceClient.getServicesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestServicesCount);
    when(cdLandingDashboardResourceClient.getEnvCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestEnvCount);
    when(pmsLandingDashboardResourceClient.getPipelinesCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestPipelinesCount);
    when(pmsLandingDashboardResourceClient.getExecutionsCount(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestExecutionsCount);
    when(userClient.getUsersCount(anyString(), eq(startTime1), eq(endTime1))).thenReturn(requestUsersCount);
    when(projectClient.getAccessibleProjectsCount(anyString(), eq(startTime1), eq(endTime1)))
        .thenReturn(requestProjectsCount);
    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview1);

    ExecutionResponse<CountOverview> actualSuccessResponse = overviewDashboardService.getCountOverview(
        accountIdentifier1, orgIdentifier1, projectIdentifier1, userId1, startTime1, endTime1);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse()).isEqualTo(expectedCountOverview2);

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(restCallResponseListForCountOverview2);
    assertThatThrownBy(()
                           -> overviewDashboardService.getCountOverview(
                               accountIdentifier1, orgIdentifier1, projectIdentifier2, userId1, startTime1, endTime1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            format("Project with identifier %s in organization with identifier %s in not accessible to the user",
                projectIdentifier2, orgIdentifier1));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetTopProjectsPanel() throws Exception {
    Call<ResponseDTO<ProjectsDashboardInfo>> requestProjectsDashboardInfo = Mockito.mock(Call.class);

    when(dashboardRBACService.listAccessibleProject(anyString(), anyString())).thenReturn(listOfAccessibleProjects);
    when(dashboardRBACService.getMapOfOrganizationIdentifierAndOrganizationName(anyString(), anyList()))
        .thenReturn(mapOfOrganizationIdentifierAndOrganizationName);
    when(cdLandingDashboardResourceClient.getTopProjects(anyString(), eq(startTime1), eq(endTime1), any()))
        .thenReturn(requestProjectsDashboardInfo);

    when(parallelRestCallExecutor.executeRestCalls(anyList()))
        .thenReturn(getRestCallSuccessResponseListForGetTopProjectsPanel(
            accountIdentifier1, projectIdentifier1, orgIdentifier1, 10L, 0.24, 10L, 10L));
    ExecutionResponse<TopProjectsPanel> actualSuccessResponse =
        overviewDashboardService.getTopProjectsPanel(accountIdentifier1, userId1, startTime1, endTime1);
    assertThat(actualSuccessResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(actualSuccessResponse.getResponse().getCDTopProjectsInfo().getResponse())
        .isEqualTo(getExpectedResponseForTopProjectsPanel(
            accountIdentifier1, orgIdentifier1, orgName1, projectIdentifier1, projectName1, 10L, 0.24, 10L, 10L)
                       .getCDTopProjectsInfo()
                       .getResponse());

    when(parallelRestCallExecutor.executeRestCalls(anyList())).thenReturn(Collections.emptyList());
    ExecutionResponse<TopProjectsPanel> actualFailureResponse =
        overviewDashboardService.getTopProjectsPanel(accountIdentifier1, userId1, startTime1, endTime1);
    assertThat(actualFailureResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILURE);
  }

  private ServiceDashboardInfo getActiveService(String accountIdentifier, long failureDeploymentsCount,
      String serviceIdentifier, String serviceName, String orgIdentifier, String projectIdentifier,
      long successDeploymentsCount, double totalDeploymentsChangeRate, long totalDeploymentsCount) {
    return ServiceDashboardInfo.builder()
        .accountIdentifier(accountIdentifier)
        .failureDeploymentsCount(failureDeploymentsCount)
        .identifier(serviceIdentifier)
        .name(serviceName)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .successDeploymentsCount(successDeploymentsCount)
        .totalDeploymentsChangeRate(totalDeploymentsChangeRate)
        .totalDeploymentsCount(totalDeploymentsCount)
        .build();
  }

  private RestCallResponse<ServicesDashboardInfo> getMostActiveServices(
      List<ServiceDashboardInfo> serviceDashboardInfoList) {
    return RestCallResponse.<ServicesDashboardInfo>builder()
        .response(ServicesDashboardInfo.builder().serviceDashboardInfoList(serviceDashboardInfoList).build())
        .requestType(GET_MOST_ACTIVE_SERVICES)
        .build();
  }

  private TimeBasedStats getTimeBasedStats(long time, long successCount, long failureCount, long count) {
    return TimeBasedStats.builder()
        .time(time)
        .countWithSuccessFailureDetails(CountWithSuccessFailureDetails.builder()
                                            .successCount(successCount)
                                            .failureCount(failureCount)
                                            .count(count)
                                            .build())
        .build();
  }

  private DeploymentsStatsSummary getExpectedDeploymentsStatsSummary(double deploymentRate,
      double deploymentRateChangeRate, double failureRate, double failureRateChangeRate, long count,
      double countChangeRate, List<TimeBasedStats> timeBasedStatsList) {
    return DeploymentsStatsSummary.builder()
        .deploymentRateAndChangeRate(
            RateAndRateChangeInfo.builder().rateChangeRate(deploymentRateChangeRate).rate(deploymentRate).build())
        .failureRateAndChangeRate(
            RateAndRateChangeInfo.builder().rateChangeRate(failureRateChangeRate).rate(failureRate).build())
        .countAndChangeRate(
            CountChangeDetails.builder()
                .count(count)
                .countChangeAndCountChangeRateInfo(
                    CountChangeAndCountChangeRateInfo.builder().countChangeRate(countChangeRate).build())
                .build())
        .deploymentStats(timeBasedStatsList)
        .build();
  }

  private ActiveServiceInfo getActiveServiceInfo(String serviceName, String serviceIdentifier, String accountIdentifier,
      String orgIdentifier, String orgName, String projectIdentifier, String projectName, long count, long successCount,
      long failureCount, double countChangeRate) {
    return ActiveServiceInfo.builder()
        .serviceInfo(ServiceInfo.builder().serviceName(serviceName).serviceIdentifier(serviceIdentifier).build())
        .accountInfo(AccountInfo.builder().accountIdentifier(accountIdentifier).build())
        .orgInfo(OrgInfo.builder().orgIdentifier(orgIdentifier).orgName(orgName).build())
        .projectInfo(ProjectInfo.builder().projectIdentifier(projectIdentifier).projectName(projectName).build())
        .countWithSuccessFailureDetails(
            CountWithSuccessFailureDetails.builder()
                .count(count)
                .successCount(successCount)
                .failureCount(failureCount)
                .countChangeAndCountChangeRateInfo(
                    CountChangeAndCountChangeRateInfo.builder().countChangeRate(countChangeRate).build())
                .build())
        .build();
  }

  private MostActiveServicesList getMostActiveServiceList(List<ActiveServiceInfo> activeServices) {
    return MostActiveServicesList.builder().activeServices(activeServices).build();
  }

  private PipelineExecutionInfo getExpectedExecutions(String accountIdentifier, String pipelineName,
      String pipelineIdentifier, String orgIdentifier, String orgName, String projectIdentifier, String projectName,
      String planExecutionId, long startTime) {
    return PipelineExecutionInfo.builder()
        .accountInfo(AccountInfo.builder().accountIdentifier(accountIdentifier).build())
        .pipelineInfo(PipelineInfo.builder().pipelineName(pipelineName).pipelineIdentifier(pipelineIdentifier).build())
        .orgInfo(OrgInfo.builder().orgIdentifier(orgIdentifier).orgName(orgName).build())
        .projectInfo(ProjectInfo.builder().projectName(projectName).projectIdentifier(projectIdentifier).build())
        .planExecutionId(planExecutionId)
        .startTs(startTime)
        .build();
  }

  private DeploymentsOverview getExpectedDeploymentsOverview(List<PipelineExecutionInfo> expectedFailed24HrsExecutions,
      List<PipelineExecutionInfo> expectedPendingApprovalExecutions,
      List<PipelineExecutionInfo> expectedPendingManualInterventionExecutions,
      List<PipelineExecutionInfo> expectedRunningExecutions) {
    return DeploymentsOverview.builder()
        .failed24HrsExecutions(expectedFailed24HrsExecutions)
        .pendingApprovalExecutions(expectedPendingApprovalExecutions)
        .pendingManualInterventionExecutions(expectedPendingManualInterventionExecutions)
        .runningExecutions(expectedRunningExecutions)
        .build();
  }

  private DeploymentsStatsOverview getExpectedResponseForDeploymentStatsOverview(
      DeploymentsStatsSummary deploymentsStatsSummary, MostActiveServicesList mostActiveServicesList,
      DeploymentsOverview deploymentsOverview) {
    return DeploymentsStatsOverview.builder()
        .deploymentsStatsSummary(deploymentsStatsSummary)
        .mostActiveServicesList(mostActiveServicesList)
        .deploymentsOverview(deploymentsOverview)
        .build();
  }

  private RestCallResponse<DeploymentStatsSummary> getDeploymentStatsSummary(long totalCount,
      double deploymentRateChangeRate, double deploymentRate, double totalCountChangeRate, double failureRate,
      double failureRateChangeRate, List<TimeBasedDeploymentInfo> timeBasedDeploymentInfo) {
    return RestCallResponse.<DeploymentStatsSummary>builder()
        .response(DeploymentStatsSummary.builder()
                      .totalCount(totalCount)
                      .deploymentRateChangeRate(deploymentRateChangeRate)
                      .deploymentRate(deploymentRate)
                      .totalCountChangeRate(totalCountChangeRate)
                      .failureRate(failureRate)
                      .failureRateChangeRate(failureRateChangeRate)
                      .timeBasedDeploymentInfoList(timeBasedDeploymentInfo)
                      .build())
        .requestType(GET_DEPLOYMENT_STATS_SUMMARY)
        .build();
  }

  private TimeBasedDeploymentInfo getTimeBasedDeploymentInfo(
      long totalCount, long failedCount, long successCount, long epochTime) {
    return TimeBasedDeploymentInfo.builder()
        .totalCount(totalCount)
        .failedCount(failedCount)
        .successCount(successCount)
        .epochTime(epochTime)
        .build();
  }

  private PipelineExecutionDashboardInfo getExecutions(String accountIdentifier, String pipelineIdentifier,
      String orgIdentifier, String projectIdentifier, String pipelineName, String planExecutionId, long startTime) {
    return PipelineExecutionDashboardInfo.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(pipelineIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .name(pipelineName)
        .planExecutionId(planExecutionId)
        .startTs(startTime)
        .build();
  }

  private RestCallResponse<PipelinesExecutionDashboardInfo> getActiveDeploymentsInfo(
      List<PipelineExecutionDashboardInfo> failed24HrsExecutions,
      List<PipelineExecutionDashboardInfo> pendingApprovalExecutions,
      List<PipelineExecutionDashboardInfo> pendingManualInterventionExecutions,
      List<PipelineExecutionDashboardInfo> runningExecutions) {
    return RestCallResponse.<PipelinesExecutionDashboardInfo>builder()
        .response(PipelinesExecutionDashboardInfo.builder()
                      .failed24HrsExecutions(failed24HrsExecutions)
                      .pendingApprovalExecutions(pendingApprovalExecutions)
                      .pendingManualInterventionExecutions(pendingManualInterventionExecutions)
                      .runningExecutions(runningExecutions)
                      .build())
        .requestType(GET_ACTIVE_DEPLOYMENTS_INFO)
        .build();
  }

  private RestCallResponse<ServicesCount> getServicesCount(long count, long newCount) {
    return RestCallResponse.<ServicesCount>builder()
        .response(ServicesCount.builder().newCount(newCount).totalCount(count).build())
        .requestType(GET_SERVICES_COUNT)
        .build();
  }

  private RestCallResponse<EnvCount> getEnvCount(long count, long newCount) {
    return RestCallResponse.<EnvCount>builder()
        .response(EnvCount.builder().newCount(newCount).totalCount(count).build())
        .requestType(GET_ENV_COUNT)
        .build();
  }

  private RestCallResponse<PipelinesCount> getPipelinesCount(long count, long newCount) {
    return RestCallResponse.<PipelinesCount>builder()
        .response(PipelinesCount.builder().newCount(newCount).totalCount(count).build())
        .requestType(GET_PIPELINES_COUNT)
        .build();
  }

  private RestCallResponse<ExecutionsCount> getExecutionsCount(long count, long newCount) {
    return RestCallResponse.<ExecutionsCount>builder()
        .response(ExecutionsCount.builder().newCount(newCount).totalCount(count).build())
        .requestType(GET_EXECUTIONS_COUNT)
        .build();
  }

  private RestCallResponse<UsersCountDTO> getUsersCount(long count, long newCount) {
    return RestCallResponse.<UsersCountDTO>builder()
        .response(UsersCountDTO.builder().totalCount(count).newCount(newCount).build())
        .requestType(GET_USER_COUNT)
        .build();
  }

  private RestCallResponse<ActiveProjectsCountDTO> getProjectsCount(Integer count) {
    return RestCallResponse.<ActiveProjectsCountDTO>builder()
        .response(ActiveProjectsCountDTO.builder().count(count).build())
        .requestType(GET_PROJECTS_COUNT)
        .build();
  }

  private CountChangeDetails getExpectedCountDetail(long count, long newCount) {
    return CountChangeDetails.builder()
        .countChangeAndCountChangeRateInfo(CountChangeAndCountChangeRateInfo.builder().countChange(newCount).build())
        .count(count)
        .build();
  }

  private CountOverview getExpectedResponseForGetCountOverview(CountChangeDetails servicesCountDetail,
      CountChangeDetails envCountDetail, CountChangeDetails pipelinesCountDetail,
      CountChangeDetails executionsCountDetail, CountChangeDetails projectsCountDetail,
      CountChangeDetails userCountDetail) {
    return CountOverview.builder()
        .servicesCountDetail(servicesCountDetail)
        .envCountDetail(envCountDetail)
        .pipelinesCountDetail(pipelinesCountDetail)
        .executionsCountDetail(executionsCountDetail)
        .projectsCountDetail(projectsCountDetail)
        .usersCountDetail(userCountDetail)
        .build();
  }

  private List<RestCallResponse> getRestCallSuccessResponseListForGetTopProjectsPanel(String accountIdentifier,
      String projectIdentifier, String orgIdentifier, long deploymentsCount, double deploymentsCountChangeRate,
      long failedDeploymentsCount, long successDeploymentsCount) {
    List<RestCallResponse> restCallResponseList = new ArrayList<>();
    restCallResponseList.add(RestCallResponse.<ProjectsDashboardInfo>builder()
                                 .response(ProjectsDashboardInfo.builder()
                                               .projectDashBoardInfoList(Collections.singletonList(
                                                   ProjectDashBoardInfo.builder()
                                                       .accountId(accountIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .deploymentsCount(deploymentsCount)
                                                       .deploymentsCountChangeRate(deploymentsCountChangeRate)
                                                       .failedDeploymentsCount(failedDeploymentsCount)
                                                       .successDeploymentsCount(successDeploymentsCount)
                                                       .build()))
                                               .build())
                                 .requestType(GET_CD_TOP_PROJECT_LIST)
                                 .build());
    return restCallResponseList;
  }

  private TopProjectsPanel getExpectedResponseForTopProjectsPanel(String accountIdentifier, String orgIdentifier,
      String orgName, String projectIdentifier, String projectName, long count, double countChangeRate,
      long successCount, long failureCount) {
    return TopProjectsPanel.builder()
        .CDTopProjectsInfo(
            ExecutionResponse.<List<TopProjectsDashboardInfo<CountWithSuccessFailureDetails>>>builder()
                .response(Collections.singletonList(
                    TopProjectsDashboardInfo.<CountWithSuccessFailureDetails>builder()
                        .accountInfo(AccountInfo.builder().accountIdentifier(accountIdentifier).build())
                        .orgInfo(OrgInfo.builder().orgIdentifier(orgIdentifier).orgName(orgName).build())
                        .projectInfo(
                            ProjectInfo.builder().projectIdentifier(projectIdentifier).projectName(projectName).build())
                        .countDetails(CountWithSuccessFailureDetails.builder()
                                          .count(count)
                                          .successCount(successCount)
                                          .failureCount(failureCount)
                                          .countChangeAndCountChangeRateInfo(CountChangeAndCountChangeRateInfo.builder()
                                                                                 .countChangeRate(countChangeRate)
                                                                                 .build())
                                          .build())
                        .build()))
                .build())
        .build();
  }
}
