/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aggregates.AggregateProjectInfo;
import io.harness.aggregates.AggregateServiceInfo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.TimeScaleDAL;
import io.harness.dashboards.DashboardHelper;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.PipelineExecutionDashboardInfo;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectDashBoardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServiceDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.rule.Owner;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class CDLandingDashboardServiceImplTest extends CategoryTest {
  public static final String PROJ_ID = "projId";
  public static final String ORG_ID = "orgId";
  public static final String ACC_ID = "accId";
  public static final String SERVICE_ID1 = "serviceId1";
  public static final String SERVICE_ID2 = "serviceId2";
  public static final long START_TS = 0;
  public static final long END_TS = 86400000;

  @Mock TimeScaleDAL timeScaleDAL;
  @InjectMocks @Spy private CDLandingDashboardServiceImpl cdLandingDashboardService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetEnvCount() {
    EnvCount envCount = cdLandingDashboardService.getEnvCount(ACC_ID, Collections.emptyList(), 0, 0);
    assertThat(envCount.getNewCount()).isEqualTo(0);

    OrgProjectIdentifier orgProjectIdentifier =
        OrgProjectIdentifier.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).build();
    List<OrgProjectIdentifier> orgProjectIdentifiers = Collections.singletonList(orgProjectIdentifier);

    doReturn(5).when(timeScaleDAL).getTotalEnvCount(any(), any());
    doReturn(4).when(timeScaleDAL).getNewEnvCount(any(), any(), any(), any());
    doReturn(2).when(timeScaleDAL).getDeletedEnvCount(any(), any(), any(), any());

    envCount = cdLandingDashboardService.getEnvCount(ACC_ID, orgProjectIdentifiers, START_TS, END_TS);
    assertThat(envCount.getTotalCount()).isEqualTo(5);
    assertThat(envCount.getNewCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetServicesCount() {
    ServicesCount servicesCount = cdLandingDashboardService.getServicesCount(ACC_ID, Collections.emptyList(), 0, 0);
    assertThat(servicesCount.getNewCount()).isEqualTo(0);

    OrgProjectIdentifier orgProjectIdentifier =
        OrgProjectIdentifier.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).build();
    List<OrgProjectIdentifier> orgProjectIdentifiers = Collections.singletonList(orgProjectIdentifier);

    doReturn(5).when(timeScaleDAL).getTotalServicesCount(any(), any());
    doReturn(4).when(timeScaleDAL).getNewServicesCount(any(), any(), any(), any());
    doReturn(2).when(timeScaleDAL).getDeletedServiceCount(any(), any(), any(), any());

    servicesCount = cdLandingDashboardService.getServicesCount(ACC_ID, orgProjectIdentifiers, START_TS, END_TS);
    assertThat(servicesCount.getTotalCount()).isEqualTo(5);
    assertThat(servicesCount.getNewCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetLast24HrsFailedExecutions() {
    doReturn(Collections.emptyList())
        .when(timeScaleDAL)
        .getFailedExecutionsForGivenTimeRange(any(), any(), any(), any());
    List<PipelineExecutionDashboardInfo> last24HrsFailedExecutions =
        cdLandingDashboardService.getLast24HrsFailedExecutions(ACC_ID, getOrgProjectTable());
    assertThat(last24HrsFailedExecutions).isEmpty();

    PipelineExecutionSummaryCd pipelineExecutionSummaryCd = mock(PipelineExecutionSummaryCd.class);
    doReturn(Collections.singletonList(pipelineExecutionSummaryCd))
        .when(timeScaleDAL)
        .getFailedExecutionsForGivenTimeRange(any(), any(), any(), any());
    last24HrsFailedExecutions = cdLandingDashboardService.getLast24HrsFailedExecutions(ACC_ID, getOrgProjectTable());
    assertThat(last24HrsFailedExecutions).hasSize(1);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testFilterByStatuses() {
    assertThat(cdLandingDashboardService.filterByStatuses(ACC_ID, null)).isNotNull();
    assertThat(cdLandingDashboardService.filterByStatuses(ACC_ID, Collections.emptyList())).isNotNull();

    List<PipelineExecutionSummaryCd> executionSummaryCdList = getExecutionSummaryList();
    PipelinesExecutionDashboardInfo pipelinesExecutionDashboardInfo =
        cdLandingDashboardService.filterByStatuses(ACC_ID, executionSummaryCdList);
    assertThat(pipelinesExecutionDashboardInfo).isNotNull();
    assertThat(pipelinesExecutionDashboardInfo.getRunningExecutions()).hasSize(1);
    assertThat(pipelinesExecutionDashboardInfo.getPendingApprovalExecutions()).hasSize(1);
    assertThat(pipelinesExecutionDashboardInfo.getPendingManualInterventionExecutions()).hasSize(1);
  }

  @NotNull
  private List<PipelineExecutionSummaryCd> getExecutionSummaryList() {
    PipelineExecutionSummaryCd pipelineExecutionSummaryCd1 = mock(PipelineExecutionSummaryCd.class);
    doReturn(ExecutionStatus.APPROVAL_WAITING.name()).when(pipelineExecutionSummaryCd1).getStatus();
    PipelineExecutionSummaryCd pipelineExecutionSummaryCd2 = mock(PipelineExecutionSummaryCd.class);
    doReturn(ExecutionStatus.RUNNING.name()).when(pipelineExecutionSummaryCd2).getStatus();
    PipelineExecutionSummaryCd pipelineExecutionSummaryCd3 = mock(PipelineExecutionSummaryCd.class);
    doReturn(ExecutionStatus.INTERVENTION_WAITING.name()).when(pipelineExecutionSummaryCd3).getStatus();
    return Arrays.asList(pipelineExecutionSummaryCd1, pipelineExecutionSummaryCd2, pipelineExecutionSummaryCd3);
  }

  private Table<Record2<String, String>> getOrgProjectTable() {
    Row2<String, String>[] orgProjectRows = new Row2[1];
    orgProjectRows[0] = row(ORG_ID, PROJ_ID);
    return DSL.values(orgProjectRows);
  }

  private List<OrgProjectIdentifier> getOrgProjectList() {
    return Arrays.asList(OrgProjectIdentifier.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).build());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetActiveDeploymentStats() {
    assertThat(cdLandingDashboardService.getActiveDeploymentStats(ACC_ID, null)).isNotNull();
    List<PipelineExecutionSummaryCd> executionSummaryList = getExecutionSummaryList();

    doReturn(executionSummaryList).when(timeScaleDAL).getPipelineExecutionsForGivenExecutionStatus(any(), any(), any());
    PipelinesExecutionDashboardInfo pipelinesExecutionDashboardInfo = PipelinesExecutionDashboardInfo.builder().build();
    doReturn(pipelinesExecutionDashboardInfo)
        .when(cdLandingDashboardService)
        .filterByStatuses(ACC_ID, executionSummaryList);
    List<PipelineExecutionDashboardInfo> failedExecutionList = new ArrayList<>();
    doReturn(failedExecutionList).when(cdLandingDashboardService).getLast24HrsFailedExecutions(any(), any());

    PipelinesExecutionDashboardInfo executionDashboardInfo =
        cdLandingDashboardService.getActiveDeploymentStats(ACC_ID, getOrgProjectList());
    assertThat(executionDashboardInfo).isEqualTo(pipelinesExecutionDashboardInfo);
    assertThat(executionDashboardInfo.getFailed24HrsExecutions()).isEqualTo(failedExecutionList);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAddServiceNames() {
    cdLandingDashboardService.addServiceNames(null, null, null);

    Map<String, ServiceDashboardInfo> combinedIdToRecordMap = new HashMap<>();
    ServiceDashboardInfo serviceDashboardInfo1 = ServiceDashboardInfo.builder().build();
    ServiceDashboardInfo serviceDashboardInfo2 = ServiceDashboardInfo.builder().build();
    combinedIdToRecordMap.put("org-proj-serviceId1", serviceDashboardInfo1);
    combinedIdToRecordMap.put("org-proj-serviceId2", serviceDashboardInfo2);

    Services service1 = getService("org", "proj", "serviceId1", "service1");
    Services service2 = getService("org", "proj", "serviceId2", "service2");
    doReturn(Arrays.asList(service1, service2)).when(timeScaleDAL).getNamesForServiceIds(ACC_ID, null);

    cdLandingDashboardService.addServiceNames(combinedIdToRecordMap, ACC_ID, null);
    assertThat(serviceDashboardInfo1.getName()).isEqualTo("service1");
    assertThat(serviceDashboardInfo2.getName()).isEqualTo("service2");
  }

  private Services getService(String org, String proj, String serviceId, String serviceName) {
    Services services = new Services();
    services.setOrgIdentifier(org);
    services.setProjectIdentifier(proj);
    services.setIdentifier(serviceId);
    services.setName(serviceName);
    return services;
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareStatusWiseCount() {
    cdLandingDashboardService.prepareStatusWiseCount(null, null, 0, 0, null);

    AggregateServiceInfo aggregateServiceInfo1 =
        new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, ExecutionStatus.SUCCESS.name(), 1);
    AggregateServiceInfo aggregateServiceInfo2 =
        new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, ExecutionStatus.FAILED.name(), 1);
    doReturn(Arrays.asList(aggregateServiceInfo1, aggregateServiceInfo2))
        .when(timeScaleDAL)
        .getStatusWiseDeploymentCountForGivenServices(
            null, ACC_ID, START_TS, END_TS, CDDashboardServiceHelper.getSuccessFailedStatusList());

    ServiceDashboardInfo serviceDashboardInfo =
        ServiceDashboardInfo.builder().orgIdentifier(ORG_ID).projectIdentifier(PROJ_ID).identifier(SERVICE_ID1).build();
    cdLandingDashboardService.prepareStatusWiseCount(
        null, ACC_ID, START_TS, END_TS, Arrays.asList(serviceDashboardInfo));
    assertThat(serviceDashboardInfo.getSuccessDeploymentsCount()).isEqualTo(1);
    assertThat(serviceDashboardInfo.getFailureDeploymentsCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareChangeRateForService() {
    cdLandingDashboardService.prepareProjectsChangeRate(null, null, 0, 0, null);

    AggregateServiceInfo aggregateServiceInfo = new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, 100);
    long duration = END_TS - START_TS;
    doReturn(Arrays.asList(aggregateServiceInfo))
        .when(timeScaleDAL)
        .getDeploymentCountForGivenServices(null, ACC_ID, START_TS - duration, END_TS - duration,
            CDDashboardServiceHelper.getSuccessFailedStatusList());

    ServiceDashboardInfo serviceDashboardInfo1 = ServiceDashboardInfo.builder()
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJ_ID)
                                                     .identifier(SERVICE_ID1)
                                                     .totalDeploymentsCount(150)
                                                     .build();
    ServiceDashboardInfo serviceDashboardInfo2 = ServiceDashboardInfo.builder()
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJ_ID)
                                                     .identifier(SERVICE_ID2)
                                                     .totalDeploymentsCount(150)
                                                     .build();
    cdLandingDashboardService.prepareServicesChangeRate(
        null, ACC_ID, START_TS, END_TS, Arrays.asList(serviceDashboardInfo1, serviceDashboardInfo2));
    assertThat(serviceDashboardInfo1.getTotalDeploymentsChangeRate()).isEqualTo(50);
    assertThat(serviceDashboardInfo2.getTotalDeploymentsChangeRate()).isEqualTo(DashboardHelper.MAX_VALUE);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareServiceInstancesChangeRate() {
    cdLandingDashboardService.prepareServiceInstancesChangeRate(null, null, 0, 0, null);

    AggregateServiceInfo aggregateServiceInfo = new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, 100);
    long duration = END_TS - START_TS;
    doReturn(Arrays.asList(aggregateServiceInfo))
        .when(timeScaleDAL)
        .getInstanceCountForGivenServices(null, ACC_ID, START_TS - duration, END_TS - duration);

    ServiceDashboardInfo serviceDashboardInfo1 = ServiceDashboardInfo.builder()
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJ_ID)
                                                     .identifier(SERVICE_ID1)
                                                     .instancesCount(150)
                                                     .build();
    ServiceDashboardInfo serviceDashboardInfo2 = ServiceDashboardInfo.builder()
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJ_ID)
                                                     .identifier(SERVICE_ID2)
                                                     .instancesCount(150)
                                                     .build();
    Map<String, ServiceDashboardInfo> idServiceDashboardInfoMap = new HashMap<>();
    idServiceDashboardInfoMap.put(ORG_ID + "-" + PROJ_ID + "-" + SERVICE_ID1, serviceDashboardInfo1);
    idServiceDashboardInfoMap.put(ORG_ID + "-" + PROJ_ID + "-" + SERVICE_ID2, serviceDashboardInfo2);
    cdLandingDashboardService.prepareServiceInstancesChangeRate(
        null, ACC_ID, START_TS, END_TS, idServiceDashboardInfoMap);
    assertThat(serviceDashboardInfo1.getInstancesCountChangeRate()).isEqualTo(50);
    assertThat(serviceDashboardInfo2.getInstancesCountChangeRate()).isEqualTo(DashboardHelper.MAX_VALUE);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetActiveServicesByInstances() {
    assertThat(cdLandingDashboardService.getActiveServicesByInstances(null, null, 0, 0)).isNotNull();

    doReturn(null).when(timeScaleDAL).getTopServicesByInstanceCount(ACC_ID, START_TS, END_TS, getOrgProjectTable());
    List<OrgProjectIdentifier> orgProjectList = getOrgProjectList();
    ServicesDashboardInfo activeServicesByInstances =
        cdLandingDashboardService.getActiveServicesByInstances(ACC_ID, orgProjectList, START_TS, END_TS);
    assertThat(activeServicesByInstances.getServiceDashboardInfoList()).isEmpty();

    AggregateServiceInfo serviceDashboardInfo = new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, 1);
    doNothing()
        .when(cdLandingDashboardService)
        .prepareServiceInstancesChangeRate(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    doNothing().when(cdLandingDashboardService).addServiceNames(any(), eq(ACC_ID), any());
    doReturn(Arrays.asList(serviceDashboardInfo))
        .when(timeScaleDAL)
        .getTopServicesByInstanceCount(eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    activeServicesByInstances =
        cdLandingDashboardService.getActiveServicesByInstances(ACC_ID, orgProjectList, START_TS, END_TS);
    assertThat(activeServicesByInstances.getServiceDashboardInfoList()).hasSize(1);
    verify(cdLandingDashboardService, times(1))
        .prepareServiceInstancesChangeRate(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    verify(cdLandingDashboardService, times(1)).addServiceNames(any(), eq(ACC_ID), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetActiveServicesByDeployments() {
    doReturn(null)
        .when(timeScaleDAL)
        .getTopServicesByDeploymentCount(
            eq(ACC_ID), eq(START_TS), eq(END_TS), any(), eq(CDDashboardServiceHelper.getSuccessFailedStatusList()));
    ServicesDashboardInfo activeServices =
        cdLandingDashboardService.getActiveServicesByDeployments(ACC_ID, getOrgProjectList(), START_TS, END_TS);
    assertThat(activeServices.getServiceDashboardInfoList()).isEmpty();

    AggregateServiceInfo serviceDashboardInfo = new AggregateServiceInfo(ORG_ID, PROJ_ID, SERVICE_ID1, 1);
    doNothing()
        .when(cdLandingDashboardService)
        .prepareServicesChangeRate(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    doNothing()
        .when(cdLandingDashboardService)
        .prepareStatusWiseCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    doNothing().when(cdLandingDashboardService).addServiceNames(any(), eq(ACC_ID), any());
    doReturn(Arrays.asList(serviceDashboardInfo))
        .when(timeScaleDAL)
        .getTopServicesByDeploymentCount(eq(ACC_ID), eq(START_TS), eq(END_TS), any(), any());
    activeServices =
        cdLandingDashboardService.getActiveServicesByDeployments(ACC_ID, getOrgProjectList(), START_TS, END_TS);
    assertThat(activeServices.getServiceDashboardInfoList()).hasSize(1);
    verify(cdLandingDashboardService, times(1))
        .prepareServicesChangeRate(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    verify(cdLandingDashboardService, times(1))
        .prepareStatusWiseCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    verify(cdLandingDashboardService, times(1)).addServiceNames(any(), eq(ACC_ID), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetActiveServices() {
    ServicesDashboardInfo servicesDashboardInfo = ServicesDashboardInfo.builder().build();
    doReturn(servicesDashboardInfo)
        .when(cdLandingDashboardService)
        .getActiveServicesByDeployments(any(), any(), eq(START_TS), eq(END_TS));
    ServicesDashboardInfo actualServicesDashboardInfo =
        cdLandingDashboardService.getActiveServices(ACC_ID, getOrgProjectList(), START_TS, END_TS, SortBy.DEPLOYMENTS);
    assertThat(actualServicesDashboardInfo).isEqualTo(servicesDashboardInfo);

    ServicesDashboardInfo servicesDashboardInfoInstances = ServicesDashboardInfo.builder().build();
    doReturn(servicesDashboardInfoInstances)
        .when(cdLandingDashboardService)
        .getActiveServicesByInstances(any(), any(), eq(START_TS), eq(END_TS));
    ServicesDashboardInfo actualServicesDashboardInfoInstances =
        cdLandingDashboardService.getActiveServices(ACC_ID, getOrgProjectList(), START_TS, END_TS, SortBy.INSTANCES);
    assertThat(actualServicesDashboardInfoInstances).isEqualTo(servicesDashboardInfoInstances);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareProjectsChangeRate() {
    doReturn(null).when(timeScaleDAL).getProjectWiseDeploymentCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = new HashMap<>();
    ProjectDashBoardInfo projectDashBoardInfo = ProjectDashBoardInfo.builder().deploymentsCount(150).build();
    combinedIdToRecordMap.put(ORG_ID + "-" + PROJ_ID, projectDashBoardInfo);
    cdLandingDashboardService.prepareProjectsChangeRate(
        getOrgProjectTable(), ACC_ID, START_TS, END_TS, combinedIdToRecordMap);

    AggregateProjectInfo aggregateProjectInfo = new AggregateProjectInfo(ORG_ID, PROJ_ID, 100);
    long duration = END_TS - START_TS;
    doReturn(Arrays.asList(aggregateProjectInfo))
        .when(timeScaleDAL)
        .getProjectWiseDeploymentCount(any(), eq(ACC_ID), eq(START_TS - duration), eq(END_TS - duration), any());
    cdLandingDashboardService.prepareProjectsChangeRate(
        getOrgProjectTable(), ACC_ID, START_TS, END_TS, combinedIdToRecordMap);
    assertThat(projectDashBoardInfo.getDeploymentsCountChangeRate()).isEqualTo(50);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPrepareProjectsStatusWiseCount() {
    doReturn(null)
        .when(timeScaleDAL)
        .getProjectWiseStatusWiseDeploymentCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    Map<String, ProjectDashBoardInfo> combinedIdToRecordMap = new HashMap<>();
    ProjectDashBoardInfo projectDashBoardInfo = ProjectDashBoardInfo.builder().deploymentsCount(150).build();
    combinedIdToRecordMap.put(ORG_ID + "-" + PROJ_ID, projectDashBoardInfo);
    cdLandingDashboardService.prepareProjectsStatusWiseCount(
        getOrgProjectTable(), ACC_ID, START_TS, END_TS, combinedIdToRecordMap);

    AggregateProjectInfo aggregateProjectInfo =
        new AggregateProjectInfo(ORG_ID, PROJ_ID, ExecutionStatus.SUCCESS.name(), 100);
    AggregateProjectInfo aggregateProjectInfo1 =
        new AggregateProjectInfo(ORG_ID, PROJ_ID, ExecutionStatus.FAILED.name(), 50);
    doReturn(Arrays.asList(aggregateProjectInfo, aggregateProjectInfo1))
        .when(timeScaleDAL)
        .getProjectWiseStatusWiseDeploymentCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    cdLandingDashboardService.prepareProjectsStatusWiseCount(
        getOrgProjectTable(), ACC_ID, START_TS, END_TS, combinedIdToRecordMap);
    assertThat(projectDashBoardInfo.getSuccessDeploymentsCount()).isEqualTo(100);
    assertThat(projectDashBoardInfo.getFailedDeploymentsCount()).isEqualTo(50);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetTopProjects() {
    assertThat(cdLandingDashboardService.getTopProjects(ACC_ID, null, START_TS, END_TS).getProjectDashBoardInfoList())
        .isEmpty();

    doReturn(null)
        .when(timeScaleDAL)
        .getTopProjectsByDeploymentCount(eq(ACC_ID), eq(START_TS), eq(END_TS), any(), any());
    assertThat(cdLandingDashboardService.getTopProjects(ACC_ID, getOrgProjectList(), START_TS, END_TS)
                   .getProjectDashBoardInfoList())
        .isEmpty();

    AggregateProjectInfo aggregateProjectInfo = new AggregateProjectInfo(ORG_ID, PROJ_ID, 10);
    doReturn(Arrays.asList(aggregateProjectInfo))
        .when(timeScaleDAL)
        .getTopProjectsByDeploymentCount(eq(ACC_ID), eq(START_TS), eq(END_TS), any(), any());
    ProjectsDashboardInfo projectsDashboardInfo =
        cdLandingDashboardService.getTopProjects(ACC_ID, getOrgProjectList(), START_TS, END_TS);
    assertThat(projectsDashboardInfo.getProjectDashBoardInfoList()).hasSize(1);
    verify(cdLandingDashboardService, times(1))
        .prepareProjectsStatusWiseCount(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
    verify(cdLandingDashboardService, times(1))
        .prepareProjectsChangeRate(any(), eq(ACC_ID), eq(START_TS), eq(END_TS), any());
  }
}
