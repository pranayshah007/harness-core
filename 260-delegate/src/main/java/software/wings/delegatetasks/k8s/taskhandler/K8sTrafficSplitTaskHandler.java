/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.TrafficSplit;
import static io.harness.k8s.model.K8sExpressions.virtualServiceNameExpression;
import static io.harness.k8s.utils.ObjectYamlUtils.toYaml;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.istio.IstioTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTrafficSplitTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTrafficSplitResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTrafficSplitTaskHandler extends K8sTaskHandler {
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private IstioTaskHelper istioTaskHelper;

  private IK8sRelease release;
  private KubernetesConfig kubernetesConfig;
  private VirtualService virtualService;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) {
    if (!(k8sTaskParameters instanceof K8sTrafficSplitTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sTrafficSplitTaskParameters"));
    }

    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters = (K8sTrafficSplitTaskParameters) k8sTaskParameters;
    K8sTrafficSplitResponse k8sTrafficSplitResponse = K8sTrafficSplitResponse.builder().build();

    boolean success = init(k8sTrafficSplitTaskParameters,
        new ExecutionLogCallback(delegateLogService, k8sTrafficSplitTaskParameters.getAccountId(),
            k8sTrafficSplitTaskParameters.getAppId(), k8sTrafficSplitTaskParameters.getActivityId(), Init));

    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(k8sTrafficSplitResponse, CommandExecutionStatus.FAILURE);
    }

    success = apply(k8sTrafficSplitTaskParameters,
        new ExecutionLogCallback(delegateLogService, k8sTrafficSplitTaskParameters.getAccountId(),
            k8sTrafficSplitTaskParameters.getAppId(), k8sTrafficSplitTaskParameters.getActivityId(), TrafficSplit));

    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(k8sTrafficSplitResponse, CommandExecutionStatus.FAILURE);
    }

    return k8sTaskHelper.getK8sTaskExecutionResponse(k8sTrafficSplitResponse, CommandExecutionStatus.SUCCESS);
  }

  @VisibleForTesting
  boolean init(K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..");

    kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sTrafficSplitTaskParameters.getK8sClusterConfig(), false);

    try {
      boolean success;

      if (virtualServiceNameExpression.equals(k8sTrafficSplitTaskParameters.getVirtualServiceName())) {
        success = initBasedOnDefaultVirtualServiceName(k8sTrafficSplitTaskParameters, executionLogCallback);
      } else {
        success = initBasedOnCustomVirtualServiceName(k8sTrafficSplitTaskParameters, executionLogCallback);
      }

      if (!success) {
        return false;
      }

      printDestinationWeights(k8sTrafficSplitTaskParameters, executionLogCallback);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);

      return true;
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  private boolean initBasedOnCustomVirtualServiceName(
      K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters, ExecutionLogCallback executionLogCallback) {
    return findVirtualServiceByName(k8sTrafficSplitTaskParameters.getVirtualServiceName(), executionLogCallback);
  }

  private boolean initBasedOnDefaultVirtualServiceName(K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters,
      ExecutionLogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Evaluating expression " + virtualServiceNameExpression);
    executionLogCallback.saveExecutionLog(
        color("\nRelease name: " + k8sTrafficSplitTaskParameters.getReleaseName(), White, Bold));

    boolean useDeclarativeRollback = k8sTrafficSplitTaskParameters.isUseDeclarativeRollback();
    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    IK8sReleaseHistory releaseHistory =
        releaseHandler.getReleaseHistory(kubernetesConfig, k8sTrafficSplitTaskParameters.getReleaseName());

    if (isEmpty(releaseHistory)) {
      executionLogCallback.saveExecutionLog("\nNo release history found for release ");
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return true;
    }

    release = releaseHistory.getLatestRelease();

    List<KubernetesResourceId> resources = new ArrayList<>();
    if (release != null) {
      resources = release.getResourceIds();
    }

    if (isEmpty(resources)) {
      executionLogCallback.saveExecutionLog("\nNo resources found in release history");
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return true;
    }

    List<KubernetesResourceId> virtualServiceResourceIds = getManagedVirtualServiceResources(resources);

    if (virtualServiceResourceIds.size() != 1) {
      executionLogCallback.saveExecutionLog(
          "Error evaluating expression " + virtualServiceNameExpression, ERROR, FAILURE);

      if (virtualServiceResourceIds.isEmpty()) {
        executionLogCallback.saveExecutionLog(
            "\nNo managed VirtualService found. Atleast one VirtualService should be present and marked with annotation "
                + HarnessAnnotations.managed + ": true",
            ERROR, FAILURE);
      } else if (virtualServiceResourceIds.size() > 1) {
        executionLogCallback.saveExecutionLog(
            "\nMore than one VirtualService found.  Only one VirtualService can be marked with annotation "
                + HarnessAnnotations.managed + ": true",
            ERROR, FAILURE);
      }

      return false;
    }

    return findVirtualServiceByName(virtualServiceResourceIds.get(0).getName(), executionLogCallback);
  }

  private boolean apply(
      K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Applying..");

    kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sTrafficSplitTaskParameters.getK8sClusterConfig(), false);

    try {
      updateVirtualServiceWithDestinationWeights(k8sTrafficSplitTaskParameters, executionLogCallback);
      if (virtualService != null) {
        executionLogCallback.saveExecutionLog("\n" + toYaml(virtualService));
      }
      virtualService =
          kubernetesContainerService.createOrReplaceFabric8IstioVirtualService(kubernetesConfig, virtualService);

      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return true;
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  private boolean findVirtualServiceByName(String virtualServiceName, ExecutionLogCallback executionLogCallback) {
    virtualService = kubernetesContainerService.getFabric8IstioVirtualService(kubernetesConfig, virtualServiceName);
    if (virtualService == null) {
      executionLogCallback.saveExecutionLog(
          "\nNo VirtualService found with name " + virtualServiceName, ERROR, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog("\nFound VirtualService with name " + color(virtualServiceName, White, Bold));

    return true;
  }

  private void updateVirtualServiceWithDestinationWeights(K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters,
      ExecutionLogCallback executionLogCallback) throws IOException {
    List<IstioDestinationWeight> istioDestinationWeights = k8sTrafficSplitTaskParameters.getIstioDestinationWeights();

    istioTaskHelper.updateVirtualServiceWithDestinationWeights(
        istioDestinationWeights, virtualService, executionLogCallback);
  }

  private void printDestinationWeights(
      K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParameters, ExecutionLogCallback executionLogCallback) {
    List<IstioDestinationWeight> istioDestinationWeights = k8sTrafficSplitTaskParameters.getIstioDestinationWeights();

    if (isNotEmpty(istioDestinationWeights)) {
      executionLogCallback.saveExecutionLog("\nFound following destinations");
      for (IstioDestinationWeight ruleWithWeight : istioDestinationWeights) {
        executionLogCallback.saveExecutionLog(ruleWithWeight.getDestination());
        executionLogCallback.saveExecutionLog("weight: " + ruleWithWeight.getWeight() + "\n");
      }
    }
  }

  private List<KubernetesResourceId> getManagedVirtualServiceResources(List<KubernetesResourceId> resourceIds) {
    List<KubernetesResourceId> managedVirtualServices = new ArrayList<>();

    for (KubernetesResourceId resourceId : resourceIds) {
      if (Kind.VirtualService.name().equals(resourceId.getKind())) {
        VirtualService istioVirtualService =
            kubernetesContainerService.getFabric8IstioVirtualService(kubernetesConfig, resourceId.getName());

        if (istioVirtualService != null && istioVirtualService.getMetadata() != null
            && isNotEmpty(istioVirtualService.getMetadata().getAnnotations())) {
          Map<String, String> annotations = istioVirtualService.getMetadata().getAnnotations();
          if (annotations.containsKey(HarnessAnnotations.managed)
              && annotations.get(HarnessAnnotations.managed).equalsIgnoreCase("true")) {
            managedVirtualServices.add(resourceId);
          }
        }
      }
    }

    return managedVirtualServices;
  }
}
