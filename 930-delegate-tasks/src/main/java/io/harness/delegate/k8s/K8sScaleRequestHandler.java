/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Scale;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.model.KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.validation.Validator.nullCheckForInvalidRequest;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sScaleRequest;
import io.harness.delegate.task.k8s.K8sScaleResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sScaleRequestHandler extends K8sRequestHandler {
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  private Kubectl client;
  private KubernetesResourceId resourceIdToScale;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  private int targetReplicaCount;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8SDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sScaleRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sScaleRequest"));
    }

    K8sScaleRequest k8sScaleRequest = (K8sScaleRequest) k8sDeployRequest;
    LogCallback logCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress);
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        k8sScaleRequest.getK8sInfraDelegateConfig(), k8SDelegateTaskParams.getWorkingDirectory(), logCallback);

    init(k8sScaleRequest, k8SDelegateTaskParams, kubernetesConfig.getNamespace(), logCallback);

    if (resourceIdToScale == null) {
      return getSuccessResponse(K8sScaleResponse.builder().build());
    }

    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sScaleRequest.getTimeoutIntervalInMin());
    LogCallback scaleLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Scale, true, commandUnitsProgress);
    List<K8sPod> beforePodList;
    scaleLogCallback.saveExecutionLog("Fetching existing pods before scale.");
    beforePodList = k8sTaskHelperBase.getPodDetails(kubernetesConfig, resourceIdToScale.getNamespace(),
        k8sScaleRequest.getReleaseName(), steadyStateTimeoutInMillis);

    boolean success = k8sTaskHelperBase.scale(
        client, k8SDelegateTaskParams, resourceIdToScale, targetReplicaCount, scaleLogCallback, true);
    if (success) {
      scaleLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
    }

    if (!k8sScaleRequest.isSkipSteadyStateCheck()) {
      K8sSteadyStateDTO k8sSteadyStateDTO =
          K8sSteadyStateDTO.builder()
              .request(k8sDeployRequest)
              .resourceIds(Collections.singletonList(resourceIdToScale))
              .executionLogCallback(k8sTaskHelperBase.getLogCallback(
                  logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress))
              .k8sDelegateTaskParams(k8SDelegateTaskParams)
              .namespace(resourceIdToScale.getNamespace())
              .denoteOverallSuccess(true)
              .isErrorFrameworkEnabled(true)
              .kubernetesConfig(kubernetesConfig)
              .build();

      K8sClient k8sClient = k8sTaskHelperBase.getKubernetesClient(k8sScaleRequest.isUseK8sApiForSteadyStateCheck());
      k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);
    }

    LogCallback wrapUpLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);

    wrapUpLogCallback.saveExecutionLog("Fetching existing pods after scale.");
    List<K8sPod> afterPodList = k8sTaskHelperBase.getPodDetails(kubernetesConfig, resourceIdToScale.getNamespace(),
        k8sScaleRequest.getReleaseName(), steadyStateTimeoutInMillis);

    K8sScaleResponse k8sScaleResponse =
        K8sScaleResponse.builder().k8sPodList(k8sTaskHelperBase.tagNewPods(beforePodList, afterPodList)).build();

    wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);

    return getSuccessResponse(k8sScaleResponse);
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  private K8sDeployResponse getSuccessResponse(K8sScaleResponse k8sScaleResponse) {
    return K8sDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sNGTaskResponse(k8sScaleResponse)
        .build();
  }

  @VisibleForTesting
  void init(K8sScaleRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, String namespace,
      LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(
        color(String.format("Release Name: [%s]", request.getReleaseName()), Yellow, Bold));

    client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());

    if (StringUtils.isEmpty(request.getWorkload())) {
      executionLogCallback.saveExecutionLog("\nNo Workload found to scale.");
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return;
    }

    try {
      resourceIdToScale = createKubernetesResourceIdFromNamespaceKindName(request.getWorkload());
    } catch (WingsException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format(
              KubernetesExceptionHints.INVALID_RESOURCE_KIND_NAME_FORMAT, request.getWorkload(), request.getWorkload()),
          format(KubernetesExceptionExplanation.INVALID_RESOURCE_KIND_NAME_FORMAT, request.getWorkload()),
          new KubernetesTaskException(ExceptionUtils.getMessage(e)));
    }

    executionLogCallback.saveExecutionLog(
        color("\nWorkload to scale is: ", White, Bold) + color(resourceIdToScale.namespaceKindNameRef(), Cyan, Bold));

    if (isBlank(resourceIdToScale.getNamespace())) {
      resourceIdToScale.setNamespace(namespace);
    }

    executionLogCallback.saveExecutionLog("\nQuerying current replicas");
    Integer currentReplicas =
        k8sTaskHelperBase.getCurrentReplicas(client, resourceIdToScale, k8sDelegateTaskParams, executionLogCallback);
    executionLogCallback.saveExecutionLog("Current replica count is " + currentReplicas);

    if (request.getInstanceUnitType() == null) {
      throw new KubernetesTaskException(
          format("Missing instance unit type. Select one of [%s] to set the scale target replica count type",
              Stream.of(NGInstanceUnitType.values()).map(NGInstanceUnitType::name).collect(Collectors.joining(", "))));
    }

    switch (request.getInstanceUnitType()) {
      case COUNT:
        targetReplicaCount = request.getInstances();
        break;

      case PERCENTAGE:
        Integer maxInstances;
        if (request.getMaxInstances().isPresent()) {
          maxInstances = request.getMaxInstances().get();
        } else {
          maxInstances = currentReplicas;
        }
        nullCheckForInvalidRequest(maxInstances,
            format("Could not get current replica count for workload %s/%s in namespace %s",
                resourceIdToScale.getKind(), resourceIdToScale.getName(), resourceIdToScale.getNamespace()),
            USER);
        targetReplicaCount = (int) Math.round(request.getInstances() * maxInstances / 100.0);
        break;

      default:
        unhandled(request.getInstanceUnitType());
    }

    executionLogCallback.saveExecutionLog("Target replica count is " + targetReplicaCount);

    executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
  }
}
