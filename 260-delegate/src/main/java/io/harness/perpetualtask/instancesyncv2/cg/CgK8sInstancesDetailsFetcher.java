/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.helm.HelmConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgK8sInstancesDetailsFetcher implements InstanceDetailsFetcher {
  private final ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  private final KubernetesContainerService kubernetesContainerService;
  private final KryoSerializer kryoSerializer;
  private final K8sTaskHelperBase k8sTaskHelperBase;
  private final KryoSerializer referenceFalseKryoSerializer;

  @Override
  public InstanceSyncData fetchRunningInstanceDetails(
      String perpetualTaskId, CgDeploymentReleaseDetails releaseDetails) {
    DirectK8sInstanceSyncTaskDetails instanceSyncTaskDetails;
    try {
      instanceSyncTaskDetails =
          AnyUtils.unpack(releaseDetails.getReleaseDetails(), DirectK8sInstanceSyncTaskDetails.class);
    } catch (Exception e) {
      log.error("Unable to unpack Instance Sync task details for Id: [{}]", releaseDetails.getTaskDetailsId(), e);
      return InstanceSyncData.newBuilder().setTaskDetailsId(releaseDetails.getTaskDetailsId()).build();
    }
    try {
      K8sClusterConfig config = (K8sClusterConfig) referenceFalseKryoSerializer.asObject(
          instanceSyncTaskDetails.getK8SClusterConfig().toByteArray());
      KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);

      DelegateTaskNotifyResponseData taskResponseData = instanceSyncTaskDetails.getIsHelm()
          ? getContainerSyncResponse(instanceSyncTaskDetails, config)
          : getK8sTaskResponse(instanceSyncTaskDetails, kubernetesConfig);

      return InstanceSyncData.newBuilder()
          .setTaskDetailsId(releaseDetails.getTaskDetailsId())
          .setTaskResponse(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(taskResponseData)))
          .setReleaseDetails(Any.pack(DirectK8sReleaseDetails.newBuilder()
                                          .setReleaseName(instanceSyncTaskDetails.getReleaseName())
                                          .setNamespace(instanceSyncTaskDetails.getNamespace())
                                          .setIsHelm(instanceSyncTaskDetails.getIsHelm())
                                          .setContainerServiceName(instanceSyncTaskDetails.getContainerServiceName())
                                          .build()))
          .setInstanceCount(getInstanceCount(instanceSyncTaskDetails.getIsHelm(), taskResponseData))
          .setExecutionStatus(getExecutionStatus(instanceSyncTaskDetails.getIsHelm(), taskResponseData))
          .build();
    } catch (Exception e) {
      log.error(
          "Exception while fetching running K8s pods for release details: [{}], infra mapping Id: [{}] of type: [{}]",
          releaseDetails.getTaskDetailsId(), releaseDetails.getInfraMappingId(), releaseDetails.getInfraMappingType(),
          e);
      return InstanceSyncData.newBuilder()
          .setTaskDetailsId(releaseDetails.getTaskDetailsId())
          .setErrorMessage("Exception while fetching running K8s pods. Exception message: " + e.getMessage())
          .setExecutionStatus(CommandExecutionStatus.FAILURE.name())
          .build();
    }
  }
  private String getExecutionStatus(boolean isHelm, DelegateTaskNotifyResponseData taskResponseData) {
    if (isHelm) {
      ContainerSyncResponse containerSyncResponse = (ContainerSyncResponse) taskResponseData;
      return containerSyncResponse.getCommandExecutionStatus() != null
          ? containerSyncResponse.getCommandExecutionStatus().name()
          : FAILURE.name();

    } else {
      K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) taskResponseData;
      return k8sTaskExecutionResponse.getCommandExecutionStatus() != null
          ? k8sTaskExecutionResponse.getCommandExecutionStatus().name()
          : FAILURE.name();
    }
  }

  private int getInstanceCount(boolean isHelm, DelegateTaskNotifyResponseData taskResponseData) {
    if (isHelm) {
      ContainerSyncResponse containerSyncResponse = (ContainerSyncResponse) taskResponseData;
      if (isNull(containerSyncResponse)) {
        return 0;
      }
      return containerSyncResponse.getContainerInfoList() != null ? containerSyncResponse.getContainerInfoList().size()
                                                                  : 0;

    } else {
      K8sInstanceSyncResponse k8sInstanceSyncResponse =
          (K8sInstanceSyncResponse) ((K8sTaskExecutionResponse) taskResponseData).getK8sTaskResponse();
      if (isNull(k8sInstanceSyncResponse)) {
        return 0;
      }
      return k8sInstanceSyncResponse.getK8sPodInfoList() != null ? k8sInstanceSyncResponse.getK8sPodInfoList().size()
                                                                 : 0;
    }
  }

  private K8sTaskExecutionResponse getK8sTaskResponse(
      DirectK8sInstanceSyncTaskDetails instanceSyncTaskDetails, KubernetesConfig kubernetesConfig) {
    try {
      long timeoutMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_STEADY_STATE_TIMEOUT);
      String namespace = instanceSyncTaskDetails.getNamespace();
      String releaseName = instanceSyncTaskDetails.getReleaseName();
      List<K8sPod> k8sPodList =
          k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutMillis);

      return K8sTaskExecutionResponse.builder()
          .k8sTaskResponse(K8sInstanceSyncResponse.builder()
                               .k8sPodInfoList(k8sPodList)
                               .releaseName(releaseName)
                               .namespace(namespace)
                               .build())
          .commandExecutionStatus((k8sPodList != null) ? SUCCESS : FAILURE)
          .build();

    } catch (Exception exception) {
      log.error(String.format("Failed to fetch k8s pod list for namespace: [%s] and releaseName:[%s] ",
                    instanceSyncTaskDetails.getNamespace(), instanceSyncTaskDetails.getReleaseName()),
          exception);
      return K8sTaskExecutionResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(exception.getMessage())
          .build();
    }
  }

  private ContainerSyncResponse getContainerSyncResponse(
      DirectK8sInstanceSyncTaskDetails instanceSyncTaskDetails, K8sClusterConfig config) {
    try {
      List<ContainerInfo> containerInfos = fetchRunningK8sPods(config, instanceSyncTaskDetails);
      return ContainerSyncResponse.builder()
          .containerInfoList(containerInfos)
          .commandExecutionStatus((containerInfos != null) ? SUCCESS : FAILURE)
          .controllerName(instanceSyncTaskDetails.getContainerServiceName())
          .isEcs(false)
          .releaseName(instanceSyncTaskDetails.getReleaseName())
          .namespace(instanceSyncTaskDetails.getNamespace())
          .build();
    } catch (Exception exception) {
      log.error(String.format("Failed to fetch containers info for namespace: [%s] and svc:[%s] ",
                    instanceSyncTaskDetails.getNamespace(), instanceSyncTaskDetails.getContainerServiceName()),
          exception);

      return ContainerSyncResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(exception.getMessage())
          .build();
    }
  }

  private List<ContainerInfo> fetchRunningK8sPods(
      K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);
    String containerServiceName = k8sInstanceSyncTaskDetails.getContainerServiceName();
    String accountId = kubernetesConfig.getAccountId();
    try {
      log.info("Kubernetes cluster config for account {}, controller: {}", accountId, containerServiceName);
      notNullCheck("KubernetesConfig", kubernetesConfig);

      // for k8s cluster version < v116 containerServiceName is not Empty
      if (isNotEmpty(containerServiceName)) {
        return fetchRunningK8sPodsForContainerServiceName(config, k8sInstanceSyncTaskDetails);
      } else {
        return fetchRunningK8sPodsDefault(config, k8sInstanceSyncTaskDetails);
      }
    } catch (Exception exception) {
      throw new InvalidRequestException(String.format("Failed to fetch containers info for namespace: [%s] ",
                                            k8sInstanceSyncTaskDetails.getNamespace()),
          exception);
    }
  }

  private List<ContainerInfo> fetchRunningK8sPodsForContainerServiceName(
      K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);
    String containerServiceName = k8sInstanceSyncTaskDetails.getContainerServiceName();
    String accountId = kubernetesConfig.getAccountId();
    List<ContainerInfo> result = new ArrayList<>();
    HasMetadata controller = kubernetesContainerService.getController(kubernetesConfig, containerServiceName);
    if (controller != null) {
      log.info("Got controller {} for account {}", controller.getMetadata().getName(), accountId);
      Map<String, String> labels = kubernetesContainerService.getPodTemplateSpec(controller).getMetadata().getLabels();
      Map<String, String> serviceLabels = new HashMap<>(labels);
      serviceLabels.remove(HARNESS_KUBERNETES_REVISION_LABEL_KEY);

      List<io.fabric8.kubernetes.api.model.Service> services =
          kubernetesContainerService.getServices(kubernetesConfig, serviceLabels);
      String serviceName = services.isEmpty() ? "None" : services.get(0).getMetadata().getName();
      log.info("Got Service {} for controller {} for account {}", serviceName, containerServiceName, accountId);
      List<V1Pod> pods = kubernetesContainerService.getRunningPodsWithLabels(
          kubernetesConfig, k8sInstanceSyncTaskDetails.getNamespace(), labels);
      log.info("Got {} pods for controller {} for account {}", pods != null ? pods.size() : 0, containerServiceName,
          accountId);
      if (isEmpty(pods)) {
        return result;
      }

      for (V1Pod pod : pods) {
        String phase = pod.getStatus().getPhase();
        log.info("Phase: {} for pod {} for controller {} for account {}", pod.getStatus().getPhase(),
            pod.getMetadata().getName(), containerServiceName, accountId);
        if ("Running".equals(phase)) {
          result.add(KubernetesContainerInfo.builder()
                         .clusterName(config.getClusterName())
                         .podName(pod.getMetadata().getName())
                         .ip(pod.getStatus().getPodIP())
                         .controllerName(containerServiceName)
                         .serviceName(serviceName)
                         .namespace(k8sInstanceSyncTaskDetails.getNamespace())
                         .releaseName(k8sInstanceSyncTaskDetails.getReleaseName())
                         .build());
        }
      }
    } else {
      log.info("Could not get controller {} for account {}", containerServiceName, accountId);
    }
    return result;
  }

  private List<ContainerInfo> fetchRunningK8sPodsDefault(
      K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);
    if (isEmpty(k8sInstanceSyncTaskDetails.getReleaseName())) {
      return Collections.emptyList();
    }
    final List<V1Pod> pods =
        kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, k8sInstanceSyncTaskDetails.getNamespace(),
            ImmutableMap.of(HelmConstants.HELM_RELEASE_LABEL, k8sInstanceSyncTaskDetails.getReleaseName()));
    return pods.stream()
        .map(pod
            -> KubernetesContainerInfo.builder()
                   .clusterName(config.getClusterName())
                   .podName(pod.getMetadata().getName())
                   .ip(pod.getStatus().getPodIP())
                   .namespace(k8sInstanceSyncTaskDetails.getNamespace())
                   .releaseName(k8sInstanceSyncTaskDetails.getReleaseName())
                   .build())
        .collect(toList());
  }
}
