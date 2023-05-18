/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ALEXANDRU_CIOFU;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.ObjectType.PHASE;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_7_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_8_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_10_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_7_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_8_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_9_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_TYPE;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_1_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_2_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_3_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_4_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_5_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_6_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_7_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_7_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_8_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_8_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_10_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_11_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_12_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_13_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_14_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_15_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_16_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_17_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_18_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_19_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_20_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_7_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_8_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_9_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.KUBE_CLUSTER;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_ARTIFACT_BUILD_1_NUM;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_ARTIFACT_BUILD_2_NUM;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_ARTIFACT_BUILD_3_NUM;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_WORKFLOW_EXECUTION_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_WORKFLOW_EXECUTION_1_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_WORKFLOW_EXECUTION_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.LAST_WORKFLOW_EXECUTION_2_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_10_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_10_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_11_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_11_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_12_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_12_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_13_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_13_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_14_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_14_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_5_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_6_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_7_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_9_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_9_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACTS_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_ID_2;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.USER_NAME_2;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static java.util.Objects.deepEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.execution.WorkflowExecutionInfo;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment;
import software.wings.beans.instance.dashboard.InstanceStatsByService;
import software.wings.beans.instance.dashboard.InstanceSummaryStatsByService;
import software.wings.beans.instance.dashboard.service.CurrentActiveInstances;
import software.wings.beans.instance.dashboard.service.DeploymentHistory;
import software.wings.beans.instance.dashboard.service.ServiceInstanceDashboard;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummaryForUI;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.instance.AggregationInfo;
import software.wings.service.impl.instance.ArtifactInfo;
import software.wings.service.impl.instance.CompareEnvironmentAggregationResponseInfo;
import software.wings.service.impl.instance.DashboardStatisticsServiceImpl;
import software.wings.service.impl.instance.EnvInfo;
import software.wings.service.impl.instance.ServiceInfoResponseSummary;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.instance.DashboardStatisticsService;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionInstance;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 9/4/18
 */
@OwnedBy(DX)
public class DashboardStatisticsServiceImplTest extends WingsBaseTest {
  public static final String CHART_NAME = "CHART_NAME";
  public static final String REPO_URL = "REPO_URL";
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private WorkflowService workflowService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private AppService appService;
  @Mock private AccountService accountService;
  @Mock private FeatureFlagService featureFlagService;
  @Inject private UsageMetricsHelper usageMetricsHelper;

  @Inject private HPersistence persistence;
  @InjectMocks @Inject private DashboardStatisticsService dashboardService = spy(DashboardStatisticsServiceImpl.class);

  @InjectMocks @Inject private DashboardStatisticsServiceImpl dashboardStatisticsService;

  private Instance instance1;
  private Instance instance2;
  private Instance instance3;
  private Instance instance4;
  private Instance instance5;
  private Instance instance6;
  private Instance instance7;
  private Instance instance8;
  private Instance deletedInstance9;
  private Instance deletedInstance10;
  private Instance deletedInstance11;
  private Instance deletedInstance12;
  private Instance deletedInstance13;
  private Instance deletedInstance14;
  private Instance deletedInstance15;
  private Instance deletedInstance16;
  private Instance instance17;
  private User user;

  private User user2;
  private long currentTime;
  private WorkflowExecutionInfo workflowExecutionInfo;

