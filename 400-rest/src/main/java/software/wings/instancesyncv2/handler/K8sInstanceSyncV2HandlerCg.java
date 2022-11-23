/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.Capability;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.K8sPodSyncException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.serializer.KryoSerializer;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.Artifact.ArtifactKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsMongoPersistence;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.ContainerMetadataType;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.groovy.util.Maps;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class K8sInstanceSyncV2HandlerCg implements CgInstanceSyncV2Handler {
  @NonNull private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @NonNull private InfrastructureMappingService infrastructureMappingService;
  @NonNull private KryoSerializer kryoSerializer;
  @NonNull private InstanceUtils instanceUtil;
  @NonNull private ServiceResourceService serviceResourceService;
  @NonNull private EnvironmentService environmentService;
  @NonNull private transient K8sStateHelper k8sStateHelper;
  @NonNull private AppService appService;
  @NonNull private WingsMongoPersistence wingsPersistence;
  @NonNull private ContainerSync containerSync;
  @NonNull private InstanceService instanceService;

  @Override
  public PerpetualTaskExecutionBundle fetchInfraConnectorDetails(SettingAttribute cloudProvider) {
    if (!SettingVariableTypes.KUBERNETES_CLUSTER.name().equals(cloudProvider.getValue().getType())) {
      log.error("Passed cloud provider is not of type KUBERNETES_CLUSTER. Type passed: [{}]",
          cloudProvider.getValue().getType());
      throw new InvalidRequestException("Cloud Provider not of type KUBERNETES_CLUSTER");
    }

    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(
                Any.pack(CgInstanceSyncTaskParams.newBuilder()
                             .setAccountId(cloudProvider.getAccountId())
                             .setCloudProviderType(cloudProvider.getValue().getType())
                             .setCloudProviderDetails(ByteString.copyFrom(new byte[0])) // Todo: to be removed later
                             .build()))
            .putAllSetupAbstractions(Maps.of(NG, "false", OWNER, cloudProvider.getAccountId()));
    cloudProvider.getValue().fetchRequiredExecutionCapabilities(null).forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.build();
  }

  @Override
  public Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> existingIdentifiers, Set<CgReleaseIdentifiers> newIdentifiers) {
    if (CollectionUtils.isEmpty(existingIdentifiers)) {
      return newIdentifiers;
    }

    if (CollectionUtils.isEmpty(newIdentifiers)) {
      return existingIdentifiers;
    }
    Set<CgReleaseIdentifiers> mergeIdentifiersSet = new HashSet<>(existingIdentifiers);
    mergeIdentifiersSet.addAll(newIdentifiers);

    return mergeIdentifiersSet;
  }

  public Map<CgReleaseIdentifiers, InstanceSyncData> getCgReleaseIdentifiersList(
      List<InstanceSyncData> instanceSyncDataList) {
    Map<CgReleaseIdentifiers, InstanceSyncData> instanceSyncDataMap = new HashMap<>();
    if (isEmpty(instanceSyncDataList)) {
      return instanceSyncDataMap;
    }
    for (InstanceSyncData instanceSyncData : instanceSyncDataList) {
      instanceSyncDataMap.put(getCgReleaseIdentifiers(instanceSyncData), instanceSyncData);
    }
    return instanceSyncDataMap;
  }

  public CgK8sReleaseIdentifier getCgReleaseIdentifiers(InstanceSyncData instanceSyncData) {
    DirectK8sReleaseDetails directK8sReleaseDetails =
        AnyUtils.unpack(instanceSyncData.getReleaseDetails(), DirectK8sReleaseDetails.class);
    return CgK8sReleaseIdentifier.builder()
        .releaseName(directK8sReleaseDetails.getReleaseName())
        .namespace(directK8sReleaseDetails.getNamespace())
        .isHelmDeployment(directK8sReleaseDetails.getIsHelm())
        .containerServiceName(isEmpty(directK8sReleaseDetails.getContainerServiceName())
                ? null
                : directK8sReleaseDetails.getContainerServiceName())
        .build();
  }

  private List<Instance> getK8sInstanceFromDelegate(InfrastructureMapping infrastructureMapping,
      CgK8sReleaseIdentifier cgK8sReleaseIdentifier, DeploymentSummary deploymentSummary) {
    List<Instance> instances = new ArrayList<>();
    ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
    List<K8sPod> k8sPods = getK8sPodsFromDelegate(containerInfraMapping, cgK8sReleaseIdentifier);
    if (isEmpty(k8sPods)) {
      return instances;
    }
    for (K8sPod k8sPod : k8sPods) {
      instances.add(buildInstanceFromPodInfo(infrastructureMapping, k8sPod, deploymentSummary));
    }
    return instances;
  }

  private List<K8sPod> getK8sPodsFromDelegate(
      ContainerInfrastructureMapping containerInfraMapping, CgK8sReleaseIdentifier cgK8sReleaseIdentifier) {
    try {
      return k8sStateHelper.fetchPodListForCluster(containerInfraMapping, cgK8sReleaseIdentifier.getNamespace(),
          cgK8sReleaseIdentifier.getReleaseName(), cgK8sReleaseIdentifier.getClusterName());
    } catch (Exception e) {
      throw new K8sPodSyncException(format("Exception in fetching podList for release %s, namespace %s",
                                        cgK8sReleaseIdentifier.getReleaseName(), cgK8sReleaseIdentifier.getNamespace()),
          e);
    }
  }

  private Instance buildInstanceFromPodInfo(
      InfrastructureMapping infraMapping, K8sPod pod, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(infraMapping, deploymentSummary);
    builder.podInstanceKey(PodInstanceKey.builder().podName(pod.getName()).namespace(pod.getNamespace()).build());
    builder.instanceInfo(K8sPodInfo.builder()
                             .releaseName(pod.getReleaseName())
                             .podName(pod.getName())
                             .ip(pod.getPodIP())
                             .namespace(pod.getNamespace())
                             .containers(pod.getContainerList()
                                             .stream()
                                             .map(container
                                                 -> K8sContainerInfo.builder()
                                                        .containerId(container.getContainerId())
                                                        .name(container.getName())
                                                        .image(container.getImage())
                                                        .build())
                                             .collect(toList()))
                             .blueGreenColor(pod.getColor())
                             .clusterName(deploymentSummary.getDeploymentInfo() instanceof K8sDeploymentInfo
                                     ? ((K8sDeploymentInfo) deploymentSummary.getDeploymentInfo()).getClusterName()
                                     : null)
                             .build());

    if (deploymentSummary != null && deploymentSummary.getArtifactStreamId() != null) {
      return populateArtifactInInstanceBuilder(builder, deploymentSummary, infraMapping, pod).build();
    }

    return builder.build();
  }

  public Map<CgReleaseIdentifiers, List<Instance>> fetchInstancesFromDb(
      Set<CgReleaseIdentifiers> cgReleaseIdentifierList, String appId, String InfraMappingId) {
    List<Instance> instancesInDb = instanceService.getInstancesForAppAndInframapping(appId, InfraMappingId);
    Map<CgReleaseIdentifiers, List<Instance>> instancesMap = new HashMap<>();

    // Todo: should be subject to optimisation, it’s ineffective and theoretically can be reduced to just creating
    // CgReleaseIdentifier based on instance
    for (CgReleaseIdentifiers cgReleaseIdentifiers : cgReleaseIdentifierList) {
      List<Instance> instances = new ArrayList<>();
      CgK8sReleaseIdentifier cgK8sReleaseIdentifier = (CgK8sReleaseIdentifier) cgReleaseIdentifiers;
      for (Instance instanceInDb : instancesInDb) {
        ContainerInfo containerInfo = (ContainerInfo) instanceInDb.getInstanceInfo();
        String containerSvcName = null;
        String namespace = null;
        String releaseName = null;
        boolean isHelmDeployment = false;
        if (containerInfo instanceof KubernetesContainerInfo) {
          namespace = ((KubernetesContainerInfo) containerInfo).getNamespace();
          releaseName = ((KubernetesContainerInfo) containerInfo).getReleaseName();
          containerSvcName = ((KubernetesContainerInfo) containerInfo).getControllerName();
          isHelmDeployment = true;
        } else if (containerInfo instanceof K8sPodInfo) {
          namespace = ((K8sPodInfo) containerInfo).getNamespace();
          releaseName = ((K8sPodInfo) containerInfo).getReleaseName();
          isHelmDeployment = false;
        }

        if (isNotEmpty(cgK8sReleaseIdentifier.getNamespace()) && isNotEmpty(cgK8sReleaseIdentifier.getReleaseName())
            && cgK8sReleaseIdentifier.getNamespace().equals(namespace)
            && cgK8sReleaseIdentifier.getReleaseName().equals(releaseName)
            && cgK8sReleaseIdentifier.isHelmDeployment() == isHelmDeployment
            && ((StringUtils.isBlank(cgK8sReleaseIdentifier.getContainerServiceName())
                    && StringUtils.isBlank(containerSvcName)
                || (cgK8sReleaseIdentifier.getContainerServiceName().equals(containerSvcName))))) {
          instances.add(instanceInDb);
        }
      }
      instancesMap.put(cgReleaseIdentifiers, instances);
    }
    return instancesMap;
  }
  @Override
  public InstanceSyncTaskDetails prepareTaskDetails(
      DeploymentSummary deploymentSummary, String cloudProviderId, String perpetualTaskId) {
    return InstanceSyncTaskDetails.builder()
        .accountId(deploymentSummary.getAccountId())
        .appId(deploymentSummary.getAppId())
        .perpetualTaskId(perpetualTaskId)
        .cloudProviderId(cloudProviderId)
        .infraMappingId(deploymentSummary.getInfraMappingId())
        .lastSuccessfulRun(0L)
        .releaseIdentifiers(createReleaseIdentifiers(deploymentSummary))
        .build();
  }

  @Override
  public Set<CgReleaseIdentifiers> createReleaseIdentifiers(DeploymentSummary deploymentSummary) {
    DeploymentInfo deploymentInfo = deploymentSummary.getDeploymentInfo();
    Set<CgReleaseIdentifiers> cgReleaseIdentifiersSet = new HashSet<>();
    if (deploymentInfo instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo k8sDeploymentInfo = (K8sDeploymentInfo) deploymentInfo;
      Set<String> namespaces = getNamespaces(k8sDeploymentInfo.getNamespaces(), k8sDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      for (String namespace : namespaces) {
        cgReleaseIdentifiersSet.add(CgK8sReleaseIdentifier.builder()
                                        .clusterName(k8sDeploymentInfo.getClusterName())
                                        .releaseName(k8sDeploymentInfo.getReleaseName())
                                        .lastDeploymentSummaryId(deploymentSummary.getUuid())
                                        .namespace(namespace)
                                        .isHelmDeployment(false)
                                        .build());
      }
      return cgReleaseIdentifiersSet;
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      ContainerDeploymentInfoWithLabels containerDeploymentInfo = (ContainerDeploymentInfoWithLabels) deploymentInfo;
      Set<String> namespaces =
          getNamespaces(containerDeploymentInfo.getNamespaces(), containerDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      Set<String> controllers = emptyIfNull(containerDeploymentInfo.getContainerInfoList())
                                    .stream()
                                    .map(io.harness.container.ContainerInfo::getWorkloadName)
                                    .filter(EmptyPredicate::isNotEmpty)
                                    .collect(Collectors.toSet());
      if (isNotEmpty(controllers)) {
        for (String namespace : namespaces) {
          cgReleaseIdentifiersSet.addAll(controllers.parallelStream()
                                             .map(controller
                                                 -> CgK8sReleaseIdentifier.builder()
                                                        .containerServiceName(isEmpty(controller) ? null : controller)
                                                        .namespace(namespace)
                                                        .releaseName(containerDeploymentInfo.getReleaseName())
                                                        .lastDeploymentSummaryId(deploymentSummary.getUuid())
                                                        .isHelmDeployment(true)
                                                        .build())
                                             .collect(Collectors.toSet()));
        }

        return cgReleaseIdentifiersSet;
      } else if (isNotEmpty(containerDeploymentInfo.getContainerInfoList())) {
        for (String namespace : namespaces) {
          cgReleaseIdentifiersSet.add(CgK8sReleaseIdentifier.builder()
                                          .namespace(namespace)
                                          .lastDeploymentSummaryId(deploymentSummary.getUuid())
                                          .releaseName(containerDeploymentInfo.getReleaseName())
                                          .isHelmDeployment(true)
                                          .build());
        }
        return cgReleaseIdentifiersSet;
      }

      return Collections.emptySet();
    }

    throw new InvalidRequestException("DeploymentInfo of type: [" + deploymentInfo.getClass().getCanonicalName()
        + "] not supported with V2 Instance Sync framework.");
  }

  private Set<String> getNamespaces(Collection<String> namespaces, String namespace) {
    Set<String> namespacesSet = new HashSet<>();
    if (StringUtils.isNotBlank(namespace)) {
      namespacesSet.add(namespace);
    }

    if (CollectionUtils.isNotEmpty(namespaces)) {
      namespacesSet.addAll(namespaces);
    }

    return namespacesSet;
  }

  @Override
  public List<CgDeploymentReleaseDetails> getDeploymentReleaseDetails(InstanceSyncTaskDetails taskDetails) {
    InfrastructureMapping infraMapping =
        infrastructureMappingService.get(taskDetails.getAppId(), taskDetails.getInfraMappingId());

    if (!(infraMapping instanceof ContainerInfrastructureMapping)) {
      log.error("Unsupported infrastructure mapping being tracked here: [{}]. InfraMappingType found: [{}]",
          taskDetails, infraMapping.getClass().getName());
      return Collections.emptyList();
    }

    if (CollectionUtils.isEmpty(taskDetails.getReleaseIdentifiers())) {
      return Collections.emptyList();
    }

    ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infraMapping;
    K8sClusterConfig clusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(containerInfraMapping, null);

    List<CgDeploymentReleaseDetails> releaseDetails = new ArrayList<>();
    taskDetails.getReleaseIdentifiers()
        .parallelStream()
        .filter(releaseIdentifier -> releaseIdentifier instanceof CgK8sReleaseIdentifier)
        .map(releaseIdentifier -> (CgK8sReleaseIdentifier) releaseIdentifier)
        .forEach(releaseIdentifier
            -> releaseDetails.add(
                CgDeploymentReleaseDetails.newBuilder()
                    .setTaskDetailsId(taskDetails.getUuid())
                    .setInfraMappingId(taskDetails.getInfraMappingId())
                    .setInfraMappingType(infraMapping.getInfraMappingType())
                    .setReleaseDetails(
                        Any.pack(DirectK8sInstanceSyncTaskDetails.newBuilder()
                                     .setReleaseName(releaseIdentifier.getReleaseName())
                                     .setNamespace(releaseIdentifier.getNamespace())
                                     .setK8SClusterConfig(ByteString.copyFrom(kryoSerializer.asBytes(clusterConfig)))
                                     .setIsHelm(releaseIdentifier.isHelmDeployment())
                                     .setContainerServiceName(isEmpty(releaseIdentifier.getContainerServiceName())
                                             ? ""
                                             : releaseIdentifier.getContainerServiceName())
                                     .build()))
                    .build()

                    ));

    return releaseDetails;
  }

  @Override
  public boolean isDeploymentInfoTypeSupported(Class<? extends DeploymentInfo> deploymentInfoClazz) {
    return deploymentInfoClazz.equals(K8sDeploymentInfo.class)
        || deploymentInfoClazz.equals(ContainerDeploymentInfoWithLabels.class);
  }

  public List<Instance> difference(List<Instance> list1, List<Instance> list2) {
    if (isEmpty(list1)) {
      return emptyList();
    }
    if (isEmpty(list2)) {
      return list1;
    }
    Map<String, Instance> instanceKeyMapList1 = getInstanceKeyMap(list1);
    Map<String, Instance> instanceKeyMapList2 = getInstanceKeyMap(list2);

    SetView<String> difference = Sets.difference(instanceKeyMapList1.keySet(), instanceKeyMapList2.keySet());

    return difference.parallelStream().map(instanceKeyMapList1::get).collect(Collectors.toList());
  }
  @Override
  public List<Instance> instancesToDelete(List<Instance> instanceInDb, List<Instance> instances) {
    return difference(instanceInDb, instances);
  }

  private Map<String, Instance> getInstanceKeyMap(List<Instance> instanceList) {
    Map<String, Instance> instanceKeyMap = new HashMap<>();
    if (isEmpty(instanceList)) {
      return instanceKeyMap;
    }
    for (Instance instance : instanceList) {
      if (Objects.nonNull(instance.getPodInstanceKey())) {
        instanceKeyMap.put(instance.getPodInstanceKey().getPodName() + instance.getPodInstanceKey().getNamespace()
                + getImageInStringFormat(instance),
            instance);
      } else if (Objects.nonNull(instance.getContainerInstanceKey())) {
        KubernetesContainerInfo k8sInfo = (KubernetesContainerInfo) instance.getInstanceInfo();
        String namespace = isNotBlank(k8sInfo.getNamespace()) ? k8sInfo.getNamespace() : "";
        String releaseName = k8sInfo.getReleaseName();
        instanceKeyMap.put(k8sInfo.getPodName() + namespace + releaseName, instance);
      }
    }

    return instanceKeyMap;
  }

  private String getImageInStringFormat(Instance instance) {
    if (instance.getInstanceInfo() instanceof K8sPodInfo) {
      return emptyIfNull(((K8sPodInfo) instance.getInstanceInfo()).getContainers())
          .stream()
          .map(K8sContainerInfo::getImage)
          .collect(Collectors.joining());
    }
    return EMPTY;
  }

  @Override
  public List<Instance> instancesToSaveAndUpdate(List<Instance> instances, List<Instance> instancesInDb) {
    if (isEmpty(instances)) {
      return emptyList();
    }
    Map<String, Instance> instancesKeyMap = getInstanceKeyMap(instances);
    Map<String, Instance> instancesInDbKeyMap = getInstanceKeyMap(instancesInDb);
    List<Instance> instancesToAddAndUpdate = new ArrayList<>();

    List<Instance> instancesToAdd = difference(instances, instancesInDb);

    SetView<String> intersection = Sets.intersection(instancesKeyMap.keySet(), instancesInDbKeyMap.keySet());
    List<Instance> instancesToUpdate = new ArrayList<>();
    for (String instanceKey : intersection) {
      Instance newInstance = instancesKeyMap.get(instanceKey);
      Instance instanceInDb = instancesInDbKeyMap.get(instanceKey);
      newInstance.setUuid(instanceInDb.getUuid());
      instancesToUpdate.add(newInstance);
    }
    instancesToAddAndUpdate.addAll(instancesToAdd);
    instancesToAddAndUpdate.addAll(instancesToUpdate);
    return instancesToAddAndUpdate;
  }

  @Override
  public Map<CgReleaseIdentifiers, List<Instance>> groupInstanceSyncData(
      Map<CgReleaseIdentifiers, DeploymentSummary> deploymentSummaries,
      Map<CgReleaseIdentifiers, InstanceSyncData> instanceSyncDataMap) {
    Map<CgReleaseIdentifiers, List<Instance>> instancesMap = new HashMap<>();

    for (CgReleaseIdentifiers cgReleaseIdentifier : deploymentSummaries.keySet()) {
      List<Instance> instances = new ArrayList<>();
      CgK8sReleaseIdentifier cgK8sReleaseIdentifier = (CgK8sReleaseIdentifier) cgReleaseIdentifier;

      List<InstanceInfo> instanceInfos =
          instanceSyncDataMap.get(cgReleaseIdentifier)
              .getInstanceDataList()
              .parallelStream()
              .map(instance -> (InstanceInfo) kryoSerializer.asObject(instance.toByteArray()))
              .collect(Collectors.toList());
      DeploymentSummary deploymentSummary = deploymentSummaries.get(cgK8sReleaseIdentifier);

      if (CollectionUtils.isEmpty(instanceInfos) || Objects.isNull(deploymentSummary)) {
        instancesMap.put(cgK8sReleaseIdentifier, emptyList());
        continue;
      }

      if (instanceInfos.get(0) instanceof K8sPodInfo) {
        InfrastructureMapping infrastructureMapping =
            infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
        instances = getInstancesForK8sPods(deploymentSummary, infrastructureMapping,
            instanceInfos.parallelStream().map(K8sPodInfo.class ::cast).collect(Collectors.toList()));
      } else if (instanceInfos.get(0) instanceof KubernetesContainerInfo) {
        InfrastructureMapping infrastructureMapping =
            infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
        ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
        instances = getInstancesForContainerPods(deploymentSummary, containerInfraMapping,
            instanceInfos.parallelStream().map(ContainerInfo.class ::cast).collect(Collectors.toList()));
      }
      instancesMap.put(cgK8sReleaseIdentifier, instances);
    }
    return instancesMap;
  }

  @Override
  public Map<CgReleaseIdentifiers, List<Instance>> getDeployedInstances(
      Set<CgReleaseIdentifiers> cgReleaseIdentifiers, DeploymentSummary deploymentSummary) {
    Map<CgReleaseIdentifiers, List<Instance>> instancesMap = new HashMap<>();
    if (isNull(deploymentSummary)) {
      log.error("Null deployment summary passed. Nothing to do here.");
      return instancesMap;
    }
    for (CgReleaseIdentifiers cgReleaseIdentifier : cgReleaseIdentifiers) {
      List<Instance> instances = new ArrayList<>();
      CgK8sReleaseIdentifier cgK8sReleaseIdentifier = (CgK8sReleaseIdentifier) cgReleaseIdentifier;

      if (deploymentSummary.getDeploymentInfo() instanceof K8sDeploymentInfo) {
        InfrastructureMapping infrastructureMapping =
            infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
        instances = getK8sInstanceFromDelegate(infrastructureMapping, cgK8sReleaseIdentifier, deploymentSummary);

      } else if (deploymentSummary.getDeploymentInfo() instanceof ContainerDeploymentInfoWithLabels) {
        ContainerMetadata containerMetadata = getContainerMetadataFromReleaseIdentifier(cgK8sReleaseIdentifier, null);
        InfrastructureMapping infrastructureMapping =
            infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
        ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;

        ContainerSyncResponse instanceSyncResponse =
            containerSync.getInstances(containerInfraMapping, singletonList(containerMetadata));
        if (instanceSyncResponse != null && CollectionUtils.isNotEmpty(instanceSyncResponse.getContainerInfoList())) {
          instances = getInstancesForContainerPods(
              deploymentSummary, containerInfraMapping, instanceSyncResponse.getContainerInfoList());
        }
      }
      instancesMap.put(cgK8sReleaseIdentifier, instances);
    }
    return instancesMap;
  }

  private List<Instance> getInstancesForContainerPods(DeploymentSummary deploymentSummary,
      ContainerInfrastructureMapping containerInfraMapping, List<ContainerInfo> containerInfoList) {
    if (CollectionUtils.isEmpty(containerInfoList)) {
      return Collections.emptyList();
    }

    List<Instance> instances = new ArrayList<>();
    for (ContainerInfo containerInfo : containerInfoList) {
      setHelmChartInfoToContainerInfo(
          ((ContainerDeploymentInfoWithLabels) deploymentSummary.getDeploymentInfo()).getHelmChartInfo(),
          containerInfo);
      Instance instance = buildInstanceFromContainerInfo(containerInfraMapping, containerInfo, deploymentSummary);
      instances.add(instance);
    }

    return instances;
  }

  private List<Instance> getInstancesForK8sPods(
      DeploymentSummary deploymentSummary, InfrastructureMapping infrastructureMapping, List<K8sPodInfo> pods) {
    if (CollectionUtils.isEmpty(pods)) {
      return Collections.emptyList();
    }

    List<Instance> instances = new ArrayList<>();
    for (K8sPodInfo pod : pods) {
      HelmChartInfo helmChartInfo =
          getK8sPodHelmChartInfo((K8sDeploymentInfo) deploymentSummary.getDeploymentInfo(), pod);
      Instance instance = buildInstanceFromPodInfo(infrastructureMapping, pod, deploymentSummary);
      ContainerInfo containerInfo = (ContainerInfo) instance.getInstanceInfo();
      setHelmChartInfoToContainerInfo(helmChartInfo, containerInfo);
      instances.add(instance);
    }

    return instances;
  }

  private ContainerMetadata getContainerMetadataFromReleaseIdentifier(
      CgK8sReleaseIdentifier releaseIdentifier, ContainerMetadataType type) {
    return ContainerMetadata.builder()
        .containerServiceName(
            isEmpty(releaseIdentifier.getContainerServiceName()) ? null : releaseIdentifier.getContainerServiceName())
        .releaseName(releaseIdentifier.getReleaseName())
        .clusterName(releaseIdentifier.getClusterName())
        .namespace(releaseIdentifier.getNamespace())
        .type(type)
        .build();
  }

  private Instance buildInstanceFromContainerInfo(
      InfrastructureMapping infraMapping, ContainerInfo containerInfo, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(infraMapping, deploymentSummary);
    builder.containerInstanceKey(generateInstanceKeyForContainer(containerInfo));
    builder.instanceInfo(containerInfo);

    return builder.build();
  }

  private ContainerInstanceKey generateInstanceKeyForContainer(ContainerInfo containerInfo) {
    ContainerInstanceKey containerInstanceKey;

    if (containerInfo instanceof KubernetesContainerInfo) {
      KubernetesContainerInfo kubernetesContainerInfo = (KubernetesContainerInfo) containerInfo;
      containerInstanceKey = ContainerInstanceKey.builder()
                                 .containerId(kubernetesContainerInfo.getPodName())
                                 .namespace(((KubernetesContainerInfo) containerInfo).getNamespace())
                                 .build();
    } else {
      String msg = "Unsupported container instance type:" + containerInfo;
      log.error(msg);
      throw new GeneralException(msg);
    }

    return containerInstanceKey;
  }

  void setHelmChartInfoToContainerInfo(HelmChartInfo helmChartInfo, ContainerInfo k8sInfo) {
    Optional.ofNullable(helmChartInfo).ifPresent(chartInfo -> {
      if (KubernetesContainerInfo.class == k8sInfo.getClass()) {
        ((KubernetesContainerInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      } else if (K8sPodInfo.class == k8sInfo.getClass()) {
        ((K8sPodInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      }
    });
  }

  private Instance buildInstanceFromPodInfo(
      InfrastructureMapping infraMapping, K8sPodInfo pod, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(infraMapping, deploymentSummary);
    builder.podInstanceKey(PodInstanceKey.builder().podName(pod.getPodName()).namespace(pod.getNamespace()).build());
    builder.instanceInfo(pod);

    if (deploymentSummary != null && deploymentSummary.getArtifactStreamId() != null) {
      return populateArtifactInInstanceBuilder(builder, deploymentSummary, infraMapping, pod).build();
    }

    return builder.build();
  }

  private Artifact findArtifactForImage(String artifactStreamId, String appId, String image) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
        .filter(ArtifactKeys.appId, appId)
        .filter("metadata.image", image)
        .disableValidation()
        .get();
  }

  private void updateInstanceWithArtifactSourceAndBuildNum(InstanceBuilder builder, String image) {
    String artifactSource;
    String tag;
    String[] splitArray = image.split(":");
    if (splitArray.length == 2) {
      artifactSource = splitArray[0];
      tag = splitArray[1];
    } else if (splitArray.length == 1) {
      artifactSource = splitArray[0];
      tag = "latest";
    } else {
      artifactSource = image;
      tag = image;
    }

    builder.lastArtifactName(image);
    builder.lastArtifactSourceName(artifactSource);
    builder.lastArtifactBuildNum(tag);
  }

  private boolean isBuildNumSame(String build1, String build2) {
    if (isEmpty(build1) || isEmpty(build2)) {
      return false;
    }
    return build1.equals(build2);
  }

  private InstanceBuilder populateArtifactInInstanceBuilder(
      InstanceBuilder builder, DeploymentSummary deploymentSummary, InfrastructureMapping infraMapping, K8sPod pod) {
    boolean instanceBuilderUpdated = false;
    Artifact firstValidArtifact = null;
    String firstValidImage = "";
    for (K8sContainer k8sContainer : pod.getContainerList()) {
      String image = k8sContainer.getImage();
      Artifact artifact = findArtifactForImage(deploymentSummary.getArtifactStreamId(), infraMapping.getAppId(), image);

      if (artifact != null) {
        if (firstValidArtifact == null) {
          firstValidArtifact = artifact;
          firstValidImage = image;
        }
        // update only if buildNumber also matches
        if (isBuildNumSame(deploymentSummary.getArtifactBuildNum(), artifact.getBuildNo())) {
          builder.lastArtifactId(artifact.getUuid());
          updateInstanceWithArtifactSourceAndBuildNum(builder, image);
          instanceBuilderUpdated = true;
          break;
        }
      }
    }

    if (!instanceBuilderUpdated && firstValidArtifact != null) {
      builder.lastArtifactId(firstValidArtifact.getUuid());
      updateInstanceWithArtifactSourceAndBuildNum(builder, firstValidImage);
      instanceBuilderUpdated = true;
    }

    if (!instanceBuilderUpdated) {
      updateInstanceWithArtifactSourceAndBuildNum(builder, pod.getContainerList().get(0).getImage());
    }

    return builder;
  }

  private InstanceBuilder populateArtifactInInstanceBuilder(InstanceBuilder builder,
      DeploymentSummary deploymentSummary, InfrastructureMapping infraMapping, K8sPodInfo pod) {
    boolean instanceBuilderUpdated = false;
    Artifact firstValidArtifact = null;
    String firstValidImage = "";
    for (K8sContainerInfo k8sContainer : pod.getContainers()) {
      String image = k8sContainer.getImage();
      Artifact artifact = findArtifactForImage(deploymentSummary.getArtifactStreamId(), infraMapping.getAppId(), image);

      if (artifact != null) {
        if (firstValidArtifact == null) {
          firstValidArtifact = artifact;
          firstValidImage = image;
        }
        // update only if buildNumber also matches
        if (isBuildNumSame(deploymentSummary.getArtifactBuildNum(), artifact.getBuildNo())) {
          builder.lastArtifactId(artifact.getUuid());
          updateInstanceWithArtifactSourceAndBuildNum(builder, image);
          instanceBuilderUpdated = true;
          break;
        }
      }
    }

    if (!instanceBuilderUpdated && firstValidArtifact != null) {
      builder.lastArtifactId(firstValidArtifact.getUuid());
      updateInstanceWithArtifactSourceAndBuildNum(builder, firstValidImage);
      instanceBuilderUpdated = true;
    }

    if (!instanceBuilderUpdated) {
      updateInstanceWithArtifactSourceAndBuildNum(builder, pod.getContainers().get(0).getImage());
    }

    return builder;
  }

  private HelmChartInfo getK8sPodHelmChartInfo(K8sDeploymentInfo deploymentInfo, K8sPodInfo pod) {
    if (deploymentInfo != null) {
      if (StringUtils.equals(pod.getBlueGreenColor(), deploymentInfo.getBlueGreenStageColor())) {
        return deploymentInfo.getHelmChartInfo();
      }
    }

    return null;
  }

  protected InstanceBuilder buildInstanceBase(InfrastructureMapping infraMapping, DeploymentSummary deploymentSummary) {
    String appId = infraMapping.getAppId();
    Application application = appService.get(appId);
    notNullCheck("Application is null for the given appId: " + appId, application);
    Environment environment = environmentService.get(appId, infraMapping.getEnvId(), false);
    notNullCheck("Environment is null for the given id: " + infraMapping.getEnvId(), environment);
    Service service = serviceResourceService.getWithDetails(appId, infraMapping.getServiceId());
    notNullCheck("Service is null for the given id: " + infraMapping.getServiceId(), service);
    String infraMappingType = infraMapping.getInfraMappingType();

    return Instance.builder()
        .uuid(generateUuid())
        .accountId(application.getAccountId())
        .appId(appId)
        .appName(application.getName())
        .envName(environment.getName())
        .envId(infraMapping.getEnvId())
        .envType(environment.getEnvironmentType())
        .computeProviderId(infraMapping.getComputeProviderSettingId())
        .computeProviderName(infraMapping.getComputeProviderName())
        .infraMappingId(infraMapping.getUuid())
        .infraMappingName(infraMapping.getDisplayName())
        .infraMappingType(infraMappingType)
        .serviceId(infraMapping.getServiceId())
        .serviceName(service.getName())
        .instanceType(instanceUtil.getInstanceType(infraMappingType))
        .lastDeployedAt(deploymentSummary.getDeployedAt())
        .lastDeployedById(deploymentSummary.getDeployedById())
        .lastDeployedByName(deploymentSummary.getDeployedByName())
        .lastWorkflowExecutionId(deploymentSummary.getWorkflowExecutionId())
        .lastWorkflowExecutionName(deploymentSummary.getWorkflowExecutionName())
        .lastPipelineExecutionId(deploymentSummary.getPipelineExecutionId())
        .lastPipelineExecutionName(deploymentSummary.getPipelineExecutionName())
        .lastArtifactId(deploymentSummary.getArtifactId())
        .lastArtifactName(deploymentSummary.getArtifactName())
        .lastArtifactStreamId(deploymentSummary.getArtifactStreamId())
        .lastArtifactSourceName(deploymentSummary.getArtifactSourceName())
        .lastArtifactBuildNum(deploymentSummary.getArtifactBuildNum());
  }
}
