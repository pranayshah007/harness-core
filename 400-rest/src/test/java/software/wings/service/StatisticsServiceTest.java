/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_2_ID;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ServiceElement;
import software.wings.beans.Account;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats.DayStat;
import software.wings.beans.stats.ServiceInstanceStatistics;
import software.wings.beans.stats.TopConsumer;
import software.wings.dl.WingsPersistence;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.StatisticsService;

import com.google.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class StatisticsServiceTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private HPersistence persistence;

  @Mock private WorkflowExecutionServiceImpl workflowExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Inject @InjectMocks private StatisticsService statisticsService;

  @Mock private HIterator<WorkflowExecution> executionIterator;
  @Inject WingsPersistence wingsPersistence;

  private User user;

  @Before
  public void setUp() throws Exception {
    user = User.Builder.anUser()
               .name(USER_NAME)
               .uuid(USER_ID)
               .accounts(asList(Account.Builder.anAccount().withUuid(ACCOUNT_ID).build(),
                   Account.Builder.anAccount().withUuid(ACCOUNT_2_ID).build()))
               .build();
    Map<String, AppPermissionSummary> appPermissionsMap = new HashMap<>();

    Map<Action, Set<String>> svcPermissions = new HashMap<>();
    svcPermissions.put(Action.READ, new HashSet<>(List.of(SERVICE_ID)));
    svcPermissions.put(Action.CREATE, new HashSet<>(List.of(SERVICE_2_ID)));
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().servicePermissions(svcPermissions).build();
    appPermissionsMap.put(APP_ID, appPermissionSummary);
    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().accountId(ACCOUNT_ID).appPermissionMapInternal(appPermissionsMap).build();
    user.setUserRequestContext(UserRequestContext.builder()
                                   .accountId(ACCOUNT_ID)
                                   .userPermissionInfo(userPermissionInfo)
                                   .appIdFilterRequired(true)
                                   .appIds(newHashSet(APP_ID))
                                   .build());

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID));
    when(appService.getAppsByAccountId(ACCOUNT_ID)).thenReturn(asList(anApplication().uuid(APP_ID).build()));
    when(workflowExecutionService.obtainWorkflowExecutionIterator(anyList(), anyLong(), any()))
        .thenReturn(executionIterator);
    when(workflowExecutionService.getInstancesDeployedFromExecution(any(WorkflowExecution.class))).thenCallRealMethod();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetServiceInstanceStatistics() {
    when(appService.list(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList(anApplication().uuid(APP_ID).name(APP_NAME).build())).build());

    List<ElementExecutionSummary> serviceExecutionSummaries =
        asList(anElementExecutionSummary()
                   .withStatus(SUCCESS)
                   .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                   .build());
    List<ElementExecutionSummary> serviceFailureExecutionSummaries =
        asList(anElementExecutionSummary()
                   .withStatus(FAILED)
                   .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                   .build());

    List<WorkflowExecution> executions =
        constructWorkflowExecutions(serviceExecutionSummaries, serviceFailureExecutionSummaries);

    when(workflowExecutionService.obtainWorkflowExecutions(anyString(), anyLong(), any())).thenReturn(executions);

    ServiceInstanceStatistics statistics = statisticsService.getServiceInstanceStatistics(ACCOUNT_ID, null, 30);
    assertThat(statistics.getStatsMap()).isNotEmpty();
    assertThat(statistics.getStatsMap().get(PROD))
        .hasSize(1)
        .containsExactlyInAnyOrder(TopConsumer.builder()
                                       .appId(APP_ID)
                                       .appName(APP_NAME)
                                       .serviceId(SERVICE_ID)
                                       .serviceName(SERVICE_NAME)
                                       .successfulActivityCount(2)
                                       .failedActivityCount(0)
                                       .totalCount(2)
                                       .build());

    assertThat(statistics.getStatsMap().get(NON_PROD)).hasSize(1);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldGetServiceInstanceStatisticsNew() {
    when(appService.list(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList(anApplication().uuid(APP_ID).name(APP_NAME).build())).build());

    List<ElementExecutionSummary> serviceExecutionSummaries =
        asList(anElementExecutionSummary()
                   .withStatus(SUCCESS)
                   .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                   .build());
    List<ElementExecutionSummary> serviceFailureExecutionSummaries =
        asList(anElementExecutionSummary()
                   .withStatus(FAILED)
                   .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                   .build());

    List<WorkflowExecution> executions =
        constructWorkflowExecutions(serviceExecutionSummaries, serviceFailureExecutionSummaries);

    wingsPersistence.save(executions);

    ServiceInstanceStatistics statistics = statisticsService.getServiceInstanceStatisticsNew(ACCOUNT_ID, null, 30);
    assertThat(statistics.getStatsMap()).isNotEmpty();
    assertThat(statistics.getStatsMap().get(PROD))
        .hasSize(1)
        .containsExactlyInAnyOrder(TopConsumer.builder()
                                       .appId(APP_ID)
                                       .appName(APP_NAME)
                                       .serviceId(SERVICE_ID)
                                       .serviceName(SERVICE_NAME)
                                       .successfulActivityCount(2)
                                       .failedActivityCount(0)
                                       .totalCount(2)
                                       .build());

    assertThat(statistics.getStatsMap().get(NON_PROD)).hasSize(1);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldGetServiceInstanceStatisticsNew_FollowRBAC() {
    try {
      UserThreadLocal.set(user);
      when(featureFlagService.isEnabled(eq(FeatureName.SPG_SERVICES_OVERVIEW_RBAC), any())).thenReturn(true);
      when(appService.list(any(PageRequest.class)))
          .thenReturn(
              aPageResponse().withResponse(asList(anApplication().uuid(APP_ID).name(APP_NAME).build())).build());

      List<ElementExecutionSummary> serviceExecutionSummaries =
          asList(anElementExecutionSummary()
                     .withStatus(SUCCESS)
                     .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_ID).build())
                     .build());
      List<ElementExecutionSummary> serviceFailureExecutionSummaries =
          asList(anElementExecutionSummary()
                     .withStatus(FAILED)
                     .withContextElement(ServiceElement.builder().name(SERVICE_NAME).uuid(SERVICE_2_ID).build())
                     .build());

      List<WorkflowExecution> executions =
          constructWorkflowExecutions(serviceExecutionSummaries, serviceFailureExecutionSummaries);

      wingsPersistence.save(executions);

      ServiceInstanceStatistics statistics = statisticsService.getServiceInstanceStatisticsNew(ACCOUNT_ID, null, 30);
      assertThat(statistics.getStatsMap()).isNotEmpty();
      assertThat(statistics.getStatsMap().get(PROD))
          .hasSize(1)
          .containsExactlyInAnyOrder(TopConsumer.builder()
                                         .appId(APP_ID)
                                         .appName(APP_NAME)
                                         .serviceId(SERVICE_ID)
                                         .serviceName(SERVICE_NAME)
                                         .successfulActivityCount(2)
                                         .failedActivityCount(0)
                                         .totalCount(2)
                                         .build());

      assertThat(statistics.getStatsMap().get(NON_PROD)).hasSize(0);
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDeploymentStatistics() {
    List<WorkflowExecution> executions = constructWorkflowServiceExecutions();

    when(workflowExecutionService.obtainWorkflowExecutions(anyList(), anyLong(), any())).thenReturn(executions);

    DeploymentStatistics deploymentStatistics =
        statisticsService.getDeploymentStatistics(ACCOUNT_ID, asList(APP_ID), 30);

    assertThat(deploymentStatistics.getStatsMap()).hasSize(3).containsOnlyKeys(EnvironmentType.values());

    AggregatedDayStats aggregatedProdDayStats = deploymentStatistics.getStatsMap().get(PROD);
    AggregatedDayStats aggregatedNonProdDayStats = deploymentStatistics.getStatsMap().get(NON_PROD);

    assertAggregatedDayStats(getStartEpoch(), aggregatedProdDayStats, aggregatedNonProdDayStats);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldGetDeploymentStatisticsNew() {
    List<WorkflowExecution> executions = constructWorkflowServiceExecutions();
    wingsPersistence.save(executions);

    DeploymentStatistics deploymentStatistics =
        statisticsService.getDeploymentStatisticsNew(ACCOUNT_ID, asList(APP_ID), 30);

    assertThat(deploymentStatistics.getStatsMap()).hasSize(3).containsOnlyKeys(EnvironmentType.values());

    AggregatedDayStats aggregatedProdDayStats = deploymentStatistics.getStatsMap().get(PROD);
    AggregatedDayStats aggregatedNonProdDayStats = deploymentStatistics.getStatsMap().get(NON_PROD);

    assertAggregatedDayStats(getStartEpoch(), aggregatedProdDayStats, aggregatedNonProdDayStats);
  }

  private long getStartEpoch() {
    return getEndEpoch(29);
  }

  private void assertAggregatedDayStats(
      long startEpoch, AggregatedDayStats aggregatedProdDayStats, AggregatedDayStats aggregatedNonProdDayStats) {
    assertThat(aggregatedNonProdDayStats.getFailedCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getInstancesCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getTotalCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getDaysStats().size()).isEqualTo(30);
    assertThat(aggregatedNonProdDayStats.getDaysStats().get(0)).isEqualTo(new DayStat(2, 2, 2, startEpoch));

    assertThat(aggregatedProdDayStats.getFailedCount()).isEqualTo(0);
    assertThat(aggregatedProdDayStats.getInstancesCount()).isEqualTo(2);
    assertThat(aggregatedProdDayStats.getTotalCount()).isEqualTo(2);
    assertThat(aggregatedProdDayStats.getDaysStats().size()).isEqualTo(30);
    assertThat(aggregatedProdDayStats.getDaysStats().get(0)).isEqualTo(new DayStat(2, 0, 2, startEpoch));
  }

  private List<WorkflowExecution> constructWorkflowServiceExecutions() {
    long startEpoch = getStartEpoch();
    return asList(aSuccessfulServiceWfExecution(generateElementExecutionSummaries(), startEpoch),
        aSuccessfulServiceWfExecution(generateElementExecutionSummaries(), startEpoch),
        aFailedServiceWfExecution(generateElementExecutionSummaries(), startEpoch),
        aFailedServiceWfExecution(generateElementExecutionSummaries(), startEpoch));
  }

  private List<ElementExecutionSummary> generateElementExecutionSummaries() {
    List<ElementExecutionSummary> serviceExecutionSummaries =
        asList(anElementExecutionSummary()
                   .withInstanceStatusSummaries(
                       asList(anInstanceStatusSummary()
                                  .withInstanceElement(anInstanceElement().uuid(generateUuid()).build())
                                  .build()))
                   .build());
    return serviceExecutionSummaries;
  }

  private WorkflowExecution aFailedServiceWfExecution(
      List<ElementExecutionSummary> serviceExecutionSummaries, long startEpoch) {
    return WorkflowExecution.builder()
        .appId(APP_ID)
        .envType(NON_PROD)
        .accountId(ACCOUNT_ID)
        .status(ExecutionStatus.FAILED)
        .workflowType(ORCHESTRATION)
        .serviceExecutionSummaries(serviceExecutionSummaries)
        .createdAt(startEpoch)
        .build();
  }

  private WorkflowExecution aSuccessfulServiceWfExecution(
      List<ElementExecutionSummary> serviceExecutionSummaries, long startEpoch) {
    return WorkflowExecution.builder()
        .appId(APP_ID)
        .accountId(ACCOUNT_ID)
        .envType(PROD)
        .status(SUCCESS)
        .workflowType(ORCHESTRATION)
        .serviceExecutionSummaries(serviceExecutionSummaries)
        .createdAt(startEpoch)
        .build();
  }

  private List<WorkflowExecution> constructWorkflowExecutions(List<ElementExecutionSummary> serviceExecutionSummaries,
      List<ElementExecutionSummary> serviceFailureExecutionSummaries) {
    long endEpoch = getEndEpoch(0);
    long startEpoch = getStartEpoch();
    return asList(aSuccessWorkflowExecution(serviceExecutionSummaries, endEpoch),
        aSuccessWorkflowExecution(serviceExecutionSummaries, endEpoch),
        aFailedWorkflowExecution(serviceFailureExecutionSummaries, startEpoch),
        aFailedWorkflowExecution(serviceFailureExecutionSummaries, startEpoch));
  }

  private WorkflowExecution aFailedWorkflowExecution(
      List<ElementExecutionSummary> serviceFailureExecutionSummaries, long startEpoch) {
    return WorkflowExecution.builder()
        .appId(APP_ID)
        .appName(APP_NAME)
        .serviceIds(List.of(SERVICE_2_ID))
        .accountId(ACCOUNT_ID)
        .envType(NON_PROD)
        .status(ExecutionStatus.FAILED)
        .workflowType(ORCHESTRATION)
        .serviceExecutionSummaries(serviceFailureExecutionSummaries)
        .createdAt(startEpoch)
        .build();
  }

  private WorkflowExecution aSuccessWorkflowExecution(
      List<ElementExecutionSummary> serviceExecutionSummaries, long endEpoch) {
    return WorkflowExecution.builder()
        .appId(APP_ID)
        .serviceIds(List.of(SERVICE_ID))
        .accountId(ACCOUNT_ID)
        .appName(APP_NAME)
        .envType(PROD)
        .status(SUCCESS)
        .workflowType(ORCHESTRATION)
        .serviceExecutionSummaries(serviceExecutionSummaries)
        .createdAt(endEpoch)
        .build();
  }

  private long getEndEpoch(int i) {
    return LocalDate.now(ZoneId.of("America/Los_Angeles"))
        .minus(i, ChronoUnit.DAYS)
        .atStartOfDay(ZoneId.of("America/Los_Angeles"))
        .toInstant()
        .toEpochMilli();
  }
}