  @Before
  public void init() {
    saveInstancesToDB();
    user = User.Builder.anUser()
               .name(USER_NAME)
               .uuid(USER_ID)
               .accounts(asList(Account.Builder.anAccount().withUuid(ACCOUNT_1_ID).build(),
                   Account.Builder.anAccount().withUuid(ACCOUNT_2_ID).build()))
               .build();
    user2 = User.Builder.anUser()
                .name(USER_NAME_2)
                .uuid(USER_ID_2)
                .accounts(asList(Account.Builder.anAccount().withUuid(ACCOUNT_1_ID).build(),
                    Account.Builder.anAccount().withUuid(ACCOUNT_2_ID).build()))
                .build();

    Map<String, AppPermissionSummaryForUI> appPermissionsMap = new HashMap<>();
    setAppPermissionsMap(appPermissionsMap, ENV_1_ID, APP_1_ID);
    setAppPermissionsMap(appPermissionsMap, ENV_2_ID, APP_2_ID);
    setAppPermissionsMap(appPermissionsMap, ENV_3_ID, APP_2_ID);
    setAppPermissionsMap(appPermissionsMap, ENV_4_ID, APP_3_ID);
    setAppPermissionsMap(appPermissionsMap, ENV_5_ID, APP_4_ID);
    setAppPermissionsMap(appPermissionsMap, ENV_6_ID, APP_5_ID);

    Map<String, AppPermissionSummary> appPermissionsMap2 = new HashMap<>();

    Map<Action, Set<String>> svcPermissions = new HashMap<>();
    svcPermissions.put(Action.READ, new HashSet<>(List.of(SERVICE_1_ID)));
    svcPermissions.put(Action.CREATE, new HashSet<>(List.of(SERVICE_2_ID)));
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().servicePermissions(svcPermissions).build();
    appPermissionsMap2.put(APP_1_ID, appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().accountId(ACCOUNT_ID).appPermissionMap(appPermissionsMap).build();

    UserPermissionInfo userPermissionInfo2 =
        UserPermissionInfo.builder().accountId(ACCOUNT_ID).appPermissionMapInternal(appPermissionsMap2).build();

    user.setUserRequestContext(UserRequestContext.builder()
                                   .accountId(ACCOUNT_ID)
                                   .userPermissionInfo(userPermissionInfo)
                                   .appIdFilterRequired(true)
                                   .appIds(Sets.newHashSet(APP_1_ID, APP_2_ID, APP_3_ID, APP_4_ID, APP_5_ID))
                                   .build());
    user2.setUserRequestContext(UserRequestContext.builder()
                                    .accountId(ACCOUNT_ID)
                                    .userPermissionInfo(userPermissionInfo2)
                                    .appIdFilterRequired(true)
                                    .appIds(Sets.newHashSet(APP_1_ID, APP_2_ID, APP_3_ID, APP_4_ID, APP_5_ID))
                                    .build());
    workflowExecutionInfo = WorkflowExecutionInfo.builder().build();
    UserThreadLocal.set(user);
    when(featureFlagService.isEnabled(eq(FeatureName.HELM_CHART_AS_ARTIFACT), any())).thenReturn(true);
    when(workflowExecutionService.getWorkflowExecutionInfo(any())).thenReturn(workflowExecutionInfo);
  }

  @After
  public void teardown() {
    UserThreadLocal.unset();
  }

  private void setAppPermissionsMap(
      Map<String, AppPermissionSummaryForUI> appPermissionsMap, String envId, String appId) {
    Map<String, Set<Action>> envPermissionMap = new HashMap<>();
    envPermissionMap.put(envId, newHashSet(Action.READ));

    AppPermissionSummaryForUI appPermissionSummaryForUI =
        AppPermissionSummaryForUI.builder().envPermissions(envPermissionMap).build();

    appPermissionsMap.put(appId, appPermissionSummaryForUI);
  }

  private void saveInstancesToDB() {
    currentTime = System.currentTimeMillis();
    instance1 = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    persistence.save(instance1);
    instance2 = buildInstance(INSTANCE_2_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_2_ID, ENV_1_ID, INFRA_MAPPING_2_ID,
        INFRA_MAPPING_2_NAME, CONTAINER_2_ID, currentTime);
    persistence.save(instance2);
    instance3 = buildInstance(INSTANCE_3_ID, ACCOUNT_1_ID, APP_2_ID, SERVICE_3_ID, ENV_2_ID, INFRA_MAPPING_3_ID,
        INFRA_MAPPING_3_NAME, CONTAINER_3_ID, currentTime - 10000);
    persistence.save(instance3);
    instance4 = buildInstance(INSTANCE_4_ID, ACCOUNT_1_ID, APP_2_ID, SERVICE_4_ID, ENV_3_ID, INFRA_MAPPING_4_ID,
        INFRA_MAPPING_4_NAME, CONTAINER_4_ID, currentTime - 20000);
    persistence.save(instance4);
    instance5 = buildInstance(INSTANCE_5_ID, ACCOUNT_1_ID, APP_3_ID, SERVICE_5_ID, ENV_4_ID, INFRA_MAPPING_5_ID,
        INFRA_MAPPING_5_NAME, CONTAINER_5_ID, currentTime - 30000);
    persistence.save(instance5);
    instance6 = buildInstance(INSTANCE_6_ID, ACCOUNT_1_ID, APP_3_ID, SERVICE_5_ID, ENV_4_ID, INFRA_MAPPING_6_ID,
        INFRA_MAPPING_6_NAME, CONTAINER_6_ID, currentTime - 30000);
    persistence.save(instance6);
    instance7 = buildInstance(INSTANCE_7_ID, ACCOUNT_2_ID, APP_4_ID, SERVICE_6_ID, ENV_5_ID, INFRA_MAPPING_7_ID,
        INFRA_MAPPING_7_NAME, CONTAINER_7_ID, currentTime - 40000);
    persistence.save(instance7);
    instance8 = buildInstance(INSTANCE_8_ID, ACCOUNT_2_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID, INFRA_MAPPING_8_ID,
        INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 50000);
    persistence.save(instance8);
    deletedInstance9 = buildInstance(INSTANCE_9_ID, ACCOUNT_1_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID, INFRA_MAPPING_8_ID,
        INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 120000, currentTime - 30000);
    persistence.save(deletedInstance9);
    deletedInstance10 = buildInstance(INSTANCE_10_ID, ACCOUNT_1_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 120000, currentTime - 60000);
    persistence.save(deletedInstance10);
    deletedInstance11 = buildInstance(INSTANCE_11_ID, ACCOUNT_1_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 30000, currentTime - 20000);
    persistence.save(deletedInstance11);
    deletedInstance12 = buildInstance(INSTANCE_12_ID, ACCOUNT_1_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 30000, currentTime - 10000);
    persistence.save(deletedInstance12);
    deletedInstance13 = buildInstance(INSTANCE_9_ID, ACCOUNT_2_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID, INFRA_MAPPING_8_ID,
        INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 120000, currentTime - 30000);
    persistence.save(deletedInstance9);
    deletedInstance14 = buildInstance(INSTANCE_10_ID, ACCOUNT_2_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 120000, currentTime - 60000);
    persistence.save(deletedInstance10);
    deletedInstance15 = buildInstance(INSTANCE_11_ID, ACCOUNT_2_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 30000, currentTime - 20000);
    persistence.save(deletedInstance11);
    deletedInstance16 = buildInstance(INSTANCE_12_ID, ACCOUNT_2_ID, APP_5_ID, SERVICE_7_ID, ENV_6_ID,
        INFRA_MAPPING_8_ID, INFRA_MAPPING_8_NAME, CONTAINER_8_ID, currentTime - 30000, currentTime - 10000);
    persistence.save(deletedInstance12);
    instance17 = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    instance17.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());
    persistence.save(instance17);
    Instance instance18 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_13_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_7_ID, SERVICE_9_ID, SERVICE_9_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance18);
    Instance instance19 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_14_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_8_ID, SERVICE_10_ID, SERVICE_10_NAME, LAST_ARTIFACT_BUILD_2_NUM, LAST_WORKFLOW_EXECUTION_2_ID,
        LAST_WORKFLOW_EXECUTION_2_NAME, INFRA_MAPPING_2_ID, INFRA_MAPPING_2_NAME, false);
    persistence.save(instance19);
    Instance instance20 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_15_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_9_ID, SERVICE_10_ID, SERVICE_10_NAME, LAST_ARTIFACT_BUILD_3_NUM, LAST_WORKFLOW_EXECUTION_2_ID,
        LAST_WORKFLOW_EXECUTION_2_NAME, INFRA_MAPPING_2_ID, INFRA_MAPPING_2_NAME, false);
    persistence.save(instance20);
    Instance instance21 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_16_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_10_ID, SERVICE_9_ID, SERVICE_9_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance21);
    Instance instance22 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_17_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_10_ID, SERVICE_11_ID, SERVICE_11_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance22);
    Instance instance23 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_18_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_10_ID, SERVICE_12_ID, SERVICE_12_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance23);
    Instance instance24 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_19_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_10_ID, SERVICE_13_ID, SERVICE_13_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance24);
    Instance instance25 = buildInstanceToTestCompareServicesByEnvironment(INSTANCE_20_ID, ACCOUNT_3_ID, APP_6_ID,
        ENV_10_ID, SERVICE_14_ID, SERVICE_14_NAME, LAST_ARTIFACT_BUILD_1_NUM, LAST_WORKFLOW_EXECUTION_1_ID,
        LAST_WORKFLOW_EXECUTION_1_NAME, INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, false);
    persistence.save(instance25);

    WorkflowExecution workflowExecution1 = buildWorkflowExecution(LAST_WORKFLOW_EXECUTION_1_ID);
    persistence.save(workflowExecution1);
    WorkflowExecution workflowExecution2 = buildWorkflowExecution(LAST_WORKFLOW_EXECUTION_2_ID);
    persistence.save(workflowExecution2);
  }

  private WorkflowExecution buildWorkflowExecution(String workflowId) {
    return WorkflowExecution.builder().uuid(workflowId).workflowId(workflowId).appId(APP_1_ID).build();
  }

  private Instance buildInstance(String instanceId, String accountId, String appId, String serviceId, String envId,
      String infraMappingId, String infraMappingName, String containerId, long createdAt) {
    InstanceBuilder builder = Instance.builder();
    setValues(builder, instanceId, accountId, appId, serviceId, envId, infraMappingId, infraMappingName, containerId);
    builder.createdAt(createdAt);
    return builder.build();
  }

  private Instance buildInstance(String instanceId, String accountId, String appId, String serviceId, String envId,
      String infraMappingId, String infraMappingName, String containerId, long createdAt, long deletedAt) {
    InstanceBuilder builder = Instance.builder();
    setValues(builder, instanceId, accountId, appId, serviceId, envId, infraMappingId, infraMappingName, containerId);
    builder.createdAt(createdAt);
    builder.isDeleted(true);
    builder.deletedAt(deletedAt);
    return builder.build();
  }

  private void setValues(InstanceBuilder instanceBuilder, String instanceId, String accountId, String appId,
      String serviceId, String envId, String infraMappingId, String infraMappingName, String containerId) {
    instanceBuilder.uuid(instanceId)
        .accountId(accountId)
        .appId(appId)
        .computeProviderId(COMPUTE_PROVIDER_NAME)
        .appName(APP_NAME)
        .envId(envId)
        .envName(ENV_NAME)
        .serviceId(serviceId)
        .serviceName(SERVICE_NAME)
        .envType(EnvironmentType.PROD)
        .infraMappingId(infraMappingId)
        .infraMappingName(infraMappingName)
        .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
        .instanceType(InstanceType.KUBERNETES_CONTAINER_INSTANCE)
        .containerInstanceKey(ContainerInstanceKey.builder().containerId(containerId).build())
        .instanceInfo(KubernetesContainerInfo.builder()
                          .clusterName(KUBE_CLUSTER)
                          .serviceName("service_a_0")
                          .controllerName("controllerName:0")
                          .podName(containerId)
                          .build())
        .build();
  }

  private Instance buildInstanceToTestCompareServicesByEnvironment(String instanceId, String accountId, String appId,
      String envId, String serviceId, String serviceName, String lastArtifactBuildNum, String lastWorkflowExecutionId,
      String lastWorkflowExecutionName, String infraMappingId, String infraMappingName, boolean isDeleted) {
    return Instance.builder()
        .uuid(instanceId)
        .accountId(accountId)
        .appId(appId)
        .envId(envId)
        .serviceId(serviceId)
        .serviceName(serviceName)
        .lastArtifactBuildNum(lastArtifactBuildNum)
        .lastWorkflowExecutionId(lastWorkflowExecutionId)
        .lastWorkflowExecutionName(lastWorkflowExecutionName)
        .infraMappingId(infraMappingId)
        .infraMappingName(infraMappingName)
        .isDeleted(isDeleted)
        .build();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shallTestInstanceStats() {
    try {
      List<String> appIdList = asList(APP_1_ID, APP_2_ID, APP_3_ID, APP_4_ID, APP_5_ID);
      List<InstanceStatsByService> currentAppInstanceStatsByService =
          dashboardService.getAppInstanceStatsByService(ACCOUNT_1_ID, appIdList, System.currentTimeMillis());
      assertThat(currentAppInstanceStatsByService).hasSize(7);
      List<InstanceStatsByService> appInstanceStatsByServiceAtTime =
          dashboardService.getAppInstanceStatsByService(ACCOUNT_1_ID, appIdList, System.currentTimeMillis());
      assertThat(appInstanceStatsByServiceAtTime).hasSize(7);
      List<Instance> currentInstances =
          dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, System.currentTimeMillis());
      assertThat(currentInstances).hasSize(6);

      List<Instance> instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 60000);
      assertThat(instancesAtTime).hasSize(2);

      appInstanceStatsByServiceAtTime =
          dashboardService.getAppInstanceStatsByService(ACCOUNT_1_ID, appIdList, currentTime - 60000);
      assertThat(appInstanceStatsByServiceAtTime).hasSize(1);
      assertThat(appInstanceStatsByServiceAtTime.get(0).getTotalCount()).isEqualTo(2);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 50000);
      assertThat(instancesAtTime).hasSize(1);

      appInstanceStatsByServiceAtTime =
          dashboardService.getAppInstanceStatsByService(ACCOUNT_1_ID, appIdList, currentTime - 50000);
      assertThat(appInstanceStatsByServiceAtTime).hasSize(1);
      assertThat(appInstanceStatsByServiceAtTime.get(0).getTotalCount()).isEqualTo(2);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 40000);
      assertThat(instancesAtTime).hasSize(1);

      appInstanceStatsByServiceAtTime =
          dashboardService.getAppInstanceStatsByService(ACCOUNT_1_ID, appIdList, currentTime - 40000);
      assertThat(appInstanceStatsByServiceAtTime).hasSize(2);
      assertThat(appInstanceStatsByServiceAtTime.get(0).getTotalCount()).isEqualTo(1);
      assertThat(appInstanceStatsByServiceAtTime.get(1).getTotalCount()).isEqualTo(2);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 30000);
      assertThat(instancesAtTime).hasSize(5);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 20000);
      assertThat(instancesAtTime).hasSize(5);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime - 10000);
      assertThat(instancesAtTime).hasSize(5);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_1_ID, currentTime);
      assertThat(instancesAtTime).hasSize(6);

      instancesAtTime = dashboardService.getAppInstancesForAccount(ACCOUNT_2_ID, currentTime - 30000);
      assertThat(instancesAtTime).hasSize(2);
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shallGetAppInstanceSummaryStatsByService() {
    try {
      List<String> appIdList = asList(APP_1_ID, APP_2_ID, APP_3_ID, APP_4_ID, APP_5_ID);
      Map<String, String> entry = new HashMap<>();
      entry.put("service1_id", "updatedService1Name");
      Set<String> setOfServiceIds = new HashSet<>();
      setOfServiceIds.add("service1_id");
      when(serviceResourceService.getServiceNamesWithAccountId(any(), any())).thenReturn(entry);

      PageResponse<InstanceSummaryStatsByService> currentAppInstanceStatsByService =
          dashboardService.getAppInstanceSummaryStatsByService(
              ACCOUNT_1_ID, appIdList, System.currentTimeMillis(), 0, 5);
      assertThat(currentAppInstanceStatsByService.getResponse()).hasSize(5);

      currentAppInstanceStatsByService = dashboardService.getAppInstanceSummaryStatsByService(
          ACCOUNT_1_ID, appIdList, System.currentTimeMillis(), 5, 10);
      assertThat(currentAppInstanceStatsByService.getResponse()).hasSize(2);

      currentAppInstanceStatsByService = dashboardService.getAppInstanceSummaryStatsByService(
          ACCOUNT_1_ID, appIdList, System.currentTimeMillis(), 0, 10);
      assertThat(currentAppInstanceStatsByService.getResponse()).hasSize(7);

      currentAppInstanceStatsByService = dashboardService.getAppInstanceSummaryStatsByService(
          ACCOUNT_1_ID, appIdList, System.currentTimeMillis(), 0, 10);
      for (InstanceSummaryStatsByService instanceSummaryStatsByService :
          currentAppInstanceStatsByService.getResponse()) {
        if (instanceSummaryStatsByService.getServiceSummary().getId().equals("service1_id")) {
          assertThat(instanceSummaryStatsByService.getServiceSummary().getName().equals("updatedService1Name"));
          break;
        }
      }

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shallGetAppInstanceSummaryStatsByServiceFollowingRBAC_Svc() {
    try {
      UserThreadLocal.set(user2);
      when(featureFlagService.isEnabled(eq(FeatureName.SPG_SERVICES_OVERVIEW_RBAC), any())).thenReturn(true);
      List<String> appIdList = asList(APP_1_ID, APP_2_ID, APP_3_ID, APP_4_ID, APP_5_ID);
      Map<String, String> entry = new HashMap<>();
      entry.put("service1_id", "updatedService1Name");
      Set<String> setOfServiceIds = new HashSet<>();
      setOfServiceIds.add("service1_id");
      when(serviceResourceService.getServiceNamesWithAccountId(any(), any())).thenReturn(entry);

      PageResponse<InstanceSummaryStatsByService> currentAppInstanceStatsByService =
          dashboardService.getAppInstanceSummaryStatsByService(
              ACCOUNT_1_ID, appIdList, System.currentTimeMillis(), 0, 5);
      assertThat(currentAppInstanceStatsByService.getResponse()).hasSize(1);

      for (InstanceSummaryStatsByService instanceSummaryStatsByService :
          currentAppInstanceStatsByService.getResponse()) {
        if (instanceSummaryStatsByService.getServiceSummary().getId().equals("service1_id")) {
          assertThat(instanceSummaryStatsByService.getServiceSummary().getName().equals("updatedService1Name"));
          break;
        }
      }

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shallGetServiceInstanceStats() {
    try {
      List<InstanceStatsByEnvironment> serviceInstances;

      serviceInstances = dashboardService.getServiceInstances(ACCOUNT_1_ID, SERVICE_2_ID, System.currentTimeMillis());
      assertThat(serviceInstances).hasSize(1);

      serviceInstances = dashboardService.getServiceInstances(ACCOUNT_1_ID, SERVICE_1_ID, System.currentTimeMillis());
      assertThat(serviceInstances).hasSize(1);

      serviceInstances = dashboardService.getServiceInstances(ACCOUNT_1_ID, SERVICE_7_ID, currentTime - 100000);
      assertThat(serviceInstances).hasSize(1);
      assertThat(serviceInstances.get(0).getInstanceStatsByArtifactList()).hasSize(1);
      assertThat(serviceInstances.get(0).getInstanceStatsByArtifactList().get(0).getInstanceStats().getTotalCount())
          .isEqualTo(2);
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shallGetServiceInstanceDashboard() {
    try {
      user.setUserRequestContext(UserRequestContext.builder()
                                     .accountId(ACCOUNT_ID)
                                     .userPermissionInfo(user.getUserRequestContext().getUserPermissionInfo())
                                     .appIdFilterRequired(true)
                                     .appIds(Sets.newHashSet(APP_1_ID))
                                     .build());

      PipelineSummary pipelineSummary =
          PipelineSummary.builder().pipelineId(PIPELINE_EXECUTION_ID).pipelineName(PIPELINE_NAME).build();

      ExecutionArgs executionArgs = new ExecutionArgs();
      executionArgs.setArtifacts(asList(Artifact.Builder.anArtifact()
                                            .withAppId(APP_1_ID)
                                            .withDisplayName(ARTIFACT_NAME)
                                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "v20")))
                                            .withUuid(ARTIFACT_ID)
                                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                                            .build()));
      Long startTS = 1630969310005L;
      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .pipelineSummary(pipelineSummary)
                                                .executionArgs(executionArgs)
                                                .appId(APP_1_ID)
                                                .status(ExecutionStatus.ERROR)
                                                .envIds(asList(ENV_1_ID))
                                                .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
                                                .infraMappingIds(asList(INFRA_MAPPING_1_ID, INFRA_MAPPING_2_ID))
                                                .workflowId(WORKFLOW_ID)
                                                .uuid(WORKFLOW_EXECUTION_ID)
                                                .name(WORKFLOW_NAME)
                                                .startTs(startTS)
                                                .build();
      /*
        This addition is to test the shouldUpdate() and updateActiveInstanceArtifactDetails() methods
        as part of this test itself
       */
      persistence.save(workflowExecution);
      Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID,
          INFRA_MAPPING_1_ID, INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
      instance.setInstanceInfo(
          K8sPodInfo.builder()
              .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
              .build());
      instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID);
      instance.setLastArtifactBuildNum("v10");
      persistence.save(instance);
      PageResponse<WorkflowExecution> executionsPageResponse =
          aPageResponse().withResponse(asList(workflowExecution)).build();
      when(workflowExecutionService.listExecutions(any(PageRequest.class), anyBoolean()))
          .thenReturn(executionsPageResponse);

      List<Service> serviceList = Lists.newArrayList();
      Service service1 = Service.builder()
                             .uuid(SERVICE_1_ID)
                             .name(SERVICE_NAME)
                             .appId(APP_1_ID)
                             .artifactStreamIds(asList(ARTIFACT_STREAM_ID))
                             .build();
      serviceList.add(service1);
      Service service2 = Service.builder().uuid(SERVICE_2_ID).name(SERVICE_NAME).appId(APP_1_ID).build();
      serviceList.add(service2);
      PageResponse<Environment> servicesPageResponse = aPageResponse().withResponse(serviceList).build();
      when(serviceResourceService.list(any(PageRequest.class), anyBoolean(), anyBoolean(), anyBoolean(), any()))
          .thenReturn(servicesPageResponse);
      when(serviceResourceService.getWithDetails(any(), any())).thenReturn(service1);
      when(artifactStreamServiceBindingService.listArtifactStreamIds(any(Service.class)))
          .thenReturn(asList(ARTIFACT_STREAM_ID));

      List<Environment> envList = Lists.newArrayList();
      envList.add(Environment.Builder.anEnvironment().uuid(ENV_1_ID).name(ENV_NAME).appId(APP_1_ID).build());
      PageResponse<Environment> envsPageResponse = aPageResponse().withResponse(envList).build();
      when(environmentService.list(any(PageRequest.class), anyBoolean(), any(), anyBoolean()))
          .thenReturn(envsPageResponse);

      List<InfrastructureMapping> infraList = Lists.newArrayList();
      InfrastructureMapping infra1 = new GcpKubernetesInfrastructureMapping();
      infra1.setEnvId(ENV_1_ID);
      infra1.setServiceId(SERVICE_1_ID);
      infra1.setAccountId(ACCOUNT_1_ID);
      infra1.setAppId(APP_1_ID);
      infra1.setUuid(INFRA_MAPPING_1_ID);
      infra1.setName(INFRA_MAPPING_1_NAME);
      infraList.add(infra1);

      InfrastructureMapping infra2 = new GcpKubernetesInfrastructureMapping();
      infra2.setEnvId(ENV_1_ID);
      infra2.setServiceId(SERVICE_2_ID);
      infra2.setAccountId(ACCOUNT_1_ID);
      infra2.setAppId(APP_1_ID);
      infra2.setUuid(INFRA_MAPPING_2_ID);
      infra2.setName(INFRA_MAPPING_2_NAME);
      infraList.add(infra2);

      PageResponse<InfrastructureMapping> infrasPageResponse = aPageResponse().withResponse(infraList).build();

      when(infraMappingService.list(any(PageRequest.class))).thenReturn(infrasPageResponse);
      long deployTime = System.currentTimeMillis();
      ArtifactSummary artifactSummary =
          ArtifactSummary.builder().id(ARTIFACT_ID).name(ARTIFACT_NAME).buildNo(BUILD_NO).build();
      EntitySummary env =
          EntitySummary.builder().id(ENV_1_ID).name(ENV_NAME).type(EntityType.ENVIRONMENT.name()).build();
      EntitySummary infraSummary1 = EntitySummary.builder()
                                        .id(INFRA_MAPPING_1_ID)
                                        .name(INFRA_MAPPING_1_NAME)
                                        .type(EntityType.INFRASTRUCTURE_MAPPING.name())
                                        .build();
      EntitySummary infraSummary2 = EntitySummary.builder()
                                        .id(INFRA_MAPPING_2_ID)
                                        .name(INFRA_MAPPING_2_NAME)
                                        .type(EntityType.INFRASTRUCTURE_MAPPING.name())
                                        .build();
      EntitySummary workflow = EntitySummary.builder()
                                   .id(WORKFLOW_EXECUTION_ID)
                                   .name(WORKFLOW_NAME)
                                   .type(EntityType.WORKFLOW.name())
                                   .build();
      EntitySummary pipeline = EntitySummary.builder()
                                   .id(PIPELINE_EXECUTION_ID)
                                   .name(PIPELINE_NAME)
                                   .type(EntityType.PIPELINE.name())
                                   .build();
      EntitySummary user = EntitySummary.builder().id(USER_ID).name(USER_NAME).type(EntityType.USER.name()).build();
      DeploymentHistory expectedDeployment = DeploymentHistory.builder()
                                                 .artifact(artifactSummary)
                                                 .deployedAt(new Date(deployTime))
                                                 .envs(asList(env))
                                                 .inframappings(asList(infraSummary1, infraSummary2))
                                                 .instanceCount(1)
                                                 .pipeline(pipeline)
                                                 .workflow(workflow)
                                                 .status("ERROR")
                                                 .triggeredBy(user)
                                                 .build();

      ServiceInstanceDashboard serviceInstanceDashboard =
          dashboardService.getServiceInstanceDashboard(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, null);
      assertThat(serviceInstanceDashboard).isNotNull();
      assertThat(serviceInstanceDashboard.getCurrentActiveInstancesList()).hasSize(1);
      assertThat(serviceInstanceDashboard.getCurrentActiveInstancesList().get(0).getInstanceCount()).isEqualTo(1);
      assertThat(serviceInstanceDashboard.getDeploymentHistoryList()).hasSize(1);
      DeploymentHistory deploymentHistory = serviceInstanceDashboard.getDeploymentHistoryList().get(0);
      assertThat(deepEquals(serviceInstanceDashboard.getCurrentActiveInstancesList().get(0).getArtifact(),
          deploymentHistory.getArtifact()));
      assertThat(deepEquals(expectedDeployment.getEnvs(), deploymentHistory.getEnvs())).isTrue();
      assertThat(deepEquals(expectedDeployment.getInframappings(), deploymentHistory.getInframappings())).isTrue();
      assertThat(deepEquals(expectedDeployment.getStatus(), deploymentHistory.getStatus())).isTrue();
      assertThat(deepEquals(expectedDeployment.getWorkflow(), deploymentHistory.getWorkflow())).isTrue();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetActiveInstancesWithManifest() {
    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<CurrentActiveInstances> activeInstances =
        dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);
    assertThat(activeInstances).hasSize(1);
    ManifestSummary manifestSummary = activeInstances.get(0).getManifest();
    assertThat(manifestSummary.getVersionNo()).isEqualTo("1");
    assertThat(manifestSummary.getName()).isEqualTo(CHART_NAME);
    assertThat(manifestSummary.getSource()).isEqualTo(REPO_URL);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateLastWorkflowExecutionAndManifestInActiveInstance() {
    Long startTS = 1630969310005L;
    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(APP_1_ID)
                                              .status(ExecutionStatus.SUCCESS)
                                              .envIds(asList(ENV_1_ID))
                                              .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
                                              .infraMappingIds(asList(INFRA_MAPPING_1_ID, INFRA_MAPPING_2_ID))
                                              .workflowId(WORKFLOW_ID)
                                              .uuid(WORKFLOW_EXECUTION_ID)
                                              .name(WORKFLOW_NAME)
                                              .startTs(startTS)
                                              .build();
    persistence.save(workflowExecution);
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());
    instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID);
    persistence.save(instance);
    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<CurrentActiveInstances> activeInstances =
        dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);
    assertThat(activeInstances).hasSize(1);
    EntitySummary executionSummary = activeInstances.get(0).getLastWorkflowExecution();
    assertThat(executionSummary.getId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(executionSummary.getName()).isEqualTo(WORKFLOW_NAME);
    ManifestSummary manifestSummary = activeInstances.get(0).getManifest();
    assertThat(manifestSummary.getVersionNo()).isEqualTo("1");
    assertThat(manifestSummary.getName()).isEqualTo(CHART_NAME);
    assertThat(manifestSummary.getSource()).isEqualTo(REPO_URL);
  }

  @Test
  @Owner(developers = {PRABU, DEEPAK_PUTHRAYA})
  @Category(UnitTests.class)
  public void shallGetDeploymentHistoryWithManifest() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(asList(Artifact.Builder.anArtifact()
                                          .withAppId(APP_1_ID)
                                          .withDisplayName(ARTIFACT_NAME)
                                          .withUuid(ARTIFACT_ID)
                                          .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                          .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                                          .build()));
    executionArgs.setHelmCharts(
        asList(HelmChart.builder().uuid(HELM_CHART_ID).serviceId(SERVICE_ID).name(CHART_NAME).version("1").build()));
    WorkflowExecution workflowExecution1 = WorkflowExecution.builder()
                                               .appId(APP_1_ID)
                                               .executionArgs(executionArgs)
                                               .status(ExecutionStatus.SUCCESS)
                                               .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
                                               .workflowId(WORKFLOW_ID)
                                               .uuid(WORKFLOW_EXECUTION_ID)
                                               .name(WORKFLOW_NAME)
                                               .build();
    ExecutionArgs executionArgs2 = new ExecutionArgs();
    executionArgs2.setHelmCharts(
        asList(HelmChart.builder().uuid(HELM_CHART_ID).name(CHART_NAME).serviceId(SERVICE_ID).version("2").build()));
    WorkflowExecution workflowExecution2 = WorkflowExecution.builder()
                                               .appId(APP_1_ID)
                                               .status(ExecutionStatus.SUCCESS)
                                               .executionArgs(executionArgs2)
                                               .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
                                               .workflowId(WORKFLOW_ID)
                                               .uuid(WORKFLOW_EXECUTION_ID)
                                               .name(WORKFLOW_NAME)
                                               .build();
    ExecutionArgs executionArgs3 = new ExecutionArgs();
    WorkflowExecution workflowExecution3 = WorkflowExecution.builder()
                                               .appId(APP_1_ID)
                                               .status(ExecutionStatus.SUCCESS)
                                               .executionArgs(executionArgs3)
                                               .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
                                               .workflowId(WORKFLOW_ID)
                                               .uuid(WORKFLOW_EXECUTION_ID)
                                               .name(WORKFLOW_NAME)
                                               .build();
    PageResponse<WorkflowExecution> pageResponse =
        aPageResponse().withResponse(asList(workflowExecution1, workflowExecution2, workflowExecution3)).build();
    when(workflowExecutionService.listExecutions(any(), eq(false))).thenReturn(pageResponse);
    when(serviceResourceService.getWithDetails(APP_1_ID, SERVICE_ID)).thenReturn(Service.builder().build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(any(Service.class)))
        .thenReturn(asList(ARTIFACT_STREAM_ID));
    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<DeploymentHistory> deploymentHistories =
        dashboardStatisticsService.getDeploymentHistory(ACCOUNT_ID, APP_1_ID, SERVICE_ID, null);
    assertThat(deploymentHistories).hasSize(3);
    DeploymentHistory deploymentHistory1 = deploymentHistories.get(0);
    assertThat(deploymentHistory1.getManifest().getName()).isEqualTo(CHART_NAME);
    assertThat(deploymentHistory1.getManifest().getVersionNo()).isEqualTo("1");
    assertThat(deploymentHistory1.getArtifact().getId()).isEqualTo(ARTIFACT_ID);
    DeploymentHistory deploymentHistory2 = deploymentHistories.get(1);
    assertThat(deploymentHistory2.getManifest().getName()).isEqualTo(CHART_NAME);
    assertThat(deploymentHistory2.getManifest().getVersionNo()).isEqualTo("2");
    assertThat(deploymentHistory2.getArtifact()).isNull();
    DeploymentHistory deploymentHistory3 = deploymentHistories.get(2);
    assertThat(deploymentHistory3.getManifest()).isNull();
    assertThat(deploymentHistory3.getArtifact()).isNull();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void shallGetCompareServicesByEnvironment() {
    PageResponse<CompareEnvironmentAggregationResponseInfo> compareEnvironmentAggregationResponseInfos =
        dashboardService.getCompareServicesByEnvironment(ACCOUNT_3_ID, APP_6_ID, ENV_7_ID, ENV_8_ID, 0, 5);
    List<ServiceInfoResponseSummary> ExpectedServiceResponseInfoSummary1 = new ArrayList<>();
    ExpectedServiceResponseInfoSummary1.add(ServiceInfoResponseSummary.builder()
                                                .lastArtifactBuildNum(LAST_ARTIFACT_BUILD_2_NUM)
                                                .lastWorkflowExecutionId(LAST_WORKFLOW_EXECUTION_2_ID)
                                                .lastWorkflowExecutionName(LAST_WORKFLOW_EXECUTION_2_NAME)
                                                .infraMappingId(INFRA_MAPPING_2_ID)
                                                .infraMappingName(INFRA_MAPPING_2_NAME)
                                                .build());
    Map<String, List<ServiceInfoResponseSummary>> ExpectedEnvInfo1 = new HashMap<>();
    ExpectedEnvInfo1.put(ENV_7_ID, new ArrayList<>());
    ExpectedEnvInfo1.put(ENV_8_ID, ExpectedServiceResponseInfoSummary1);
    CompareEnvironmentAggregationResponseInfo ExpectedCompareEnvironmentAggregationResponseInfo1 =
        CompareEnvironmentAggregationResponseInfo.builder()
            .serviceId(SERVICE_10_ID)
            .serviceName(SERVICE_10_NAME)
            .count("1")
            .envInfo(ExpectedEnvInfo1)
            .build();
    List<ServiceInfoResponseSummary> ExpectedServiceResponseInfoSummary2 = new ArrayList<>();
    ExpectedServiceResponseInfoSummary2.add(ServiceInfoResponseSummary.builder()
                                                .lastArtifactBuildNum(LAST_ARTIFACT_BUILD_1_NUM)
                                                .lastWorkflowExecutionId(LAST_WORKFLOW_EXECUTION_1_ID)
                                                .lastWorkflowExecutionName(LAST_WORKFLOW_EXECUTION_1_NAME)
                                                .infraMappingId(INFRA_MAPPING_1_ID)
                                                .infraMappingName(INFRA_MAPPING_1_NAME)
                                                .build());
    Map<String, List<ServiceInfoResponseSummary>> ExpectedEnvInfo2 = new HashMap<>();
    ExpectedEnvInfo2.put(ENV_7_ID, ExpectedServiceResponseInfoSummary2);
    ExpectedEnvInfo2.put(ENV_8_ID, new ArrayList<>());
    CompareEnvironmentAggregationResponseInfo ExpectedCompareEnvironmentAggregationResponseInfo2 =
        CompareEnvironmentAggregationResponseInfo.builder()
            .serviceId(SERVICE_9_ID)
            .serviceName(SERVICE_9_NAME)
            .count("1")
            .envInfo(ExpectedEnvInfo2)
            .build();
    assertThat(compareEnvironmentAggregationResponseInfos).hasSize(2);
    CompareEnvironmentAggregationResponseInfo compareEnvironmentAggregationResponseInfo1 =
        compareEnvironmentAggregationResponseInfos.get(0);
    assertThat(compareEnvironmentAggregationResponseInfo1)
        .isEqualTo(ExpectedCompareEnvironmentAggregationResponseInfo1);
    CompareEnvironmentAggregationResponseInfo compareEnvironmentAggregationResponseInfo2 =
        compareEnvironmentAggregationResponseInfos.get(1);
    assertThat(compareEnvironmentAggregationResponseInfo2)
        .isEqualTo(ExpectedCompareEnvironmentAggregationResponseInfo2);

    PageResponse<CompareEnvironmentAggregationResponseInfo> compareEnvironmentAggregationResponseInfos1 =
        dashboardService.getCompareServicesByEnvironment(ACCOUNT_3_ID, APP_6_ID, ENV_8_ID, ENV_9_ID, 0, 5);
    List<ServiceInfoResponseSummary> ExpectedServiceResponseInfoSummary3 = new ArrayList<>();
    ExpectedServiceResponseInfoSummary3.add(ServiceInfoResponseSummary.builder()
                                                .lastArtifactBuildNum(LAST_ARTIFACT_BUILD_2_NUM)
                                                .lastWorkflowExecutionId(LAST_WORKFLOW_EXECUTION_2_ID)
                                                .lastWorkflowExecutionName(LAST_WORKFLOW_EXECUTION_2_NAME)
                                                .infraMappingId(INFRA_MAPPING_2_ID)
                                                .infraMappingName(INFRA_MAPPING_2_NAME)
                                                .build());
    List<ServiceInfoResponseSummary> ExpectedServiceResponseInfoSummary4 = new ArrayList<>();
    ExpectedServiceResponseInfoSummary4.add(ServiceInfoResponseSummary.builder()
                                                .lastArtifactBuildNum(LAST_ARTIFACT_BUILD_3_NUM)
                                                .lastWorkflowExecutionId(LAST_WORKFLOW_EXECUTION_2_ID)
                                                .lastWorkflowExecutionName(LAST_WORKFLOW_EXECUTION_2_NAME)
                                                .infraMappingId(INFRA_MAPPING_2_ID)
                                                .infraMappingName(INFRA_MAPPING_2_NAME)
                                                .build());
    Map<String, List<ServiceInfoResponseSummary>> ExpectedEnvInfo3 = new HashMap<>();
    ExpectedEnvInfo3.put(ENV_8_ID, ExpectedServiceResponseInfoSummary3);
    ExpectedEnvInfo3.put(ENV_9_ID, ExpectedServiceResponseInfoSummary4);
    CompareEnvironmentAggregationResponseInfo ExpectedCompareEnvironmentAggregationResponseInfo3 =
        CompareEnvironmentAggregationResponseInfo.builder()
            .serviceId(SERVICE_10_ID)
            .serviceName(SERVICE_10_NAME)
            .count("2")
            .envInfo(ExpectedEnvInfo3)
            .build();
    assertThat(compareEnvironmentAggregationResponseInfos1).hasSize(1);
    CompareEnvironmentAggregationResponseInfo compareEnvironmentAggregationResponseInfo3 =
        compareEnvironmentAggregationResponseInfos1.get(0);
    assertThat(compareEnvironmentAggregationResponseInfo3)
        .isEqualTo(ExpectedCompareEnvironmentAggregationResponseInfo3);

    PageResponse<CompareEnvironmentAggregationResponseInfo> compareEnvironmentAggregationResponseInfos2 =
        dashboardService.getCompareServicesByEnvironment(ACCOUNT_3_ID, APP_6_ID, ENV_9_ID, ENV_10_ID, 0, 5);
    assertThat(compareEnvironmentAggregationResponseInfos2).hasSize(5);

    PageResponse<CompareEnvironmentAggregationResponseInfo> compareEnvironmentAggregationResponseInfos3 =
        dashboardService.getCompareServicesByEnvironment(ACCOUNT_3_ID, APP_6_ID, ENV_9_ID, ENV_10_ID, 5, 5);
    assertThat(compareEnvironmentAggregationResponseInfos3).hasSize(1);
  }

  @Test
  @Owner(developers = ALEXANDRU_CIOFU)
  @Category(UnitTests.class)
  public void testLastWorkflowExecutionDate() {
    Long startTS = 1630969310005L;
    Long deployedAt = 1630969317105L;
    WorkflowExecution workflowExecution = createWorkflowExecution(WORKFLOW_EXECUTION_ID, startTS);
    persistence.save(workflowExecution);
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());

    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());

    instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID);
    persistence.save(instance);
    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<CurrentActiveInstances> activeInstances =
        dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);
    assertThat(activeInstances).hasSize(1);
    assertThat(activeInstances.get(0).getLastWorkflowExecutionDate().getTime()).isEqualTo(startTS.longValue());

    WorkflowExecution latestWFExecution = createWorkflowExecution(WORKFLOW_EXECUTION_ID + "ABC", deployedAt);
    persistence.save(latestWFExecution);
    doReturn(latestWFExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
    instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID + "ABC");
    instance.setLastDeployedAt(deployedAt);
    persistence.save(instance);
    dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    activeInstances = dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);
    assertThat(activeInstances).hasSize(1);
    assertThat(activeInstances.get(0).getLastWorkflowExecutionDate().getTime()).isEqualTo(deployedAt.longValue());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testLastDeployedAtTimestampGivenThatLatestExecutionFailed() {
    long someTime = 1630969310005L;
    long someTimeLater = 1630969317105L;

    WorkflowExecution oldSuccessfulExecution = createWorkflowExecution(WORKFLOW_EXECUTION_ID + "_old", someTime);
    WorkflowExecution latestFailedExecution = createWorkflowExecution(WORKFLOW_EXECUTION_ID + "_new", someTimeLater);

    persistence.save(oldSuccessfulExecution);
    persistence.save(latestFailedExecution);

    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());

    instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID + "_new");
    persistence.save(instance);
    doReturn(oldSuccessfulExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());

    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<CurrentActiveInstances> activeInstances =
        dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);
    assertThat(activeInstances).hasSize(1);
    assertThat(activeInstances.get(0).getLastWorkflowExecutionDate().getTime()).isEqualTo(someTime);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testLastDeployedAtTimestampGivenRetentionPolicy() {
    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);
    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());

    instance.setLastWorkflowExecutionId(WORKFLOW_EXECUTION_ID + "_new");
    persistence.save(instance);

    DashboardStatisticsServiceImpl dashboardStatisticsService = (DashboardStatisticsServiceImpl) dashboardService;
    List<CurrentActiveInstances> activeInstances =
        dashboardStatisticsService.getCurrentActiveInstances(ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID);

    verify(workflowExecutionService, never())
        .getLastSuccessfulWorkflowExecution(any(), any(), any(), any(), any(), any());
    assertThat(activeInstances).hasSize(1);
    assertThat(activeInstances.get(0).getLastWorkflowExecutionDate()).isNull();
  }

  private WorkflowExecution createWorkflowExecution(String uuid, long startTime) {
    return WorkflowExecution.builder()
        .appId(APP_1_ID)
        .status(ExecutionStatus.SUCCESS)
        .envIds(asList(ENV_1_ID))
        .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
        .infraMappingIds(asList(INFRA_MAPPING_1_ID, INFRA_MAPPING_2_ID))
        .workflowId(WORKFLOW_ID)
        .uuid(uuid)
        .name(WORKFLOW_NAME)
        .startTs(startTime)
        .build();
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldReturnActiveInstancesWithSuccessDeployWhenLastWorkflowExecutionIsSuccess() {
    long someTime = 1630969310005L;
    long someTimeLater = 1630969317105L;

    Artifact art = Artifact.Builder.anArtifact()
                       .withBuildNo(BUILD_NO)
                       .withUuid(ARTIFACT_ID)
                       .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                       .withDisplayName(ARTIFACTS_NAME)
                       .withArtifactStreamType("ARTIFACT")
                       .build();

    WorkflowExecution oldSuccessfulExecution =
        createWorkflowExecutionWithArtifacts(WORKFLOW_EXECUTION_ID + "_old", someTime, ExecutionStatus.SUCCESS, art);
    WorkflowExecution latestSuccessfulExecution = createWorkflowExecutionWithArtifacts(
        WORKFLOW_EXECUTION_ID + "_new", someTimeLater, ExecutionStatus.SUCCESS, art);

    ArtifactInfo artInfo = createArtifactInfo(art.getUuid(), art.getDisplayName(), art.getBuildNo(),
        art.getArtifactStreamId(), art.getArtifactSourceName(), latestSuccessfulExecution.getUuid());
    EnvInfo envInfo = createEnvInfo(ENV_1_ID, ENV_NAME, ENV_TYPE);

    StateExecutionInstance stEI = StateExecutionInstance.Builder.aStateExecutionInstance()
                                      .status(ExecutionStatus.SUCCESS)
                                      .stateType(PHASE)
                                      .executionUuid(WORKFLOW_EXECUTION_ID + "_new")
                                      .rollback(true)
                                      .build();

    persistence.save(oldSuccessfulExecution);
    persistence.save(latestSuccessfulExecution);
    persistence.save(stEI);

    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);

    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());

    persistence.save(instance);

    AggregationInfo aggInfo = createAggregationInfo(ENV_1_ID, art.getUuid(), envInfo, artInfo);

    doReturn(oldSuccessfulExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(
            ACCOUNT_1_ID, APP_1_ID, LAST_WORKFLOW_EXECUTION_1_ID, ENV_1_ID, SERVICE_1_ID, INFRA_MAPPING_1_ID);

    doReturn(latestSuccessfulExecution)
        .when(workflowExecutionService)
        .getLastWorkflowExecution(
            ACCOUNT_1_ID, APP_1_ID, LAST_WORKFLOW_EXECUTION_1_ID, ENV_1_ID, SERVICE_1_ID, INFRA_MAPPING_1_ID);
    List<CurrentActiveInstances> activeList = dashboardStatisticsService.constructCurrentActiveInstances(
        List.of(aggInfo), APP_1_ID, ACCOUNT_1_ID, SERVICE_1_ID);

    assertThat(activeList).hasSize(1);
    assertThat(activeList.get(0).getArtifact().getName()).isEqualTo(art.getDisplayName());
    assertThat(activeList.get(0).getArtifact().getId()).isEqualTo(art.getUuid());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldReturnActiveInstancesWithSuccessDeployAndFailedRollbackWhenLastWorkflowExecutionFailed() {
    long someTime = 1630969310005L;
    long someTimeLater = 1630969317105L;

    Artifact art = Artifact.Builder.anArtifact()
                       .withBuildNo(BUILD_NO)
                       .withUuid(ARTIFACT_ID)
                       .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                       .withDisplayName(ARTIFACTS_NAME)
                       .withArtifactStreamType("ARTIFACT")
                       .build();

    WorkflowExecution oldSuccessfulExecution =
        createWorkflowExecutionWithArtifacts(WORKFLOW_EXECUTION_ID + "_old", someTime, ExecutionStatus.SUCCESS, art);
    art.setUuid(ARTIFACT_ID + "new");
    WorkflowExecution latestFailedExecution = createWorkflowExecutionWithArtifacts(
        WORKFLOW_EXECUTION_ID + "_new", someTimeLater, ExecutionStatus.FAILED, art);

    ArtifactInfo artInfo = createArtifactInfo(art.getUuid(), art.getDisplayName(), art.getBuildNo(),
        art.getArtifactStreamId(), art.getArtifactSourceName(), oldSuccessfulExecution.getUuid());
    EnvInfo envInfo = createEnvInfo(ENV_1_ID, ENV_NAME, ENV_TYPE);

    StateExecutionInstance stEI = StateExecutionInstance.Builder.aStateExecutionInstance()
                                      .status(ExecutionStatus.FAILED)
                                      .stateType(PHASE)
                                      .executionUuid(WORKFLOW_EXECUTION_ID + "_new")
                                      .rollback(true)
                                      .build();

    persistence.save(oldSuccessfulExecution);
    persistence.save(latestFailedExecution);
    persistence.save(stEI);

    Instance instance = buildInstance(INSTANCE_1_ID, ACCOUNT_1_ID, APP_1_ID, SERVICE_1_ID, ENV_1_ID, INFRA_MAPPING_1_ID,
        INFRA_MAPPING_1_NAME, CONTAINER_1_ID, currentTime);

    instance.setInstanceInfo(
        K8sPodInfo.builder()
            .helmChartInfo(HelmChartInfo.builder().name(CHART_NAME).repoUrl(REPO_URL).version("1").build())
            .build());

    persistence.save(instance);

    AggregationInfo aggInfo = createAggregationInfo(ENV_1_ID, art.getUuid(), envInfo, artInfo);

    doReturn(oldSuccessfulExecution)
        .when(workflowExecutionService)
        .getLastSuccessfulWorkflowExecution(
            ACCOUNT_1_ID, APP_1_ID, LAST_WORKFLOW_EXECUTION_1_ID, ENV_1_ID, SERVICE_1_ID, INFRA_MAPPING_1_ID);

    doReturn(latestFailedExecution)
        .when(workflowExecutionService)
        .getLastWorkflowExecution(
            ACCOUNT_1_ID, APP_1_ID, LAST_WORKFLOW_EXECUTION_1_ID, ENV_1_ID, SERVICE_1_ID, INFRA_MAPPING_1_ID);
    List<CurrentActiveInstances> activeList = dashboardStatisticsService.constructCurrentActiveInstances(
        List.of(aggInfo), APP_1_ID, ACCOUNT_1_ID, SERVICE_1_ID);

    assertThat(activeList).hasSize(1);
    assertThat(activeList.get(0).getArtifact().getId()).isEqualTo(art.getUuid());
  }

  private EnvInfo createEnvInfo(String envId, String envName, String envType) {
    EnvInfo envInfo = new EnvInfo();
    envInfo.setId(envId);
    envInfo.setName(envName);
    envInfo.setType(envType);
    return envInfo;
  }

  private ArtifactInfo createArtifactInfo(
      String artId, String artName, String artBuildNo, String artStreamId, String artSourceName, String artLastWEId) {
    ArtifactInfo artInfo = new ArtifactInfo();
    artInfo.setId(artId);
    artInfo.setName(artName);
    artInfo.setBuildNo(artBuildNo);
    artInfo.setStreamId(artStreamId);
    artInfo.setDeployedAt(1630969310005L);
    artInfo.setSourceName(artSourceName);
    artInfo.setLastWorkflowExecutionId(artLastWEId);
    return artInfo;
  }

  private AggregationInfo createAggregationInfo(
      String envId, String lastArtId, EnvInfo envInfo, ArtifactInfo artifactInfo) {
    AggregationInfo.ID aggId = new AggregationInfo.ID();
    aggId.setServiceId(null);
    aggId.setEnvId(envId);
    aggId.setLastArtifactId(lastArtId);

    AggregationInfo aggInfo = new AggregationInfo();
    aggInfo.set_id(aggId);
    aggInfo.setCount(1);
    aggInfo.setAppInfo(EntitySummary.builder().build());
    aggInfo.setInfraMappingInfo(EntitySummary.builder().id(INFRA_MAPPING_1_ID).build());
    aggInfo.setEnvInfo(envInfo);
    aggInfo.setArtifactInfo(artifactInfo);

    return aggInfo;
  }

  private WorkflowExecution createWorkflowExecutionWithArtifacts(
      String uuid, long startTime, ExecutionStatus status, Artifact artifact) {
    return WorkflowExecution.builder()
        .appId(APP_1_ID)
        .status(status)
        .envIds(asList(ENV_1_ID))
        .artifacts(List.of(artifact))
        .serviceIds(asList(SERVICE_1_ID, SERVICE_2_ID))
        .infraMappingIds(asList(INFRA_MAPPING_1_ID, INFRA_MAPPING_2_ID))
        .workflowId(LAST_WORKFLOW_EXECUTION_1_ID)
        .uuid(uuid)
        .name(LAST_WORKFLOW_EXECUTION_1_NAME)
        .startTs(startTime)
        .build();
  }
}
