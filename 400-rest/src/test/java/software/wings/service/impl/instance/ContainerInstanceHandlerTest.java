/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.HarnessLabelValues.colorBlue;
import static io.harness.k8s.model.HarnessLabelValues.colorGreen;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_ASSIGNED;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_INVALID;
import static io.harness.perpetualtask.PerpetualTaskType.CONTAINER_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.PDC_INSTANCE_SYNC;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.container.Label.Builder.aLabel;
import static software.wings.beans.infrastructure.instance.InstanceType.ECS_CONTAINER_INSTANCE;
import static software.wings.beans.infrastructure.instance.InstanceType.KUBERNETES_CONTAINER_INSTANCE;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ECS_CLUSTER;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.KUBE_CLUSTER;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.GeneralException;
import io.harness.exception.K8sPodSyncException;
import io.harness.exception.runtime.NoInstancesException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AwsCodeDeployDeploymentInfo;
import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.HelmSetupExecutionSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.container.Label;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo.Builder;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
@OwnedBy(CDP)
public class ContainerInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private InstanceService instanceService;
  @Mock private AppService appService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock private ContainerSync containerSync;
  @Mock private DeploymentService deploymentService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PerpetualTaskService perpetualTaskService;
  @InjectMocks @Inject ContainerInstanceHandler containerInstanceHandler;

  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(any());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build()).when(serviceResourceService).getWithDetails(any(), any());
  }

  private InfrastructureMapping getInframapping(String inframappingType) {
    if (inframappingType.equals(InfrastructureMappingType.AWS_ECS.getName())) {
      return EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping()
          .withAppId(APP_ID)
          .withRegion(US_EAST)
          .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
          .withUuid(INFRA_MAPPING_ID)
          .withClusterName(ECS_CLUSTER)
          .withEnvId(ENV_ID)
          .withInfraMappingType(InfrastructureMappingType.AWS_ECS.getName())
          .withServiceId(SERVICE_ID)
          .withUuid(INFRA_MAPPING_ID)
          .withAccountId(ACCOUNT_ID)
          .build();
    } else {
      return GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping()
          .withAppId(APP_ID)
          .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
          .withUuid(INFRA_MAPPING_ID)
          .withClusterName("k")
          .withNamespace("default")
          .withEnvId(ENV_ID)
          .withInfraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
          .withServiceId(SERVICE_ID)
          .withUuid(INFRA_MAPPING_ID)
          .withAccountId(ACCOUNT_ID)
          .build();
    }
  }

  // expected 1 Delete
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstancesDelete_ECS() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.AWS_ECS.name())).when(infraMappingService).get(any(), any());

    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_ECS.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("taskARN:0").build())
            .instanceInfo(Builder.anEcsContainerInfo()
                              .withClusterName("ECSCluster")
                              .withServiceName("service_a_1")
                              .withStartedAt(0)
                              .withStartedBy("user1")
                              .withTaskArn("taskARN:0")
                              .withTaskDefinitionArn("taskDefinitionArn")
                              .build())
            .build());

    ContainerSyncResponse containerSyncResponse = ContainerSyncResponse.builder()
                                                      .commandExecutionStatus(SUCCESS)
                                                      .containerInfoList(Collections.emptyList())
                                                      .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframappingNotRemovedFully(any(), any());
    doReturn(containerSyncResponse).when(containerSync).getInstances(any(), anyList());
    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    assertionsForDelete(INSTANCE_1_ID);
  }

  // expected  1 add
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstancesAdd_ECS() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.AWS_ECS.name())).when(infraMappingService).get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();

    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_ECS.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("taskARN:0").build())
            .lastWorkflowExecutionId("id")
            .instanceInfo(Builder.anEcsContainerInfo()
                              .withClusterName("ECSCluster")
                              .withServiceName("service_a_1")
                              .withStartedAt(0)
                              .withStartedBy("user1")
                              .withTaskArn("taskARN:0")
                              .withTaskDefinitionArn("taskDefinitionArn")
                              .build())
            .build());

    ContainerSyncResponse containerSyncResponse = ContainerSyncResponse.builder()
                                                      .commandExecutionStatus(SUCCESS)
                                                      .containerInfoList(asList(Builder.anEcsContainerInfo()
                                                                                    .withClusterName(ECS_CLUSTER)
                                                                                    .withServiceName("service_a_1")
                                                                                    .withTaskArn("taskARN:0")
                                                                                    .withStartedAt(0)
                                                                                    .withStartedBy("user1")
                                                                                    .build(),
                                                          Builder.anEcsContainerInfo()
                                                              .withClusterName(ECS_CLUSTER)
                                                              .withServiceName("service_a_1")
                                                              .withTaskArn("taskARN:2")
                                                              .withStartedAt(0)
                                                              .withStartedBy("user1")
                                                              .build()))
                                                      .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframappingNotRemovedFully(any(), any());
    doReturn(containerSyncResponse).when(containerSync).getInstances(any(), anyList());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    assertionsForSave("taskARN:2", InstanceType.ECS_CONTAINER_INSTANCE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testSyncInstancesDoNothing7DaysOldDeletedAndAddNew_ECS() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.AWS_ECS.name())).when(infraMappingService).get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();

    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_ECS.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("taskARN:0").build())
            .lastWorkflowExecutionId("id")
            .isDeleted(true)
            .instanceInfo(Builder.anEcsContainerInfo()
                              .withClusterName("ECSCluster")
                              .withServiceName("service_a_1")
                              .withStartedAt(0)
                              .withStartedBy("user1")
                              .withTaskArn("taskARN:0")
                              .withTaskDefinitionArn("taskDefinitionArn")
                              .build())
            .build());

    ContainerSyncResponse containerSyncResponse = ContainerSyncResponse.builder()
                                                      .commandExecutionStatus(SUCCESS)
                                                      .containerInfoList(asList(Builder.anEcsContainerInfo()
                                                                                    .withClusterName(ECS_CLUSTER)
                                                                                    .withServiceName("service_a_1")
                                                                                    .withTaskArn("taskARN:2")
                                                                                    .withStartedAt(0)
                                                                                    .withStartedBy("user1")
                                                                                    .build()))
                                                      .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframappingNotRemovedFully(any(), any());
    doReturn(containerSyncResponse).when(containerSync).getInstances(any(), anyList());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    assertionsForSave("taskARN:2", InstanceType.ECS_CONTAINER_INSTANCE);
    Set<String> instancesToNotDelete = new HashSet<>();
    instancesToNotDelete.add(INSTANCE_1_ID);
    verify(instanceService, never()).delete(eq(instancesToNotDelete));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testNewDeployment_DeleteOlderInstance_ECS() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.AWS_ECS.name())).when(infraMappingService).get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_ECS.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("taskARN:0").build())
            .instanceInfo(Builder.anEcsContainerInfo()
                              .withClusterName("ECSCluster")
                              .withServiceName("service_b_1")
                              .withStartedAt(0)
                              .withStartedBy("user1")
                              .withTaskArn("taskARN:0")
                              .withTaskDefinitionArn("taskDefinitionArn")
                              .build())
            .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframappingNotRemovedFully(any(), any());
    doReturn(ContainerSyncResponse.builder()
                 .commandExecutionStatus(SUCCESS)
                 .containerInfoList(Collections.EMPTY_LIST)
                 .build())
        .when(containerSync)
        .getInstances(any(), anyList());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                              .clusterName(ECS_CLUSTER)
                                              .containerSvcName("service_b_1")
                                              .build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .build()),
        false, onDemandRollbackInfo);
    assertionsForDelete(INSTANCE_1_ID);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testNewDeployment_AddNewInstance_ECS() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.AWS_ECS.name())).when(infraMappingService).get(any(), any());

    doReturn(Collections.emptyList())
        .when(instanceService)
        .getInstancesForAppAndInframappingNotRemovedFully(any(), any());
    doReturn(ContainerSyncResponse.builder()
                 .commandExecutionStatus(SUCCESS)
                 .containerInfoList(asList(Builder.anEcsContainerInfo()
                                               .withClusterName(ECS_CLUSTER)
                                               .withServiceName("service_a_1")
                                               .withTaskArn("taskARN:0")
                                               .withStartedAt(0)
                                               .withStartedBy("user1")
                                               .build()))
                 .build())
        .when(containerSync)
        .getInstances(any(), anyList());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                              .clusterName(ECS_CLUSTER)
                                              .containerSvcName("service_a_1")
                                              .build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .build()),
        false, onDemandRollbackInfo);

    assertionsForSave("taskARN:0", InstanceType.ECS_CONTAINER_INSTANCE);
  }

  private void testSyncInstances(PageResponse<Instance> pageResponse, ContainerSyncResponse containerSyncResponse,
      String containerId, InstanceType instanceType) throws Exception {
    doReturn(pageResponse).when(instanceService).list(any());

    doReturn(ContainerSyncResponse.builder().commandExecutionStatus(SUCCESS).containerInfoList(asList()).build())
        .doReturn(containerSyncResponse)
        .when(containerSync)
        .getInstances(any(), anyList());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    // assertions(containerId, instanceType, false);
  }

  private void assertions(String containerId, String instanceId, InstanceType instanceType, boolean checkSaveOrUpdate)
      throws Exception {
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    // This asserts only 1 instance is deleted
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(INSTANCE_1_ID) || idTobeDeleted.contains(INSTANCE_2_ID)).isTrue();

    if (checkSaveOrUpdate) {
      ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
      verify(instanceService, times(1)).save(captorInstance.capture());

      List<Instance> capturedInstances = captorInstance.getAllValues();
      assertThat(capturedInstances.get(0).getContainerInstanceKey().getContainerId()).isEqualTo(containerId);
      assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(instanceType);
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_DeleteInstance_Kubernetes() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();

    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .podName("pod:0")
                              .build())
            .build());

    ContainerSyncResponse containerSyncResponse = ContainerSyncResponse.builder()
                                                      .commandExecutionStatus(SUCCESS)
                                                      .containerInfoList(Collections.EMPTY_LIST)
                                                      .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(ContainerSyncResponse.builder().commandExecutionStatus(SUCCESS).containerInfoList(asList()).build())
        .doReturn(containerSyncResponse)
        .when(containerSync)
        .getInstances(any(), anyList());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    assertionsForDelete(INSTANCE_1_ID);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_AddInstance_Kubernetes() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").build())
            .lastWorkflowExecutionId("id")
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .podName("pod:0")
                              .build())
            .build());

    ContainerSyncResponse containerSyncResponse = ContainerSyncResponse.builder()
                                                      .commandExecutionStatus(SUCCESS)
                                                      .containerInfoList(asList(KubernetesContainerInfo.builder()
                                                                                    .clusterName(KUBE_CLUSTER)
                                                                                    .controllerName("controllerName:0")
                                                                                    .podName("pod:0")
                                                                                    .serviceName("service_a_0")
                                                                                    .build(),
                                                          KubernetesContainerInfo.builder()
                                                              .clusterName(KUBE_CLUSTER)
                                                              .controllerName("controllerName:1")
                                                              .podName("pod:1")
                                                              .serviceName("service_a_0")
                                                              .build()))
                                                      .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(containerSyncResponse).when(containerSync).getInstances(any(), anyList());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    assertionsForSave("pod:1", KUBERNETES_CONTAINER_INSTANCE);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testNewDeployment_DeleteOldInstances_Kubernetes() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .namespace("default")
                              .podName("pod:0")
                              .build())
            .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(ContainerSyncResponse.builder().commandExecutionStatus(SUCCESS).containerInfoList(asList()).build())
        .when(containerSync)
        .getInstances(any(), anyList());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                              .clusterName(KUBE_CLUSTER)
                                              .containerSvcName("controllerName:0")
                                              .namespace("default")
                                              .build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .build()),
        false, onDemandRollbackInfo);

    assertionsForDelete(INSTANCE_1_ID);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testNewDeployment_AddNewInstance_Kubernetes() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    doReturn(Collections.emptyList()).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(ContainerSyncResponse.builder()
                 .commandExecutionStatus(SUCCESS)
                 .containerInfoList(asList(KubernetesContainerInfo.builder()
                                               .clusterName(KUBE_CLUSTER)
                                               .controllerName("controllerName:0")
                                               .podName("pod:1")
                                               .serviceName("service_a_1")
                                               .namespace("default")
                                               .build()))
                 .build())
        .when(containerSync)
        .getInstances(any(), anyList());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                              .clusterName(KUBE_CLUSTER)
                                              .containerSvcName("controllerName:0")
                                              .namespace("default")
                                              .build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .build()),
        false, onDemandRollbackInfo);

    assertionsForSave("pod:1", KUBERNETES_CONTAINER_INSTANCE);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testNewDeployment_Kubernetes_Rollback() throws Exception {
    doReturn(Optional.of(
                 DeploymentSummary.builder()
                     .deploymentInfo(ContainerDeploymentInfoWithNames.builder().containerSvcName("service_a_1").build())
                     .accountId(ACCOUNT_ID)
                     .infraMappingId(INFRA_MAPPING_ID)
                     .workflowExecutionId("workfloeExecution_1")
                     .stateExecutionInstanceId("stateExecutionInstanceId")
                     .artifactBuildNum("1")
                     .artifactName("old")
                     .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .podName("pod:0")
                              .build())
            .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(ContainerSyncResponse.builder().commandExecutionStatus(SUCCESS).containerInfoList(asList()).build())
        .doReturn(ContainerSyncResponse.builder()
                      .commandExecutionStatus(SUCCESS)
                      .containerInfoList(asList(KubernetesContainerInfo.builder()
                                                    .clusterName(KUBE_CLUSTER)
                                                    .controllerName("controllerName:1")
                                                    .podName("pod:1")
                                                    .serviceName("service_a_1")
                                                    .build()))
                      .build())
        .when(containerSync)
        .getInstances(any(), anyList());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                              .clusterName(KUBE_CLUSTER)
                                              .containerSvcName("controllerName:0")
                                              .build())
                          .accountId(ACCOUNT_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .workflowExecutionId("workfloeExecution_1")
                          .stateExecutionInstanceId("stateExecutionInstanceId")
                          .build(),
            DeploymentSummary.builder()
                .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                    .clusterName(KUBE_CLUSTER)
                                    .containerSvcName("controllerName:1")
                                    .build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        true, onDemandRollbackInfo);

    assertions_rollback("pod:1", KUBERNETES_CONTAINER_INSTANCE, true);
  }

  private void assertionsForDelete(String instanceId) throws Exception {
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    // This asserts only 1 instance is deleted
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(instanceId)).isTrue();
  }

  private void assertionsForNoDelete() {
    verify(instanceService, never()).delete(anySet());
  }

  private void assertions_rollback(String containerId, InstanceType instanceType, boolean checkSaveOrUpdate)
      throws Exception {
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(INSTANCE_1_ID)).isTrue();

    if (checkSaveOrUpdate) {
      ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
      verify(instanceService, times(1)).save(captorInstance.capture());

      List<Instance> capturedInstances = captorInstance.getAllValues();
      assertThat(capturedInstances.get(0).getContainerInstanceKey().getContainerId()).isEqualTo(containerId);
      assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(instanceType);
      assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("old");
      assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    }
  }

  private void assertionsForSave(String containerId, InstanceType instanceType) throws Exception {
    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    assertThat(capturedInstances.get(0).getContainerInstanceKey().getContainerId()).isEqualTo(containerId);
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(instanceType);
  }

  private void assertionsForSaveK8sInstance(String podName, String... imageNames) throws Exception {
    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).saveOrUpdate(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    assertThat(capturedInstances.get(0).getPodInstanceKey().getPodName()).isEqualTo(podName);
    assertThat(((K8sPodInfo) capturedInstances.get(0).getInstanceInfo())
                   .getContainers()
                   .stream()
                   .map(K8sContainerInfo::getImage)
                   .collect(Collectors.toList()))
        .contains(imageNames);
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(KUBERNETES_CONTAINER_INSTANCE);
  }

  private void assertionsForNoSaveInstance() throws Exception {
    verify(instanceService, never()).saveOrUpdate(anyList());
    verify(instanceService, never()).saveOrUpdate(any(Instance.class));
    verify(instanceService, never()).save(any(Instance.class));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNewDeployment_AddNewInstance_K8sV2() throws Exception {
    doReturn(Collections.emptyList()).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    when(k8sStateHelper.fetchPodListForCluster(any(GcpKubernetesInfrastructureMapping.class), any(), any(), any()))
        .thenReturn(asList(K8sPod.builder()
                               .name("podName")
                               .namespace("default")
                               .releaseName("releaseName")
                               .containerList(asList(
                                   K8sContainer.builder().image("image1:version1").containerId("containerId1").build()))
                               .build()));

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    final Map<String, String> metadata = new HashMap<>();
    metadata.put("image", "image1:version1");
    persistence.save(anArtifact()
                         .withUuid(ARTIFACT_ID)
                         .withArtifactStreamId(ARTIFACT_STREAM_ID)
                         .withAppId("app_id")
                         .withMetadata(new ArtifactMetadata(metadata))
                         .build());

    containerInstanceHandler.handleNewDeployment(Arrays.asList(DeploymentSummary.builder()
                                                                   .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                       .namespace("default")
                                                                                       .releaseName("releaseName")
                                                                                       .releaseNumber(1)
                                                                                       .build())
                                                                   .accountId(ACCOUNT_ID)
                                                                   .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                                   .workflowExecutionId("workflowExecution_1")
                                                                   .stateExecutionInstanceId("stateExecutionInstanceId")
                                                                   .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService).saveOrUpdate(captor.capture());

    Instance instance = captor.getValue();
    assertThat(instance.getLastArtifactId()).isEqualTo(ARTIFACT_ID);
    assertThat(instance.getLastArtifactName()).isEqualTo("image1:version1");
    assertThat(instance.getLastArtifactSourceName()).isEqualTo("image1");
    assertThat(instance.getLastArtifactBuildNum()).isEqualTo("version1");

    when(k8sStateHelper.fetchPodListForCluster(any(GcpKubernetesInfrastructureMapping.class), any(), any(), any()))
        .thenReturn(asList(K8sPod.builder()
                               .name("podName")
                               .namespace("default")
                               .releaseName("releaseName")
                               .containerList(asList(
                                   K8sContainer.builder().image("image2:version2").containerId("containerId2").build(),
                                   K8sContainer.builder().image("image1:version1").containerId("containerId1").build()))
                               .build()));
    containerInstanceHandler.handleNewDeployment(Arrays.asList(DeploymentSummary.builder()
                                                                   .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                       .namespace("default")
                                                                                       .releaseName("releaseName")
                                                                                       .releaseNumber(1)
                                                                                       .build())
                                                                   .accountId(ACCOUNT_ID)
                                                                   .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                                   .workflowExecutionId("workflowExecution_1")
                                                                   .stateExecutionInstanceId("stateExecutionInstanceId")
                                                                   .build()),
        false, onDemandRollbackInfo);

    captor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(2)).saveOrUpdate(captor.capture());

    instance = captor.getAllValues().get(1);
    assertThat(instance.getLastArtifactId()).isEqualTo(ARTIFACT_ID);
    assertThat(instance.getLastArtifactName()).isEqualTo("image1:version1");
    assertThat(instance.getLastArtifactSourceName()).isEqualTo("image1");
    assertThat(instance.getLastArtifactBuildNum()).isEqualTo("version1");

    persistence.delete(Artifact.class, ARTIFACT_ID);
    when(k8sStateHelper.fetchPodListForCluster(any(GcpKubernetesInfrastructureMapping.class), any(), any(), any()))
        .thenReturn(asList(K8sPod.builder()
                               .name("podName")
                               .namespace("default")
                               .releaseName("releaseName")
                               .containerList(asList(
                                   K8sContainer.builder().image("image3:version3").containerId("containerId3").build(),
                                   K8sContainer.builder().image("image1:version1").containerId("containerId1").build()))
                               .build()));
    containerInstanceHandler.handleNewDeployment(Arrays.asList(DeploymentSummary.builder()
                                                                   .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                       .namespace("default")
                                                                                       .releaseName("releaseName")
                                                                                       .releaseNumber(1)
                                                                                       .build())
                                                                   .accountId(ACCOUNT_ID)
                                                                   .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                                   .workflowExecutionId("workflowExecution_1")
                                                                   .stateExecutionInstanceId("stateExecutionInstanceId")
                                                                   .build()),
        false, onDemandRollbackInfo);

    captor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).saveOrUpdate(captor.capture());

    instance = captor.getAllValues().get(2);
    assertThat(instance.getLastArtifactName()).isEqualTo("image3:version3");
    assertThat(instance.getLastArtifactSourceName()).isEqualTo("image3");
    assertThat(instance.getLastArtifactBuildNum()).isEqualTo("version3");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testNewDeployment_AddNewInstance_UpdateBuildNum() throws Exception {
    doReturn(Collections.emptyList()).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    when(k8sStateHelper.fetchPodListForCluster(any(GcpKubernetesInfrastructureMapping.class), any(), any(), any()))
        .thenReturn(asList(K8sPod.builder()
                               .name("podName")
                               .namespace("default")
                               .releaseName("releaseName")
                               .containerList(asList(
                                   K8sContainer.builder().image("image1:version1").containerId("containerId1").build()))
                               .build()));

    when(featureFlagService.isEnabled(eq(FeatureName.CDP_UPDATE_INSTANCE_DETAILS_WITH_IMAGE_SUFFIX), eq(ACCOUNT_ID)))
        .thenReturn(true);

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    final Map<String, String> metadata = new HashMap<>();
    metadata.put("buildNo", "version1");
    metadata.put("image", "slightlyDifferent/image1:version1");
    persistence.save(anArtifact()
                         .withUuid(ARTIFACT_ID)
                         .withArtifactStreamId(ARTIFACT_STREAM_ID)
                         .withAppId("app_id")
                         .withMetadata(new ArtifactMetadata(metadata))
                         .build());

    containerInstanceHandler.handleNewDeployment(Arrays.asList(DeploymentSummary.builder()
                                                                   .deploymentInfo(K8sDeploymentInfo.builder()
                                                                                       .namespace("default")
                                                                                       .releaseName("releaseName")
                                                                                       .releaseNumber(1)
                                                                                       .build())
                                                                   .accountId(ACCOUNT_ID)
                                                                   .artifactStreamId(ARTIFACT_STREAM_ID)
                                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                                   .workflowExecutionId("workflowExecution_1")
                                                                   .stateExecutionInstanceId("stateExecutionInstanceId")
                                                                   .artifactBuildNum("version1")
                                                                   .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService).saveOrUpdate(captor.capture());

    Instance instance = captor.getValue();
    assertThat(instance.getLastArtifactId()).isEqualTo(ARTIFACT_ID);
    assertThat(instance.getLastArtifactName()).isEqualTo("image1:version1");
    assertThat(instance.getLastArtifactSourceName()).isEqualTo("image1");
    assertThat(instance.getLastArtifactBuildNum()).isEqualTo("version1");

    persistence.delete(Artifact.class, ARTIFACT_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSetHelmChartVersionToContainerInfo() {
    setHelmChartVersionForHelmDeploymentInfo();
    doNothingIfNullHelmChartInfo();
  }

  private void doNothingIfNullHelmChartInfo() {
    containerInstanceHandler.setHelmChartInfoToContainerInfo(null, null);
  }

  private void setHelmChartVersionForHelmDeploymentInfo() {
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().version("0.1.1").name("harness").build();
    KubernetesContainerInfo k8sInfo = KubernetesContainerInfo.builder().build();
    containerInstanceHandler.setHelmChartInfoToContainerInfo(helmChartInfo, k8sInfo);
    assertThat(k8sInfo.getHelmChartInfo().getVersion()).isEqualTo("0.1.1");
    assertThat(k8sInfo.getHelmChartInfo().getName()).isEqualTo("harness");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getContainerDeploymentInfosWithLabelsForHelm() {
    final HelmSetupExecutionSummary helmSetupExecutionSummary =
        HelmSetupExecutionSummary.builder().namespace("default").releaseName("test").newVersion(1).build();
    final HelmChartInfo helmChartInfo = HelmChartInfo.builder().version("1.1.1").name("harness").build();

    final ContainerDeploymentInfoWithLabels deploymentInfo =
        (ContainerDeploymentInfoWithLabels) containerInstanceHandler.getContainerDeploymentInfosWithLabelsForHelm(
            "harness", helmSetupExecutionSummary.getNamespace(), asList(aLabel().build()), helmSetupExecutionSummary,
            HelmExecutionSummary.builder().helmChartInfo(helmChartInfo).releaseName("r-1").build());
    assertThat(deploymentInfo.getHelmChartInfo().getVersion()).isEqualTo("1.1.1");
    assertThat(deploymentInfo.getHelmChartInfo().getName()).isEqualTo("harness");
    assertThat(deploymentInfo.getClusterName()).isEqualTo("harness");
    assertThat(deploymentInfo.getNamespace()).isEqualTo("default");
    assertThat(deploymentInfo.getNewVersion()).isEqualTo("1");
    assertThat(deploymentInfo.getLabels()).hasSize(1);
    assertThat(deploymentInfo.getReleaseName()).isEqualTo("r-1");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void test_AddNewK8sPod() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final String statefulSetPodName = "harness-statefulset-0";
    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = Collections.emptyList();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name(statefulSetPodName)
                        .podIP("ip-1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:1.1").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("default").releaseName("release-123").build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    assertionsForSaveK8sInstance(statefulSetPodName, "nginx:1.1");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void test_DeleteAndAddStatefulIfImageChanges() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final String statefulSetPodName = "harness-statefulset-0";
    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = buildK8sInstance(statefulSetPodName);

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name(statefulSetPodName)
                        .podIP("ip-1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:1.1").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("default").releaseName("release-123").build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    assertionsForDelete(INSTANCE_1_ID);
    assertionsForSaveK8sInstance(statefulSetPodName, "nginx:1.1");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void test_DeleteAndAddStatefulIfSideCarImageAdded() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final String statefulSetPodName = "harness-statefulset-0";
    PageResponse<Instance> pageResponse = new PageResponse<>();
    final List<Instance> instances = buildK8sInstance(statefulSetPodName);

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name(statefulSetPodName)
                        .podIP("ip-1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:0.1").build(),
                            K8sContainer.builder().image("sidecar").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("default").releaseName("release-123").build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    assertionsForDelete(INSTANCE_1_ID);
    assertionsForSaveK8sInstance(statefulSetPodName, "nginx:0.1", "sidecar");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void test_k8sDeployment_newImage() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final List<Instance> instances = buildK8sInstance("harness-pod-4n42hbh");

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name("harness-pod-n523nk")
                        .podIP("ip-1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:1.1").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("default").releaseName("release-123").build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    assertionsForDelete(INSTANCE_1_ID);
    assertionsForSaveK8sInstance("harness-pod-n523nk", "nginx:1.1");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void test_k8sDeployment_sameImage() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final List<Instance> instances = buildK8sInstance("harness-pod-n523nk");

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name("harness-pod-n523nk")
                        .podIP("ip-1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:0.1").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("default").releaseName("release-123").build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId("workfloeExecution_1")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .build()),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    assertionsForNoDelete();
    assertionsForNoSaveInstance();
  }

  private List<Instance> buildK8sInstance(String podName) {
    return singletonList(buildInstanceWith(podName,
        K8sPodInfo.builder()
            .ip("ip-1")
            .podName(podName)
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .build()));
  }

  private Instance buildInstanceWith(String podName, InstanceInfo instanceInfo) {
    return Instance.builder()
        .uuid(INSTANCE_1_ID)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .computeProviderId(COMPUTE_PROVIDER_NAME)
        .appName(APP_NAME)
        .envId(ENV_ID)
        .envName(ENV_NAME)
        .envType(EnvironmentType.PROD)
        .infraMappingId(INFRA_MAPPING_ID)
        .infraMappingType(InfrastructureMappingType.GCP_KUBERNETES.getName())
        .instanceType(KUBERNETES_CONTAINER_INSTANCE)
        .podInstanceKey(PodInstanceKey.builder().namespace("default").podName(podName).build())
        .containerInstanceKey(ContainerInstanceKey.builder().namespace("default").containerId(podName).build())
        .instanceInfo(instanceInfo)
        .lastWorkflowExecutionName("Current Workflow")
        .build();
  }

  private Instance buildK8sInstanceWithHelmChartInfo(String podName, HelmChartInfo helmChartInfo) {
    return buildInstanceWith(podName,
        K8sPodInfo.builder()
            .ip("ip-1")
            .podName(podName)
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .helmChartInfo(helmChartInfo)
            .build());
  }

  private Instance buildContainerInstanceWithHelmChartInfo(String podName, HelmChartInfo helmChartInfo) {
    return buildInstanceWith(podName,
        KubernetesContainerInfo.builder()
            .clusterName(KUBE_CLUSTER)
            .ip("ip-1")
            .controllerName("controllerName")
            .podName(podName)
            .namespace("default")
            .helmChartInfo(helmChartInfo)
            .build());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_HelmChartDeployment_newHelmChartInfoOldInstances() {
    DeploymentInfo deploymentInfoWithLabels =
        ContainerDeploymentInfoWithLabels.builder()
            .helmChartInfo(helmChartInfoWithVersion("1.1.0"))
            .labels(singletonList(Label.Builder.aLabel().withName("sample").withValue("sample").build()))
            .containerInfoList(asList(ContainerInfo.builder().workloadName("workloadName").build()))
            .build();
    DeploymentInfo deploymentInfoWithNames =
        ContainerDeploymentInfoWithNames.builder().clusterName("clusterName").build();

    List<Instance> instances =
        asList(buildContainerInstanceWithHelmChartInfo("sample-pod-1", helmChartInfoWithVersion("1.0.0")),
            buildContainerInstanceWithHelmChartInfo("sample-pod-2", helmChartInfoWithVersion("1.0.0")));

    test_HelmChartDeployment_newHelmChartInfoOldInstancesWith(deploymentInfoWithLabels, instances,
        asList(helmChartInfoWithVersion("1.1.0"), helmChartInfoWithVersion("1.1.0")));

    test_HelmChartDeployment_newHelmChartInfoOldInstancesWith(deploymentInfoWithNames, instances, emptyList());
  }

  private void test_HelmChartDeployment_newHelmChartInfoOldInstancesWith(
      DeploymentInfo deploymentInfo, List<Instance> instances, List<HelmChartInfo> expectedUpdate) {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();

    reset(instanceService);
    doReturn(Sets.newHashSet("controllerName"))
        .when(containerSync)
        .getControllerNames(any(ContainerInfrastructureMapping.class), anyMap(), any());
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(ContainerSyncResponse.builder()
                 .commandExecutionStatus(SUCCESS)
                 .containerInfoList(instances.stream()
                                        .map(instance
                                            -> KubernetesContainerInfo.builder()
                                                   .podName(instance.getContainerInstanceKey().getContainerId())
                                                   .namespace(instance.getContainerInstanceKey().getNamespace())
                                                   .build())
                                        .collect(Collectors.toList()))
                 .build())
        .when(containerSync)
        .getInstances(any(ContainerInfrastructureMapping.class), anyList());

    containerInstanceHandler.handleNewDeployment(
        singletonList(deploymentSummary), false, OnDemandRollbackInfo.builder().build());

    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(expectedUpdate.size())).saveOrUpdate(instanceCaptor.capture());

    List<Instance> updatedInstances = instanceCaptor.getAllValues();
    List<HelmChartInfo> updatedHelmChartInfo = updatedInstances.stream()
                                                   .map(Instance::getInstanceInfo)
                                                   .filter(KubernetesContainerInfo.class ::isInstance)
                                                   .map(KubernetesContainerInfo.class ::cast)
                                                   .map(KubernetesContainerInfo::getHelmChartInfo)
                                                   .collect(Collectors.toList());

    assertThat(updatedHelmChartInfo).isEqualTo(expectedUpdate);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_K8sHelmChartDeployment_newInstances() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    doReturn(Collections.emptyList()).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name("sample-pod")
                        .podIP("ip-127.0.0.1")
                        .namespace("default")
                        .containerList(asList(K8sContainer.builder().image("nginx:0.1").build()))
                        .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        asList(getDeploymentSummaryWithHelmChartInfo(
            HelmChartInfo.builder().name("helmChartName").version("1.0.0").repoUrl("repoUrl").build())),
        false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());
    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).saveOrUpdate(instanceCaptor.capture());

    Instance savedInstance = instanceCaptor.getValue();
    assertThat(savedInstance.getInstanceInfo()).isInstanceOf(K8sPodInfo.class);
    K8sPodInfo k8sPodInfo = (K8sPodInfo) savedInstance.getInstanceInfo();
    assertThat(k8sPodInfo.getHelmChartInfo().getName()).isEqualTo("helmChartName");
    assertThat(k8sPodInfo.getHelmChartInfo().getVersion()).isEqualTo("1.0.0");
    assertThat(k8sPodInfo.getHelmChartInfo().getRepoUrl()).isEqualTo("repoUrl");

    assertionsForNoDelete();
  }

  private HelmChartInfo helmChartInfoWithVersion(String version) {
    return HelmChartInfo.builder().version(version).name("helmChartName").repoUrl("repoUrl").build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_K8sHelmChartDeployment_newHelmChartVersion() throws Exception {
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());

    final List<Instance> instances =
        asList(buildK8sInstanceWithHelmChartInfo("sample-pod", helmChartInfoWithVersion("1.0.0")),
            buildK8sInstanceWithHelmChartInfo("sample-pod-2", helmChartInfoWithVersion("1.2.0")));

    instances.get(0).setLastWorkflowExecutionName("Current Workflow");
    instances.get(1).setLastWorkflowExecutionName("Another Workflow");

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(asList(K8sPod.builder()
                        .name("sample-pod")
                        .podIP("ip-127.0.0.1")
                        .namespace("default")
                        .containerList(singletonList(K8sContainer.builder().image("nginx:0.1").build()))
                        .build(),
                 K8sPod.builder()
                     .name("sample-pod-2")
                     .podIP("ip-127.0.0.1")
                     .namespace("default")
                     .containerList(singletonList(K8sContainer.builder().image("nginx:0.1").build()))
                     .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        singletonList(getDeploymentSummaryWithHelmChartInfo(helmChartInfoWithVersion("1.1.0"))), false,
        OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).saveOrUpdate(instanceCaptor.capture());

    Instance savedInstance = instanceCaptor.getValue();
    assertThat(savedInstance.getInstanceInfo()).isInstanceOf(K8sPodInfo.class);
    K8sPodInfo k8sPodInfo = (K8sPodInfo) savedInstance.getInstanceInfo();
    assertThat(k8sPodInfo.getPodName()).isEqualTo("sample-pod");
    assertThat(k8sPodInfo.getHelmChartInfo()).isEqualTo(helmChartInfoWithVersion("1.1.0"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_K8sHelmChartDeployment_autoScaleSync() throws Exception {
    final List<Instance> instances =
        singletonList(buildK8sInstanceWithHelmChartInfo("smaple-pod-1", helmChartInfoWithVersion("1.1.0")));

    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(singletonList(K8sPod.builder()
                               .name("sample-pod-2")
                               .namespace("default")
                               .containerList(singletonList(K8sContainer.builder().image("helm-image:1.0").build()))
                               .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(ContainerInfrastructureMapping.class), any(), any(), any());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.ITERATOR);

    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).saveOrUpdate(instanceCaptor.capture());
    K8sPodInfo k8sPodInfo = (K8sPodInfo) instanceCaptor.getValue().getInstanceInfo();
    assertThat(k8sPodInfo.getPodName()).isEqualTo("sample-pod-2");
    assertThat(k8sPodInfo.getHelmChartInfo()).isEqualTo(helmChartInfoWithVersion("1.1.0"));
  }

  private DeploymentSummary getDeploymentSummaryWithHelmChartInfo(HelmChartInfo helmChartInfo) {
    return DeploymentSummary.builder()
        .accountId(ACCOUNT_ID)
        .infraMappingId(INFRA_MAPPING_ID)
        .workflowExecutionId("workflowExecution_1")
        .stateExecutionInstanceId("stateExecutionInstanceId")
        .workflowExecutionName("Current Workflow")
        .deploymentInfo(K8sDeploymentInfo.builder()
                            .namespace("default")
                            .releaseName("release-123")
                            .helmChartInfo(helmChartInfo)
                            .build())
        .build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_GetRelevantHelmChartInfo_fromDeploymentSummary() {
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();

    ContainerDeploymentInfoWithLabels deploymentInfoWithLabels =
        ContainerDeploymentInfoWithLabels.builder().helmChartInfo(helmChartInfo).build();
    testGetContainerHelmChartInfoWithDeploymentInfo(deploymentInfoWithLabels, helmChartInfo);

    K8sDeploymentInfo k8sDeploymentInfo = K8sDeploymentInfo.builder().helmChartInfo(helmChartInfo).build();
    testGetContainerHelmChartInfoWithDeploymentInfo(k8sDeploymentInfo, null);

    ContainerDeploymentInfoWithNames deploymentInfoWithNames = ContainerDeploymentInfoWithNames.builder().build();
    testGetContainerHelmChartInfoWithDeploymentInfo(deploymentInfoWithNames, null);

    ContainerDeploymentInfoWithLabels withLabelsAndNoChartInfo = ContainerDeploymentInfoWithLabels.builder().build();
    testGetContainerHelmChartInfoWithDeploymentInfo(withLabelsAndNoChartInfo, null);
  }

  private void testGetContainerHelmChartInfoWithDeploymentInfo(DeploymentInfo deploymentInfo, HelmChartInfo expected) {
    List<Instance> emptyInstances = Collections.emptyList();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    HelmChartInfo result = containerInstanceHandler.getContainerHelmChartInfo(deploymentSummary, emptyInstances);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_GetContainerHelmChartInfo_fromExistingInstances() {
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    List<Instance> sample = singletonList(buildContainerInstanceWithHelmChartInfo("sample-pod-1", helmChartInfo));
    testGetContainerHelmChartInfoWithExistingInstances(sample, helmChartInfo);

    List<Instance> multiple = asList(buildContainerInstanceWithHelmChartInfo("sample-pod-1", helmChartInfo),
        buildContainerInstanceWithHelmChartInfo("sample-pod-2", helmChartInfo));
    testGetContainerHelmChartInfoWithExistingInstances(multiple, helmChartInfo);

    List<Instance> atLeastOneHasHelmChartInfo = asList(buildContainerInstanceWithHelmChartInfo("sample-pod-1", null),
        buildContainerInstanceWithHelmChartInfo("sample-pod-2", null),
        buildContainerInstanceWithHelmChartInfo("sample-pod-3", helmChartInfo));
    testGetContainerHelmChartInfoWithExistingInstances(atLeastOneHasHelmChartInfo, helmChartInfo);

    HelmChartInfo outdatedHelmChartInfo = HelmChartInfo.builder().name("chart").version("0.9.0").build();
    long epochNow = Instant.now().toEpochMilli();
    List<Instance> useLatest = asList(buildContainerInstanceWithHelmChartInfo("sample-pod-1", outdatedHelmChartInfo),
        buildContainerInstanceWithHelmChartInfo("sample-pod-2", helmChartInfo),
        buildContainerInstanceWithHelmChartInfo("sample-pod-3", outdatedHelmChartInfo));
    useLatest.get(0).setLastDeployedAt(epochNow - 1000L); // outdated
    useLatest.get(1).setLastDeployedAt(epochNow); // actual
    useLatest.get(2).setLastDeployedAt(epochNow - 500L); // outdated
    testGetContainerHelmChartInfoWithExistingInstances(useLatest, helmChartInfo);
  }

  private void testGetContainerHelmChartInfoWithExistingInstances(List<Instance> instances, HelmChartInfo expected) {
    HelmChartInfo result = containerInstanceHandler.getContainerHelmChartInfo(null, instances);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_syncK8sHelmChartInfo_forBlueGreenDeployment() throws Exception {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .accountId(ACCOUNT_ID)
                                              .infraMappingId(INFRA_MAPPING_ID)
                                              .workflowExecutionId("workflowExecution_1")
                                              .stateExecutionInstanceId("stateExecutionInstanceId")
                                              .workflowExecutionName("Current Workflow")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .namespace("default")
                                                                  .releaseName("release-123")
                                                                  .helmChartInfo(helmChartInfoWithVersion("1.1.0"))
                                                                  .blueGreenStageColor(colorBlue)
                                                                  .build())
                                              .build();
    Instance instanceWithGreenColor = buildInstanceWith("sample-pod-1",
        K8sPodInfo.builder()
            .blueGreenColor(colorGreen)
            .helmChartInfo(helmChartInfoWithVersion("1.0.0"))
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .build());
    instanceWithGreenColor.setLastWorkflowExecutionId("workflowExecution_1");
    Instance instanceWithBlueColor = buildInstanceWith("sample-pod-2",
        K8sPodInfo.builder()
            .blueGreenColor(colorBlue)
            .helmChartInfo(helmChartInfoWithVersion("1.0.0"))
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .build());
    instanceWithBlueColor.setLastWorkflowExecutionId("workflowExecution_1");

    /* Given:
        - New deployment with blue stage color
        - No existing instances in database
        Should store 2 new instances:
         - 1 green instance with null helm chart info (is not matching the deployment color)
         - 1 blue instance using HelmChartInfo fromm deployment summary (1.1.0)
     */
    test_syncK8sHelmChartInfo_blueGreenDeploymentWith(deploymentSummary, emptyList(),
        asList(k8sPodWithColorLabel("sample-pod-1", colorGreen), k8sPodWithColorLabel("sample-pod-2", colorBlue)),
        asList(null, helmChartInfoWithVersion("1.1.0")));

    /* Given:
       - New deployment with blue stage color
       - 1 green instance existing in db
       Should store 1 new instance:
        - 1 blue instance using HelmChartInfo fromm deployment summary (1.1.0)
    */
    test_syncK8sHelmChartInfo_blueGreenDeploymentWith(deploymentSummary, singletonList(instanceWithGreenColor),
        asList(k8sPodWithColorLabel("sample-pod-1", colorGreen), k8sPodWithColorLabel("sample-pod-2", colorBlue)),
        singletonList(helmChartInfoWithVersion("1.1.0")));

    /* Given:
       - New deployment with blue stage color
       - 1 green and 1 blue instance existing in db
       Should update 1 instance:
        - existing 1 blue instance (sample-pod-2) with helmChartInfo from deployment summary
    */
    test_syncK8sHelmChartInfo_blueGreenDeploymentWith(deploymentSummary,
        asList(instanceWithGreenColor, instanceWithBlueColor),
        asList(k8sPodWithColorLabel("sample-pod-1", colorGreen), k8sPodWithColorLabel("sample-pod-2", colorBlue)),
        singletonList(helmChartInfoWithVersion("1.1.0")));
  }

  public void test_syncK8sHelmChartInfo_blueGreenDeploymentWith(DeploymentSummary deploymentSummary,
      List<Instance> instances, List<K8sPod> pods, List<HelmChartInfo> expectedVersions) throws Exception {
    reset(instanceService);
    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);

    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(pods).when(k8sStateHelper).fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.handleNewDeployment(
        singletonList(deploymentSummary), false, OnDemandRollbackInfo.builder().onDemandRollback(false).build());

    verify(instanceService, times(expectedVersions.size())).saveOrUpdate(instanceCaptor.capture());
    List<Instance> savedInstances = instanceCaptor.getAllValues();
    IntStream.range(0, savedInstances.size()).forEach(idx -> {
      Instance instance = savedInstances.get(idx);
      HelmChartInfo expectedVersion = expectedVersions.get(idx);
      assertThat(instance.getInstanceInfo()).isInstanceOf(K8sPodInfo.class);
      K8sPodInfo k8sPodInfo = (K8sPodInfo) instance.getInstanceInfo();
      assertThat(k8sPodInfo.getHelmChartInfo()).isEqualTo(expectedVersion);
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void test_syncK8sHelmChartInfo_forBlueGreenDeploymentAutoScale() throws Exception {
    Instance instanceWithGreenColor = buildInstanceWith("sample-pod-g1",
        K8sPodInfo.builder()
            .blueGreenColor(colorGreen)
            .helmChartInfo(helmChartInfoWithVersion("1.0.0"))
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .build());
    Instance instanceWithBlueColor = buildInstanceWith("sample-pod-b1",
        K8sPodInfo.builder()
            .blueGreenColor(colorBlue)
            .helmChartInfo(helmChartInfoWithVersion("1.1.0"))
            .namespace("default")
            .releaseName("release-123")
            .containers(singletonList(K8sContainerInfo.builder().image("nginx:0.1").build()))
            .build());

    /* Given:
        - 1 green instance with helm chart version 1.0.0 exist in db
        - 1 blue instance with helm chart version 1.1.0 exist in db
        - 1 new green pod and 1 new blue pod
       Should store 2 new instances:
        - 1 green instance using HelmChartInfo from <sample-pod-g1> (1.0.0)
        - 1 blue instance using HelmChartInfo fromm <sample-pod-b1> (1.1.0)
    */
    test_syncK8sHelmChartInfo_forBlueGreenDeploymentAutoScaleWith(asList(instanceWithGreenColor, instanceWithBlueColor),
        asList(k8sPodWithColorLabel("sample-pod-g1", colorGreen), k8sPodWithColorLabel("sample-pod-g2", colorGreen),
            k8sPodWithColorLabel("sample-pod-b1", colorBlue), k8sPodWithColorLabel("sample-pod-b2", colorBlue)),
        asList(helmChartInfoWithVersion("1.0.0"), helmChartInfoWithVersion("1.1.0")));

    /* Given:
        - 1 blue instance with helm chart version 1.1.0 exist in db
        - 1 new green pod and 1 new blue pod
       Should store 2 new instances:
        - 1 green instance with no HelmChartInfo, since there is any instances with this color marker
        - 1 blue instance using HelmChartInfo fromm <sample-pod-b1> (1.1.0)
     */
    test_syncK8sHelmChartInfo_forBlueGreenDeploymentAutoScaleWith(singletonList(instanceWithBlueColor),
        asList(k8sPodWithColorLabel("sample-pod-g1", colorGreen), k8sPodWithColorLabel("sample-pod-b1", colorBlue),
            k8sPodWithColorLabel("sample-pod-b2", colorBlue)),
        asList(null, helmChartInfoWithVersion("1.1.0")));
  }

  public void test_syncK8sHelmChartInfo_forBlueGreenDeploymentAutoScaleWith(
      List<Instance> instances, List<K8sPod> pods, List<HelmChartInfo> expectedVersions) throws Exception {
    reset(instanceService);
    ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
    doReturn(getInframapping(InfrastructureMappingType.GCP_KUBERNETES.name()))
        .when(infraMappingService)
        .get(any(), any());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(pods).when(k8sStateHelper).fetchPodListForCluster(any(), any(), any(), any());

    containerInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.ITERATOR);

    verify(instanceService, times(expectedVersions.size())).saveOrUpdate(instanceCaptor.capture());
    List<Instance> savedInstances = instanceCaptor.getAllValues();
    IntStream.range(0, savedInstances.size()).forEach(idx -> {
      Instance instance = savedInstances.get(idx);
      HelmChartInfo expectedVersion = expectedVersions.get(idx);
      assertThat(instance.getInstanceInfo()).isInstanceOf(K8sPodInfo.class);
      K8sPodInfo k8sPodInfo = (K8sPodInfo) instance.getInstanceInfo();
      assertThat(k8sPodInfo.getHelmChartInfo()).isEqualTo(expectedVersion);
    });
  }

  private K8sPod k8sPodWithColorLabel(String podName, String colorValue) {
    return K8sPod.builder()
        .name(podName)
        .podIP("ip-127.0.0.1")
        .namespace("default")
        .containerList(singletonList(K8sContainer.builder().image("nginx:0.1").build()))
        .labels(ImmutableMap.of(HarnessLabels.color, colorValue))
        .build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getDeploymentSummaryMapIfContainerSvcNamePresent() {
    final ContainerInfrastructureMapping infraMapping =
        DirectKubernetesInfrastructureMapping.builder().namespace("default").build();
    final List<DeploymentSummary> deploymentSummaries = asList(
        DeploymentSummary.builder()
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .labels(asList(aLabel().withName("key").withValue("value").build()))
                                .containerInfoList(asList(ContainerInfo.builder().workloadName("workload").build()))
                                .releaseName("release")
                                .build())
            .build(),
        DeploymentSummary.builder()
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .labels(asList(aLabel().withName("key").withValue("value-1").build()))
                                .containerInfoList(asList(ContainerInfo.builder().workloadName("workload-1").build()))
                                .releaseName("release-1")
                                .build())
            .build());

    doReturn(Sets.newHashSet("workload"))
        .when(containerSync)
        .getControllerNames(eq(infraMapping), eq(ImmutableMap.of("key", "value")), eq("default"));
    doReturn(Sets.newHashSet("workload-1"))
        .when(containerSync)
        .getControllerNames(eq(infraMapping), eq(ImmutableMap.of("key", "value-1")), eq("default"));

    final Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap =
        containerInstanceHandler.getDeploymentSummaryMap(deploymentSummaries, ArrayListMultimap.create(), infraMapping);

    assertThat(deploymentSummaryMap.keySet())
        .containsExactlyInAnyOrder(ContainerMetadata.builder()
                                       .releaseName("release")
                                       .containerServiceName("workload")
                                       .namespace("default")
                                       .build(),
            ContainerMetadata.builder()
                .namespace("default")
                .containerServiceName("workload-1")
                .releaseName("release-1")
                .build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getDeploymentSummaryMapIfContainerSvcNameNotPresent() {
    final ContainerInfrastructureMapping infraMapping =
        DirectKubernetesInfrastructureMapping.builder().namespace("default").build();
    final List<DeploymentSummary> deploymentSummaries =
        asList(DeploymentSummary.builder()
                   .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                       .labels(asList(aLabel().withName("key").withValue("value").build()))
                                       .containerInfoList(asList(ContainerInfo.builder().build()))
                                       .releaseName("release")
                                       .build())
                   .build(),
            DeploymentSummary.builder()
                .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                    .labels(asList(aLabel().withName("key").withValue("value-1").build()))
                                    .containerInfoList(asList(ContainerInfo.builder().build()))
                                    .releaseName("release-1")
                                    .build())
                .build());

    doReturn(Sets.newHashSet("workload"))
        .when(containerSync)
        .getControllerNames(eq(infraMapping), eq(ImmutableMap.of("key", "value")), eq("default"));
    doReturn(Sets.newHashSet("workload-1"))
        .when(containerSync)
        .getControllerNames(eq(infraMapping), eq(ImmutableMap.of("key", "value-1")), eq("default"));

    final Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap =
        containerInstanceHandler.getDeploymentSummaryMap(deploymentSummaries, ArrayListMultimap.create(), infraMapping);

    assertThat(deploymentSummaryMap.keySet())
        .containsExactlyInAnyOrder(ContainerMetadata.builder().releaseName("release").namespace("default").build(),
            ContainerMetadata.builder().namespace("default").releaseName("release-1").build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldUpdateAllIfWorkloadNameIsEmpty() {
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .podName("pod:0")
                              .build())
            .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .build())
            .build());

    EcsContainerInfo containerInfo = Builder.anEcsContainerInfo().build();
    ContainerSyncResponse responseData;
    responseData = ContainerSyncResponse.builder()
                       .containerInfoList(Collections.singletonList(containerInfo))
                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                       .build();
    ContainerInfrastructureMapping infrastructureMapping;
    infrastructureMapping = DirectKubernetesInfrastructureMapping.builder().build();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, responseData);

    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_1_ID));
    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_2_ID));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesWithMatchingWorkloadName() {
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_0")
                              .controllerName("controllerName:0")
                              .podName("pod:0")
                              .build())
            .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .build())
            .build());

    EcsContainerInfo containerInfo = Builder.anEcsContainerInfo().build();
    ContainerSyncResponse responseData;
    responseData = ContainerSyncResponse.builder()
                       .containerInfoList(Collections.singletonList(containerInfo))
                       .controllerName("controllerName:0")
                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                       .build();
    ContainerInfrastructureMapping infrastructureMapping;
    infrastructureMapping = DirectKubernetesInfrastructureMapping.builder().build();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, responseData);

    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_1_ID));
    verify(instanceService, never()).delete(Sets.newHashSet(INSTANCE_2_ID));

    responseData = ContainerSyncResponse.builder()
                       .containerInfoList(Collections.singletonList(containerInfo))
                       .controllerName("controllerName:1")
                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                       .build();
    infrastructureMapping = DirectKubernetesInfrastructureMapping.builder().build();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, responseData);

    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_2_ID));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesAndDeleteAnomalicDupplicates() {
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .releaseName(null)
                              .build())
            .lastWorkflowExecutionId("1")
            .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .releaseName("")
                              .build())
            .lastWorkflowExecutionId("2")
            .build());

    ContainerSyncResponse responseData = ContainerSyncResponse.builder()
                                             .containerInfoList(asList(KubernetesContainerInfo.builder()
                                                                           .controllerName("controllerName:1")
                                                                           .podName("pod:1")
                                                                           .namespace("default")
                                                                           .serviceName("service_a_1")
                                                                           .clusterName(KUBE_CLUSTER)
                                                                           .build(),
                                                 KubernetesContainerInfo.builder()
                                                     .controllerName("controllerName:1")
                                                     .podName("pod:2")
                                                     .namespace("default")
                                                     .serviceName("service_a_1")
                                                     .clusterName(KUBE_CLUSTER)
                                                     .build()))
                                             .controllerName("controllerName:1")
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .build();
    ContainerInfrastructureMapping infrastructureMapping;
    infrastructureMapping = DirectKubernetesInfrastructureMapping.builder()
                                .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
                                .build();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, responseData);

    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_2_ID));
    ArgumentCaptor<Instance> instanceToBeSaved = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).save(instanceToBeSaved.capture());

    Instance savedInstance = instanceToBeSaved.getValue();
    assertThat(savedInstance.getInstanceInfo()).isNotNull();
    assertThat(savedInstance.getInstanceInfo()).isInstanceOf(KubernetesContainerInfo.class);
    KubernetesContainerInfo savedInstanceInfo = (KubernetesContainerInfo) savedInstance.getInstanceInfo();
    assertThat(savedInstanceInfo.getControllerName()).isEqualTo("controllerName:1");
    assertThat(savedInstanceInfo.getPodName()).isEqualTo("pod:2");
    assertThat(savedInstanceInfo.getServiceName()).isEqualTo("service_a_1");
    assertThat(savedInstanceInfo.getClusterName()).isEqualTo(KUBE_CLUSTER);
    assertThat(savedInstanceInfo.getNamespace()).isEqualTo("default");
    assertThat(savedInstanceInfo.getReleaseName()).isNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldDeleteOldInstancesWithoutReleaseName() {
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .namespace("default")
                              .build())
            .lastWorkflowExecutionId("1")
            .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace("default").build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .releaseName("release")
                              .namespace("default")
                              .build())
            .lastWorkflowExecutionId("2")
            .build());

    ContainerSyncResponse responseData = ContainerSyncResponse.builder()
                                             .containerInfoList(asList(KubernetesContainerInfo.builder()
                                                                           .controllerName("controllerName:1")
                                                                           .podName("pod:1")
                                                                           .namespace("default")
                                                                           .serviceName("service_a_1")
                                                                           .clusterName(KUBE_CLUSTER)
                                                                           .releaseName("release")
                                                                           .build()))
                                             .controllerName("controllerName:1")
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .build();

    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .build();

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(responseData)
        .when(containerSync)
        .getInstances(infrastructureMapping,
            asList(ContainerMetadata.builder()
                       .releaseName("release")
                       .namespace("default")
                       .containerServiceName("controllerName:1")
                       .type(null)
                       .build()));
    doReturn(ContainerSyncResponse.builder()
                 .containerInfoList(emptyList())
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .build())
        .when(containerSync)
        .getInstances(infrastructureMapping,
            asList(ContainerMetadata.builder()
                       .namespace("default")
                       .containerServiceName("controllerName:1")
                       .type(null)
                       .build()));
    doReturn(infrastructureMapping).when(infraMappingService).get(any(), any());

    containerInstanceHandler.syncInstances("", "", InstanceSyncFlow.ITERATOR);

    verify(instanceService, times(1)).delete(Sets.newHashSet(INSTANCE_1_ID));
    verify(instanceService, times(0)).save(any(Instance.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseNewPod() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));
    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("release1", "namespace1", "instance1", "instance2", "instance3", "instance4");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, singletonList("instance4"), emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseNoNewPods() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));
    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("release1", "namespace1", "instance1", "instance2", "instance3");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseDeletedPods() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));

    K8sInstanceSyncResponse instanceSyncResponse = creteK8sPodSyncResponseWith("release1", "namespace1", "instance1");
    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), asList("instance2", "instance3"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseMixed() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));

    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("release1", "namespace1", "instance2", "instance4", "instance5");
    assertSavedAndDeletedInstances(
        instancesInDb, instanceSyncResponse, asList("instance4", "instance5"), asList("instance1", "instance3"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNewPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceY"),
        createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceY"));

    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2", "instance6");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, singletonList("instance6"), emptyList());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNewPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceY", "controller1"),
            createKubernetesContainerInstance("instance4", "releaseY", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceY", "controller1"));

    ContainerSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance1", "instance2", "instance6");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, singletonList("instance6"), emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseControllerNameIsEmpty() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", null),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", null),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceY", null),
            createKubernetesContainerInstance("instance4", "releaseY", "namespaceX", null),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceY", null));

    // Ref: ContainerInstanceSyncPerpetualTaskClient#getPerpetualTaskData at 207, controllerName will be always empty
    // string when the actual value is null
    ContainerSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "", "instance1", "instance2", "instance6");
    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, singletonList("instance6"), emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNoNewPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceY"),
        createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceY"));

    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), emptyList());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNoNewPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance4", "releaseX", "namespaceY", "controller1"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance6", "releaseY", "namespaceY", "controller3"));

    ContainerSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance1", "instance2");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareDeletedPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceX"),
        createK8sPodInstance("instance4", "releaseX", "namespaceY"),
        createK8sPodInstance("instance5", "releaseY", "namespaceX"),
        createK8sPodInstance("instance6", "releaseY", "namespaceY"));

    K8sInstanceSyncResponse instanceSyncResponse = creteK8sPodSyncResponseWith("releaseX", "namespaceX", "instance2");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), asList("instance1", "instance3"));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareDeletedPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance4", "releaseX", "namespaceY", "controller2"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance6", "releaseY", "namespaceY", "controller3"));

    ContainerSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance2");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, emptyList(), asList("instance1", "instance3"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareMixed() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceX"),
        createK8sPodInstance("instance4", "releaseX", "namespaceX"),
        createK8sPodInstance("instance5", "releaseX", "namespaceY"),
        createK8sPodInstance("instance6", "releaseY", "namespaceX"),
        createK8sPodInstance("instance7", "releaseY", "namespaceY"));

    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("releaseX", "namespaceX", "instance2", "instance3", "instance8", "instance9");

    assertSavedAndDeletedInstances(
        instancesInDb, instanceSyncResponse, asList("instance8", "instance9"), asList("instance1", "instance4"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareMixedHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceX"),
            createKubernetesContainerInstance("instance4", "releaseX", "namespaceX"),
            createKubernetesContainerInstance("instance5", "releaseX", "namespaceY"),
            createKubernetesContainerInstance("instance6", "releaseY", "namespaceX"),
            createKubernetesContainerInstance("instance7", "releaseY", "namespaceY"));

    ContainerSyncResponse instanceSyncResponse = createContainerSyncResponseWith(
        "releaseX", "namespaceX", null, "instance2", "instance3", "instance8", "instance9");

    assertSavedAndDeletedInstances(
        instancesInDb, instanceSyncResponse, asList("instance8", "instance9"), asList("instance1", "instance4"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldAddInstancesFromPerpetualTaskEvenIfNoAnyOtherInstancesExistsInDb() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceX"),
        createK8sPodInstance("instance6", "releaseY", "namespaceY"));

    K8sInstanceSyncResponse instanceSyncResponse =
        creteK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2", "instance3");

    assertSavedAndDeletedInstances(
        instancesInDb, instanceSyncResponse, asList("instance1", "instance2", "instance3"), emptyList());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void shouldNotChangeInstancesFromPerpetualTaskIfResponseFromDelegateIsSameECS() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createECSInstance("instance1"), createECSInstance("instance2"), createECSInstance("instance3"));

    ContainerSyncResponse instanceSyncResponse = containerSyncResponse("instance1", "instance2", "instance3");
    assertSavedAndDeletedInstancesEcs(instancesInDb, instanceSyncResponse, emptyList(), emptyList());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskIfResponseFromDelegateIsNotSameECS() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createECSInstance("instance1"), createECSInstance("instance2"), createECSInstance("instance3"));

    ContainerSyncResponse instanceSyncResponse = containerSyncResponse("instance4", "instance5", "instance6");
    assertSavedAndDeletedInstancesEcs(instancesInDb, instanceSyncResponse,
        asList("instance4", "instance5", "instance6"), asList("instance1", "instance2", "instance3"));
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskIfResponseFromDelegateWithOneInstanceSameECS() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createECSInstance("instance1"), createECSInstance("instance2"), createECSInstance("instance3"));

    ContainerSyncResponse instanceSyncResponse = containerSyncResponse("instance1", "instance5", "instance6");
    assertSavedAndDeletedInstancesEcs(
        instancesInDb, instanceSyncResponse, asList("instance5", "instance6"), asList("instance2", "instance3"));
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnUnsupportedInfraMapping() throws Exception {
    AwsInfrastructureMapping awsInfrastructureMapping =
        AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping().build();
    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(awsInfrastructureMapping, null);
  }

  @Test(expected = K8sPodSyncException.class)
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionK8sPodSyncExceptionForK8s() {
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping().build();
    K8sTaskExecutionResponse k8sTaskExecutionResponse =
        K8sTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).build();
    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        infrastructureMapping, k8sTaskExecutionResponse);
  }

  @Test(expected = K8sPodSyncException.class)
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionK8sPodSyncExceptionForHelm() {
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping().build();
    ContainerSyncResponse k8sTaskExecutionResponse =
        ContainerSyncResponse.builder().commandExecutionStatus(FAILURE).build();
    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        infrastructureMapping, k8sTaskExecutionResponse);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void generateDeploymentKeyTestForContainerDeploymentInfoWithLabels() throws Exception {
    List<Label> labelList = Arrays.asList(createLabel("l1"), createLabel("l2"));
    DeploymentInfo deploymentInfo = ContainerDeploymentInfoWithLabels.builder()
                                        .labels(new ArrayList<Label>(asList(createLabel("l1"), createLabel("l2"))))
                                        .namespace("NS1")
                                        .releaseName("RN1")
                                        .build();
    DeploymentKey deploymentKey = containerInstanceHandler.generateDeploymentKey(deploymentInfo);
    ContainerDeploymentKey containerDeploymentKey = (ContainerDeploymentKey) deploymentKey;
    assertThat(labelList).containsExactlyInAnyOrderElementsOf(containerDeploymentKey.getLabels());
  }
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void generateDeploymentKeyTestForContainerDeploymentInfoWithNames() throws Exception {
    DeploymentInfo deploymentInfo =
        ContainerDeploymentInfoWithNames.builder().containerSvcName("service1").clusterName("cl1").build();
    ContainerDeploymentKey containerDeploymentKey =
        (ContainerDeploymentKey) containerInstanceHandler.generateDeploymentKey(deploymentInfo);
    assertEquals("service1", containerDeploymentKey.getContainerServiceName());
  }
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void generateDeploymentKeyTestForK8sDeployment() throws Exception {
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder().releaseName("Name1").clusterName("cl1").build();
    K8sDeploymentKey k8sDeploymentKey =
        (K8sDeploymentKey) containerInstanceHandler.generateDeploymentKey(deploymentInfo);
    assertEquals("Name1", k8sDeploymentKey.getReleaseName());
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void generateDeploymentKeyTestUnsupportedTypeThrowsException() throws Exception {
    DeploymentInfo deploymentInfo = AwsCodeDeployDeploymentInfo.builder().build();
    containerInstanceHandler.generateDeploymentKey(deploymentInfo);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldThrowNoInstancesExceptionInstancesExistsInDb() {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"));

    K8sInstanceSyncResponse instanceSyncResponse = creteK8sPodSyncResponseWith("releaseX", "namespaceX");
    assertThatThrownBy(()
                           -> assertSavedAndDeletedInstances(
                               instancesInDb, instanceSyncResponse, emptyList(), asList("instance1", "instance2")))
        .isInstanceOf(NoInstancesException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldThrowNoInstancesExceptionNOInstancesExistsInDb() {
    K8sInstanceSyncResponse instanceSyncResponse = creteK8sPodSyncResponseWith("releaseX", "namespaceX");

    assertThatThrownBy(
        () -> assertSavedAndDeletedInstances(emptyList(), instanceSyncResponse, emptyList(), emptyList()))
        .isInstanceOf(NoInstancesException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldThrowNoInstancesExceptionForKubernetesContainerDeployment() {
    List<Instance> instancesInDb = Arrays.asList(createKubernetesContainerInstance("instance1", "release", "default"),
        createKubernetesContainerInstance("instance2", "release", "default"));

    ContainerSyncResponse syncResponse = ContainerSyncResponse.builder()
                                             .containerInfoList(emptyList())
                                             .isEcs(false)
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .build();

    assertThatThrownBy(() -> assertSavedAndDeletedInstances(instancesInDb, syncResponse, emptyList(), emptyList()))
        .isInstanceOf(NoInstancesException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldThrowNoInstancesExceptionForKubernetesContainerDeploymentNoInstancesInDb() {
    ContainerSyncResponse syncResponse = createContainerSyncResponseWith("release-name", "default", "controller");

    assertThatThrownBy(() -> assertSavedAndDeletedInstances(emptyList(), syncResponse, emptyList(), emptyList()))
        .isInstanceOf(NoInstancesException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldAddInstancesFromContainerSyncEvenNoInstancesInDb() {
    ContainerSyncResponse syncResponse =
        createContainerSyncResponseWith("release-name", "default", "controller", "instance-1", "instance-2");

    assertSavedAndDeletedInstances(emptyList(), syncResponse, asList("instance-1", "instance-2"), emptyList());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCleanupInvalidV1PerpetualTask() {
    List<PerpetualTaskRecord> perpetualTaskRecordList = List.of(PerpetualTaskRecord.builder()
                                                                    .uuid("perpetualTaskId1")
                                                                    .perpetualTaskType(CONTAINER_INSTANCE_SYNC)
                                                                    .state(TASK_INVALID)
                                                                    .build(),
        PerpetualTaskRecord.builder()
            .uuid("perpetualTaskId2")
            .perpetualTaskType(CONTAINER_INSTANCE_SYNC)
            .state(TASK_ASSIGNED)
            .build(),
        PerpetualTaskRecord.builder()
            .uuid("perpetualTaskId3")
            .perpetualTaskType(PDC_INSTANCE_SYNC)
            .state(TASK_INVALID)
            .build());
    doReturn(perpetualTaskRecordList).when(perpetualTaskService).listAllTasksForAccount("accountId");
    containerInstanceHandler.cleanupInvalidV1PerpetualTask("accountId");
    verify(perpetualTaskService, times(1)).deleteTask(any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUpdateExistingArtifactIdOnNewDeployment() throws Exception {
    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingId(UUID)
            .deploymentInfo(
                K8sDeploymentInfo.builder().namespace("default").releaseName("releaseName").releaseNumber(2).build())
            .build();

    List<K8sPod> existingK8sPod =
        asList(createK8sPod("pod-1", "releaseName", "default"), createK8sPod("pod-2", "releaseName", "default"));
    List<Instance> instancesInDb =
        asList(createK8sPodInstance("pod-1", "releaseName", "default", "oldArtifactId", "oldWorkflow"),
            createK8sPodInstance("pod-2", "releaseName", "default", "newArtifactId", "newWorkflow"));

    assertSavedAndDeletedInstancesOnNewDeployment(
        deploymentSummary, instancesInDb, existingK8sPod, singletonList("pod-1"), emptyList(), null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldNotUpdateExistingArtifactIdOnNewDeployment() throws Exception {
    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingId(UUID)
            .deploymentInfo(
                K8sDeploymentInfo.builder().namespace("default").releaseName("releaseName").releaseNumber(2).build())
            .build();

    HashMap<String, String> metadata = new HashMap<>();
    metadata.put("image", "test");
    Artifact artifact = anArtifact()
                            .withUuid("artifactId")
                            .withArtifactStreamId("artifactStreamId")
                            .withAppId(APP_ID)
                            .withMetadata(new ArtifactMetadata(metadata))
                            .build();

    List<K8sPod> existingK8sPod =
        asList(createK8sPod("pod-1", "releaseName", "default"), createK8sPod("pod-2", "releaseName", "default"));
    List<Instance> instancesInDb =
        asList(createK8sPodInstance("pod-1", "releaseName", "default", "artifactId", "oldWorkflow"),
            createK8sPodInstance("pod-2", "releaseName", "default", "newArtifactId", "newWorkflow"));

    assertSavedAndDeletedInstancesOnNewDeployment(
        deploymentSummary, instancesInDb, existingK8sPod, singletonList("pod-1"), emptyList(), artifact);
  }

  private void assertSavedAndDeletedInstances(List<Instance> instancesInDb, K8sInstanceSyncResponse syncResponse,
      List<String> savedInstances, List<String> deletedInstances) throws Exception {
    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(APP_ID, UUID);
    Exception thrownException = null;

    try {
      containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping,
          K8sTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).k8sTaskResponse(syncResponse).build());
    } catch (Exception e) {
      thrownException = e;
    }

    assertSavedAndDeletedInstances(actualSavedInstances
        -> assertThat(actualSavedInstances.stream()
                          .map(Instance::getInstanceInfo)
                          .map(K8sPodInfo.class ::cast)
                          .map(K8sPodInfo::getPodName))
               .containsExactlyInAnyOrderElementsOf(savedInstances),
        actualDeletedInstancesIds
        -> assertThat(actualDeletedInstancesIds).containsExactlyInAnyOrderElementsOf(deletedInstances));

    if (thrownException != null) {
      throw thrownException;
    }
  }

  private void assertSavedAndDeletedInstancesEcs(List<Instance> instancesInDb, ContainerSyncResponse syncResponse,
      List<String> savedInstances, List<String> deletedInstances) throws Exception {
    ContainerInfrastructureMapping infrastructureMapping =
        EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping()
            .withAppId(APP_ID)
            .withAccountId(ACCOUNT_ID)
            .withInfraMappingType(InfrastructureMappingType.AWS_ECS.name())
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframappingNotRemovedFully(APP_ID, UUID);
    Exception thrownException = null;

    try {
      containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, syncResponse);
    } catch (Exception e) {
      thrownException = e;
    }
    ArgumentCaptor<Instance> savedInstancesCaptor = ArgumentCaptor.forClass(Instance.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    verify(instanceService, atLeast(0)).save(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());
    assertThat(savedInstancesCaptor.getAllValues()
                   .stream()
                   .map(Instance::getInstanceInfo)
                   .map(EcsContainerInfo.class ::cast)
                   .map(EcsContainerInfo::getTaskArn))
        .containsExactlyInAnyOrderElementsOf(savedInstances);

    assertThat(deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(deletedInstances);

    if (thrownException != null) {
      throw thrownException;
    }
  }
  private void assertSavedAndDeletedInstancesOnNewDeployment(DeploymentSummary deploymentSummary,
      List<Instance> instancesInDb, List<K8sPod> podList, List<String> savedInstances, List<String> deletedInstances,
      Artifact artifact) throws Exception {
    List<DeploymentSummary> deploymentSummaries = singletonList(deploymentSummary);

    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .namespace("default")
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(podList)
        .when(k8sStateHelper)
        .fetchPodListForCluster(infrastructureMapping, "default", "releaseName", null);

    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(APP_ID, UUID);

    doReturn(infrastructureMapping).when(infraMappingService).get(APP_ID, UUID);

    if (artifact != null) {
      persistence.save(artifact);
    }

    containerInstanceHandler.handleNewDeployment(deploymentSummaries, false, null);

    assertSavedAndDeletedInstances(actualSavedInstances -> {
      assertThat(actualSavedInstances.stream()
                     .map(Instance::getInstanceInfo)
                     .map(K8sPodInfo.class ::cast)
                     .map(K8sPodInfo::getPodName))
          .containsExactlyInAnyOrderElementsOf(savedInstances);
      String expectedArtifactId = artifact != null ? artifact.getUuid() : deploymentSummary.getArtifactId();
      assertThat(actualSavedInstances.stream().map(Instance::getLastArtifactId))
          .containsExactlyInAnyOrderElementsOf(
              savedInstances.stream().map(s -> expectedArtifactId).collect(Collectors.toList()));
    }, actualDeletedInstances -> assertThat(deletedInstances).isEmpty());
  }

  private void assertSavedAndDeletedInstances(
      Consumer<List<Instance>> savedInstancesHandler, Consumer<List<String>> deletedInstancesHandler) {
    ArgumentCaptor<Instance> savedInstancesCaptor = ArgumentCaptor.forClass(Instance.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, atLeast(0)).saveOrUpdate(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());

    savedInstancesHandler.accept(savedInstancesCaptor.getAllValues());
    deletedInstancesHandler.accept(
        deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toList()));
  }

  private void assertSavedAndDeletedInstances(List<Instance> instancesInDb, ContainerSyncResponse containerSyncResponse,
      List<String> savedInstances, List<String> deletedInstances) {
    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(APP_ID, UUID);

    containerInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, containerSyncResponse);

    ArgumentCaptor<Instance> savedInstancesCaptor = ArgumentCaptor.forClass(Instance.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, atLeast(0)).save(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());

    assertThat(savedInstancesCaptor.getAllValues()
                   .stream()
                   .map(Instance::getInstanceInfo)
                   .map(KubernetesContainerInfo.class ::cast)
                   .map(KubernetesContainerInfo::getPodName))
        .containsExactlyInAnyOrderElementsOf(savedInstances);
    assertThat(deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(deletedInstances);
  }

  private Instance createK8sPodInstance(String id, String releaseName, String namespace) {
    return createK8sPodInstance(id, releaseName, namespace, null, "1");
  }

  private Instance createK8sPodInstance(
      String id, String releaseName, String namespace, String artifactId, String workflowExecutionId) {
    return Instance.builder()
        .uuid(id)
        .instanceType(KUBERNETES_CONTAINER_INSTANCE)
        .podInstanceKey(PodInstanceKey.builder().podName(id).namespace(namespace).build())
        .instanceInfo(K8sPodInfo.builder()
                          .podName(id)
                          .namespace(namespace)
                          .releaseName(releaseName)
                          .containers(singletonList(K8sContainerInfo.builder().image("test").build()))
                          .build())
        .lastArtifactId(artifactId)
        .lastWorkflowExecutionName("workflowName")
        .lastWorkflowExecutionId(workflowExecutionId)
        .build();
  }

  private Instance createECSInstance(String id) {
    InstanceInfo instanceInfo = EcsContainerInfo.Builder.anEcsContainerInfo().withClusterName("cl1").build();
    return Instance.builder()
        .uuid(id)
        .instanceType(ECS_CONTAINER_INSTANCE)
        .lastWorkflowExecutionId("WFexec")
        .containerInstanceKey(ContainerInstanceKey.builder().containerId(id).build())
        .instanceInfo(instanceInfo)
        .build();
  }
  private Instance createKubernetesContainerInstance(String id, String releaseName, String namespace) {
    return createKubernetesContainerInstance(id, releaseName, namespace, null);
  }

  private Instance createKubernetesContainerInstance(
      String id, String releaseName, String namespace, String controllerName) {
    return Instance.builder()
        .uuid(id)
        .instanceType(KUBERNETES_CONTAINER_INSTANCE)
        .containerInstanceKey(ContainerInstanceKey.builder().containerId(id).namespace(namespace).build())
        .instanceInfo(KubernetesContainerInfo.builder()
                          .clusterName(KUBE_CLUSTER)
                          .podName(id)
                          .namespace(namespace)
                          .releaseName(releaseName)
                          .controllerName(controllerName)
                          .build())
        .lastWorkflowExecutionId("1")
        .build();
  }

  private K8sInstanceSyncResponse creteK8sPodSyncResponseWith(String releaseName, String namespace, String... podIds) {
    return K8sInstanceSyncResponse.builder()
        .releaseName(releaseName)
        .namespace(namespace)
        .k8sPodInfoList(
            Arrays.stream(podIds).map(id -> createK8sPod(id, releaseName, namespace)).collect(Collectors.toList()))
        .build();
  }
  public Label createLabel(String name) {
    return Label.Builder.aLabel().withName(name).build();
  }
  private ContainerSyncResponse containerSyncResponse(String... ids) {
    List<EcsContainerInfo> list = new ArrayList<>();
    for (String id : ids) {
      EcsContainerInfo info = createECSInfo(id);
      list.add(info);
    }
    LinkedList<software.wings.beans.infrastructure.instance.info.ContainerInfo> list1 = new LinkedList<>(list);
    return ContainerSyncResponse.builder().commandExecutionStatus(SUCCESS).isEcs(true).containerInfoList(list1).build();
  }

  private EcsContainerInfo createECSInfo(String id) {
    return EcsContainerInfo.Builder.anEcsContainerInfo().withTaskArn(id).withClusterName("cl1").build();
  }

  private ContainerSyncResponse createContainerSyncResponseWith(
      String releaseName, String namespace, String controllerName, String... podIds) {
    return ContainerSyncResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .namespace(namespace)
        .releaseName(releaseName)
        .containerInfoList(Arrays.stream(podIds)
                               .map(id
                                   -> KubernetesContainerInfo.builder()
                                          .clusterName(CLUSTER_NAME)
                                          .releaseName(releaseName)
                                          .namespace(namespace)
                                          .podName(id)
                                          .build())
                               .collect(Collectors.toList()))
        .controllerName(controllerName)
        .build();
  }

  private K8sPod createK8sPod(String id, String releaseName, String namespace) {
    return K8sPod.builder()
        .podIP(id)
        .name(id)
        .releaseName(releaseName)
        .namespace(namespace)
        .containerList(singletonList(K8sContainer.builder().name(id).image("test").containerId(id).build()))
        .build();
  }
}
