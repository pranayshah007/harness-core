/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.FeatureName.CDP_UPDATE_INSTANCE_DETAILS_WITH_IMAGE_SUFFIX;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_CONTAINER_DEPLOYMENTS;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.perpetualtask.PerpetualTaskState.TASK_INVALID;
import static io.harness.perpetualtask.PerpetualTaskType.CONTAINER_INSTANCE_SYNC;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.container.Label.Builder.aLabel;
import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.GeneralException;
import io.harness.exception.K8sPodSyncException;
import io.harness.exception.runtime.NoInstancesException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.api.CommandStepExecutionSummary;
import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.HelmSetupExecutionSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.api.KubernetesSteadyStateCheckExecutionSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.k8s.K8sExecutionSummary;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.container.Label;
import software.wings.beans.infrastructure.instance.ContainerDeploymentInfo;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.service.ContainerInstanceSyncPerpetualTaskCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.ContainerMetadataType;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.request.ContainerFilter;
import software.wings.service.impl.instance.sync.request.ContainerSyncRequest;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.sm.ExecutionContext;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.StepExecutionSummary;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
@BreakDependencyOn("software.wings.dl.WingsPersistence")
@BreakDependencyOn("software.wings.sm.StateType")
public class ContainerInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private ContainerSync containerSync;
  @Inject private transient K8sStateHelper k8sStateHelper;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ContainerInstanceSyncPerpetualTaskCreator taskCreator;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    // Key - containerSvcName, Value - Instances
    ContainerInfrastructureMapping containerInfraMapping = getContainerInfraMapping(appId, infraMappingId);
    syncInstancesInternal(containerInfraMapping, ArrayListMultimap.create(), null, false, null, instanceSyncFlow);
  }

  private ContainerInfrastructureMapping getContainerInfraMapping(String appId, String inframappingId) {
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, inframappingId);
    notNullCheck("Infra mapping is null for id:" + inframappingId, infrastructureMapping);

    if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
      String msg = "Incompatible infrastructure mapping type found:" + infrastructureMapping.getInfraMappingType();
      throw new GeneralException(msg);
    }

    return (ContainerInfrastructureMapping) infrastructureMapping;
  }

  private void syncInstancesInternal(ContainerInfrastructureMapping containerInfraMapping,
      Multimap<ContainerMetadata, Instance> containerMetadataInstanceMap,
      List<DeploymentSummary> newDeploymentSummaries, boolean rollback, DelegateResponseData responseData,
      InstanceSyncFlow instanceSyncFlow) {
    String appId = containerInfraMapping.getAppId();
    Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap =
        getDeploymentSummaryMap(newDeploymentSummaries, containerMetadataInstanceMap, containerInfraMapping);

    loadContainerSvcNameInstanceMap(containerInfraMapping, containerMetadataInstanceMap);

    if (containerMetadataInstanceMap == null) {
      return;
    }

    if (instanceSyncFlow == PERPETUAL_TASK && responseData != null) {
      ContainerMetadata perpetualTaskMetadata = getContainerMetadataFromInstanceSyncResponse(responseData);

      // In case if there is no entry in containerMetadataInstanceMap (meaning that there is no instances in db
      // for given release name and namespace) we will add all instances from perpetual task response
      if (perpetualTaskMetadata != null && !containerMetadataInstanceMap.containsKey(perpetualTaskMetadata)) {
        if (responseData instanceof K8sTaskExecutionResponse) {
          K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
          K8sInstanceSyncResponse syncResponse =
              (K8sInstanceSyncResponse) k8sTaskExecutionResponse.getK8sTaskResponse();

          // Current logic is to catch any exception and if there is no successful sync status during 7 days then delete
          // all infra perpetual tasks. We're exploiting this to handle the case when replica is scaled down to 0 and
          // after n time is scaled back, so we will delete perpetual task only if after 7 days there are no pods
          if (isEmpty(syncResponse.getK8sPodInfoList())) {
            // In this case there is nothing to be processed since there is no instances in db that need to be removed
            log.info("Still there is no pods found for [app: {}, namespace: {}, release name: {}]", appId,
                syncResponse.getNamespace(), syncResponse.getReleaseName());
            throw new NoInstancesException(format("No pods found for namespace: %s and release name: %s",
                syncResponse.getNamespace(), syncResponse.getReleaseName()));
          }

          processK8sPodsInstances(containerInfraMapping, perpetualTaskMetadata, emptyList(), deploymentSummaryMap,
              syncResponse.getK8sPodInfoList());
          return;

        } else if (responseData instanceof ContainerSyncResponse) {
          ContainerSyncResponse syncResponse = (ContainerSyncResponse) responseData;

          // Current logic is to catch any exception and if there is no successful sync status during 7 days then delete
          // all infra perpetual tasks. We're exploiting this to handle the case when replica is scaled down to 0 and
          // after n time is scaled back, so we will delete perpetual task only if after 7 days there are no pods
          if (isEmpty(syncResponse.getContainerInfoList())) {
            // In this case there is nothing to be processed since there is no instances in db that need to be removed
            log.info("Still there is no containers found for [app: {}, namespace: {}, release name: {}]", appId,
                syncResponse.getNamespace(), syncResponse.getReleaseName());
            throw new NoInstancesException(format("No containers found for namespace: %s and release name: %s",
                syncResponse.getNamespace(), syncResponse.getReleaseName()));
          }

          processContainerServiceInstances(rollback, containerInfraMapping, deploymentSummaryMap, perpetualTaskMetadata,
              emptyList(), syncResponse.getContainerInfoList());

          return;
        }
      }
    }

    NoInstancesException noInstancesException = null;
    // This is to handle the case of the instances stored in the new schema.
    if (containerMetadataInstanceMap.size() > 0) {
      for (ContainerMetadata containerMetadata : containerMetadataInstanceMap.keySet()) {
        Collection<Instance> instancesInDB = Optional.ofNullable(containerMetadataInstanceMap.get(containerMetadata))
                                                 .orElse(emptyList())
                                                 .stream()
                                                 .filter(Objects::nonNull)
                                                 .collect(toList());

        if (containerMetadata.getType() == ContainerMetadataType.K8S) {
          // log.info("Found {} instances in DB for app {} and releaseName {}", instancesInDB.size(), appId,
          //   containerMetadata.getReleaseName());

          if (instanceSyncFlow == PERPETUAL_TASK && responseData instanceof K8sTaskExecutionResponse) {
            K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
            K8sInstanceSyncResponse syncResponse =
                (K8sInstanceSyncResponse) k8sTaskExecutionResponse.getK8sTaskResponse();
            // In case instances are from perpetual task response, we would like to apply it only if the response
            // release name and namespace matches the current ContainerMetadata releaseName and namespace
            // Otherwise it will lead to deleting the pods that are not part of response's releaseName and namespace
            if (isNotEmpty(syncResponse.getNamespace()) && isNotEmpty(syncResponse.getReleaseName())
                && syncResponse.getNamespace().equals(containerMetadata.getNamespace())
                && syncResponse.getReleaseName().equals(containerMetadata.getReleaseName())
                && ((StringUtils.isBlank(syncResponse.getClusterName())
                        && StringUtils.isBlank(containerMetadata.getClusterName()))
                    || (syncResponse.getClusterName().equals(containerMetadata.getClusterName())))) {
              processK8sPodsInstances(containerInfraMapping, containerMetadata, instancesInDB, deploymentSummaryMap,
                  syncResponse.getK8sPodInfoList());

              // Current logic is to catch any exception and if there is no successful sync status during 7 days then
              // delete all infra perpetual tasks. We're exploiting this to handle the case when replica is scaled down
              // to 0 and after n time is scaled back, so we will delete perpetual task only if after 7 days there are
              // no pods
              if (isEmpty(syncResponse.getK8sPodInfoList())) {
                log.info("No pods found for [app: {}, namespace: {}, release name: {}]", appId,
                    syncResponse.getNamespace(), syncResponse.getReleaseName());
                noInstancesException =
                    new NoInstancesException(format("No pods found for namespace: %s and release name: %s",
                        syncResponse.getNamespace(), syncResponse.getReleaseName()));
              }
            }
          } else {
            // For iterator and new deployment flow will fetch existing pods from cluster
            handleK8sInstances(containerInfraMapping, deploymentSummaryMap, containerMetadata, instancesInDB);
          }

        } else {
          ContainerSyncResponse syncResponse = null;
          if (responseData != null && instanceSyncFlow == PERPETUAL_TASK) {
            syncResponse = (ContainerSyncResponse) responseData;
            if (!responseBelongsToCurrentSetOfContainers(containerMetadata, syncResponse)) {
              continue;
            }
            // don't update ecs instances if delegate response is failure
            if (syncResponse.getCommandExecutionStatus() == FAILURE) {
              continue;
            }
          }

          handleContainerServiceInstances(rollback, responseData, instanceSyncFlow, containerInfraMapping,
              deploymentSummaryMap, containerMetadata, instancesInDB);

          if (syncResponse != null && !syncResponse.isEcs()) {
            // Current logic is to catch any exception and if there is no successful sync status during 7 days then
            // delete all infra perpetual tasks. We're exploiting this to handle the case when replica is scaled down to
            // 0 and after n time is scaled back, so we will delete perpetual task only if after 7 days there are no
            // containers
            if (isEmpty(syncResponse.getContainerInfoList())) {
              log.info("No containers found for [app: {}, namespace: {}, release name: {}]", appId,
                  syncResponse.getNamespace(), syncResponse.getReleaseName());

              noInstancesException =
                  new NoInstancesException(format("No containers found for namespace: %s and release name: %s",
                      syncResponse.getNamespace(), syncResponse.getReleaseName()));
            }
          }
        }
      }
    }

    if (instanceSyncFlow == PERPETUAL_TASK && noInstancesException != null) {
      throw noInstancesException;
    }
  }

  private boolean responseBelongsToCurrentSetOfContainers(
      ContainerMetadata containerMetadata, ContainerSyncResponse syncResponse) {
    final boolean controllerNamePresent = isNotEmpty(syncResponse.getControllerName());
    final boolean sameControllerName =
        controllerNamePresent && syncResponse.getControllerName().equals(containerMetadata.getContainerServiceName());
    final boolean releaseNamePresent = isNotEmpty(syncResponse.getReleaseName());
    final boolean sameReleaseName =
        releaseNamePresent && syncResponse.getReleaseName().equals(containerMetadata.getReleaseName());
    final boolean namespacePresent = isNotEmpty(syncResponse.getNamespace());
    final boolean sameNamespace =
        namespacePresent && syncResponse.getNamespace().equals(containerMetadata.getNamespace());
    return (!controllerNamePresent || sameControllerName) && (!releaseNamePresent || sameReleaseName)
        && (!namespacePresent || sameNamespace);
  }

  /**
   * In the case of Ecs we have some older instances that are already marked as deleted in the
   * map of instances in DB. We can't delete them again as then there deletedAt field in those
   * would be updated, which we don't want.
   */
  private SetView<String> getInstancesToBeDeleted(ContainerInfrastructureMapping containerInfraMapping,
      Map<String, Instance> instancesInDBMap, Map<String, ContainerInfo> latestContainerInfoMap) {
    if (containerInfraMapping instanceof EcsInfrastructureMapping) {
      Set<String> instanceKeysToBeDeleted = new HashSet<>();
      for (Map.Entry<String, Instance> entry : instancesInDBMap.entrySet()) {
        String key = entry.getKey();
        Instance instance = entry.getValue();
        if (instance.isDeleted()) {
          continue;
        }
        if (!latestContainerInfoMap.containsKey(key)) {
          instanceKeysToBeDeleted.add(key);
        }
      }
      return Sets.difference(instanceKeysToBeDeleted, emptySet());
    } else {
      return Sets.difference(instancesInDBMap.keySet(), latestContainerInfoMap.keySet());
    }
  }

  private void handleContainerServiceInstances(boolean rollback, DelegateResponseData responseData,
      InstanceSyncFlow instanceSyncFlow, ContainerInfrastructureMapping containerInfraMapping,
      Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap, ContainerMetadata containerMetadata,
      Collection<Instance> instancesInDB) {
    // Get all the instances for the given containerSvcName (In kubernetes, this is replication Controller and in
    // ECS it is taskDefinition)
    List<ContainerInfo> latestContainerInfoList =
        getContainerInfos(responseData, instanceSyncFlow, containerInfraMapping, containerMetadata);
    // log.info("Found {} instances from remote server for app {} and containerSvcName {}",
    // latestContainerInfoList.size(),
    //    containerInfraMapping.getAppId(), containerMetadata.getContainerServiceName());
    processContainerServiceInstances(rollback, containerInfraMapping, deploymentSummaryMap, containerMetadata,
        instancesInDB, latestContainerInfoList);
  }

  private void processContainerServiceInstances(boolean rollback, ContainerInfrastructureMapping containerInfraMapping,
      Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap, ContainerMetadata containerMetadata,
      Collection<Instance> instancesInDB, List<ContainerInfo> latestContainerInfoList) {
    // Key - containerId(taskId in ECS / podId+namespace in Kubernetes), Value - ContainerInfo
    Map<String, ContainerInfo> latestContainerInfoMap = new HashMap<>();
    HelmChartInfo helmChartInfo = getContainerHelmChartInfo(deploymentSummaryMap.get(containerMetadata), instancesInDB);
    for (ContainerInfo info : latestContainerInfoList) {
      if (info instanceof KubernetesContainerInfo) {
        KubernetesContainerInfo k8sInfo = (KubernetesContainerInfo) info;
        String namespace = isNotBlank(k8sInfo.getNamespace()) ? k8sInfo.getNamespace() : "";
        String releaseName = getReleaseNameKey(k8sInfo);
        latestContainerInfoMap.put(k8sInfo.getPodName() + namespace + releaseName, info);
        setHelmChartInfoToContainerInfo(helmChartInfo, k8sInfo);
      } else {
        latestContainerInfoMap.put(((EcsContainerInfo) info).getTaskArn(), info);
      }
    }

    // Key - containerId (taskId in ECS / podId in Kubernetes), Value - Instance
    Map<String, Instance> instancesInDBMap = new HashMap<>();

    // If there are prior instances in db already
    for (Instance instance : instancesInDB) {
      ContainerInstanceKey key = instance.getContainerInstanceKey();
      String namespace = isNotBlank(key.getNamespace()) ? key.getNamespace() : "";
      String releaseName = getReleaseNameKey(instance.getInstanceInfo());
      String instanceMapKey = key.getContainerId() + namespace + releaseName;

      if (!instancesInDBMap.containsKey(instanceMapKey)) {
        instancesInDBMap.put(instanceMapKey, instance);
      } else {
        instancesInDBMap.put(instanceMapKey + instance.getUuid(), instance);
      }
    }

    // Find the instances that were yet to be added to db
    SetView<String> instancesToBeAdded = Sets.difference(latestContainerInfoMap.keySet(), instancesInDBMap.keySet());

    SetView<String> instancesToBeDeleted =
        getInstancesToBeDeleted(containerInfraMapping, instancesInDBMap, latestContainerInfoMap);

    Set<String> instanceIdsToBeDeleted = new HashSet<>();
    for (String containerId : instancesToBeDeleted) {
      Instance instance = instancesInDBMap.get(containerId);
      if (instance != null) {
        instanceIdsToBeDeleted.add(instance.getUuid());
      }
    }

    if (instancesToBeAdded.size() > 0 || instanceIdsToBeDeleted.size() > 0) {
      log.info(
          "Instances to be added {}, to be deleted {}, in DB {}, Running {} for ContainerSvcName: {}, Namespace {} and AppId: {}",
          instancesToBeAdded.size(), instanceIdsToBeDeleted.size(), instancesInDB.size(),
          latestContainerInfoMap.keySet().size(), containerMetadata.getContainerServiceName(),
          containerMetadata.getNamespace(), containerInfraMapping.getAppId());
    }

    if (isNotEmpty(instanceIdsToBeDeleted)) {
      instanceService.delete(instanceIdsToBeDeleted);
    }

    DeploymentSummary deploymentSummary;
    if (isNotEmpty(instancesToBeAdded)) {
      // newDeploymentInfo would be null in case of sync job.
      if (!deploymentSummaryMap.containsKey(containerMetadata) && isNotEmpty(instancesInDB)) {
        Optional<Instance> instanceWithExecutionInfoOptional = getInstanceWithExecutionInfo(instancesInDB);
        if (!instanceWithExecutionInfoOptional.isPresent()) {
          log.warn("Couldn't find an instance from a previous deployment");
          return;
        }

        DeploymentSummary deploymentSummaryFromPrevious =
            DeploymentSummary.builder().deploymentInfo(ContainerDeploymentInfoWithNames.builder().build()).build();
        // We pick one of the existing instances from db for the same controller / task definition
        generateDeploymentSummaryFromInstance(instanceWithExecutionInfoOptional.get(), deploymentSummaryFromPrevious);
        deploymentSummary = deploymentSummaryFromPrevious;
      } else {
        deploymentSummary =
            getDeploymentSummaryForInstanceCreation(deploymentSummaryMap.get(containerMetadata), rollback);
      }

      if (deploymentSummary == null) {
        deploymentSummary = DeploymentSummary.builder()
                                .deployedByName(AUTO_SCALE)
                                .deployedById(AUTO_SCALE)
                                .deployedAt(System.currentTimeMillis())
                                .build();
      }

      for (String containerId : instancesToBeAdded) {
        ContainerInfo containerInfo = latestContainerInfoMap.get(containerId);
        Instance instance = buildInstanceFromContainerInfo(containerInfraMapping, containerInfo, deploymentSummary);
        instanceService.save(instance);
      }
    }

    // Update the existing instances helm chart info
    if (deploymentSummaryMap.containsKey(containerMetadata)) {
      deploymentSummary = deploymentSummaryMap.get(containerMetadata);
      if (deploymentSummary.getDeploymentInfo() instanceof ContainerDeploymentInfoWithLabels) {
        ContainerDeploymentInfoWithLabels deploymentInfo =
            (ContainerDeploymentInfoWithLabels) deploymentSummary.getDeploymentInfo();
        SetView<String> instancesToBeUpdated =
            Sets.intersection(latestContainerInfoMap.keySet(), instancesInDBMap.keySet());
        for (String instanceId : instancesToBeUpdated) {
          Instance instance = instancesInDBMap.get(instanceId);
          if (updateHelmChartInfoForContainerInstances(deploymentInfo, instance)) {
            instanceService.saveOrUpdate(instance);
          }
        }
      }
    }
  }

  private String getReleaseNameKey(InstanceInfo instanceInfo) {
    String releaseName = null;
    if (instanceInfo instanceof KubernetesContainerInfo) {
      releaseName = ((KubernetesContainerInfo) instanceInfo).getReleaseName();
    } else if (instanceInfo instanceof K8sPodInfo) {
      releaseName = ((K8sPodInfo) instanceInfo).getReleaseName();
    }

    return isNotBlank(releaseName) ? releaseName : "";
  }

  private List<ContainerInfo> getContainerInfos(DelegateResponseData responseData, InstanceSyncFlow instanceSyncFlow,
      ContainerInfrastructureMapping containerInfraMapping, ContainerMetadata containerMetadata) {
    ContainerSyncResponse instanceSyncResponse = null;
    if (PERPETUAL_TASK != instanceSyncFlow) {
      instanceSyncResponse = containerSync.getInstances(containerInfraMapping, singletonList(containerMetadata));
    } else if (responseData instanceof ContainerSyncResponse) {
      instanceSyncResponse = (ContainerSyncResponse) responseData;
    }

    if (instanceSyncResponse == null) {
      throw new GeneralException(
          "InstanceSyncResponse is null for containerSvcName: " + containerMetadata.getContainerServiceName());
    }

    return Optional.ofNullable(instanceSyncResponse.getContainerInfoList()).orElse(emptyList());
  }

  private void handleK8sInstances(ContainerInfrastructureMapping containerInfraMapping,
      Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap, ContainerMetadata containerMetadata,
      Collection<Instance> instancesInDB) {
    List<K8sPod> k8sPods = getK8sPodsFromDelegate(containerInfraMapping, containerMetadata);
    processK8sPodsInstances(containerInfraMapping, containerMetadata, instancesInDB, deploymentSummaryMap, k8sPods);
  }

  @VisibleForTesting
  HelmChartInfo getContainerHelmChartInfo(DeploymentSummary deploymentSummary, Collection<Instance> existingInstances) {
    return Optional.ofNullable(deploymentSummary)
        .map(DeploymentSummary::getDeploymentInfo)
        .filter(ContainerDeploymentInfoWithLabels.class ::isInstance)
        .map(ContainerDeploymentInfoWithLabels.class ::cast)
        .map(ContainerDeploymentInfoWithLabels::getHelmChartInfo)
        .orElseGet(()
                       -> existingInstances.stream()
                              .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
                              .map(Instance::getInstanceInfo)
                              .filter(KubernetesContainerInfo.class ::isInstance)
                              .map(KubernetesContainerInfo.class ::cast)
                              .map(KubernetesContainerInfo::getHelmChartInfo)
                              .filter(Objects::nonNull)
                              .findFirst()
                              .orElse(null));
  }

  @VisibleForTesting
  void setHelmChartInfoToContainerInfo(HelmChartInfo helmChartInfo, ContainerInfo k8sInfo) {
    Optional.ofNullable(helmChartInfo).ifPresent(chartInfo -> {
      if (KubernetesContainerInfo.class == k8sInfo.getClass()) {
        ((KubernetesContainerInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      } else if (K8sPodInfo.class == k8sInfo.getClass()) {
        ((K8sPodInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      }
    });
  }

  private boolean updateHelmChartInfoForContainerInstances(
      ContainerDeploymentInfoWithLabels deploymentInfo, Instance instance) {
    if (!(instance.getInstanceInfo() instanceof KubernetesContainerInfo)) {
      return false;
    }

    KubernetesContainerInfo containerInfo = (KubernetesContainerInfo) instance.getInstanceInfo();
    if (deploymentInfo.getHelmChartInfo() != null
        && !deploymentInfo.getHelmChartInfo().equals(containerInfo.getHelmChartInfo())) {
      containerInfo.setHelmChartInfo(deploymentInfo.getHelmChartInfo());
      return true;
    }

    return false;
  }

  private List<K8sPod> getK8sPodsFromDelegate(
      ContainerInfrastructureMapping containerInfraMapping, ContainerMetadata containerMetadata) {
    try {
      return k8sStateHelper.fetchPodListForCluster(containerInfraMapping, containerMetadata.getNamespace(),
          containerMetadata.getReleaseName(), containerMetadata.getClusterName());
    } catch (Exception e) {
      throw new K8sPodSyncException(
          format("Exception in fetching podList for release %s, namespace %s. Detail error Msg: %s",
              containerMetadata.getReleaseName(), containerMetadata.getNamespace(), e.getMessage()),
          e);
    }
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

  private String getImageInStringFormat(K8sPod pod) {
    return emptyIfNull(pod.getContainerList()).stream().map(K8sContainer::getImage).collect(Collectors.joining());
  }

  private void processK8sPodsInstances(ContainerInfrastructureMapping containerInfraMapping,
      ContainerMetadata containerMetadata, Collection<Instance> instancesInDB,
      Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap, List<K8sPod> currentPods) {
    Map<String, K8sPod> currentPodsMap = new HashMap<>();
    Map<String, Instance> dbPodMap = new HashMap<>();

    currentPods.forEach(podInfo
        -> currentPodsMap.put(podInfo.getName() + podInfo.getNamespace() + getImageInStringFormat(podInfo), podInfo));
    instancesInDB.forEach(podInstance
        -> dbPodMap.put(podInstance.getPodInstanceKey().getPodName() + podInstance.getPodInstanceKey().getNamespace()
                + getImageInStringFormat(podInstance),
            podInstance));

    SetView<String> instancesToBeAdded = Sets.difference(currentPodsMap.keySet(), dbPodMap.keySet());
    SetView<String> instancesToBeDeleted = Sets.difference(dbPodMap.keySet(), currentPodsMap.keySet());

    Set<String> instanceIdsToBeDeleted =
        instancesToBeDeleted.stream().map(instancePodName -> dbPodMap.get(instancePodName).getUuid()).collect(toSet());

    if (instancesToBeAdded.size() > 0 || instanceIdsToBeDeleted.size() > 0) {
      log.info(
          "[InstanceSync for inframapping {} namespace {} release {}] Got {} running Pods. InstancesToBeAdded:{} InstancesToBeDeleted:{}, DBInstances: {}",
          containerInfraMapping.getUuid(), containerMetadata.getNamespace(), containerMetadata.getReleaseName(),
          currentPods.size(), instancesToBeAdded.size(), instanceIdsToBeDeleted.size(), instancesInDB.size());
    }

    if (isNotEmpty(instanceIdsToBeDeleted)) {
      instanceService.delete(instanceIdsToBeDeleted);
    }

    DeploymentSummary deploymentSummary = deploymentSummaryMap.get(containerMetadata);
    for (String podName : instancesToBeAdded) {
      if (deploymentSummary == null) {
        deploymentSummary = DeploymentSummary.builder().build();
        InstanceInfo info = null;
        if (!instancesInDB.isEmpty()) {
          generateDeploymentSummaryFromInstance(instancesInDB.stream().findFirst().get(), deploymentSummary);
          info = instancesInDB.stream()
                     .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
                     .findFirst()
                     .get()
                     .getInstanceInfo();
        } else {
          Instance lastDiscoveredInstance = instanceService.getLastDiscoveredInstance(
              containerInfraMapping.getAppId(), containerInfraMapping.getUuid());

          if (Objects.nonNull(lastDiscoveredInstance)) {
            generateDeploymentSummaryFromInstance(lastDiscoveredInstance, deploymentSummary);
            info = lastDiscoveredInstance.getInstanceInfo();
          } else {
            deploymentSummary.setDeployedByName(AUTO_SCALE);
            deploymentSummary.setDeployedById(AUTO_SCALE);
            deploymentSummary.setDeployedAt(System.currentTimeMillis());
          }
        }

        if (Objects.nonNull(info) && info instanceof K8sPodInfo) {
          K8sPodInfo instanceInfo = (K8sPodInfo) info;
          if (Objects.isNull(deploymentSummary.getDeploymentInfo())) {
            deploymentSummary.setDeploymentInfo(K8sDeploymentInfo.builder()
                                                    .clusterName(instanceInfo.getClusterName())
                                                    .releaseName(instanceInfo.getReleaseName())
                                                    .namespace(instanceInfo.getNamespace())
                                                    .blueGreenStageColor(instanceInfo.getBlueGreenColor())
                                                    .helmChartInfo(instanceInfo.getHelmChartInfo())
                                                    .build());
          }
        }
      }
      HelmChartInfo helmChartInfo =
          getK8sPodHelmChartInfo(deploymentSummary, currentPodsMap.get(podName), instancesInDB);
      Instance instance =
          buildInstanceFromPodInfo(containerInfraMapping, currentPodsMap.get(podName), deploymentSummary);
      ContainerInfo containerInfo = (ContainerInfo) instance.getInstanceInfo();
      setHelmChartInfoToContainerInfo(helmChartInfo, containerInfo);
      instanceService.saveOrUpdate(instance);
    }

    // log.info("Instances to be added {}", instancesToBeAdded.size());

    if (deploymentSummaryMap.get(containerMetadata) != null) {
      deploymentSummary = deploymentSummaryMap.get(containerMetadata);
      SetView<String> instancesToBeUpdated = Sets.intersection(currentPodsMap.keySet(), dbPodMap.keySet());

      for (String podName : instancesToBeUpdated) {
        Instance instanceToBeUpdated = dbPodMap.get(podName);
        K8sPod k8sPod = currentPodsMap.get(podName);
        String deploymentWorkflowName = Optional.ofNullable(deploymentSummary.getWorkflowExecutionName()).orElse("");
        if (deploymentWorkflowName.equals(instanceToBeUpdated.getLastWorkflowExecutionName())) {
          boolean updated = updateHelmChartInfoForExistingK8sPod(instanceToBeUpdated, k8sPod, deploymentSummary);

          if (!deploymentSummary.getWorkflowExecutionId().equals(instanceToBeUpdated.getLastWorkflowExecutionId())) {
            updateInstanceBasedOnDeploymentSummary(instanceToBeUpdated, k8sPod, deploymentSummary);
            updated = true;
          }

          if (updated) {
            instanceService.saveOrUpdate(instanceToBeUpdated);
          }
        }
      }
    }
  }

  private void updateInstanceBasedOnDeploymentSummary(
      Instance instanceToBeUpdated, K8sPod pod, DeploymentSummary deploymentSummary) {
    // In case when there is no artifact matches in artifact collection we will use artifactId from deploymentSummary
    // In case if pod is coming from auto scale and need to be updated, we need to ensure that artifact id is or from
    // actual artifact from artifact collection or we should update it from deployment summary
    Artifact artifact =
        findArtifactBasedOnK8sPod(pod, deploymentSummary.getArtifactStreamId(), deploymentSummary.getAppId());
    if (artifact == null) {
      instanceToBeUpdated.setLastArtifactId(deploymentSummary.getArtifactId());
    }

    // all the fields marked 'last' are updated except for artifact details
    instanceToBeUpdated.setLastDeployedById(deploymentSummary.getDeployedById());
    instanceToBeUpdated.setLastDeployedByName(deploymentSummary.getDeployedByName());
    instanceToBeUpdated.setLastDeployedAt(deploymentSummary.getDeployedAt());

    instanceToBeUpdated.setLastWorkflowExecutionId(deploymentSummary.getWorkflowExecutionId());
    instanceToBeUpdated.setLastWorkflowExecutionName(deploymentSummary.getWorkflowExecutionName());
    instanceToBeUpdated.setLastPipelineExecutionId(deploymentSummary.getPipelineExecutionId());
    instanceToBeUpdated.setLastPipelineExecutionName(deploymentSummary.getPipelineExecutionName());
  }

  private Artifact findArtifactBasedOnK8sPod(K8sPod pod, String artifactStreamId, String appId) {
    if (artifactStreamId != null) {
      for (K8sContainer k8sContainer : pod.getContainerList()) {
        Artifact artifact = findArtifactForImage(artifactStreamId, appId, k8sContainer.getImage());

        if (artifact != null) {
          return artifact;
        }
      }
    }

    return null;
  }

  private HelmChartInfo getK8sPodHelmChartInfo(
      DeploymentSummary deploymentSummary, K8sPod pod, Collection<Instance> instances) {
    if (deploymentSummary != null && deploymentSummary.getDeploymentInfo() instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo deploymentInfo = (K8sDeploymentInfo) deploymentSummary.getDeploymentInfo();
      if (StringUtils.equals(pod.getColor(), deploymentInfo.getBlueGreenStageColor())) {
        return deploymentInfo.getHelmChartInfo();
      }
    }

    return instances.stream()
        .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
        .map(Instance::getInstanceInfo)
        .filter(K8sPodInfo.class ::isInstance)
        .map(K8sPodInfo.class ::cast)
        .filter(podInfo -> StringUtils.equals(podInfo.getBlueGreenColor(), pod.getColor()))
        .findFirst()
        .map(K8sPodInfo::getHelmChartInfo)
        .orElse(null);
  }

  private boolean updateHelmChartInfoForExistingK8sPod(
      Instance instance, K8sPod pod, DeploymentSummary deploymentSummary) {
    if (!(instance.getInstanceInfo() instanceof K8sPodInfo)
        || !(deploymentSummary.getDeploymentInfo() instanceof K8sDeploymentInfo)) {
      return false;
    }

    K8sPodInfo k8sPodInfo = (K8sPodInfo) instance.getInstanceInfo();
    K8sDeploymentInfo deploymentInfo = (K8sDeploymentInfo) deploymentSummary.getDeploymentInfo();
    if (StringUtils.equals(deploymentInfo.getBlueGreenStageColor(), pod.getColor())) {
      if (deploymentInfo.getHelmChartInfo() != null
          && !deploymentInfo.getHelmChartInfo().equals(k8sPodInfo.getHelmChartInfo())) {
        k8sPodInfo.setHelmChartInfo(deploymentInfo.getHelmChartInfo());
        return true;
      }
    }

    return false;
  }

  @VisibleForTesting
  Map<ContainerMetadata, DeploymentSummary> getDeploymentSummaryMap(List<DeploymentSummary> newDeploymentSummaries,
      Multimap<ContainerMetadata, Instance> containerInstances, ContainerInfrastructureMapping containerInfraMapping) {
    if (EmptyPredicate.isEmpty(newDeploymentSummaries)) {
      return emptyMap();
    }

    Map<ContainerMetadata, DeploymentSummary> deploymentSummaryMap = new HashMap<>();

    if (newDeploymentSummaries.stream().iterator().next().getDeploymentInfo()
            instanceof ContainerDeploymentInfoWithLabels) {
      Map<String, String> labelMap = new HashMap<>();
      for (DeploymentSummary deploymentSummary : newDeploymentSummaries) {
        ContainerDeploymentInfoWithLabels containerDeploymentInfo =
            (ContainerDeploymentInfoWithLabels) deploymentSummary.getDeploymentInfo();
        containerDeploymentInfo.getLabels().forEach(
            labelEntry -> labelMap.put(labelEntry.getName(), labelEntry.getValue()));

        String namespace = containerInfraMapping.getNamespace();
        if (ExpressionEvaluator.containsVariablePattern(namespace)) {
          namespace = containerDeploymentInfo.getNamespace();
        }

        final List<String> namespaces = containerDeploymentInfo.getNamespaces();

        boolean isControllerNamesRetrievable = emptyIfNull(containerDeploymentInfo.getContainerInfoList())
                                                   .stream()
                                                   .map(io.harness.container.ContainerInfo::getWorkloadName)
                                                   .anyMatch(EmptyPredicate::isNotEmpty);
        /*
         We need controller names only if release name is not set
         */
        if (isControllerNamesRetrievable || isEmpty(containerDeploymentInfo.getContainerInfoList())) {
          Set<String> controllerNames = containerSync.getControllerNames(containerInfraMapping, labelMap, namespace);

          log.info(
              "Number of controllers returned for executionId [{}], inframappingId [{}], appId [{}] from labels: {}",
              newDeploymentSummaries.iterator().next().getWorkflowExecutionId(), containerInfraMapping.getUuid(),
              newDeploymentSummaries.iterator().next().getAppId(), controllerNames.size());

          for (String controllerName : controllerNames) {
            ContainerMetadata containerMetadata = ContainerMetadata.builder()
                                                      .containerServiceName(controllerName)
                                                      .namespace(namespace)
                                                      .releaseName(containerDeploymentInfo.getReleaseName())
                                                      .build();
            deploymentSummaryMap.put(containerMetadata, deploymentSummary);
            containerInstances.put(containerMetadata, null);
          }
        } else {
          if (isNotEmpty(namespaces)) {
            namespaces.stream()
                .map(ns
                    -> ContainerMetadata.builder()
                           .releaseName(containerDeploymentInfo.getReleaseName())
                           .namespace(ns)
                           .build())
                .forEach(containerMetadata -> {
                  deploymentSummaryMap.put(containerMetadata, deploymentSummary);
                  containerInstances.put(containerMetadata, null);
                });
          } else {
            // should not be called now
            ContainerMetadata containerMetadata = ContainerMetadata.builder()
                                                      .namespace(namespace)
                                                      .releaseName(containerDeploymentInfo.getReleaseName())
                                                      .build();
            deploymentSummaryMap.put(containerMetadata, deploymentSummary);
            containerInstances.put(containerMetadata, null);
          }
        }
      }
    } else if (newDeploymentSummaries.stream().iterator().next().getDeploymentInfo() instanceof K8sDeploymentInfo) {
      newDeploymentSummaries.forEach(deploymentSummary -> {
        K8sDeploymentInfo deploymentInfo = (K8sDeploymentInfo) deploymentSummary.getDeploymentInfo();

        String releaseName = deploymentInfo.getReleaseName();
        Set<String> namespaces = new HashSet<>();
        if (isNotBlank(deploymentInfo.getNamespace())) {
          namespaces.add(deploymentInfo.getNamespace());
        }

        if (isNotEmpty(deploymentInfo.getNamespaces())) {
          namespaces.addAll(deploymentInfo.getNamespaces());
        }

        for (String namespace : namespaces) {
          deploymentSummaryMap.put(ContainerMetadata.builder()
                                       .type(ContainerMetadataType.K8S)
                                       .releaseName(releaseName)
                                       .namespace(namespace)
                                       .clusterName(deploymentInfo.getClusterName())
                                       .build(),
              deploymentSummary);
        }
      });
    } else {
      newDeploymentSummaries.forEach(deploymentSummary -> {
        ContainerDeploymentInfoWithNames deploymentInfo =
            (ContainerDeploymentInfoWithNames) deploymentSummary.getDeploymentInfo();
        deploymentSummaryMap.put(ContainerMetadata.builder()
                                     .containerServiceName(deploymentInfo.getContainerSvcName())
                                     .namespace(deploymentInfo.getNamespace())
                                     .build(),
            deploymentSummary);
      });
    }

    return deploymentSummaryMap;
  }

  private void loadContainerSvcNameInstanceMap(
      ContainerInfrastructureMapping containerInfraMapping, Multimap<ContainerMetadata, Instance> instanceMap) {
    String appId = containerInfraMapping.getAppId();
    List<Instance> instanceListInDBForInfraMapping = (containerInfraMapping instanceof EcsInfrastructureMapping)
        ? instanceService.getInstancesForAppAndInframappingNotRemovedFully(appId, containerInfraMapping.getUuid())
        : getInstances(appId, containerInfraMapping.getUuid());

    // log.info("Found {} instances for app {}", instanceListInDBForInfraMapping.size(), appId);
    for (Instance instance : instanceListInDBForInfraMapping) {
      InstanceInfo instanceInfo = instance.getInstanceInfo();
      if (instanceInfo instanceof ContainerInfo) {
        ContainerInfo containerInfo = (ContainerInfo) instanceInfo;
        String containerSvcName = getContainerSvcName(containerInfo);
        String namespace = null;
        String releaseName = null;
        String clusterName = null;
        if (containerInfo instanceof KubernetesContainerInfo) {
          namespace = ((KubernetesContainerInfo) containerInfo).getNamespace();
          releaseName = ((KubernetesContainerInfo) containerInfo).getReleaseName();
        } else if (containerInfo instanceof K8sPodInfo) {
          namespace = ((K8sPodInfo) containerInfo).getNamespace();
          releaseName = ((K8sPodInfo) containerInfo).getReleaseName();
          if (StringUtils.isNotBlank(containerInfo.getClusterName())) {
            clusterName = containerInfo.getClusterName();
          }
        }
        ContainerMetadataType type = containerInfo instanceof K8sPodInfo ? ContainerMetadataType.K8S : null;
        instanceMap.put(ContainerMetadata.builder()
                            .type(type)
                            .containerServiceName(containerSvcName)
                            .namespace(namespace)
                            .releaseName(isNotEmpty(releaseName) ? releaseName : null)
                            .clusterName(clusterName)
                            .build(),
            instance);
      } else {
        throw new GeneralException("UnSupported instance deploymentInfo type" + instance.getInstanceType().name());
      }
    }
  }

  private ContainerMetadata getContainerMetadataFromInstanceSyncResponse(DelegateResponseData responseData) {
    String syncNamespace;
    String syncReleaseName;
    String clusterName = null;
    String containerServiceName = null;
    ContainerMetadataType syncType = null;
    if (responseData instanceof K8sTaskExecutionResponse) {
      K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
      if (k8sTaskExecutionResponse.getCommandExecutionStatus().equals(FAILURE)) {
        throw new K8sPodSyncException(format("Failed to fetch PodList Msg: %s. Status: %s",
            k8sTaskExecutionResponse.getErrorMessage(), k8sTaskExecutionResponse.getCommandExecutionStatus()));
      }
      K8sInstanceSyncResponse syncResponse = (K8sInstanceSyncResponse) k8sTaskExecutionResponse.getK8sTaskResponse();

      syncNamespace = syncResponse.getNamespace();
      syncReleaseName = syncResponse.getReleaseName();
      if (StringUtils.isNotBlank(syncResponse.getClusterName())) {
        clusterName = syncResponse.getClusterName();
      }
      syncType = ContainerMetadataType.K8S;
    } else if (responseData instanceof ContainerSyncResponse) {
      ContainerSyncResponse containerSyncResponse = (ContainerSyncResponse) responseData;
      if (containerSyncResponse.getCommandExecutionStatus().equals(FAILURE)) {
        throw new K8sPodSyncException(format("Failed to fetch PodList Msg: %s. Status: %s",
            containerSyncResponse.getErrorMessage(), containerSyncResponse.getCommandExecutionStatus()));
      }
      if (containerSyncResponse.isEcs()) {
        return null;
      }

      syncNamespace = containerSyncResponse.getNamespace();
      syncReleaseName = containerSyncResponse.getReleaseName();
      // Ref: ContainerInstanceSyncPerpetualTaskClient#getPerpetualTaskData at 207. We're always setting empty string if
      // controller name is null. These changes are behind FF KEEP_PT_AFTER_K8S_DOWNSCALE, we should revisit later
      // if this transformation does make sense or it does make more sense to get first controller from instance info
      containerServiceName =
          isEmpty(containerSyncResponse.getControllerName()) ? null : containerSyncResponse.getControllerName();
    } else {
      return null;
    }

    if (isNotEmpty(syncNamespace) && isNotEmpty(syncReleaseName)) {
      return ContainerMetadata.builder()
          .type(syncType)
          .namespace(syncNamespace)
          .releaseName(syncReleaseName)
          .containerServiceName(containerServiceName)
          .clusterName(clusterName)
          .build();
    }

    return null;
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    Multimap<ContainerMetadata, Instance> containerSvcNameInstanceMap = ArrayListMultimap.create();

    if (isEmpty(deploymentSummaries)) {
      return;
    }

    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    String appId = deploymentSummaries.iterator().next().getAppId();
    log.info("Handling new container deployment for inframappingId [{}]", infraMappingId);
    validateDeploymentInfos(deploymentSummaries);

    if (deploymentSummaries.iterator().next().getDeploymentInfo() instanceof ContainerDeploymentInfoWithNames) {
      deploymentSummaries.forEach(deploymentSummary -> {
        ContainerDeploymentInfoWithNames deploymentInfo =
            (ContainerDeploymentInfoWithNames) deploymentSummary.getDeploymentInfo();
        containerSvcNameInstanceMap.put(ContainerMetadata.builder()
                                            .containerServiceName(deploymentInfo.getContainerSvcName())
                                            .namespace(deploymentInfo.getNamespace())
                                            .build(),
            null);
      });
    } else if (deploymentSummaries.iterator().next().getDeploymentInfo() instanceof K8sDeploymentInfo) {
      deploymentSummaries.forEach(deploymentSummary -> {
        K8sDeploymentInfo deploymentInfo = (K8sDeploymentInfo) deploymentSummary.getDeploymentInfo();

        String releaseName = deploymentInfo.getReleaseName();
        Set<String> namespaces = new HashSet<>();
        if (isNotBlank(deploymentInfo.getNamespace())) {
          namespaces.add(deploymentInfo.getNamespace());
        }

        if (isNotEmpty(deploymentInfo.getNamespaces())) {
          namespaces.addAll(deploymentInfo.getNamespaces());
        }

        for (String namespace : namespaces) {
          containerSvcNameInstanceMap.put(ContainerMetadata.builder()
                                              .type(ContainerMetadataType.K8S)
                                              .releaseName(releaseName)
                                              .namespace(namespace)
                                              .clusterName(deploymentInfo.getClusterName())
                                              .build(),
              null);
        }
      });
    }

    ContainerInfrastructureMapping containerInfraMapping = getContainerInfraMapping(appId, infraMappingId);
    syncInstancesInternal(
        containerInfraMapping, containerSvcNameInstanceMap, deploymentSummaries, rollback, null, NEW_DEPLOYMENT);
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_CONTAINER_DEPLOYMENTS);
  }

  private void validateDeploymentInfos(List<DeploymentSummary> deploymentSummaries) {
    for (DeploymentSummary deploymentSummary : deploymentSummaries) {
      DeploymentInfo deploymentInfo = deploymentSummary.getDeploymentInfo();
      if (!(deploymentInfo instanceof ContainerDeploymentInfoWithNames)
          && !(deploymentInfo instanceof ContainerDeploymentInfoWithLabels)
          && !(deploymentInfo instanceof K8sDeploymentInfo)) {
        throw new GeneralException("Incompatible deployment info type: " + deploymentInfo);
      }
    }
  }

  public boolean isContainerDeployment(InfrastructureMapping infrastructureMapping) {
    return infrastructureMapping instanceof ContainerInfrastructureMapping;
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

    if (phaseStepExecutionSummary == null) {
      if (log.isDebugEnabled()) {
        log.debug("PhaseStepExecutionSummary is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }
    List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
    // This was observed when the "deploy containers" step was executed in rollback and no commands were
    // executed since setup failed.
    if (stepExecutionSummaryList == null) {
      if (log.isDebugEnabled()) {
        log.debug("StepExecutionSummaryList is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }

    List<DeploymentInfo> k8sDeploymentInfoList = new ArrayList<>();
    boolean isK8sDeployment = false;
    for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
      if (stepExecutionSummary != null) {
        if (stepExecutionSummary instanceof CommandStepExecutionSummary) {
          CommandStepExecutionSummary commandStepExecutionSummary = (CommandStepExecutionSummary) stepExecutionSummary;
          String clusterName = commandStepExecutionSummary.getClusterName();
          Set<String> containerSvcNameSet = Sets.newHashSet();

          if (checkIfContainerServiceDataAvailable(
                  stateExecutionInstanceId, commandStepExecutionSummary, containerSvcNameSet)) {
            return Optional.empty();
          }

          List<DeploymentInfo> containerDeploymentInfoWithNames = getContainerDeploymentInfos(
              clusterName, commandStepExecutionSummary.getNamespace(), commandStepExecutionSummary);

          return Optional.of(containerDeploymentInfoWithNames);

        } else if (stepExecutionSummary instanceof K8sExecutionSummary) {
          isK8sDeployment = true;
          k8sDeploymentInfoList.add(getK8sDeploymentInfo((K8sExecutionSummary) stepExecutionSummary));
        } else if (stepExecutionSummary instanceof HelmSetupExecutionSummary
            || stepExecutionSummary instanceof KubernetesSteadyStateCheckExecutionSummary) {
          if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
            log.warn("Inframapping is not container type. cannot proceed for state execution instance: {}",
                stateExecutionInstanceId);
            return Optional.empty();
          }

          String clusterName = ((ContainerInfrastructureMapping) infrastructureMapping).getClusterName();

          List<Label> labels = new ArrayList<>();

          DeploymentInfo deploymentInfo;
          if (stepExecutionSummary instanceof HelmSetupExecutionSummary) {
            HelmSetupExecutionSummary helmSetupExecutionSummary = (HelmSetupExecutionSummary) stepExecutionSummary;
            labels.add(aLabel().withName("release").withValue(helmSetupExecutionSummary.getReleaseName()).build());
            deploymentInfo =
                getContainerDeploymentInfosWithLabelsForHelm(clusterName, helmSetupExecutionSummary.getNamespace(),
                    labels, helmSetupExecutionSummary, workflowExecution.getHelmExecutionSummary());
          } else {
            KubernetesSteadyStateCheckExecutionSummary kubernetesSteadyStateCheckExecutionSummary =
                (KubernetesSteadyStateCheckExecutionSummary) stepExecutionSummary;
            labels.addAll(kubernetesSteadyStateCheckExecutionSummary.getLabels());
            deploymentInfo = getContainerDeploymentInfosWithLabels(
                clusterName, kubernetesSteadyStateCheckExecutionSummary.getNamespace(), labels);
          }

          if (deploymentInfo == null) {
            return Optional.empty();
          }

          return Optional.of(singletonList(deploymentInfo));
        }
      }
    }
    return isK8sDeployment ? Optional.of(k8sDeploymentInfoList) : Optional.empty();
  }

  private boolean checkIfContainerServiceDataAvailable(String stateExecutionInstanceId,
      CommandStepExecutionSummary commandStepExecutionSummary, Set<String> containerSvcNameSet) {
    if (commandStepExecutionSummary.getOldInstanceData() != null) {
      containerSvcNameSet.addAll(commandStepExecutionSummary.getOldInstanceData()
                                     .stream()
                                     .map(ContainerServiceData::getName)
                                     .collect(toList()));
    }

    if (commandStepExecutionSummary.getNewInstanceData() != null) {
      List<String> newcontainerSvcNames = commandStepExecutionSummary.getNewInstanceData()
                                              .stream()
                                              .map(ContainerServiceData::getName)
                                              .collect(toList());
      containerSvcNameSet.addAll(newcontainerSvcNames);
    }

    // Filter out null values
    List<String> serviceNames = containerSvcNameSet.stream().filter(EmptyPredicate::isNotEmpty).collect(toList());

    if (isEmpty(serviceNames)) {
      log.warn(
          "Both old and new container services are empty. Cannot proceed for phase step for state execution instance: {}",
          stateExecutionInstanceId);
      return true;
    }
    return false;
  }

  private DeploymentInfo getContainerDeploymentInfosWithLabels(
      String clusterName, String namespace, List<Label> labels) {
    return ContainerDeploymentInfoWithLabels.builder()
        .clusterName(clusterName)
        .namespace(namespace)
        .labels(labels)
        .build();
  }

  @VisibleForTesting
  DeploymentInfo getContainerDeploymentInfosWithLabelsForHelm(String clusterName, String namespace, List<Label> labels,
      HelmSetupExecutionSummary executionSummary, HelmExecutionSummary helmExecutionSummary) {
    Integer version = executionSummary.getRollbackVersion() == null ? executionSummary.getNewVersion()
                                                                    : executionSummary.getRollbackVersion();

    if (version == null) {
      return null;
    }

    return ContainerDeploymentInfoWithLabels.builder()
        .clusterName(clusterName)
        .namespace(namespace)
        .labels(labels)
        .newVersion(version.toString())
        .helmChartInfo(helmExecutionSummary.getHelmChartInfo())
        .containerInfoList(helmExecutionSummary.getContainerInfoList())
        .releaseName(helmExecutionSummary.getReleaseName())
        .namespaces(executionSummary.getNamespaces())
        .build();
  }

  private DeploymentInfo getK8sDeploymentInfo(K8sExecutionSummary k8sExecutionSummary) {
    return K8sDeploymentInfo.builder()
        .namespace(k8sExecutionSummary.getNamespace())
        .releaseName(k8sExecutionSummary.getReleaseName())
        .releaseNumber(k8sExecutionSummary.getReleaseNumber())
        .namespaces(k8sExecutionSummary.getNamespaces())
        .helmChartInfo(k8sExecutionSummary.getHelmChartInfo())
        .blueGreenStageColor(k8sExecutionSummary.getBlueGreenStageColor())
        .clusterName(k8sExecutionSummary.getClusterName())
        .build();
  }

  private List<DeploymentInfo> getContainerDeploymentInfos(
      String clusterName, String namespace, CommandStepExecutionSummary commandStepExecutionSummary) {
    List<DeploymentInfo> containerDeploymentInfoWithNames = new ArrayList<>();
    addToDeploymentInfoWithNames(
        clusterName, namespace, commandStepExecutionSummary.getNewInstanceData(), containerDeploymentInfoWithNames);
    addToDeploymentInfoWithNames(
        clusterName, namespace, commandStepExecutionSummary.getOldInstanceData(), containerDeploymentInfoWithNames);

    return containerDeploymentInfoWithNames;
  }

  private void addToDeploymentInfoWithNames(String clusterName, String namespace,
      List<ContainerServiceData> containerServiceDataList, List<DeploymentInfo> containerDeploymentInfoWithNames) {
    if (isNotEmpty(containerServiceDataList)) {
      containerServiceDataList.forEach(containerServiceData
          -> containerDeploymentInfoWithNames.add(ContainerDeploymentInfoWithNames.builder()
                                                      .containerSvcName(containerServiceData.getName())
                                                      .uniqueNameIdentifier(containerServiceData.getUniqueIdentifier())
                                                      .clusterName(clusterName)
                                                      .namespace(namespace)
                                                      .build()));
    }
  }

  private Instance buildInstanceFromContainerInfo(
      InfrastructureMapping infraMapping, ContainerInfo containerInfo, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(null, infraMapping, deploymentSummary);
    builder.containerInstanceKey(generateInstanceKeyForContainer(containerInfo));
    builder.instanceInfo(containerInfo);

    return builder.build();
  }

  private Instance buildInstanceFromPodInfo(
      InfrastructureMapping infraMapping, K8sPod pod, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(null, infraMapping, deploymentSummary);
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
      } else if (featureFlagService.isEnabled(
                     CDP_UPDATE_INSTANCE_DETAILS_WITH_IMAGE_SUFFIX, deploymentSummary.getAccountId())) {
        String imageSuffix = image.substring(image.lastIndexOf('/') + 1);
        artifact = findArtifactWithBuildNum(
            deploymentSummary.getArtifactStreamId(), infraMapping.getAppId(), deploymentSummary.getArtifactBuildNum());
        if (artifact != null) {
          if (firstValidArtifact == null) {
            firstValidArtifact = artifact;
            firstValidImage = image;
          }
          if (isNotEmpty(artifact.getMetadata().get("image"))
              && artifact.getMetadata().get("image").endsWith(imageSuffix)) {
            builder.lastArtifactId(artifact.getUuid());
            updateInstanceWithArtifactSourceAndBuildNum(builder, image);
            instanceBuilderUpdated = true;
            break;
          }
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

  private boolean isBuildNumSame(String build1, String build2) {
    if (isEmpty(build1) || isEmpty(build2)) {
      return false;
    }
    return build1.equals(build2);
  }

  private Artifact findArtifactForImage(String artifactStreamId, String appId, String image) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
        .filter(ArtifactKeys.appId, appId)
        .filter("metadata.image", image)
        .disableValidation()
        .get();
  }

  private Artifact findArtifactWithBuildNum(String artifactStreamId, String appId, String buildNo) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
        .filter(ArtifactKeys.appId, appId)
        .filter("metadata.buildNo", buildNo)
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

  private ContainerInstanceKey generateInstanceKeyForContainer(ContainerInfo containerInfo) {
    ContainerInstanceKey containerInstanceKey;

    if (containerInfo instanceof KubernetesContainerInfo) {
      KubernetesContainerInfo kubernetesContainerInfo = (KubernetesContainerInfo) containerInfo;
      containerInstanceKey = ContainerInstanceKey.builder()
                                 .containerId(kubernetesContainerInfo.getPodName())
                                 .namespace(((KubernetesContainerInfo) containerInfo).getNamespace())
                                 .build();
    } else if (containerInfo instanceof EcsContainerInfo) {
      EcsContainerInfo ecsContainerInfo = (EcsContainerInfo) containerInfo;
      containerInstanceKey = ContainerInstanceKey.builder().containerId(ecsContainerInfo.getTaskArn()).build();
    } else {
      String msg = "Unsupported container instance type:" + containerInfo;
      throw new GeneralException(msg);
    }

    return containerInstanceKey;
  }

  private String getContainerSvcName(ContainerInfo containerInfo) {
    if (containerInfo instanceof KubernetesContainerInfo) {
      return ((KubernetesContainerInfo) containerInfo).getControllerName();
    } else if (containerInfo instanceof EcsContainerInfo) {
      return ((EcsContainerInfo) containerInfo).getServiceName();
    }
    if (containerInfo instanceof K8sPodInfo) {
      return null;
    } else {
      throw new GeneralException(
          "Unsupported container deploymentInfo type:" + containerInfo.getClass().getCanonicalName());
    }
  }

  private ContainerSyncResponse getLatestInstancesFromContainerServer(
      Collection<software.wings.beans.infrastructure.instance.ContainerDeploymentInfo>
          containerDeploymentInfoCollection,
      InstanceType instanceType) {
    ContainerFilter containerFilter =
        ContainerFilter.builder().containerDeploymentInfoCollection(containerDeploymentInfoCollection).build();

    ContainerSyncRequest instanceSyncRequest = ContainerSyncRequest.builder().filter(containerFilter).build();
    if (instanceType == InstanceType.KUBERNETES_CONTAINER_INSTANCE
        || instanceType == InstanceType.ECS_CONTAINER_INSTANCE) {
      return containerSync.getInstances(instanceSyncRequest);
    } else {
      String msg = "Unsupported container instance type:" + instanceType;
      throw new GeneralException(msg);
    }
  }

  public Set<ContainerMetadata> getContainerServiceNames(
      ExecutionContext context, String serviceId, String infraMappingId, Optional<String> infrastructureDefinitionId) {
    Set<ContainerMetadata> containerMetadataSet = Sets.newHashSet();
    List<StateExecutionInstance> executionDataList =
        workflowExecutionService.getStateExecutionData(context.getAppId(), context.getWorkflowExecutionId(), serviceId,
            infraMappingId, infrastructureDefinitionId, StateType.PHASE_STEP, WorkflowServiceHelper.DEPLOY_CONTAINERS);
    executionDataList.forEach(stateExecutionData -> {
      List<StateExecutionData> deployPhaseStepList =
          stateExecutionData.getStateExecutionMap()
              .entrySet()
              .stream()
              .filter(entry -> entry.getKey().equals(WorkflowServiceHelper.DEPLOY_CONTAINERS))
              .map(Entry::getValue)
              .collect(toList());
      deployPhaseStepList.forEach(phaseStep -> {
        PhaseStepExecutionSummary phaseStepExecutionSummary =
            ((PhaseStepExecutionData) phaseStep).getPhaseStepExecutionSummary();
        Preconditions.checkNotNull(
            phaseStepExecutionSummary, "PhaseStepExecutionSummary is null for stateExecutionInstanceId: " + phaseStep);
        List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
        Preconditions.checkNotNull(
            stepExecutionSummaryList, "stepExecutionSummaryList null for " + phaseStepExecutionSummary);

        for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
          if (stepExecutionSummary instanceof CommandStepExecutionSummary) {
            CommandStepExecutionSummary commandStepExecutionSummary =
                (CommandStepExecutionSummary) stepExecutionSummary;
            if (commandStepExecutionSummary.getOldInstanceData() != null) {
              containerMetadataSet.addAll(commandStepExecutionSummary.getOldInstanceData()
                                              .stream()
                                              .map(containerServiceData
                                                  -> ContainerMetadata.builder()
                                                         .containerServiceName(containerServiceData.getName())
                                                         .namespace(commandStepExecutionSummary.getNamespace())
                                                         .build())
                                              .collect(toList()));
            }

            if (commandStepExecutionSummary.getNewInstanceData() != null) {
              containerMetadataSet.addAll(commandStepExecutionSummary.getNewInstanceData()
                                              .stream()
                                              .map(containerServiceData
                                                  -> ContainerMetadata.builder()
                                                         .containerServiceName(containerServiceData.getName())
                                                         .namespace(commandStepExecutionSummary.getNamespace())
                                                         .build())
                                              .collect(toList()));
            }

            Preconditions.checkState(!containerMetadataSet.isEmpty(),
                "Both old and new container services are empty. Cannot proceed for phase step "
                    + commandStepExecutionSummary.getServiceId());
          }
        }
      });
    });

    return containerMetadataSet;
  }

  public List<ContainerInfo> getContainerInfoForService(Set<ContainerMetadata> containerMetadataSet,
      ExecutionContext context, String infrastructureMappingId, String serviceId) {
    Preconditions.checkState(!containerMetadataSet.isEmpty(), "empty for " + context.getWorkflowExecutionId());
    InfrastructureMapping infrastructureMapping = infraMappingService.get(context.getAppId(), infrastructureMappingId);
    InstanceType instanceType = instanceUtil.getInstanceType(infrastructureMapping.getInfraMappingType());
    Preconditions.checkNotNull(instanceType, "Null for " + infrastructureMappingId);

    String containerSvcNameNoRevision =
        getcontainerSvcNameNoRevision(instanceType, containerMetadataSet.iterator().next().getContainerServiceName());
    Map<String, ContainerDeploymentInfo> containerSvcNameDeploymentInfoMap =
        instanceService.getContainerDeploymentInfoList(containerSvcNameNoRevision, context.getAppId())
            .stream()
            .collect(toMap(ContainerDeploymentInfo::getContainerSvcName, identity()));

    for (ContainerMetadata containerMetadata : containerMetadataSet) {
      ContainerDeploymentInfo containerDeploymentInfo =
          containerSvcNameDeploymentInfoMap.get(containerMetadata.getContainerServiceName());
      if (containerDeploymentInfo == null) {
        containerDeploymentInfo = ContainerDeploymentInfo.builder()
                                      .appId(context.getAppId())
                                      .containerSvcName(containerMetadata.getContainerServiceName())
                                      .infraMappingId(infrastructureMappingId)
                                      .workflowId(context.getWorkflowId())
                                      .workflowExecutionId(context.getWorkflowExecutionId())
                                      .serviceId(serviceId)
                                      .namespace(containerMetadata.getNamespace())
                                      .build();

        containerSvcNameDeploymentInfoMap.put(containerMetadata.getContainerServiceName(), containerDeploymentInfo);
      }
    }
    ContainerSyncResponse instanceSyncResponse =
        getLatestInstancesFromContainerServer(containerSvcNameDeploymentInfoMap.values(), instanceType);
    Preconditions.checkNotNull(instanceSyncResponse, "InstanceSyncResponse");

    return instanceSyncResponse.getContainerInfoList();
  }

  private String getcontainerSvcNameNoRevision(InstanceType instanceType, String containerSvcName) {
    String delimiter;
    if (instanceType == InstanceType.ECS_CONTAINER_INSTANCE) {
      delimiter = "__";
    } else if (instanceType == InstanceType.KUBERNETES_CONTAINER_INSTANCE) {
      delimiter = "-";
    } else {
      String msg = "Unsupported container instance type:" + instanceType;
      throw new GeneralException(msg);
    }

    if (containerSvcName == null) {
      return null;
    }

    int index = containerSvcName.lastIndexOf(delimiter);
    if (index == -1) {
      return containerSvcName;
    }
    return containerSvcName.substring(0, index);
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    if (deploymentInfo instanceof ContainerDeploymentInfoWithNames) {
      ContainerDeploymentInfoWithNames deploymentInfoWithNames = (ContainerDeploymentInfoWithNames) deploymentInfo;
      String keyName = isNotEmpty(deploymentInfoWithNames.getUniqueNameIdentifier())
          ? deploymentInfoWithNames.getUniqueNameIdentifier()
          : deploymentInfoWithNames.getContainerSvcName();

      return ContainerDeploymentKey.builder().containerServiceName(keyName).build();
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      ContainerDeploymentInfoWithLabels info = (ContainerDeploymentInfoWithLabels) deploymentInfo;
      ContainerDeploymentKey key = ContainerDeploymentKey.builder().labels(info.getLabels()).build();

      // For Helm
      if (EmptyPredicate.isNotEmpty(info.getNewVersion())) {
        key.setNewVersion(info.getNewVersion());
      }
      return key;
    } else if (deploymentInfo instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo k8sDeploymentInfo = (K8sDeploymentInfo) deploymentInfo;
      return K8sDeploymentKey.builder()
          .releaseName(k8sDeploymentInfo.getReleaseName())
          .releaseNumber(k8sDeploymentInfo.getReleaseNumber())
          .build();
    } else {
      throw new GeneralException("Unsupported DeploymentInfo type for Container: " + deploymentInfo);
    }
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof ContainerDeploymentKey) {
      deploymentSummary.setContainerDeploymentKey((ContainerDeploymentKey) deploymentKey);
    } else if (deploymentKey instanceof K8sDeploymentKey) {
      deploymentSummary.setK8sDeploymentKey((K8sDeploymentKey) deploymentKey);
    } else {
      throw new GeneralException("Invalid deploymentKey passed for ContainerDeploymentKey" + deploymentKey);
    }
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.of(FeatureName.MOVE_CONTAINER_INSTANCE_SYNC_TO_PERPETUAL_TASK);
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return taskCreator;
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
      String msg = "Incompatible infrastructure mapping type found:" + infrastructureMapping.getInfraMappingType();
      throw new GeneralException(msg);
    }

    ContainerInfrastructureMapping containerInfrastructureMapping =
        (ContainerInfrastructureMapping) infrastructureMapping;
    syncInstancesInternal(
        containerInfrastructureMapping, ArrayListMultimap.create(), null, false, response, PERPETUAL_TASK);
  }

  public void cleanupInvalidV1PerpetualTask(String accountId) {
    List<PerpetualTaskRecord> perpetualTaskRecordList = perpetualTaskService.listAllTasksForAccount(accountId);
    for (PerpetualTaskRecord perpetualTaskRecord : perpetualTaskRecordList) {
      if (Objects.equals(perpetualTaskRecord.getPerpetualTaskType(), CONTAINER_INSTANCE_SYNC)
          && Objects.equals(perpetualTaskRecord.getState(), TASK_INVALID)) {
        perpetualTaskService.deleteTask(accountId, perpetualTaskRecord.getUuid());
        log.info("Deleted Instance Sync V1 Perpetual task: [{}] .", perpetualTaskRecord.getUuid());
      }
    }
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    if (!(response instanceof ContainerSyncResponse) && !(response instanceof K8sTaskExecutionResponse)) {
      throw new GeneralException("Incompatible response data received from perpetual task execution");
    }

    return response instanceof K8sTaskExecutionResponse
        ? getK8sPerpetualTaskStatus((K8sTaskExecutionResponse) response)
        : getContainerSyncPerpetualTaskStatus((ContainerSyncResponse) response);
  }

  private Status getK8sPerpetualTaskStatus(K8sTaskExecutionResponse response) {
    boolean success = response.getCommandExecutionStatus() == SUCCESS;
    String errorMessage = success ? null : response.getErrorMessage();

    return Status.builder().retryable(true).errorMessage(errorMessage).success(success).build();
  }

  private Status getContainerSyncPerpetualTaskStatus(ContainerSyncResponse response) {
    boolean success = response.getCommandExecutionStatus() == SUCCESS;
    boolean deleteTask;
    if (response.isEcs()) {
      // Ecs
      deleteTask = success && !response.isEcsServiceExists();
    } else {
      // K8s v1
      deleteTask = false;
    }

    String errorMessage = success ? null : response.getErrorMessage();

    return Status.builder().retryable(!deleteTask).errorMessage(errorMessage).success(success).build();
  }
}
