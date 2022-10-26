/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgK8sInstancesDetailsFetcher implements InstanceDetailsFetcher {
  private final ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  private final KryoSerializer kryoSerializer;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

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
      K8sClusterConfig config =
          (K8sClusterConfig) kryoSerializer.asObject(instanceSyncTaskDetails.getK8SClusterConfig().toByteArray());
      List<InstanceInfo> runningK8sPods = fetchRunningK8sPods(config, instanceSyncTaskDetails);
      return InstanceSyncData.newBuilder()
          .setTaskDetailsId(releaseDetails.getTaskDetailsId())
          .addAllInstanceData(runningK8sPods.parallelStream()
                                  .map(pod -> ByteString.copyFrom(kryoSerializer.asBytes(pod)))
                                  .collect(toList()))
          .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
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

  public List<InstanceInfo> fetchRunningK8sPods(
      K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);
    notNullCheck("KubernetesConfig", kubernetesConfig);

    if (false) {
      return null;
    } else {
      try {
        long timeoutMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_STEADY_STATE_TIMEOUT);
        List<K8sPod> k8sPodList = k8sTaskHelperBase.getPodDetails(
            kubernetesConfig, config.getNamespace(), k8sInstanceSyncTaskDetails.getReleaseName(), timeoutMillis);
        return k8sPodList.stream()
            .map(pod -> {
              List<K8sContainerInfo> k8sContainerInfos = pod.getContainerList()
                                                             .stream()
                                                             .map(container
                                                                 -> K8sContainerInfo.builder()
                                                                        .containerId(container.getContainerId())
                                                                        .image(container.getImage())
                                                                        .name(container.getName())
                                                                        .build())
                                                             .collect(toList());
              return K8sPodInfo.builder()
                  .containers(k8sContainerInfos)
                  .clusterName(config.getClusterName())
                  .podName(pod.getName())
                  .ip(pod.getPodIP())
                  .namespace(k8sInstanceSyncTaskDetails.getNamespace())
                  .releaseName(k8sInstanceSyncTaskDetails.getReleaseName())
                  .build();
            })
            .collect(toList());
      } catch (Exception exception) {
        throw new InvalidRequestException(String.format("Failed to fetch containers info for namespace: [%s] ",
                                              k8sInstanceSyncTaskDetails.getNamespace()),
            exception);
      }
    }
  }
}
