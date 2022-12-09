/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.DirectK8sReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.api.PcfDeploymentInfo;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.handler.K8sInstanceSyncV2HandlerCg;
import software.wings.instancesyncv2.model.BasicDeploymentIdentifier;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingVariableTypes;

import com.google.protobuf.Any;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CgInstanceSyncServiceV2Test extends CategoryTest {
  @Mock InfrastructureMapping infrastructureMapping = new DirectKubernetesInfrastructureMapping();

  @Mock private K8sInstanceSyncV2HandlerCg k8sHandler;

  @InjectMocks CgInstanceSyncServiceV2 cgInstanceSyncServiceV2;
  @Mock CgInstanceSyncV2HandlerFactory handlerFactory;
  @Mock private DelegateServiceGrpcClient delegateServiceClient;
  @Mock private CgInstanceSyncTaskDetailsService taskDetailsService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsServiceImpl cloudProviderService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private DeploymentService deploymentService;
  @Mock private InstanceService instanceService;
  @Mock private PersistentLocker persistentLocker;

  @Before
  public void setup() {
    initMocks(this);

    AcquiredLock<?> acquiredLock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireLock(any(), any(), any())).thenReturn(acquiredLock);
    when(persistentLocker.waitToAcquireLock(eq(DeploymentSummary.class), any(), any(), any())).thenReturn(acquiredLock);

    doReturn(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
        .when(k8sHandler)
        .getDeleteReleaseAfter(any(CgK8sReleaseIdentifier.class), any(InstanceSyncData.class));
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSyncRollbackDeployment() {
    DeploymentSummary deploymentSummaryRollback =
        DeploymentSummary.builder()
            .appId("appId")
            .k8sDeploymentKey(K8sDeploymentKey.builder().releaseName("releaseName").releaseNumber(1).build())
            .infraMappingId("infraMappingId")
            .accountId("accountId")
            .deploymentInfo(K8sDeploymentInfo.builder()
                                .releaseName("releaseName")
                                .namespace("namespace")
                                .clusterName("clusterName")
                                .build())
            .build();
    DeploymentSummary prevDeploymentSummary =
        DeploymentSummary.builder()
            .appId("appId")
            .k8sDeploymentKey(K8sDeploymentKey.builder().releaseName("releaseName").releaseNumber(1).build())
            .infraMappingId("infraMappingId")
            .accountId("accountId")
            .deploymentInfo(K8sDeploymentInfo.builder()
                                .releaseName("releaseName")
                                .namespace("namespace")
                                .clusterName("clusterName")
                                .build())
            .build();
    DeploymentEvent deploymentEvent = DeploymentEvent.builder()
                                          .deploymentSummaries(Collections.singletonList(deploymentSummaryRollback))
                                          .isRollback(true)
                                          .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
    doReturn(Optional.of(prevDeploymentSummary)).when(deploymentService).get(any(DeploymentSummary.class));
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

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(true).when(k8sHandler).isDeploymentInfoTypeSupported(any());

    ArgumentCaptor<PerpetualTaskId> captor = ArgumentCaptor.forClass(PerpetualTaskId.class);
    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);

    verify(delegateServiceClient, times(1)).resetPerpetualTask(any(), captor.capture(), any());

    PerpetualTaskId perpetualTaskId = captor.getValue();
    assertThat(perpetualTaskId.getId()).isEqualTo("perpetualTaskId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSync() {
    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder()
            .deploymentSummaries(Collections.singletonList(
                DeploymentSummary.builder()
                    .appId("appId")
                    .k8sDeploymentKey(K8sDeploymentKey.builder().releaseName("releaseName").releaseNumber(1).build())
                    .infraMappingId("infraMappingId")
                    .accountId("accountId")
                    .deploymentInfo(K8sDeploymentInfo.builder()
                                        .releaseName("releaseName")
                                        .namespace("namespace")
                                        .clusterName("clusterName")
                                        .build())
                    .build()))
            .isRollback(false)
            .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
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

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(true).when(k8sHandler).isDeploymentInfoTypeSupported(any());

    ArgumentCaptor<PerpetualTaskId> captor = ArgumentCaptor.forClass(PerpetualTaskId.class);
    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);

    verify(delegateServiceClient, times(1)).resetPerpetualTask(any(), captor.capture(), any());

    PerpetualTaskId perpetualTaskId = captor.getValue();
    assertThat(perpetualTaskId.getId()).isEqualTo("perpetualTaskId");
  }

  @Rule public ExpectedException expectedEx = ExpectedException.none();

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleInstanceSyncNegativeCase() {
    expectedEx.expect(InvalidRequestException.class);
    expectedEx.expectMessage(
        "Instance Sync V2 not enabled for deployment info type: software.wings.api.PcfDeploymentInfo");
    // unsupported Deployment info
    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder()
            .deploymentSummaries(Collections.singletonList(
                DeploymentSummary.builder()
                    .appId("appId")
                    .k8sDeploymentKey(K8sDeploymentKey.builder().releaseName("releaseName").releaseNumber(1).build())
                    .infraMappingId("infraMappingId")
                    .accountId("accountId")
                    .deploymentInfo(PcfDeploymentInfo.builder().build())
                    .build()))
            .build();

    InfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setComputeProviderSettingId("varID");
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

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(false).when(k8sHandler).isDeploymentInfoTypeSupported(any());

    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchTaskDetails() {
    doReturn(asList(InstanceSyncTaskDetails.builder()
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

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(true).when(k8sHandler).isDeploymentInfoTypeSupported(any());

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
                                            .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                                                            .setReleaseName("releaseName")
                                                                            .setNamespace("namespace")
                                                                            .setIsHelm(false)
                                                                            .build()))
                                            .build();

    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("taskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                 .setAccountId("accountId");

    builder.addInstanceData(instanceSyncData);
    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());
    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .releaseIdentifiers(Collections.singleton(
                     CgK8sReleaseIdentifier.builder()
                         .releaseName("releaseName")
                         .namespace("namespace")
                         .deploymentIdentifiers(Collections.singleton(BasicDeploymentIdentifier.builder().build()))
                         .isHelmDeployment(false)
                         .build()))
                 .appId("appId")
                 .cloudProviderId("cpId")
                 .build())
        .when(taskDetailsService)
        .getForId(anyString());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(true).when(k8sHandler).isDeploymentInfoTypeSupported(any());
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", builder.build());
    verify(taskDetailsService, times(1)).updateLastRun(captor.capture(), anySet(), anySet());
    assertThat(captor.getValue()).isEqualTo("taskId");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResultWithCleanup() {
    final InstanceSyncData instanceSyncData1 = createDummyInstanceSyncData("taskId1", "release-1", "default");
    final InstanceSyncData instanceSyncData2 = createDummyInstanceSyncData("taskId1", "release-2", "namespace1");
    final InstanceSyncData instanceSyncData3 = createDummyInstanceSyncData("taskId2", "release-3", "default");

    final CgInstanceSyncResponse instanceSyncResponse = CgInstanceSyncResponse.newBuilder()
                                                            .setPerpetualTaskId("taskId")
                                                            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                            .setAccountId("accountId")
                                                            .addInstanceData(instanceSyncData1)
                                                            .addInstanceData(instanceSyncData2)
                                                            .addInstanceData(instanceSyncData3)
                                                            .build();

    final InstanceSyncTaskDetails taskDetails1 = createDummyInstanceSyncTaskDetails("taskId1",
        new HashSet<>(asList(createDummyReleaseIdentifier("release-1", "default"),
            createDummyReleaseIdentifier("release-2", "namespace1"),
            createDummyReleaseIdentifier("release-x", "default"))));

    final InstanceSyncTaskDetails taskDetails2 = createDummyInstanceSyncTaskDetails("taskId2",
        new HashSet<>(asList(createDummyReleaseIdentifier("release-3", "default"),
            createDummyReleaseIdentifier("release-x", "default"))));

    doReturn(taskDetails1).when(taskDetailsService).getForId("taskId1");
    doReturn(taskDetails2).when(taskDetailsService).getForId("taskId2");
    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());
    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));

    Map<CgReleaseIdentifiers, InstanceSyncData> t1 = new HashMap<>();
    t1.put(createDummyReleaseIdentifier("release-1", "default"), instanceSyncData1);
    t1.put(createDummyReleaseIdentifier("release-2", "namespace1"), instanceSyncData2);
    Map<CgReleaseIdentifiers, InstanceSyncData> t2 = new HashMap<>();
    t2.put(createDummyReleaseIdentifier("release-3", "default"), instanceSyncData3);

    doReturn(t1).when(k8sHandler).getCgReleaseIdentifiersList(Arrays.asList(instanceSyncData1, instanceSyncData2));
    doReturn(t2).when(k8sHandler).getCgReleaseIdentifiersList(Collections.singletonList(instanceSyncData3));

    doReturn(true).when(k8sHandler).isDeploymentInfoTypeSupported(any());
    doReturn(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7))
        .when(k8sHandler)
        .getDeleteReleaseAfter(createDummyReleaseIdentifier("release-1", "default"), instanceSyncData1);
    doReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        .when(k8sHandler)
        .getDeleteReleaseAfter(createDummyReleaseIdentifier("release-2", "namespace1"), instanceSyncData2);
    doReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        .when(k8sHandler)
        .getDeleteReleaseAfter(createDummyReleaseIdentifier("release-3", "default"), instanceSyncData3);

    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", instanceSyncResponse);

    ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Set<CgReleaseIdentifiers>> updateReleasesCaptor = ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<CgReleaseIdentifiers>> deleteReleasesCaptor = ArgumentCaptor.forClass(Set.class);
    verify(taskDetailsService, times(2))
        .updateLastRun(taskIdCaptor.capture(), updateReleasesCaptor.capture(), deleteReleasesCaptor.capture());

    assertThat(taskIdCaptor.getAllValues()).containsExactlyInAnyOrder("taskId1", "taskId2");
    assertThat(updateReleasesCaptor.getAllValues())
        .containsExactlyInAnyOrder(
            Collections.singleton(createDummyReleaseIdentifier("release-1", "default")), Collections.emptySet());
    assertThat(deleteReleasesCaptor.getAllValues())
        .containsExactlyInAnyOrder(Collections.singleton(createDummyReleaseIdentifier("release-2", "namespace1")),
            Collections.singleton(createDummyReleaseIdentifier("release-3", "default")));
  }

  private InstanceSyncData createDummyInstanceSyncData(String taskId, String releaseName, String namespace) {
    return InstanceSyncData.newBuilder()
        .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
        .setTaskDetailsId(taskId)
        .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                        .setReleaseName(releaseName)
                                        .setNamespace(namespace)
                                        .setIsHelm(false)
                                        .build()))
        .build();
  }

  private CgReleaseIdentifiers createDummyReleaseIdentifier(String releaseName, String namespace) {
    return CgK8sReleaseIdentifier.builder()
        .releaseName(releaseName)
        .namespace(namespace)
        .isHelmDeployment(false)
        .deploymentIdentifiers(Collections.singleton(
            BasicDeploymentIdentifier.builder().lastDeploymentSummaryUuid("lastDeploymentSummaryId").build()))
        .build();
  }

  private InstanceSyncTaskDetails createDummyInstanceSyncTaskDetails(
      String taskId, Set<CgReleaseIdentifiers> releaseIdentifiers) {
    return InstanceSyncTaskDetails.builder()
        .perpetualTaskId("perpetualTaskId" + taskId)
        .accountId("accountId")
        .releaseIdentifiers(releaseIdentifiers)
        .appId("appId")
        .cloudProviderId("cpId")
        .build();
  }
}
