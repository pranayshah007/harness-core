/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm.steadystate;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.helm.steadystate.SteadyStateConstants.ON_DELETE_STRATEGY;
import static io.harness.delegate.task.helm.steadystate.SteadyStateConstants.STATEFULSET_KIND;
import static io.harness.delegate.task.helm.steadystate.SteadyStateConstants.STATEFULSET_UPDATE_STRATEGY_PATH;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.HelmClientException;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.helm.HelmClientUtils;
import io.harness.helm.HelmCommandData;
import io.harness.helm.HelmConstants;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class HelmSteadyStateService {
  @Inject private HelmClient helmClient;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  public List<KubernetesResource> readManifestFromHelmRelease(HelmCommandData helmCommandData) throws Exception {
    final LogCallback logCallback = helmCommandData.getLogCallback();
    logCallback.saveExecutionLog(color(format("Retrieve helm manifest from release [%s] and namespace [%s]",
                                           helmCommandData.getReleaseName(), helmCommandData.getNamespace()),
        LogColor.White, LogWeight.Bold));

    HelmCliResponse response = helmClient.getManifest(helmCommandData, helmCommandData.getNamespace());

    if (CommandExecutionStatus.SUCCESS != response.getCommandExecutionStatus()) {
      throw new HelmClientException(
          format("Failed to get manifest: %s", response.getErrorStreamOutput()), HelmCliCommandType.GET_MANIFEST);
    }

    List<KubernetesResource> resources = HelmClientUtils.readManifestFromHelmOutput(response.getOutput());
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, helmCommandData.getNamespace());
    return resources;
  }

  public List<KubernetesResourceId> findEligibleWorkloadIds(List<KubernetesResource> resources) {
    List<KubernetesResource> eligibleWorkloads = ManifestHelper.getEligibleWorkloads(resources);

    return eligibleWorkloads.stream()
        .filter(this::filterHooks)
        .filter(this::filterStrategy)
        .map(KubernetesResource::getResourceId)
        .collect(Collectors.toList());
  }

  private boolean filterHooks(KubernetesResource resource) {
    return resource.getMetadataAnnotationValue(HelmConstants.HELM_HOOK_ANNOTATION) == null;
  }

  private boolean filterStrategy(KubernetesResource resource) {
    switch (resource.getResourceId().getKind()) {
      case STATEFULSET_KIND:
        String updateStrategyType = (String) resource.getField(STATEFULSET_UPDATE_STRATEGY_PATH);
        if (isNotEmpty(updateStrategyType)) {
          return !updateStrategyType.equalsIgnoreCase(ON_DELETE_STRATEGY);
        }

        return true;
      default:
        return true;
    }
  }
}
