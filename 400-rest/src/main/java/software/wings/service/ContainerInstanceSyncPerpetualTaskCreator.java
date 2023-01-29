/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.INSTANCE_SYNC_V2_CG;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.InstanceSyncConstants.CLUSTER_NAME;
import static software.wings.service.InstanceSyncConstants.CONTAINER_SERVICE_NAME;
import static software.wings.service.InstanceSyncConstants.CONTAINER_TYPE;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.InstanceSyncConstants.INTERVAL_MINUTES;
import static software.wings.service.InstanceSyncConstants.NAMESPACE;
import static software.wings.service.InstanceSyncConstants.RELEASE_NAME;
import static software.wings.service.InstanceSyncConstants.TIMEOUT_SECONDS;
import static software.wings.service.impl.ContainerMetadataType.K8S;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.instancesync.ContainerInstanceSyncPerpetualTaskClientParams;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.api.BaseContainerDeploymentInfo;
import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.ContainerMetadataType;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.Utils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.protobuf.util.Durations;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public class ContainerInstanceSyncPerpetualTaskCreator extends AbstractInstanceSyncPerpetualTaskCreator {
  @Inject private FeatureFlagService featureFlagService;
  static final boolean ALLOW_DUPLICATE = false;

  @Override
  public List<String> createPerpetualTasks(InfrastructureMapping infrastructureMapping) {
    final String appId = infrastructureMapping.getAppId();
    final String infraMappingId = infrastructureMapping.getUuid();
    Set<ContainerMetadata> containersMetadata = getContainerMetadataFromInstances(appId, infraMappingId);

    /*For Instance sync v2 skipping the InstanceSyncPerpetualTaskMigrationJob for instance sync v2 and
KUBERNETES_CLUSTER We are doing this because we already going have migration mechanism for instance sync v2 and if we
don’t skip this then both migration job interferes each other One more reason is when we enable this FF
INSTANCE_SYNC_V2_CG we do not want the migration job to recreate the Perpetual task of instance sync v1*/

    if (featureFlagService.isEnabled(INSTANCE_SYNC_V2_CG, infrastructureMapping.getAccountId())
        && Objects.equals(
            infrastructureMapping.getComputeProviderType(), SettingVariableTypes.KUBERNETES_CLUSTER.name())) {
      return emptyList();
    } else {
      return createPerpetualTasks(containersMetadata, infrastructureMapping);
    }
  }

  private Set<ContainerMetadata> getContainerMetadataFromInstances(String appId, String infrastructureMappingId) {
    List<Instance> instances = instanceService.getInstancesForAppAndInframapping(appId, infrastructureMappingId);
    log.info("Found {} instances for app {}", instances.size(), appId);

    return emptyIfNull(instances)
        .stream()
        .map(Instance::getInstanceInfo)
        .filter(ContainerInfo.class ::isInstance)
        .map(ContainerInfo.class ::cast)
        .filter(containerInfo
            -> containerInfo instanceof KubernetesContainerInfo || containerInfo instanceof K8sPodInfo
                || containerInfo instanceof EcsContainerInfo)
        .map(containerInfo
            -> ContainerMetadata.builder()
                   .type(getContainerMetadataTypeFrom(containerInfo))
                   .containerServiceName(getContainerSvcNameIfAvailable(containerInfo))
                   .namespace(getNamespaceIfAvailable(containerInfo))
                   .releaseName(getReleaseNameIfAvailable(containerInfo))
                   .clusterName(containerInfo.getClusterName())
                   .build())
        .collect(Collectors.toSet());
  }

  private String getNamespaceIfAvailable(ContainerInfo containerInfo) {
    return !(containerInfo instanceof EcsContainerInfo) ? getNamespace(containerInfo) : null;
  }

  private String getNamespace(ContainerInfo containerInfo) {
    return containerInfo instanceof KubernetesContainerInfo ? ((KubernetesContainerInfo) containerInfo).getNamespace()
                                                            : ((K8sPodInfo) containerInfo).getNamespace();
  }

  private String getReleaseNameIfAvailable(ContainerInfo containerInfo) {
    return containerInfo instanceof K8sPodInfo ? ((K8sPodInfo) containerInfo).getReleaseName() : null;
  }

  private ContainerMetadataType getContainerMetadataTypeFrom(ContainerInfo containerInfo) {
    return containerInfo instanceof K8sPodInfo ? K8S : null;
  }

  private String getContainerSvcNameIfAvailable(ContainerInfo containerInfo) {
    return !(containerInfo instanceof K8sPodInfo) ? getContainerSvcName(containerInfo) : null;
  }

  private String getContainerSvcName(ContainerInfo containerInfo) {
    return containerInfo instanceof KubernetesContainerInfo
        ? ((KubernetesContainerInfo) containerInfo).getControllerName()
        : ((EcsContainerInfo) containerInfo).getServiceName();
  }

  @Override
  public List<String> createPerpetualTasksForNewDeployment(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping) {
    SetView<ContainerMetadata> containersMetadataToExamine =
        getContainerMetadataDiff(deploymentSummaries, existingPerpetualTasks);

    return createPerpetualTasks(containersMetadataToExamine, infrastructureMapping);
  }

  @Override
  public List<PerpetualTaskRecord> createPerpetualTasksBackup(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping) {
    SetView<ContainerMetadata> containersMetadataToExamine =
        getContainerMetadataDiff(deploymentSummaries, existingPerpetualTasks);

    return containersMetadataToExamine.stream()
        .map(containerMetadata -> clientParamsBuilder(containerMetadata, infrastructureMapping))
        .map(params -> createForBackup(params, infrastructureMapping))
        .collect(Collectors.toList());
  }

  private ContainerInstanceSyncPerpetualTaskClientParams clientParamsBuilder(
      ContainerMetadata containerMetadata, InfrastructureMapping infrastructureMapping) {
    return ContainerInstanceSyncPerpetualTaskClientParams.builder()
        .appId(infrastructureMapping.getAppId())
        .inframappingId(infrastructureMapping.getUuid())
        .containerSvcName(containerMetadata.getContainerServiceName())
        .namespace(containerMetadata.getNamespace())
        .releaseName(containerMetadata.getReleaseName())
        .containerType(nonNull(containerMetadata.getType()) ? containerMetadata.getType().name() : null)
        .clusterName(containerMetadata.getClusterName())
        .build();
  }

  private SetView<ContainerMetadata> getContainerMetadataDiff(
      List<DeploymentSummary> deploymentSummaries, List<PerpetualTaskRecord> existingPerpetualTasks) {
    Set<ContainerMetadata> existingContainersMetadata =
        existingPerpetualTasks.stream().map(this::getContainerMetadataFromPT).collect(Collectors.toSet());

    Set<ContainerMetadata> newDeploymentContainersMetadata =
        deploymentSummaries.stream()
            .map(DeploymentSummary::getDeploymentInfo)
            .filter(deploymentInfo
                -> deploymentInfo instanceof BaseContainerDeploymentInfo || deploymentInfo instanceof K8sDeploymentInfo)
            .flatMap(deploymentInfo -> extractContainerMetadata(deploymentInfo).stream())
            .collect(Collectors.toSet());

    return Sets.difference(newDeploymentContainersMetadata, existingContainersMetadata);
  }

  @Override
  public Optional<String> restorePerpetualTask(
      PerpetualTaskRecord perpetualTask, List<PerpetualTaskRecord> existingPerpetualTasks) {
    Set<ContainerMetadata> existingContainersMetadata =
        existingPerpetualTasks.stream().map(this::getContainerMetadataFromPT).collect(Collectors.toSet());

    ContainerMetadata newPTContainerMetadata = getContainerMetadataFromPT(perpetualTask);
    if (existingContainersMetadata.contains(newPTContainerMetadata)) {
      log.warn("Perpetual task with old uuid {} already exists with", perpetualTask.getUuid());
      return Optional.empty();
    }

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                         .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                         .build();

    return Optional.of(
        perpetualTaskService.createTask(PerpetualTaskType.CONTAINER_INSTANCE_SYNC, perpetualTask.getAccountId(),
            perpetualTask.getClientContext(), schedule, ALLOW_DUPLICATE, perpetualTask.getTaskDescription()));
  }

  private ContainerMetadata getContainerMetadataFromPT(PerpetualTaskRecord record) {
    return ContainerMetadata.builder()
        .containerServiceName(
            record.getClientContext().getClientParams().get(InstanceSyncConstants.CONTAINER_SERVICE_NAME))
        .namespace(record.getClientContext().getClientParams().get(InstanceSyncConstants.NAMESPACE))
        .releaseName(record.getClientContext().getClientParams().get(InstanceSyncConstants.RELEASE_NAME))
        .type(extractContainerMetadataType(
            record.getClientContext().getClientParams().get(InstanceSyncConstants.CONTAINER_TYPE)))
        .clusterName(record.getClientContext().getClientParams().get(CLUSTER_NAME))
        .build();
  }
  private ContainerMetadataType extractContainerMetadataType(String containerTypeRecord) {
    return K8S.name().equals(containerTypeRecord) ? K8S : null;
  }

  private List<String> createPerpetualTasks(
      Set<ContainerMetadata> containersMetadata, InfrastructureMapping infrastructureMapping) {
    return containersMetadata.stream()
        .map(containerMetadata -> clientParamsBuilder(containerMetadata, infrastructureMapping))
        .map(params -> create(params, infrastructureMapping))
        .collect(Collectors.toList());
  }

  private PerpetualTaskRecord createForBackup(
      ContainerInstanceSyncPerpetualTaskClientParams clientParams, InfrastructureMapping infraMapping) {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(HARNESS_APPLICATION_ID, clientParams.getAppId());
    clientParamMap.put(INFRASTRUCTURE_MAPPING_ID, clientParams.getInframappingId());
    clientParamMap.put(NAMESPACE, clientParams.getNamespace());
    clientParamMap.put(RELEASE_NAME, clientParams.getReleaseName());
    clientParamMap.put(CONTAINER_SERVICE_NAME, clientParams.getContainerSvcName());
    clientParamMap.put(CONTAINER_TYPE, Utils.emptyIfNull(clientParams.getContainerType()));

    if (Objects.nonNull(clientParams.getClusterName())) {
      clientParamMap.put(CLUSTER_NAME, clientParams.getClusterName());
    }

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                         .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                         .build();

    return createPerpetualTaskRecord(PerpetualTaskType.CONTAINER_INSTANCE_SYNC, infraMapping.getAccountId(),
        clientContext, schedule, getTaskDescription(infraMapping));
  }

  public PerpetualTaskRecord createPerpetualTaskRecord(String perpetualTaskType, String accountId,

      PerpetualTaskClientContext clientContext, PerpetualTaskSchedule schedule, String taskDescription) {
    return PerpetualTaskRecord.builder()
        .accountId(accountId)
        .perpetualTaskType(perpetualTaskType)
        .clientContext(clientContext)
        .timeoutMillis(Durations.toMillis(schedule.getTimeout()))
        .delegateId("")
        .assignIteration(currentTimeMillis())
        .taskDescription(taskDescription)
        .build();
  }
  private String create(
      ContainerInstanceSyncPerpetualTaskClientParams clientParams, InfrastructureMapping infraMapping) {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(HARNESS_APPLICATION_ID, clientParams.getAppId());
    clientParamMap.put(INFRASTRUCTURE_MAPPING_ID, clientParams.getInframappingId());
    clientParamMap.put(NAMESPACE, clientParams.getNamespace());
    clientParamMap.put(RELEASE_NAME, clientParams.getReleaseName());
    clientParamMap.put(CONTAINER_SERVICE_NAME, clientParams.getContainerSvcName());
    clientParamMap.put(CONTAINER_TYPE, Utils.emptyIfNull(clientParams.getContainerType()));

    if (Objects.nonNull(clientParams.getClusterName())) {
      clientParamMap.put(CLUSTER_NAME, clientParams.getClusterName());
    }

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(INTERVAL_MINUTES))
                                         .setTimeout(Durations.fromSeconds(TIMEOUT_SECONDS))
                                         .build();

    return perpetualTaskService.createTask(PerpetualTaskType.CONTAINER_INSTANCE_SYNC, infraMapping.getAccountId(),
        clientContext, schedule, ALLOW_DUPLICATE, getTaskDescription(infraMapping));
  }

  private Set<ContainerMetadata> extractContainerMetadata(DeploymentInfo deploymentInfo) {
    return deploymentInfo instanceof BaseContainerDeploymentInfo
        ? getContainerMetadataFromContainerDeploymentInfo((BaseContainerDeploymentInfo) deploymentInfo)
        : getContainerMetadataFromK8DeploymentInfo((K8sDeploymentInfo) deploymentInfo);
  }

  private Set<ContainerMetadata> getContainerMetadataFromContainerDeploymentInfo(
      BaseContainerDeploymentInfo baseContainerDeploymentInfo) {
    if (baseContainerDeploymentInfo instanceof ContainerDeploymentInfoWithNames) {
      return ImmutableSet.of(
          ContainerMetadata.builder()
              .containerServiceName(
                  ((ContainerDeploymentInfoWithNames) baseContainerDeploymentInfo).getContainerSvcName())
              .namespace(((ContainerDeploymentInfoWithNames) baseContainerDeploymentInfo).getNamespace())
              .build());
    } else if (baseContainerDeploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      final ContainerDeploymentInfoWithLabels containerDeploymentInfoWithLabels =
          (ContainerDeploymentInfoWithLabels) baseContainerDeploymentInfo;
      Set<String> controllers = emptyIfNull(containerDeploymentInfoWithLabels.getContainerInfoList())
                                    .stream()
                                    .map(io.harness.container.ContainerInfo::getWorkloadName)
                                    .filter(EmptyPredicate::isNotEmpty)
                                    .collect(Collectors.toSet());
      if (isNotEmpty(controllers)) {
        return controllers.stream()
            .map(controller
                -> ContainerMetadata.builder()
                       .namespace(containerDeploymentInfoWithLabels.getNamespace())
                       .containerServiceName(controller)
                       .releaseName(containerDeploymentInfoWithLabels.getReleaseName())
                       .build())
            .collect(Collectors.toSet());
      } else if (isNotEmpty(containerDeploymentInfoWithLabels.getContainerInfoList())) {
        if (isNotEmpty(containerDeploymentInfoWithLabels.getNamespaces())) {
          return containerDeploymentInfoWithLabels.getNamespaces()
              .stream()
              .map(ns
                  -> ContainerMetadata.builder()
                         .namespace(ns)
                         .releaseName(containerDeploymentInfoWithLabels.getReleaseName())
                         .build())
              .collect(Collectors.toSet());
        }
        return ImmutableSet.of(ContainerMetadata.builder()
                                   .namespace(containerDeploymentInfoWithLabels.getNamespace())
                                   .releaseName(containerDeploymentInfoWithLabels.getReleaseName())
                                   .build());
      }
    }
    return Collections.emptySet();
  }

  private Set<ContainerMetadata> getContainerMetadataFromK8DeploymentInfo(K8sDeploymentInfo k8sDeploymentInfo) {
    return getNamespaces(k8sDeploymentInfo)
        .stream()
        .map(namespace
            -> ContainerMetadata.builder()
                   .type(K8S)
                   .releaseName(k8sDeploymentInfo.getReleaseName())
                   .namespace(namespace)
                   .clusterName(k8sDeploymentInfo.getClusterName())
                   .build())
        .collect(Collectors.toSet());
  }

  private Set<String> getNamespaces(K8sDeploymentInfo k8sDeploymentInfo) {
    Set<String> namespaces = new HashSet<>();
    if (isNotBlank(k8sDeploymentInfo.getNamespace())) {
      namespaces.add(k8sDeploymentInfo.getNamespace());
    }

    if (isNotEmpty(k8sDeploymentInfo.getNamespaces())) {
      namespaces.addAll(k8sDeploymentInfo.getNamespaces());
    }

    return namespaces;
  }
}
