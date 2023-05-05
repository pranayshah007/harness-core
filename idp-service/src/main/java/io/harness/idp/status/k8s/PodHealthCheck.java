/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.k8s;

import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfo.CurrentStatusEnum;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Slf4j
public class PodHealthCheck implements HealthCheck {
  @Inject private NamespaceService namespaceService;
  @Inject private K8sClient k8sClient;
  public static final String MESSAGE_SEPARATOR = ". ";

  @Override
  public Optional<StatusInfo> getCurrentStatus(String accountId) {
    StatusInfo statusInfo = new StatusInfo();
    String namespace = getNamespaceForAccountId(accountId);
    V1PodList podList = k8sClient.getBackstagePodList(namespace);
    if (CollectionUtils.isEmpty(podList.getItems())) {
      statusInfo.setCurrentStatus(CurrentStatusEnum.NOT_FOUND);
      statusInfo.setReason("No pod exists for namespace: " + namespace);
    } else {
      int failedPods = 0;
      for (V1Pod pod : podList.getItems()) {
        if (isPodInRunningPhase(pod) && isContainerInRunningState(pod) && isPodInReadyCondition(pod) != null) {
          statusInfo.setCurrentStatus(CurrentStatusEnum.RUNNING);
          break;
        } else if (isPodInFailedPhase(pod) || isContainerInTerminatedState(pod)) {
          failedPods++;
        }
      }

      if (failedPods == podList.getItems().size()) {
        statusInfo.setCurrentStatus(CurrentStatusEnum.FAILED);
        statusInfo.setReason(getPodMessage(podList.getItems().get(0)));
      } else if (statusInfo.getCurrentStatus() == null) {
        statusInfo.setCurrentStatus(CurrentStatusEnum.PENDING);
        statusInfo.setReason(getPodMessage(podList.getItems().get(0)));
      }
    }
    return Optional.of(statusInfo);
  }

  private String getNamespaceForAccountId(String accountId) {
    NamespaceInfo namespace = namespaceService.getNamespaceForAccountIdentifier(accountId);
    return namespace.getNamespace();
  }

  private boolean isPodInRunningPhase(V1Pod pod) {
    return CurrentStatusEnum.RUNNING.toString().equalsIgnoreCase(pod.getStatus().getPhase());
  }
  private boolean isPodInFailedPhase(V1Pod pod) {
    return CurrentStatusEnum.FAILED.toString().equalsIgnoreCase(pod.getStatus().getPhase());
  }

  private boolean isContainerInRunningState(V1Pod pod) {
    for (V1ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
      if (containerStatus.getState().getRunning() != null) {
        return true;
      }
    }
    return false;
  }

  private boolean isContainerInTerminatedState(V1Pod pod) {
    for (V1ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
      if (containerStatus.getState().getTerminated() != null
          || containerStatus.getLastState().getTerminated() != null) {
        return true;
      }
    }
    return false;
  }

  private V1PodCondition isPodInReadyCondition(V1Pod pod) {
    return pod.getStatus()
        .getConditions()
        .stream()
        .filter(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()))
        .findFirst()
        .orElse(null);
  }

  private String getPodMessage(V1Pod pod) {
    String msg = null;
    for (V1ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
      if (containerStatus.getState().getWaiting() != null) {
        msg = containerStatus.getState().getWaiting().getMessage();
      } else {
        msg = containerStatus.getState().getTerminated().getMessage();
      }
    }

    if (msg == null) {
      msg = pod.getStatus()
                .getConditions()
                .stream()
                .map(V1PodCondition::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(MESSAGE_SEPARATOR));
    }
    return msg;
  }
}
