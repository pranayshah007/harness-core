/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.perpetualtask.PerpetualTaskType.K8S_WATCH;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskClientParams;
import io.harness.perpetualtask.k8s.watch.K8WatchPerpetualTaskClientParams;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class CEPerpetualTaskManagerTest extends CategoryTest {
  private String clusterRecordId = "clusterId";
  private String clusterName = "clusterName";
  private String region = "region";
  private String cloudProviderId = "CLOUD_PROVIDER_ID";

  private String accountId = "ACCOUNT_ID";
  private static final String COMPANY_NAME = "Harness";
  private static final String ACCOUNT_NAME = "Harness";
  private Account account;

  public static final String username = "dummyUsername";
  public static final String password = "dummyPassword";
  private SettingAttribute cloudProvider;

  private Cluster k8sCluster;
  private Cluster ecsCluster;
  private ClusterRecord clusterRecord;
  private ClusterRecord ecsClusterRecord;

  private String podTaskId = "POD_WATCHER_TASK_ID";
  private String nodeTaskId = "NODE_WATCHER_TASK_ID";
  private String taskId = "TASK_ID";
  PageResponse<ClusterRecord> response;

  @Mock private ClusterRecordService clusterRecordService;
  @Mock private PerpetualTaskService perpetualTaskService;

  @InjectMocks CEPerpetualTaskManager manager;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    account = anAccount()
                  .withUuid(accountId)
                  .withCompanyName(COMPANY_NAME)
                  .withAccountName(ACCOUNT_NAME)
                  .withAccountKey("ACCOUNT_KEY")
                  .withCloudCostEnabled(true)
                  .build();

    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder().accountId(accountId).build();
    cloudProvider = aSettingAttribute()
                        .withCategory(SettingCategory.CLOUD_PROVIDER)
                        .withUuid(cloudProviderId)
                        .withAccountId(accountId)
                        .withValue(kubernetesClusterConfig)
                        .build();

    k8sCluster = DirectKubernetesCluster.builder().clusterName(clusterName).cloudProviderId(cloudProviderId).build();
    ecsCluster = EcsCluster.builder().clusterName(clusterName).cloudProviderId(cloudProviderId).region(region).build();
    clusterRecord = ClusterRecord.builder().uuid(clusterRecordId).accountId(accountId).cluster(k8sCluster).build();
    ecsClusterRecord = ClusterRecord.builder().uuid(clusterRecordId).accountId(accountId).cluster(ecsCluster).build();

    response = aPageResponse().withResponse(Arrays.asList(clusterRecord)).build();
    when(clusterRecordService.list(eq(accountId), eq(null), eq(cloudProviderId))).thenReturn(response);
    when(clusterRecordService.attachPerpetualTaskId(eq(clusterRecord), anyString())).thenReturn(clusterRecord);
    when(clusterRecordService.removePerpetualTaskId(isA(ClusterRecord.class), anyString())).thenReturn(clusterRecord);
    when(perpetualTaskService.getPerpetualTaskType(anyString())).thenReturn(K8S_WATCH);
    when(perpetualTaskService.createTask(eq(PerpetualTaskType.ECS_CLUSTER), eq(accountId),
             isA(PerpetualTaskClientContext.class), isA(PerpetualTaskSchedule.class), eq(false), eq("")))
        .thenReturn(taskId);
    when(perpetualTaskService.createTask(eq(PerpetualTaskType.K8S_WATCH), eq(accountId),
             isA(PerpetualTaskClientContext.class), isA(PerpetualTaskSchedule.class), eq(false), eq("")))
        .thenReturn(taskId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldCreateEcsTask() {
    EcsPerpetualTaskClientParams params =
        new EcsPerpetualTaskClientParams("region", cloudProviderId, "clusterName", "clusterId");
    manager.createEcsTask(accountId, params);
    verify(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.ECS_CLUSTER), eq(accountId), isA(PerpetualTaskClientContext.class),
            isA(PerpetualTaskSchedule.class), eq(false), eq(""));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreateK8WatchTask() {
    K8WatchPerpetualTaskClientParams params =
        new K8WatchPerpetualTaskClientParams(cloudProviderId, "clusterId", "clusterName");
    manager.createK8WatchTask(accountId, params);
    verify(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.K8S_WATCH), eq(accountId), isA(PerpetualTaskClientContext.class),
            isA(PerpetualTaskSchedule.class), eq(false), eq(""));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreatePerpetualTasksForAccount() {
    when(clusterRecordService.list(eq(accountId), eq(DIRECT_KUBERNETES))).thenReturn(Arrays.asList(clusterRecord));
    manager.createPerpetualTasks(account, DIRECT_KUBERNETES);
    verify(clusterRecordService, times(1)).attachPerpetualTaskId(eq(clusterRecord), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTasksForAccount() {
    String[] tasks = {podTaskId, nodeTaskId};
    clusterRecord.setPerpetualTaskIds(tasks);
    when(clusterRecordService.list(eq(accountId), eq(DIRECT_KUBERNETES))).thenReturn(Arrays.asList(clusterRecord));
    manager.deletePerpetualTasks(account, DIRECT_KUBERNETES);
    verify(clusterRecordService, times(2)).removePerpetualTaskId(eq(clusterRecord), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreatePerpetualTasksForCloudProvider() {
    manager.createPerpetualTasks(cloudProvider);
    verify(clusterRecordService, times(1)).attachPerpetualTaskId(eq(clusterRecord), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldResetPerpetualTasksForCloudProvider() {
    manager.createPerpetualTasks(cloudProvider);
    String[] tasks = {podTaskId, nodeTaskId};
    clusterRecord.setPerpetualTaskIds(tasks);
    manager.resetPerpetualTasks(cloudProvider);
    verify(perpetualTaskService).resetTask(accountId, podTaskId, null);
    verify(perpetualTaskService).resetTask(accountId, nodeTaskId, null);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTasksForCloudProvider() {
    String[] tasks = {podTaskId, nodeTaskId};
    clusterRecord.setPerpetualTaskIds(tasks);
    when(clusterRecordService.list(eq(accountId), eq(null), eq(cloudProviderId)))
        .thenReturn(new ArrayList<>(Arrays.asList(clusterRecord)));
    manager.deletePerpetualTasks(cloudProvider);
    verify(clusterRecordService, times(2)).removePerpetualTaskId(isA(ClusterRecord.class), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreatePerpetualTasksForCluster() {
    manager.createPerpetualTasks(clusterRecord);
    verify(clusterRecordService, times(1)).attachPerpetualTaskId(eq(clusterRecord), anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldCreatePerpetualTasksForECSCluster() {
    manager.createPerpetualTasks(ecsClusterRecord);
    verify(clusterRecordService, times(1)).attachPerpetualTaskId(eq(ecsClusterRecord), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldResetPerpetualTasksForCluster() {
    manager.createPerpetualTasks(clusterRecord);
    String[] tasks = {podTaskId, nodeTaskId};
    clusterRecord.setPerpetualTaskIds(tasks);
    manager.resetPerpetualTasks(clusterRecord);
    verify(perpetualTaskService).resetTask(accountId, podTaskId, null);
    verify(perpetualTaskService).resetTask(accountId, nodeTaskId, null);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskForCluster() {
    manager.createPerpetualTasks(clusterRecord);
    manager.deletePerpetualTasks(clusterRecord);
    verify(clusterRecordService, never()).removePerpetualTaskId(isA(ClusterRecord.class), anyString());

    manager.createPerpetualTasks(clusterRecord);
    String[] tasks = {podTaskId, nodeTaskId};
    clusterRecord.setPerpetualTaskIds(tasks);
    manager.deletePerpetualTasks(clusterRecord);
    verify(clusterRecordService, times(2)).removePerpetualTaskId(isA(ClusterRecord.class), anyString());
  }
}
