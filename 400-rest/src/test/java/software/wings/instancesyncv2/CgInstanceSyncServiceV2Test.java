/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.api.PcfDeploymentInfo;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2DeploymentHelperFactory;
import software.wings.instancesyncv2.handler.K8sInstanceSyncV2DeploymentHelperCg;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.instance.ContainerInstanceHandler;
import software.wings.service.impl.instance.InstanceHandlerFactoryService;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskService;
import software.wings.service.impl.instance.Status;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingVariableTypes;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CgInstanceSyncServiceV2Test extends CategoryTest {
  @Mock InfrastructureMapping infrastructureMapping = new DirectKubernetesInfrastructureMapping();

  @Mock private K8sInstanceSyncV2DeploymentHelperCg k8sHandler;

  @InjectMocks CgInstanceSyncServiceV2 cgInstanceSyncServiceV2;
  @Mock CgInstanceSyncV2DeploymentHelperFactory handlerFactory;
  @Mock private CgInstanceSyncTaskDetailsService taskDetailsService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsServiceImpl cloudProviderService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private InstanceService instanceService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AcquiredLock acquiredLock;
  @Mock private ContainerInstanceHandler containerInstanceHandler;
  @Mock private InstanceHandlerFactoryService instanceHandlerFactory;
  @Mock private DeploymentService deploymentService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Mock private DelegateTaskService delegateTaskService;

  @Before
  public void setup() {
    doReturn(acquiredLock).when(persistentLocker).waitToAcquireLock(any(), any(), any(), any());
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doReturn(acquiredLock)
        .when(persistentLocker)
        .tryToAcquireLock(eq(InfrastructureMapping.class), any(), eq(Duration.ofSeconds(180)));
    doReturn(containerInstanceHandler).when(instanceHandlerFactory).getInstanceHandler(any());
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSyncException() {
    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder()
            .deploymentSummaries(Collections.singletonList(DeploymentSummary.builder()
                                                               .appId("appId")
                                                               .infraMappingId("infraMappingId")
                                                               .accountId("accountId")
                                                               .k8sDeploymentKey(K8sDeploymentKey.builder().build())
                                                               .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                   .releaseName("releaseName")
                                                                                   .namespace("namespace")
                                                                                   .clusterName("clusterName")
                                                                                   .build())
                                                               .build()))
            .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    infraMapping.setComputeProviderType(String.valueOf(KUBERNETES_CLUSTER));
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(null).when(taskDetailsService).getForInfraMapping(anyString(), anyString());

    doReturn(false).when(delegateTaskService).isTaskTypeSupportedByAllDelegates(anyString(), anyString());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .cloudProviderId("cpID")
                 .build())
        .when(taskDetailsService)
        .fetchForCloudProvider(anyString(), anyString());

    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));

    assertThatThrownBy(() -> cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSync() {
    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder()
            .deploymentSummaries(Collections.singletonList(DeploymentSummary.builder()
                                                               .appId("appId")
                                                               .infraMappingId("infraMappingId")
                                                               .accountId("accountId")
                                                               .k8sDeploymentKey(K8sDeploymentKey.builder().build())
                                                               .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                   .releaseName("releaseName")
                                                                                   .namespace("namespace")
                                                                                   .clusterName("clusterName")
                                                                                   .build())
                                                               .build()))
            .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    infraMapping.setComputeProviderType(String.valueOf(KUBERNETES_CLUSTER));
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .cloudProviderId("cpID")
                 .build())
        .when(taskDetailsService)
        .getForInfraMapping(anyString(), anyString());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .cloudProviderId("cpID")
                 .build())
        .when(taskDetailsService)
        .fetchForCloudProvider(anyString(), anyString());

    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);

    verify(perpetualTaskService, times(1)).resetTask(any(), captor.capture(), any());

    String perpetualTaskId = captor.getValue();
    assertThat(perpetualTaskId).isEqualTo("perpetualTaskId");
  }

  @Rule public ExpectedException expectedEx = ExpectedException.none();
  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSyncNegativeCase() {
    // unsupported Deployment info
    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder()
            .deploymentSummaries(Collections.singletonList(DeploymentSummary.builder()
                                                               .appId("appId")
                                                               .infraMappingId("infraMappingId")
                                                               .accountId("accountId")
                                                               .deploymentInfo(PcfDeploymentInfo.builder().build())
                                                               .build()))
            .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    infraMapping.setComputeProviderType(String.valueOf(KUBERNETES_CLUSTER));
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .cloudProviderId("cpID")
                 .build())
        .when(taskDetailsService)
        .getForInfraMapping(anyString(), anyString());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .cloudProviderId("cpID")
                 .build())
        .when(taskDetailsService)
        .fetchForCloudProvider(anyString(), anyString());

    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));
    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchTaskDetails() {
    doReturn(Arrays.asList(InstanceSyncTaskDetails.builder()
                               .perpetualTaskId("perpetualTaskId")
                               .accountId("accountId")
                               .appId("appId")
                               .cloudProviderId("cpID")
                               .build()))
        .when(taskDetailsService)
        .fetchAllForPerpetualTask(anyString(), anyString());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));

    InstanceSyncTrackedDeploymentDetails instanceSyncTrackedDeploymentDetails =
        cgInstanceSyncServiceV2.fetchTaskDetails("perpetualTaskId", "accountId");

    assertThat(instanceSyncTrackedDeploymentDetails.getPerpetualTaskId()).isEqualTo("perpetualTaskId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResult() {
    InstanceSyncData instanceSyncData = InstanceSyncData.newBuilder()
                                            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                            .setTaskDetailsId("taskId")
                                            .build();

    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("taskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                 .setAccountId("accountId");

    builder.addInstanceData(instanceSyncData);
    Set<CgReleaseIdentifiers> newIdentifiers = Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                                         .releaseName("releaseName")
                                                                         .clusterName("clusterName")
                                                                         .namespace("namespace")
                                                                         .isHelmDeployment(false)
                                                                         .build());
    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());
    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .infraMappingId("infraMappingId")
                 .releaseIdentifiers(newIdentifiers)
                 .cloudProviderId("cpId")
                 .build())
        .when(taskDetailsService)
        .getForId(anyString());

    doReturn(Arrays.asList(InstanceSyncTaskDetails.builder()
                               .perpetualTaskId("perpetualTaskId")
                               .uuid("taskId")
                               .accountId("accountId")
                               .appId("appId")
                               .lastSuccessfulRun(System.currentTimeMillis())
                               .infraMappingId("infraMappingId")
                               .releaseIdentifiers(newIdentifiers)
                               .cloudProviderId("cpId")
                               .build()))
        .when(taskDetailsService)
        .fetchAllForPerpetualTask(anyString(), anyString());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());
    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));
    doReturn(Status.builder().success(true).build()).when(containerInstanceHandler).getStatus(any(), any());
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", builder.build());
    verify(taskDetailsService, times(1)).updateLastRun(captor.capture(), any(), any());
    assertThat(captor.getValue()).isEqualTo("taskId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResultCleanUp() {
    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("taskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SKIPPED.name())
                                                 .setAccountId("accountId");

    doReturn(false).when(taskDetailsService).isInstanceSyncTaskDetailsExist(anyString(), anyString());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());
    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(k8sHandler).when(handlerFactory).getHelper(any(SettingVariableTypes.class));
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", builder.build());
    verify(perpetualTaskService, times(1)).deleteTask(any(String.class), captor.capture());
    assertThat(captor.getValue()).isEqualTo("perpetualTaskId");
  }
}
