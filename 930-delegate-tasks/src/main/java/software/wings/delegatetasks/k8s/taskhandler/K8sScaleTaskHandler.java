/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Scale;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.model.KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.validation.Validator.nullCheckForInvalidRequest;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sScaleTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sScaleResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class K8sScaleTaskHandler extends K8sTaskHandler {
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  private Kubectl client;
  private KubernetesResourceId resourceIdToScale;
  private int targetReplicaCount;
  private K8sScaleResponse k8sScaleResponse;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sScaleTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("k8sTaskParameters", "Must be instance of K8sScaleTaskParameters"));
    }

    K8sScaleTaskParameters k8sScaleTaskParameters = (K8sScaleTaskParameters) k8sTaskParameters;

    k8sScaleResponse = K8sScaleResponse.builder().build();

    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateHelper.getKubernetesConfig(k8sScaleTaskParameters.getK8sClusterConfig(), false);

    boolean success = init(k8sScaleTaskParameters, k8sDelegateTaskParams, kubernetesConfig.getNamespace(),
        new ExecutionLogCallback(delegateLogService, k8sScaleTaskParameters.getAccountId(),
            k8sScaleTaskParameters.getAppId(), k8sScaleTaskParameters.getActivityId(), Init));

    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(k8sScaleResponse, CommandExecutionStatus.FAILURE);
    }

    if (resourceIdToScale == null) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(k8sScaleResponse, CommandExecutionStatus.SUCCESS);
    }

    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sScaleTaskParameters.getTimeoutIntervalInMin());
    List<K8sPod> beforePodList = k8sTaskHelperBase.getPodDetails(kubernetesConfig, resourceIdToScale.getNamespace(),
        k8sScaleTaskParameters.getReleaseName(), steadyStateTimeoutInMillis);

    success = k8sTaskHelperBase.scale(client, k8sDelegateTaskParams, resourceIdToScale, targetReplicaCount,
        new ExecutionLogCallback(delegateLogService, k8sScaleTaskParameters.getAccountId(),
            k8sScaleTaskParameters.getAppId(), k8sScaleTaskParameters.getActivityId(), Scale),
        false);

    if (!success) {
      return k8sTaskHelper.getK8sTaskExecutionResponse(k8sScaleResponse, CommandExecutionStatus.FAILURE);
    }

    if (!k8sScaleTaskParameters.isSkipSteadyStateCheck()) {
      success = k8sTaskHelperBase.doStatusCheck(client, resourceIdToScale, k8sDelegateTaskParams,
          new ExecutionLogCallback(delegateLogService, k8sTaskParameters.getAccountId(), k8sTaskParameters.getAppId(),
              k8sTaskParameters.getActivityId(), WaitForSteadyState));

      if (!success) {
        return k8sTaskHelper.getK8sTaskExecutionResponse(k8sScaleResponse, CommandExecutionStatus.FAILURE);
      }
    }

    List<K8sPod> afterPodList = k8sTaskHelperBase.getPodDetails(kubernetesConfig, resourceIdToScale.getNamespace(),
        k8sScaleTaskParameters.getReleaseName(), steadyStateTimeoutInMillis);

    k8sScaleResponse.setK8sPodList(tagNewPods(beforePodList, afterPodList));
    return k8sTaskHelper.getK8sTaskExecutionResponse(k8sScaleResponse, CommandExecutionStatus.SUCCESS);
  }

  @VisibleForTesting
  List<K8sPod> tagNewPods(List<K8sPod> beforePodList, List<K8sPod> afterPodList) {
    Set<String> beforePodSet = emptyIfNull(beforePodList).stream().map(K8sPod::getName).collect(Collectors.toSet());

    List<K8sPod> allPods = new ArrayList<>(emptyIfNull(afterPodList));

    allPods.forEach(pod -> {
      if (!beforePodSet.contains(pod.getName())) {
        pod.setNewPod(true);
      }
    });
    return allPods;
  }

  @VisibleForTesting
  boolean init(K8sScaleTaskParameters k8sScaleTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams,
      String namespace, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    try {
      client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());

      if (StringUtils.isEmpty(k8sScaleTaskParameters.getWorkload())) {
        executionLogCallback.saveExecutionLog("\nNo Workload found to scale.");
        executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        return true;
      }

      resourceIdToScale = createKubernetesResourceIdFromNamespaceKindName(k8sScaleTaskParameters.getWorkload());

      executionLogCallback.saveExecutionLog(
          color("\nWorkload to scale is: ", White, Bold) + color(resourceIdToScale.namespaceKindNameRef(), Cyan, Bold));

      if (isBlank(resourceIdToScale.getNamespace())) {
        resourceIdToScale.setNamespace(namespace);
      }

      executionLogCallback.saveExecutionLog("\nQuerying current replicas");
      Integer currentReplicas =
          k8sTaskHelperBase.getCurrentReplicas(client, resourceIdToScale, k8sDelegateTaskParams, executionLogCallback);
      executionLogCallback.saveExecutionLog("Current replica count is " + currentReplicas);

      switch (k8sScaleTaskParameters.getInstanceUnitType()) {
        case COUNT:
          targetReplicaCount = k8sScaleTaskParameters.getInstances();
          break;

        case PERCENTAGE:
          Integer maxInstances;
          if (k8sScaleTaskParameters.getMaxInstances().isPresent()) {
            maxInstances = k8sScaleTaskParameters.getMaxInstances().get();
          } else {
            maxInstances = currentReplicas;
          }
          nullCheckForInvalidRequest(maxInstances,
              format("Could not get current replica count for workload %s/%s in namespace %s",
                  resourceIdToScale.getKind(), resourceIdToScale.getName(), resourceIdToScale.getNamespace()),
              USER);
          targetReplicaCount = (int) Math.round(k8sScaleTaskParameters.getInstances() * maxInstances / 100.0);
          break;

        default:
          unhandled(k8sScaleTaskParameters.getInstanceUnitType());
      }

      executionLogCallback.saveExecutionLog("Target replica count is " + targetReplicaCount);

      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);

      return true;
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }
}
