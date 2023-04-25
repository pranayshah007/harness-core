/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.InstanceSyncConstants.CONTAINER_SERVICE_NAME;
import static software.wings.service.InstanceSyncConstants.CONTAINER_TYPE;
import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.NAMESPACE;
import static software.wings.service.InstanceSyncConstants.RELEASE_NAME;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Durations;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ContainerInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  @Mock private InstanceService instanceService;
  @Mock private PerpetualTaskService perpetualTaskService;
  private static final PerpetualTaskSchedule SCHEDULE = PerpetualTaskSchedule.newBuilder()
                                                            .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                                            .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                                            .build();

  @InjectMocks private ContainerInstanceSyncPerpetualTaskCreator perpetualTaskCreator;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private FeatureFlagService featureFlagService;
  private ContainerInfrastructureMapping infrastructureMapping;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    infrastructureMapping = getContainerInfrastructureMapping();
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(ENV_NAME)
                        .build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(SERVICE_NAME)
                        .build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication()
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .name(APP_NAME)
                        .build());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createK8sPerpetualTasks() {
    doReturn(false).when(featureFlagService).isEnabled(eq(FeatureName.INSTANCE_SYNC_V2_CG), any());
    doReturn(getK8sContainerInstances())
        .when(instanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));

    final List<String> perpetualTaskIds = perpetualTaskCreator.createPerpetualTasks(infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(perpetualTaskIds).isNotEmpty();
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(CONTAINER_TYPE)))
        .containsOnly("K8S");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(NAMESPACE)))
        .containsExactlyInAnyOrder("namespace-1", "namespace-2", "namespace-3");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(RELEASE_NAME)))
        .containsExactlyInAnyOrder("release-1", "release-2", "release-3");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void createK8sPerpetualTasksInstanceSyncV2() {
    doReturn(true).when(featureFlagService).isEnabled(eq(FeatureName.INSTANCE_SYNC_V2_CG), any());
    doReturn(getK8sContainerInstances())
        .when(instanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));

    ContainerInfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setAccountId(InstanceSyncTestConstants.ACCOUNT_ID);
    infraMapping.setAppId(InstanceSyncTestConstants.APP_ID);
    infraMapping.setUuid(InstanceSyncTestConstants.INFRA_MAPPING_ID);
    infraMapping.setComputeProviderType(KUBERNETES_CLUSTER.name());
    infraMapping.setDisplayName("infraName");
    infraMapping.setAccountId(ACCOUNT_ID);
    final List<String> perpetualTaskIds = perpetualTaskCreator.createPerpetualTasks(infraMapping);
    assertThat(perpetualTaskIds).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCreatePerpetualTasksBackup() {
    List<PerpetualTaskRecord> existingRecords =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(PerpetualTaskClientContext.builder()
                                      .clientParams(ImmutableMap.of(
                                          CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-1", RELEASE_NAME, "release-1"))
                                      .build())
                   .build());

    List<PerpetualTaskRecord> perpetualTaskRecords = perpetualTaskCreator.createPerpetualTasksBackup(
        asList(
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("namespace-1").releaseName("release-1").build())
                .build(),
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("namespace-2").releaseName("release-2").build())
                .build()),
        existingRecords, infrastructureMapping);

    assertThat(perpetualTaskRecords).isNotEmpty();
    assertThat(perpetualTaskRecords.size()).isEqualTo(1);
    assertThat(perpetualTaskRecords.get(0).getPerpetualTaskType()).isEqualTo("CONTAINER_INSTANCE_SYNC");
    assertThat(perpetualTaskRecords.get(0).getClientContext().getClientParams().get("releaseName"))
        .isEqualTo("release-2");
    assertThat(perpetualTaskRecords.get(0).getClientContext().getClientParams().get("containerType")).isEqualTo("K8S");
    assertThat(perpetualTaskRecords.get(0).getClientContext().getClientParams().get("namespace"))
        .isEqualTo("namespace-2");
    assertThat(perpetualTaskRecords.get(0).getClientContext().getClientParams().get("infrastructureMappingId"))
        .isEqualTo("infraMapping_Id");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRestorePerpetualTask() {
    PerpetualTaskRecord perpetualTask = PerpetualTaskRecord.builder()
                                            .accountId(ACCOUNT_ID)
                                            .taskDescription("")
                                            .clientContext(PerpetualTaskClientContext.builder()
                                                               .clientParams(ImmutableMap.of(CONTAINER_TYPE, "K8S",
                                                                   NAMESPACE, "namespace-1", RELEASE_NAME, "release-1"))
                                                               .build())
                                            .build();

    List<PerpetualTaskRecord> existingPerpetualTasks =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(PerpetualTaskClientContext.builder()
                                      .clientParams(ImmutableMap.of(
                                          CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-2", RELEASE_NAME, "release-1"))
                                      .build())
                   .build(),
            PerpetualTaskRecord.builder()
                .clientContext(PerpetualTaskClientContext.builder()
                                   .clientParams(ImmutableMap.of(
                                       CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-1", RELEASE_NAME, "release-2"))
                                   .build())
                .build());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));
    Optional<String> PerpetualTaskRecordId =
        perpetualTaskCreator.restorePerpetualTask(perpetualTask, existingPerpetualTasks);

    assertThat(PerpetualTaskRecordId.get()).isEqualTo("perpetual-task-id");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRestorePerpetualTaskFilter() {
    PerpetualTaskRecord perpetualTask = PerpetualTaskRecord.builder()
                                            .accountId(ACCOUNT_ID)
                                            .taskDescription("")
                                            .clientContext(PerpetualTaskClientContext.builder()
                                                               .clientParams(ImmutableMap.of(CONTAINER_TYPE, "K8S",
                                                                   NAMESPACE, "namespace-1", RELEASE_NAME, "release-1"))
                                                               .build())
                                            .build();

    List<PerpetualTaskRecord> existingPerpetualTasks =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(PerpetualTaskClientContext.builder()
                                      .clientParams(ImmutableMap.of(
                                          CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-1", RELEASE_NAME, "release-1"))
                                      .build())
                   .build(),
            PerpetualTaskRecord.builder()
                .clientContext(PerpetualTaskClientContext.builder()
                                   .clientParams(ImmutableMap.of(
                                       CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-1", RELEASE_NAME, "release-2"))
                                   .build())
                .build());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));
    Optional<String> PerpetualTaskRecordId =
        perpetualTaskCreator.restorePerpetualTask(perpetualTask, existingPerpetualTasks);

    assertThat(PerpetualTaskRecordId.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createAzurePerpetualTasks() {
    doReturn(false).when(featureFlagService).isEnabled(eq(FeatureName.INSTANCE_SYNC_V2_CG), any());
    doReturn(getAzureContainerInstances())
        .when(instanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));

    final List<String> perpetualTaskIds = perpetualTaskCreator.createPerpetualTasks(infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(CONTAINER_TYPE)))
        .containsOnly("");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(NAMESPACE)))
        .containsExactlyInAnyOrder("namespace-1", "namespace-2", "namespace-3");
    assertThat(captor.getAllValues()
                   .stream()
                   .map(PerpetualTaskClientContext::getClientParams)
                   .map(x -> x.get(CONTAINER_SERVICE_NAME)))
        .containsExactlyInAnyOrder("service-1", "service-2", "service-3");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createAwsPerpetualTasks() {
    doReturn(false).when(featureFlagService).isEnabled(eq(FeatureName.INSTANCE_SYNC_V2_CG), any());
    doReturn(getAwsContainerInstances())
        .when(instanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perpetual-task-id")
        .when(perpetualTaskService)
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID), any(),
            any(), eq(false), eq(""));

    final List<String> perpetualTaskIds = perpetualTaskCreator.createPerpetualTasks(infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(3))
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(CONTAINER_TYPE)))
        .containsOnly("");
    assertThat(captor.getAllValues()
                   .stream()
                   .map(PerpetualTaskClientContext::getClientParams)
                   .map(x -> x.get(CONTAINER_SERVICE_NAME)))
        .containsExactlyInAnyOrder("service-1", "service-2", "service-3");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createK8sPerpetualTasksForNewDeployment() {
    List<PerpetualTaskRecord> existingRecords =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(PerpetualTaskClientContext.builder()
                                      .clientParams(ImmutableMap.of(
                                          CONTAINER_TYPE, "K8S", NAMESPACE, "namespace-1", RELEASE_NAME, "release-1"))
                                      .build())
                   .build());

    perpetualTaskCreator.createPerpetualTasksForNewDeployment(
        asList(
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("namespace-1").releaseName("release-1").build())
                .build(),
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deploymentInfo(K8sDeploymentInfo.builder().namespace("namespace-2").releaseName("release-2").build())
                .build()),
        existingRecords, infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(CONTAINER_TYPE)))
        .containsExactlyInAnyOrder("K8S");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(NAMESPACE)))
        .containsExactlyInAnyOrder("namespace-2");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(RELEASE_NAME)))
        .containsExactlyInAnyOrder("release-2");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void createContainerServicePerpetualTasksForNewDeployment() {
    List<PerpetualTaskRecord> existingRecords =
        asList(PerpetualTaskRecord.builder()
                   .clientContext(
                       PerpetualTaskClientContext.builder()
                           .clientParams(ImmutableMap.of(NAMESPACE, "namespace-1", CONTAINER_SERVICE_NAME, "service-1"))
                           .build())
                   .build());

    perpetualTaskCreator.createPerpetualTasksForNewDeployment(
        asList(DeploymentSummary.builder()
                   .appId(InstanceSyncTestConstants.APP_ID)
                   .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                   .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                   .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                       .namespace("namespace-1")
                                       .containerSvcName("service-1")
                                       .build())
                   .build(),
            DeploymentSummary.builder()
                .appId(InstanceSyncTestConstants.APP_ID)
                .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .deploymentInfo(ContainerDeploymentInfoWithNames.builder()
                                    .namespace("namespace-2")
                                    .containerSvcName("service-2")
                                    .build())
                .build()),
        existingRecords, infrastructureMapping);

    ArgumentCaptor<PerpetualTaskClientContext> captor = ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    verify(perpetualTaskService, times(1))
        .createTask(eq(PerpetualTaskType.CONTAINER_INSTANCE_SYNC), eq(InstanceSyncTestConstants.ACCOUNT_ID),
            captor.capture(), eq(SCHEDULE), eq(false),
            eq("Application: [appName], Service: [serviceName], Environment: [envName], Infrastructure: [infraName]"));

    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(CONTAINER_TYPE)))
        .containsOnly("");
    assertThat(
        captor.getAllValues().stream().map(PerpetualTaskClientContext::getClientParams).map(x -> x.get(NAMESPACE)))
        .containsExactlyInAnyOrder("namespace-2");
    assertThat(captor.getAllValues()
                   .stream()
                   .map(PerpetualTaskClientContext::getClientParams)
                   .map(x -> x.get(CONTAINER_SERVICE_NAME)))
        .containsExactlyInAnyOrder("service-2");
  }

  private ContainerInfrastructureMapping getContainerInfrastructureMapping() {
    ContainerInfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setAccountId(InstanceSyncTestConstants.ACCOUNT_ID);
    infraMapping.setAppId(InstanceSyncTestConstants.APP_ID);
    infraMapping.setUuid(InstanceSyncTestConstants.INFRA_MAPPING_ID);
    infraMapping.setDisplayName("infraName");
    infraMapping.setAccountId(ACCOUNT_ID);
    return infraMapping;
  }

  private List<Instance> getK8sContainerInstances() {
    return asList(Instance.builder()
                      .uuid("id-1")
                      .instanceInfo(K8sPodInfo.builder().namespace("namespace-1").releaseName("release-1").build())
                      .build(),
        Instance.builder()
            .uuid("id-2")
            .instanceInfo(K8sPodInfo.builder().namespace("namespace-2").releaseName("release-2").build())
            .build(),
        Instance.builder()
            .uuid("id-3")
            .instanceInfo(K8sPodInfo.builder().namespace("namespace-3").releaseName("release-3").build())
            .build(),
        Instance.builder()
            .uuid("id-5")
            .instanceInfo(K8sPodInfo.builder().namespace("namespace-1").releaseName("release-1").build())
            .build());
  }

  private List<Instance> getAzureContainerInstances() {
    return asList(
        Instance.builder()
            .uuid("id-1")
            .instanceInfo(
                KubernetesContainerInfo.builder().namespace("namespace-1").controllerName("service-1").build())
            .build(),
        Instance.builder()
            .uuid("id-2")
            .instanceInfo(
                KubernetesContainerInfo.builder().namespace("namespace-2").controllerName("service-2").build())
            .build(),
        Instance.builder()
            .uuid("id-3")
            .instanceInfo(
                KubernetesContainerInfo.builder().namespace("namespace-3").controllerName("service-3").build())
            .build(),
        Instance.builder()
            .uuid("id-5")
            .instanceInfo(
                KubernetesContainerInfo.builder().namespace("namespace-1").controllerName("service-1").build())
            .build());
  }

  private List<Instance> getAwsContainerInstances() {
    return asList(Instance.builder()
                      .uuid("id-1")
                      .instanceInfo(EcsContainerInfo.Builder.anEcsContainerInfo().withServiceName("service-1").build())
                      .build(),
        Instance.builder()
            .uuid("id-2")
            .instanceInfo(EcsContainerInfo.Builder.anEcsContainerInfo().withServiceName("service-2").build())
            .build(),
        Instance.builder()
            .uuid("id-3")
            .instanceInfo(EcsContainerInfo.Builder.anEcsContainerInfo().withServiceName("service-3").build())
            .build(),
        Instance.builder()
            .uuid("id-5")
            .instanceInfo(EcsContainerInfo.Builder.anEcsContainerInfo().withServiceName("service-1").build())
            .build());
  }
}
