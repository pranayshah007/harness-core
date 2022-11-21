package software.wings.instancesyncv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.HarnessLabelValues.colorBlue;
import static io.harness.k8s.model.HarnessLabelValues.colorGreen;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.container.Label.Builder.aLabel;
import static software.wings.beans.infrastructure.instance.InstanceType.KUBERNETES_CONTAINER_INSTANCE;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.*;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.mongo.MongoPersistence;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.DirectK8sReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsMongoPersistence;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.handler.K8sInstanceSyncV2HandlerCg;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.instance.InstanceSyncFlow;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class InstanceSyncV2ServiceK8sHandlerIntegrationTest extends CategoryTest {
  @Mock InfrastructureMapping infrastructureMapping = new DirectKubernetesInfrastructureMapping();

  @InjectMocks private K8sInstanceSyncV2HandlerCg k8sHandler;

  @InjectMocks CgInstanceSyncServiceV2 cgInstanceSyncServiceV2;
  @Mock CgInstanceSyncV2HandlerFactory handlerFactory;
  @Mock private WingsMongoPersistence mongoPersistence;

  @Mock private DelegateServiceGrpcClient delegateServiceClient;
  @Mock private InstanceUtils instanceUtil;
  @Mock private CgInstanceSyncTaskDetailsService taskDetailsService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsServiceImpl cloudProviderService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private transient K8sStateHelper k8sStateHelper;
  @Mock private ContainerSync containerSync;
  @Mock private AppService appService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock private InstanceService instanceService;
  @Mock private DeploymentService deploymentService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name("appId").uuid("appId").accountId("accountId").build())
        .when(appService)
        .get(any());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build()).when(serviceResourceService).getWithDetails(any(), any());

    InfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder()
                                             .appId("appId")
                                             .infraMappingType("K8s")
                                             .accountId("accountId")
                                             .build();
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());

    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mongoPersistence).createQuery(any(), any());
    doReturn(mockQuery).when(mongoPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).project(any(), anyBoolean());
    doReturn(mockQuery).when(mockQuery).disableValidation();

    HashMap<String, String> metadata = new HashMap<>();
    metadata.put("image", "test");
    doReturn(Artifact.Builder.anArtifact()
                 .withUuid("newArtifactId")
                 .withDisplayName("newArtifactId")
                 .withArtifactStreamId("artifactStreamId")
                 .withArtifactSourceName("artifactSourceName")
                 .withMetadata(new ArtifactMetadata(metadata))
                 .build())
        .when(mockQuery)
        .get();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSyncInstances_AddAndNoDeleteInstance_Rollback() throws Exception {
    final List<Instance> instancesInDb = asList(buildInstanceWithHelm("pod:0",
                                                    KubernetesContainerInfo.builder()
                                                        .namespace("namespace")
                                                        .releaseName("releaseName")
                                                        .clusterName("clusterName")
                                                        .serviceName("serviceName")
                                                        .controllerName("controllerName:0")
                                                        .podName("pod:0")
                                                        .build()),
        buildInstanceWithHelm("pod:3",
            KubernetesContainerInfo.builder()
                .namespace("namespace")
                .releaseName("releaseName")
                .clusterName("clusterName")
                .serviceName("serviceName")
                .controllerName("controllerName:0")
                .podName("pod:3")
                .build()),
        buildInstanceWithHelm("pod:4",
            KubernetesContainerInfo.builder()
                .namespace("namespace")
                .releaseName("releaseName")
                .clusterName("clusterName")
                .serviceName("serviceName")
                .controllerName("controllerName:1")
                .podName("pod:4")
                .build()));

    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .artifactBuildNum("1.0")
            .appId("appId")
            .accountId("accountId")
            .infraMappingId("infraMappingId")
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .namespaces(asList("namespace"))
                                .clusterName("clusterName")
                                .containerInfoList(asList(ContainerInfo.builder()
                                                              .namespace("namespace")
                                                              .releaseName("releaseName")
                                                              .workloadName("controllerName:0")
                                                              .podName("pod:0")
                                                              .build()))
                                .releaseName("releaseName")
                                .build())
            .build();

    DeploymentSummary deploymentSummary2 =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .artifactBuildNum("1.0")
            .appId("appId")
            .accountId("accountId")
            .infraMappingId("infraMappingId")
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .namespaces(asList("namespace"))
                                .clusterName("clusterName")
                                .containerInfoList(asList(ContainerInfo.builder()
                                                              .namespace("namespace")
                                                              .releaseName("releaseName")
                                                              .workloadName("controllerName:1")
                                                              .podName("pod:0")
                                                              .build()))
                                .releaseName("releaseName")
                                .build())
            .build();

    DeploymentEvent deploymentEvent = DeploymentEvent.builder()
                                          .deploymentSummaries(asList(deploymentSummary, deploymentSummary2))
                                          .isRollback(true)
                                          .build();

    doReturn(Optional.of(
                 DeploymentSummary.builder()
                     .deploymentInfo(ContainerDeploymentInfoWithNames.builder().containerSvcName("service_a_1").build())
                     .accountId("accountId")
                     .infraMappingId("infraMappingId")
                     .workflowExecutionId("newWorkflow")
                     .stateExecutionInstanceId("stateExecutionInstanceId")
                     .artifactBuildNum("1")
                     .artifactName("old")
                     .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    ContainerSyncResponse containerSyncResponse =
        ContainerSyncResponse.builder()
            .containerInfoList(
                asList(createKubernetesContainerInfo("pod:0", "releaseName", "namespace", "controllerName:0"),
                    createKubernetesContainerInfo("pod:1", "releaseName", "namespace", "controllerName:0")))
            .build();

    assertSavedAndDeletedInstancesOnNewDeploymentHelm(
        deploymentEvent, instancesInDb, containerSyncResponse, asList("pod:0", "pod:1"), emptyList(), null);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_AddAndNoDeleteInstance_Helm() throws Exception {
    final List<Instance> instancesInDb = emptyList();

    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .artifactBuildNum("1.0")
            .appId("appId")
            .accountId("accountId")
            .infraMappingId("infraMappingId")
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .namespaces(asList("namespace"))
                                .clusterName("clusterName")
                                .containerInfoList(asList(ContainerInfo.builder()
                                                              .namespace("namespace")
                                                              .releaseName("releaseName")
                                                              .workloadName("controllerName:0")
                                                              .podName("pod:0")
                                                              .build()))
                                .releaseName("releaseName")
                                .build())
            .build();

    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder().deploymentSummaries(singletonList(deploymentSummary)).isRollback(false).build();

    ContainerSyncResponse containerSyncResponse =
        ContainerSyncResponse.builder()
            .containerInfoList(
                asList(createKubernetesContainerInfo("pod:0", "releaseName", "namespace", "controllerName:0"),
                    createKubernetesContainerInfo("pod:1", "releaseName", "namespace", "controllerName:0")))
            .build();

    assertSavedAndDeletedInstancesOnNewDeploymentHelm(
        deploymentEvent, instancesInDb, containerSyncResponse, asList("pod:0", "pod:1"), emptyList(), null);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_AddAndDeleteInstance_Helm() throws Exception {
    final List<Instance> instancesInDb = asList(buildInstanceWithHelm("pod:0",
                                                    KubernetesContainerInfo.builder()
                                                        .namespace("namespace")
                                                        .releaseName("releaseName")
                                                        .clusterName("clusterName")
                                                        .serviceName("serviceName")
                                                        .controllerName("controllerName:0")
                                                        .podName("pod:0")
                                                        .build()),
        buildInstanceWithHelm("pod:3",
            KubernetesContainerInfo.builder()
                .namespace("namespace")
                .releaseName("releaseName")
                .clusterName("clusterName")
                .serviceName("serviceName")
                .controllerName("controllerName:0")
                .podName("pod:3")
                .build()),
        buildInstanceWithHelm("pod:4",
            KubernetesContainerInfo.builder()
                .namespace("namespace")
                .releaseName("releaseName")
                .clusterName("clusterName")
                .serviceName("serviceName")
                .controllerName("controllerName:1")
                .podName("pod:4")
                .build()));

    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .artifactId("newArtifactId")
            .artifactStreamId("artifactStreamId")
            .workflowExecutionId("newWorkflow")
            .workflowExecutionName("workflowName")
            .artifactBuildNum("1.0")
            .appId("appId")
            .accountId("accountId")
            .infraMappingId("infraMappingId")
            .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                .namespaces(asList("namespace"))
                                .clusterName("clusterName")
                                .containerInfoList(asList(ContainerInfo.builder()
                                                              .namespace("namespace")
                                                              .releaseName("releaseName")
                                                              .workloadName("controllerName:0")
                                                              .podName("pod:0")
                                                              .build()))
                                .releaseName("releaseName")
                                .build())
            .build();

    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder().deploymentSummaries(singletonList(deploymentSummary)).isRollback(false).build();

    ContainerSyncResponse containerSyncResponse =
        ContainerSyncResponse.builder()
            .containerInfoList(
                asList(createKubernetesContainerInfo("pod:0", "releaseName", "namespace", "controllerName:0"),
                    createKubernetesContainerInfo("pod:1", "releaseName", "namespace", "controllerName:0")))
            .build();

    assertSavedAndDeletedInstancesOnNewDeploymentHelm(
        deploymentEvent, instancesInDb, containerSyncResponse, asList("pod:0", "pod:1"), asList("pod:3"), null);
  }

  private HelmChartInfo helmChartInfoWithVersion(String version) {
    return HelmChartInfo.builder().version(version).name("helmChartName").repoUrl("repoUrl").build();
  }
  private Instance buildInstanceWithHelm(String podName, InstanceInfo instanceInfo) {
    return Instance.builder()
        .uuid(podName)
        .accountId("accoutId")
        .appId("appId")
        .computeProviderId("COMPUTE_PROVIDER_NAME")
        .appName("appName")
        .envId("envId")
        .envName("envName")
        .envType(EnvironmentType.PROD)
        .infraMappingId("infraMappingId")
        .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.getName())
        .instanceType(KUBERNETES_CONTAINER_INSTANCE)
        .containerInstanceKey(ContainerInstanceKey.builder().namespace("namespace").containerId(podName).build())
        .instanceInfo(instanceInfo)
        .lastWorkflowExecutionName("Current Workflow")
        .build();
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
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesWithMatchingWorkloadName() {
    String namespace = "namespace";
    String releaseName = "releaseName";
    final List<Instance> instances = asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .instanceType(KUBERNETES_CONTAINER_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:0").namespace(namespace).build())
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
            .containerInstanceKey(ContainerInstanceKey.builder().containerId("pod:1").namespace(namespace).build())
            .instanceInfo(KubernetesContainerInfo.builder()
                              .clusterName(KUBE_CLUSTER)
                              .serviceName("service_a_1")
                              .controllerName("controllerName:1")
                              .podName("pod:1")
                              .build())
            .build());
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(null).when(kryoSerializer).asObject((byte[]) any());

    List<K8sPodInfo> k8sPodInfos = new ArrayList<>();
    InstanceSyncData instanceSyncData =
        InstanceSyncData.newBuilder()
            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
            .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                            .setReleaseName(releaseName)
                                            .setNamespace(namespace)
                                            .setIsHelm(true)
                                            .setContainerServiceName("controllerName:1")
                                            .build()))
            .addAllInstanceData(k8sPodInfos.parallelStream()
                                    .map(pod -> ByteString.copyFrom(kryoSerializer.asBytes(pod)))
                                    .collect(toList()))
            .setTaskDetailsId("taskId")
            .build();

    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("perpetualTaskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                 .setAccountId("accountId");

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .releaseIdentifiers(Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                               .releaseName(releaseName)
                                                               .namespace(namespace)
                                                               .lastDeploymentSummaryId("lastDeploymentSummaryId")
                                                               .isHelmDeployment(false)
                                                               .build()))
                 .cloudProviderId("cpId")
                 .build())
        .when(taskDetailsService)
        .getForId(any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(InstanceType.KUBERNETES_CONTAINER_INSTANCE).when(instanceUtil).getInstanceType(any());

    doReturn(DeploymentSummary.builder()
                 .appId("appId")
                 .infraMappingId("infraMappingId")
                 .accountId("accountId")
                 .deploymentInfo(K8sDeploymentInfo.builder()
                                     .releaseName(releaseName)
                                     .namespace(namespace)
                                     .clusterName("clusterName")
                                     .build())
                 .build())
        .when(deploymentService)
        .get("lastDeploymentSummaryId");

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));

    builder.addInstanceData(instanceSyncData);
    CgInstanceSyncResponse instanceSyncResponse = builder.build();

    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", instanceSyncResponse);

    verify(instanceService, times(1)).delete(emptySet());
    verify(instanceService, never()).delete(Sets.newHashSet(INSTANCE_2_ID));
    verify(instanceService, never()).delete(Sets.newHashSet(INSTANCE_1_ID));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldNotUpdateExistingArtifactIdOnNewDeployment() throws Exception {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .artifactId("newArtifactId")
                                              .artifactStreamId("artifactStreamId")
                                              .workflowExecutionId("newWorkflow")
                                              .workflowExecutionName("workflowName")
                                              .artifactBuildNum("1.0")
                                              .appId("appId")
                                              .accountId("accountId")
                                              .infraMappingId("infraMappingId")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .namespace("default")
                                                                  .clusterName("clusterName")
                                                                  .blueGreenStageColor("blue")
                                                                  .releaseName("releaseName")
                                                                  .releaseNumber(2)
                                                                  .build())
                                              .build();

    DeploymentEvent deploymentEvent =
        DeploymentEvent.builder().deploymentSummaries(singletonList(deploymentSummary)).isRollback(false).build();

    HashMap<String, String> metadata = new HashMap<>();
    metadata.put("image", "test");
    Artifact artifact = anArtifact()
                            .withUuid("artifactId")
                            .withArtifactStreamId("artifactStreamId")
                            .withAppId("appId")
                            .withMetadata(new ArtifactMetadata(metadata))
                            .build();

    List<K8sPod> existingK8sPod =
        asList(createK8sPod("pod-1", "releaseName", "default"), createK8sPod("pod-2", "releaseName", "default"));
    List<Instance> instancesInDb =
        asList(createK8sPodInstance("pod-1", "releaseName", "default", "artifactId", "oldWorkflow"),
            createK8sPodInstance("pod-2", "releaseName", "default", "newArtifactId", "newWorkflow"));

    assertSavedAndDeletedInstancesOnNewDeployment(
        deploymentEvent, instancesInDb, existingK8sPod, asList("pod-1", "pod-2"), emptyList(), artifact);
  }

  private void assertSavedAndDeletedInstancesOnNewDeploymentHelm(DeploymentEvent deploymentEvent,
      List<Instance> instancesInDb, ContainerSyncResponse containerSyncResponse, List<String> savedInstances,
      List<String> deletedInstances, Artifact artifact) throws Exception {
    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .appId("appId")
            .accountId("accountId")
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .namespace("default")
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(containerSyncResponse).when(containerSync).getInstances(any(), anyList());

    doReturn(InstanceSyncTaskDetails.builder()
                 .infraMappingId("infraMappingId")
                 .perpetualTaskId("perpetualTaskId")
                 .cloudProviderId("cloudProviderId")
                 .appId("appId")
                 .accountId("accountId")
                 .releaseIdentifiers(singleton(CgK8sReleaseIdentifier.builder()
                                                   .releaseName("releaseName")
                                                   .clusterName("clusterName")
                                                   .namespace("default")
                                                   .lastDeploymentSummaryId("lastDeploymentSummaryId")
                                                   .build()))
                 .build())
        .when(taskDetailsService)
        .getForInfraMapping(any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(any());

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(infrastructureMapping).when(infrastructureMappingService).get(any(), any());

    if (artifact != null) {
      mongoPersistence.save(artifact);
    }

    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);

    ArgumentCaptor<List<Instance>> savedInstancesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, times(1)).saveOrUpdate(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());

    // assert for Add instance
    assertThat(savedInstancesCaptor.getAllValues()
                   .get(0)
                   .stream()
                   .map(Instance::getInstanceInfo)
                   .map(KubernetesContainerInfo.class ::cast)
                   .map(KubernetesContainerInfo::getPodName))
        .containsExactlyInAnyOrderElementsOf(savedInstances);

    assertThat(deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(deletedInstances);
  }

  private void assertSavedAndDeletedInstancesOnNewDeployment(DeploymentEvent deploymentEvent,
      List<Instance> instancesInDb, List<K8sPod> podList, List<String> savedInstances, List<String> deletedInstances,
      Artifact artifact) throws Exception {
    ContainerInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .appId("appId")
            .accountId("accountId")
            .infraMappingType(InfrastructureMappingType.DIRECT_KUBERNETES.name())
            .namespace("default")
            .build();
    infrastructureMapping.setUuid(UUID);

    doReturn(podList)
        .when(k8sStateHelper)
        .fetchPodListForCluster(infrastructureMapping, "default", "releaseName", "clusterName");

    doReturn(InstanceSyncTaskDetails.builder()
                 .infraMappingId("infraMappingId")
                 .perpetualTaskId("perpetualTaskId")
                 .cloudProviderId("cloudProviderId")
                 .appId("appId")
                 .accountId("accountId")
                 .releaseIdentifiers(singleton(CgK8sReleaseIdentifier.builder()
                                                   .releaseName("releaseName")
                                                   .clusterName("clusterName")
                                                   .namespace("default")
                                                   .lastDeploymentSummaryId("lastDeploymentSummaryId")
                                                   .build()))
                 .build())
        .when(taskDetailsService)
        .getForInfraMapping(any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(any());

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));
    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(infrastructureMapping).when(infrastructureMappingService).get(any(), any());

    if (artifact != null) {
      mongoPersistence.save(artifact);
    }

    cgInstanceSyncServiceV2.handleInstanceSync(deploymentEvent);

    ArgumentCaptor<List<Instance>> savedInstancesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, times(1)).saveOrUpdate(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());

    // assert for Add and update instance
    assertThat(savedInstancesCaptor.getAllValues()
                   .get(0)
                   .stream()
                   .map(Instance::getInstanceInfo)
                   .map(K8sPodInfo.class ::cast)
                   .map(K8sPodInfo::getPodName))
        .containsExactlyInAnyOrderElementsOf(savedInstances);

    assertThat(deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(deletedInstances);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseNewPod() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("release1", "namespace1", "instance1", "instance2", "instance3", "instance4");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse,
        asList("instance1", "instance2", "instance3", "instance4"), emptyList(), "release1", "namespace1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseNoNewPods() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));
    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("release1", "namespace1", "instance1", "instance2", "instance3");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance1", "instance2", "instance3"),
        emptyList(), "release1", "namespace1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseDeletedPods() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));

    CgInstanceSyncResponse instanceSyncResponse = createK8sPodSyncResponseWith("release1", "namespace1", "instance1");
    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance1"),
        asList("instance2", "instance3"), "release1", "namespace1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseMixed() throws Exception {
    List<Instance> instancesInDb = asList(createK8sPodInstance("instance1", "release1", "namespace1"),
        createK8sPodInstance("instance2", "release1", "namespace1"),
        createK8sPodInstance("instance3", "release1", "namespace1"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("release1", "namespace1", "instance2", "instance4", "instance5");
    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance2", "instance4", "instance5"),
        asList("instance1", "instance3"), "release1", "namespace1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNewPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceY"),
        createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceY"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2", "instance6");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance1", "instance2", "instance6"),
        emptyList(), "releaseX", "namespaceX");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNewPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceY", "controller1"),
            createKubernetesContainerInstance("instance4", "releaseY", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceY", "controller1"));

    CgInstanceSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance1", "instance2", "instance6");

    assertSavedAndDeletedInstancesForKubernetesContainerInfo(instancesInDb, instanceSyncResponse,
        asList("instance1", "instance2", "instance6"), emptyList(), "releaseX", "namespaceX", "controller1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
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
    CgInstanceSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "", "instance1", "instance2", "instance6");
    assertSavedAndDeletedInstancesForKubernetesContainerInfo(instancesInDb, instanceSyncResponse,
        asList("instance1", "instance2", "instance6"), emptyList(), "releaseX", "namespaceX", null);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNoNewPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceY"),
        createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceY"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2");

    assertSavedAndDeletedInstances(
        instancesInDb, instanceSyncResponse, asList("instance1", "instance2"), emptyList(), "releaseX", "namespaceX");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareNoNewPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance4", "releaseX", "namespaceY", "controller1"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance6", "releaseY", "namespaceY", "controller3"));

    CgInstanceSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance1", "instance2");

    assertSavedAndDeletedInstancesForKubernetesContainerInfo(instancesInDb, instanceSyncResponse,
        asList("instance1", "instance2"), emptyList(), "releaseX", "namespaceX", "controller1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareDeletedPods() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceX"),
        createK8sPodInstance("instance4", "releaseX", "namespaceY"),
        createK8sPodInstance("instance5", "releaseY", "namespaceX"),
        createK8sPodInstance("instance6", "releaseY", "namespaceY"));

    CgInstanceSyncResponse instanceSyncResponse = createK8sPodSyncResponseWith("releaseX", "namespaceX", "instance2");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance2"),
        asList("instance1", "instance3"), "releaseX", "namespaceX");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareDeletedPodsHelm() throws Exception {
    List<Instance> instancesInDb =
        Arrays.asList(createKubernetesContainerInstance("instance1", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance2", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance3", "releaseX", "namespaceX", "controller1"),
            createKubernetesContainerInstance("instance4", "releaseX", "namespaceY", "controller2"),
            createKubernetesContainerInstance("instance5", "releaseY", "namespaceX", "controller2"),
            createKubernetesContainerInstance("instance6", "releaseY", "namespaceY", "controller3"));

    CgInstanceSyncResponse instanceSyncResponse =
        createContainerSyncResponseWith("releaseX", "namespaceX", "controller1", "instance2");

    assertSavedAndDeletedInstancesForKubernetesContainerInfo(instancesInDb, instanceSyncResponse, asList("instance2"),
        asList("instance1", "instance3"), "releaseX", "namespaceX", "controller1");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldUpdateInstancesFromPerpetualTaskResponseReleaseAndNamespaceAwareMixed() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance1", "releaseX", "namespaceX"),
        createK8sPodInstance("instance2", "releaseX", "namespaceX"),
        createK8sPodInstance("instance3", "releaseX", "namespaceX"),
        createK8sPodInstance("instance4", "releaseX", "namespaceX"),
        createK8sPodInstance("instance5", "releaseX", "namespaceY"),
        createK8sPodInstance("instance6", "releaseY", "namespaceX"),
        createK8sPodInstance("instance7", "releaseY", "namespaceY"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("releaseX", "namespaceX", "instance2", "instance3", "instance8", "instance9");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse,
        asList("instance2", "instance3", "instance8", "instance9"), asList("instance1", "instance4"), "releaseX",
        "namespaceX");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
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

    CgInstanceSyncResponse instanceSyncResponse = createContainerSyncResponseWith(
        "releaseX", "namespaceX", null, "instance2", "instance3", "instance8", "instance9");

    assertSavedAndDeletedInstancesForKubernetesContainerInfo(instancesInDb, instanceSyncResponse,
        asList("instance2", "instance3", "instance8", "instance9"), asList("instance1", "instance4"), "releaseX",
        "namespaceX", null);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldAddInstancesFromPerpetualTaskEvenIfNoAnyOtherInstancesExistsInDb() throws Exception {
    List<Instance> instancesInDb = Arrays.asList(createK8sPodInstance("instance4", "releaseY", "namespaceX"),
        createK8sPodInstance("instance5", "releaseY", "namespaceX"),
        createK8sPodInstance("instance6", "releaseY", "namespaceY"));

    CgInstanceSyncResponse instanceSyncResponse =
        createK8sPodSyncResponseWith("releaseX", "namespaceX", "instance1", "instance2", "instance3");

    assertSavedAndDeletedInstances(instancesInDb, instanceSyncResponse, asList("instance1", "instance2", "instance3"),
        emptyList(), "releaseX", "namespaceX");
  }

  private CgInstanceSyncResponse createK8sPodSyncResponseWith(String releaseName, String namespace, String... podIds) {
    doAnswer(invocation -> {
      String podId = new String((byte[]) invocation.getArgument(0), StandardCharsets.UTF_8);
      return createK8sPodInfo(podId, releaseName, namespace);
    })
        .when(kryoSerializer)
        .asObject((byte[]) any());

    InstanceSyncData instanceSyncData =
        InstanceSyncData.newBuilder()
            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
            .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                            .setReleaseName(releaseName)
                                            .setNamespace(namespace)
                                            .setIsHelm(false)
                                            .setContainerServiceName("")
                                            .build()))
            .addAllInstanceData(Arrays.stream(podIds).map(pod -> ByteString.copyFrom(pod.getBytes())).collect(toList()))
            .setTaskDetailsId("taskId")
            .build();

    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("perpetualTaskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                 .setAccountId("accountId");

    builder.addInstanceData(instanceSyncData);
    return builder.build();
  }

  private CgInstanceSyncResponse createContainerSyncResponseWith(
      String releaseName, String namespace, String controllerName, String... podIds) {
    doAnswer(invocation -> {
      String podId = new String((byte[]) invocation.getArgument(0), StandardCharsets.UTF_8);
      return createKubernetesContainerInfo(podId, releaseName, namespace, null);
    })
        .when(kryoSerializer)
        .asObject((byte[]) any());

    InstanceSyncData instanceSyncData =
        InstanceSyncData.newBuilder()
            .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
            .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                            .setReleaseName(releaseName)
                                            .setNamespace(namespace)
                                            .setIsHelm(true)
                                            .setContainerServiceName(controllerName == null ? "" : controllerName)
                                            .build()))
            .addAllInstanceData(Arrays.stream(podIds).map(pod -> ByteString.copyFrom(pod.getBytes())).collect(toList()))
            .setTaskDetailsId("taskId")
            .build();

    CgInstanceSyncResponse.Builder builder = CgInstanceSyncResponse.newBuilder()
                                                 .setPerpetualTaskId("perpetualTaskId")
                                                 .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                 .setAccountId("accountId");

    builder.addInstanceData(instanceSyncData);
    return builder.build();
  }

  private void assertSavedAndDeletedInstances(List<Instance> instancesInDb,
      CgInstanceSyncResponse cgInstanceSyncResponse, List<String> savedInstances, List<String> deletedInstances,
      String releaseName, String namespace) throws Exception {
    Exception thrownException = null;
    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .releaseIdentifiers(Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                               .releaseName(releaseName)
                                                               .namespace(namespace)
                                                               .lastDeploymentSummaryId("lastDeploymentSummaryId")
                                                               .isHelmDeployment(false)
                                                               .build()))
                 .cloudProviderId("cpId")
                 .build())
        .when(taskDetailsService)
        .getForId(any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(InstanceType.KUBERNETES_CONTAINER_INSTANCE).when(instanceUtil).getInstanceType(any());

    doReturn(DeploymentSummary.builder()
                 .appId("appId")
                 .infraMappingId("infraMappingId")
                 .accountId("accountId")
                 .deploymentInfo(K8sDeploymentInfo.builder()
                                     .releaseName(releaseName)
                                     .namespace(namespace)
                                     .clusterName("clusterName")
                                     .build())
                 .build())
        .when(deploymentService)
        .get("lastDeploymentSummaryId");

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));

    try {
      cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", cgInstanceSyncResponse);
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

  private void assertSavedAndDeletedInstancesForKubernetesContainerInfo(List<Instance> instancesInDb,
      CgInstanceSyncResponse cgInstanceSyncResponse, List<String> savedInstances, List<String> deletedInstances,
      String releaseName, String namespace, String Controller) {
    doReturn(instancesInDb).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doReturn(InstanceSyncTaskDetails.builder()
                 .perpetualTaskId("perpetualTaskId")
                 .accountId("accountId")
                 .appId("appId")
                 .releaseIdentifiers(Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                               .releaseName(releaseName)
                                                               .namespace(namespace)
                                                               .lastDeploymentSummaryId("lastDeploymentSummaryId")
                                                               .isHelmDeployment(true)
                                                               .containerServiceName(Controller)
                                                               .build()))
                 .cloudProviderId("cpId")
                 .build())
        .when(taskDetailsService)
        .getForId(any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withAccountId("accountId")
                 .withAppId("appId")
                 .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
                 .build())
        .when(cloudProviderService)
        .get(anyString());

    doReturn(InstanceType.KUBERNETES_CONTAINER_INSTANCE).when(instanceUtil).getInstanceType(any());

    doReturn(DeploymentSummary.builder()
                 .appId("appId")
                 .infraMappingId("infraMappingId")
                 .accountId("accountId")
                 .deploymentInfo(ContainerDeploymentInfoWithLabels.builder()
                                     .releaseName(releaseName)
                                     .namespace(namespace)
                                     .clusterName("clusterName")
                                     .build())
                 .build())
        .when(deploymentService)
        .get("lastDeploymentSummaryId");

    doReturn(k8sHandler).when(handlerFactory).getHandler(any(SettingVariableTypes.class));

    cgInstanceSyncServiceV2.processInstanceSyncResult("perpetualTaskId", cgInstanceSyncResponse);

    ArgumentCaptor<List<Instance>> savedInstancesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, times(1)).saveOrUpdate(savedInstancesCaptor.capture());
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());

    assertThat(savedInstancesCaptor.getAllValues()
                   .get(0)
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
        .infraMappingId("infraMappingId")
        .appId("appId")
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

  private K8sPodInfo createK8sPodInfo(String id, String releaseName, String namespace) {
    return K8sPodInfo.builder()
        .ip(id)
        .podName(id)
        .releaseName(releaseName)
        .namespace(namespace)
        .clusterName("clusterName")
        .containers(singletonList(K8sContainerInfo.builder().name(id).image("test").containerId(id).build()))
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

  private KubernetesContainerInfo createKubernetesContainerInfo(
      String id, String releaseName, String namespace, String controllerName) {
    return KubernetesContainerInfo.builder()
        .ip(id)
        .podName(id)
        .releaseName(releaseName)
        .controllerName(controllerName)
        .namespace(namespace)
        .clusterName("clusterName")
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
                          .clusterName("clusterName")
                          .podName(id)
                          .namespace(namespace)
                          .releaseName(releaseName)
                          .controllerName(controllerName)
                          .build())
        .lastWorkflowExecutionId("1")
        .build();
  }

  private void assertSavedAndDeletedInstances(
      Consumer<List<Instance>> savedInstancesHandler, Consumer<List<String>> deletedInstancesHandler) {
    ArgumentCaptor<List<Instance>> savedInstancesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Set<String>> deletedInstancesCaptor =
        ArgumentCaptor.forClass((Class<Set<String>>) (Object) Set.class);

    // don't care about the number of calls until we save/delete right instances
    verify(instanceService, atLeast(0)).delete(deletedInstancesCaptor.capture());
    verify(instanceService, times(1)).saveOrUpdate(savedInstancesCaptor.capture());

    savedInstancesHandler.accept(savedInstancesCaptor.getAllValues().get(0));
    deletedInstancesHandler.accept(
        deletedInstancesCaptor.getAllValues().stream().flatMap(Set::stream).collect(toList()));
  }
}
