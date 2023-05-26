/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.KubernetesResourceId.createKubernetesResourceIdsFromKindName;

import static software.wings.beans.LogColor.GrayDark;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sDeleteBaseHandler {
  @Inject K8sTaskHelperBase k8sTaskHelperBase;

  public List<KubernetesResourceId> getResourceIdsToDelete(K8sDeleteRequest k8sDeleteRequest,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    DeleteResourcesType deleteResourcesType = k8sDeleteRequest.getDeleteResourcesType();
    switch (deleteResourcesType) {
      case ReleaseName:
        return getReleaseNameResourceIdsToDelete(k8sDeleteRequest, kubernetesConfig, executionLogCallback);
      case ResourceName:
        return getResourceNameResourceIdsToDelete(k8sDeleteRequest.getResources());
      default:
        throw new UnsupportedOperationException(
            String.format("Delete resource type: [%s]", deleteResourcesType.name()));
    }
  }

  private List<KubernetesResourceId> getReleaseNameResourceIdsToDelete(K8sDeleteRequest k8sDeleteRequest,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    boolean deleteNamespaceForRelease = k8sDeleteRequest.isDeleteNamespacesForRelease();
    String releaseName = k8sDeleteRequest.getReleaseName();
    String namespaceMessage = createNamespaceInfoMessage(deleteNamespaceForRelease);

    executionLogCallback.saveExecutionLog(
        String.format("All resources in release [%s] are selected for deletion.", releaseName));
    executionLogCallback.saveExecutionLog(color(namespaceMessage, GrayDark, Bold));

    return k8sTaskHelperBase.getResourceIdsForDeletion(k8sDeleteRequest.isUseDeclarativeRollback(), releaseName,
        kubernetesConfig, executionLogCallback, deleteNamespaceForRelease);
  }

  private String createNamespaceInfoMessage(boolean deleteNamespaceForRelease) {
    StringBuilder namespaceMessageBuilder = new StringBuilder(256);

    namespaceMessageBuilder.append("Delete namespace is set to : ")
        .append(String.format("<%s>. ", deleteNamespaceForRelease));
    if (!deleteNamespaceForRelease) {
      namespaceMessageBuilder.append("Skipping deleting namespace resources.");
    }
    return namespaceMessageBuilder.toString();
  }

  public List<KubernetesResourceId> getResourceNameResourceIdsToDelete(String resources) {
    if (StringUtils.isEmpty(resources)) {
      return Collections.emptyList();
    }

    if ("*".equals(resources.trim())) {
      throw new InvalidArgumentsException("Invalid resource name. Use release name instead.");
    }

    return createKubernetesResourceIdsFromKindName(resources);
  }

  public K8sDeployResponse getSuccessResponse() {
    return K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }
}
