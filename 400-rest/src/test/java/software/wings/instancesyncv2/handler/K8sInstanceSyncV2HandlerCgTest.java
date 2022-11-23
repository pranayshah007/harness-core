/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.seeddata.SampleDataProviderConstants.HARNESS_SAMPLE_APP;
import static io.harness.seeddata.SampleDataProviderConstants.HARNESS_SAMPLE_APP_DESC;

import static software.wings.beans.infrastructure.instance.InstanceType.KUBERNETES_CONTAINER_INSTANCE;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.dl.WingsMongoPersistence;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class K8sInstanceSyncV2HandlerCgTest extends CategoryTest {
  @InjectMocks K8sInstanceSyncV2HandlerCg k8sInstanceSyncV2HandlerCg;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private InstanceUtils instanceUtil;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private EnvironmentService environmentService;
  @Mock private AppService appService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private WingsMongoPersistence wingsPersistence;
  @Mock private ContainerSync containerSync;
  @Mock private InstanceService instanceService;

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchInfraConnectorDetails() {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withAccountId("accountId")
            .withAppId("appId")
            .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
            .build();

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle =
        k8sInstanceSyncV2HandlerCg.fetchInfraConnectorDetails(settingAttribute);
    assertThat(perpetualTaskExecutionBundle).isNotNull();
    assertThat(perpetualTaskExecutionBundle.getTaskParams().getTypeUrl())
        .isEqualTo("type.googleapis.com/io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void mergeReleaseIdentifiers() {
    Set<CgReleaseIdentifiers> existingIdentifiers = Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                                              .releaseName("releaseName")
                                                                              .clusterName("clusterName")
                                                                              .namespace("namespace1")
                                                                              .isHelmDeployment(false)
                                                                              .build());
    Set<CgReleaseIdentifiers> newIdentifiers = Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                                         .releaseName("releaseName")
                                                                         .clusterName("clusterName")
                                                                         .namespace("namespace")
                                                                         .isHelmDeployment(false)
                                                                         .build());
    Set<CgReleaseIdentifiers> result =
        k8sInstanceSyncV2HandlerCg.mergeReleaseIdentifiers(existingIdentifiers, newIdentifiers);
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void prepareTaskDetails() {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .appId("appId")
                                              .infraMappingId("infraMappingId")
                                              .accountId("accountId")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .releaseName("releaseName")
                                                                  .namespace("namespace")
                                                                  .clusterName("clusterName")
                                                                  .build())
                                              .build();
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        k8sInstanceSyncV2HandlerCg.prepareTaskDetails(deploymentSummary, "cloudProviderId", "perpetualId");
    assertThat(instanceSyncTaskDetails).isNotNull();
    assertThat(instanceSyncTaskDetails.getAccountId()).isEqualTo("accountId");
    assertThat(instanceSyncTaskDetails.getPerpetualTaskId()).isEqualTo("perpetualId");
    assertThat(instanceSyncTaskDetails.getCloudProviderId()).isEqualTo("cloudProviderId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void createReleaseIdentifiersTest() {
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder()
                                        .releaseName("releaseName")
                                        .namespace("namespace")
                                        .namespaces(Set.of("namespace1", "namespace2"))
                                        .clusterName("clusterName")
                                        .build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    Set<CgReleaseIdentifiers> releaseIdentifiers =
        k8sInstanceSyncV2HandlerCg.createReleaseIdentifiers(deploymentSummary);
    assertThat(releaseIdentifiers).isNotNull();
    assertThat(releaseIdentifiers.size()).isEqualTo(3);
    assertThat(releaseIdentifiers.stream().findAny().get().getClass()).isEqualTo(CgK8sReleaseIdentifier.class);

    deploymentInfo = ContainerDeploymentInfoWithLabels.builder()
                         .releaseName("releaseName")
                         .namespace("namespace")
                         .clusterName("clusterName")
                         .containerInfoList(
                             new ArrayList<>(Arrays.asList(ContainerInfo.builder().containerId("containerId").build())))
                         .build();
    DeploymentSummary deploymentSummary1 = DeploymentSummary.builder().deploymentInfo(deploymentInfo).build();
    Set<CgReleaseIdentifiers> newIdentifiers = k8sInstanceSyncV2HandlerCg.createReleaseIdentifiers(deploymentSummary1);
    assertThat(newIdentifiers).isNotNull();
    CgK8sReleaseIdentifier identifier = (CgK8sReleaseIdentifier) newIdentifiers.stream().findFirst().get();
    assertThat(identifier.getReleaseName()).isEqualTo("releaseName");
    assertThat(identifier.isHelmDeployment()).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getDeploymentReleaseDetails() {
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        InstanceSyncTaskDetails.builder()
            .cloudProviderId("cloudProviderId")
            .perpetualTaskId("perpetualId")
            .appId("appId")
            .accountId("accountId")
            .uuid("uuid")
            .infraMappingId("infraMappingId")
            .releaseIdentifiers(Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                          .releaseName("releaseName")
                                                          .clusterName("clusterName")
                                                          .namespace("namespace1")
                                                          .isHelmDeployment(false)
                                                          .build()))
            .build();

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    InfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder()
                                             .appId("appId")
                                             .infraMappingType("K8s")
                                             .accountId("accountId")
                                             .build();
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(K8sClusterConfig.builder().clusterName("clusterName").build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(), any());

    List<CgDeploymentReleaseDetails> cgDeploymentReleaseDetails =
        k8sInstanceSyncV2HandlerCg.getDeploymentReleaseDetails(instanceSyncTaskDetails);
    assertThat(cgDeploymentReleaseDetails).isNotNull();
    assertThat(cgDeploymentReleaseDetails.size()).isEqualTo(1);
    assertThat(cgDeploymentReleaseDetails.get(0).getInfraMappingType()).isEqualTo("K8s");
    assertThat(cgDeploymentReleaseDetails.get(0).getInfraMappingId()).isEqualTo("infraMappingId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetDeployedInstances() throws InterruptedException {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .appId("appId")
                                              .infraMappingId("infraMappingId")
                                              .accountId("accountId")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .releaseName("releaseName")
                                                                  .namespace("namespace")
                                                                  .clusterName("clusterName")
                                                                  .build())
                                              .build();
    InfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder()
                                             .appId("appId")
                                             .infraMappingType("K8s")
                                             .accountId("accountId")
                                             .envId("envId")
                                             .build();
    infraMapping.setServiceId("serviceId");
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(KUBERNETES_CONTAINER_INSTANCE).when(instanceUtil).getInstanceType(anyString());
    doReturn(Application.Builder.anApplication()
                 .name(HARNESS_SAMPLE_APP)
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(appService)
        .get(anyString());

    doReturn(Environment.Builder.anEnvironment()
                 .name("envName")
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(environmentService)
        .get(anyString(), anyString(), anyBoolean());

    doReturn(Service.builder()
                 .name("serviceName")
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    doReturn(Collections.singletonList(
                 K8sPod.builder()
                     .name("podName")
                     .namespace("namespace")
                     .releaseName("releaseName")
                     .containerList(Collections.singletonList(
                         K8sContainer.builder().containerId("containerId").image("image").name("nginx").build()))
                     .build()))
        .when(k8sStateHelper)
        .fetchPodListForCluster(any(), any(), anyString(), anyString());

    CgK8sReleaseIdentifier cgK8sReleaseIdentifier = CgK8sReleaseIdentifier.builder()
                                                        .releaseName("releaseName")
                                                        .clusterName("clusterName")
                                                        .namespace("namespace1")
                                                        .isHelmDeployment(false)
                                                        .build();

    Set<CgReleaseIdentifiers> cgReleaseIdentifiers = new HashSet<>();
    cgReleaseIdentifiers.add(cgK8sReleaseIdentifier);

    Map<CgReleaseIdentifiers, List<Instance>> instancesMap =
        k8sInstanceSyncV2HandlerCg.getDeployedInstances(cgReleaseIdentifiers, deploymentSummary);
    assertThat(instancesMap).isNotNull();
    assertThat(instancesMap.size()).isEqualTo(1);
    List<Instance> instances = instancesMap.get(cgK8sReleaseIdentifier);
    assertThat(instances.size()).isEqualTo(1);
    K8sPodInfo k8sPodInfo = (K8sPodInfo) instances.get(0).getInstanceInfo();
    assertThat(instances.get(0).getEnvId()).isEqualTo("envId");
    assertThat(instances.get(0).getServiceId()).isEqualTo("serviceId");
    assertThat(k8sPodInfo.getClusterName()).isEqualTo("clusterName");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetDeployedInstancesOverride() {
    List<InstanceInfo> instanceInfos = Arrays.asList(
        K8sPodInfo.builder()
            .podName("podName")
            .namespace("namespace")
            .releaseName("releaseName")
            .clusterName("clusterName")
            .containers(Collections.singletonList(
                K8sContainerInfo.builder().containerId("containerId").image("image").name("nginx").build()))
            .build());
    Instance instance =
        Instance.builder()
            .instanceInfo(
                K8sPodInfo.builder()
                    .podName("podName")
                    .namespace("namespace")
                    .releaseName("releaseName")
                    .clusterName("clusterName")
                    .containers(Collections.singletonList(
                        K8sContainerInfo.builder().containerId("containerId").image("image").name("nginx").build()))
                    .build())
            .appId("appId")
            .infraMappingId("infraMappingId")
            .accountId("accountId")
            .appName("appName")
            .serviceId("serviceId")
            .serviceName("serviceName")
            .computeProviderId("computeProviderId")
            .computeProviderName("computeProviderId")
            .envId("envId")
            .build();
    InfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder()
                                             .appId("appId")
                                             .infraMappingType("K8s")
                                             .accountId("accountId")
                                             .envId("envId")
                                             .build();
    infraMapping.setServiceId("serviceId");
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(KUBERNETES_CONTAINER_INSTANCE).when(instanceUtil).getInstanceType(anyString());
    doReturn(Application.Builder.anApplication()
                 .name(HARNESS_SAMPLE_APP)
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(appService)
        .get(anyString());

    doReturn(Environment.Builder.anEnvironment()
                 .name("envName")
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(environmentService)
        .get(anyString(), anyString(), anyBoolean());

    doReturn(Service.builder()
                 .name("serviceName")
                 .description(HARNESS_SAMPLE_APP_DESC)
                 .accountId("accountId")
                 .sample(true)
                 .build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    CgK8sReleaseIdentifier cgK8sReleaseIdentifier = CgK8sReleaseIdentifier.builder()
                                                        .releaseName("releaseName")
                                                        .clusterName("clusterName")
                                                        .namespace("namespace")
                                                        .isHelmDeployment(false)
                                                        .build();

    doAnswer(invocation -> {
      String podId = String.valueOf((byte[]) invocation.getArgument(0));
      return createK8sPod(podId, "releaseName", "namespace");
    })
        .when(kryoSerializer)
        .asObject((byte[]) any());
    List<String> podIds = Arrays.asList("instance1", "instance2", "instance3");
    InstanceSyncData instanceSyncData =
        InstanceSyncData.newBuilder()
            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
            .addAllInstanceData(podIds.stream().map(pod -> ByteString.copyFrom(pod.getBytes())).collect(toList()))
            .setTaskDetailsId("taskId")
            .build();

    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .appId("appId")
                                              .infraMappingId("infraMappingId")
                                              .accountId("accountId")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .releaseName("releaseName")
                                                                  .namespace("namespace")
                                                                  .clusterName("clusterName")
                                                                  .build())
                                              .build();

    Map<CgReleaseIdentifiers, DeploymentSummary> deploymentSummaryMap = new HashMap<>();

    Map<CgReleaseIdentifiers, InstanceSyncData> instanceSyncDataMap = new HashMap<>();
    Map<CgReleaseIdentifiers, List<Instance>> instancesInDbMap = new HashMap<>();
    instanceSyncDataMap.put(cgK8sReleaseIdentifier, instanceSyncData);
    instancesInDbMap.put(cgK8sReleaseIdentifier, Collections.singletonList(instance));
    List<Instance> instancesInDb = Collections.singletonList(instance);
    deploymentSummaryMap.put(cgK8sReleaseIdentifier, deploymentSummary);
    instancesInDbMap.put(cgK8sReleaseIdentifier, instancesInDb);
    Map<CgReleaseIdentifiers, List<Instance>> instancesMap =
        k8sInstanceSyncV2HandlerCg.groupInstanceSyncData(deploymentSummaryMap, instanceSyncDataMap);
    assertThat(instancesMap).isNotNull();
    assertThat(instancesMap.size()).isEqualTo(1);
    List<Instance> instances = instancesMap.get(cgK8sReleaseIdentifier);
    assertThat(instances.size()).isEqualTo(3);
    assertThat(instances.get(0).getEnvId()).isEqualTo("envId");
  }
  private K8sPodInfo createK8sPod(String id, String releaseName, String namespace) {
    return K8sPodInfo.builder()
        .ip(id)
        .podName(id)
        .releaseName(releaseName)
        .namespace(namespace)
        .containers(
            Collections.singletonList(K8sContainerInfo.builder().name(id).image("test").containerId(id).build()))
        .build();
  }
}
