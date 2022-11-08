/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
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
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.handler.K8sInstanceSyncV2HandlerCg;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingVariableTypes;

import java.util.Arrays;
import java.util.Collections;
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

  @Mock private InstanceService instanceService;

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
                                                               .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                   .releaseName("releaseName")
                                                                                   .namespace("namespace")
                                                                                   .clusterName("clusterName")
                                                                                   .build())
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
            .deploymentSummaries(Collections.singletonList(DeploymentSummary.builder()
                                                               .appId("appId")
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
    verify(taskDetailsService, times(1)).updateLastRun(captor.capture());
    assertThat(captor.getValue()).isEqualTo("taskId");
  }
}
