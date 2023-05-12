/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.K8sSwapServiceSelectorsBaseHandler;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.WingsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.KubernetesSwapServiceSelectorsParams;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.sm.states.KubernetesSwapServiceSelectorsResponse;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class KubernetesSwapServiceSelectorsTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private K8sSwapServiceSelectorsBaseHandler k8sSwapServiceSelectorsBaseHandler;

  public static final String KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME = "Kubernetes Swap Service Selectors";

  public KubernetesSwapServiceSelectorsTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public KubernetesSwapServiceSelectorsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public KubernetesSwapServiceSelectorsResponse run(Object[] parameters) {
    KubernetesSwapServiceSelectorsParams kubernetesSwapServiceSelectorsParams =
        (KubernetesSwapServiceSelectorsParams) parameters[0];

    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(delegateLogService,
        kubernetesSwapServiceSelectorsParams.getAccountId(), kubernetesSwapServiceSelectorsParams.getAppId(),
        kubernetesSwapServiceSelectorsParams.getActivityId(), kubernetesSwapServiceSelectorsParams.getCommandName());

    executionLogCallback.saveExecutionLog(
        String.format("Begin execution of command %s", KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME), LogLevel.INFO);

    try {
      KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
          kubernetesSwapServiceSelectorsParams.getContainerServiceParams());

      boolean success = k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(kubernetesConfig,
          kubernetesSwapServiceSelectorsParams.getService1(), kubernetesSwapServiceSelectorsParams.getService2(),
          executionLogCallback);

      if (!success) {
        return KubernetesSwapServiceSelectorsResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
      }

      return KubernetesSwapServiceSelectorsResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build();
    } catch (WingsException e) {
      Misc.logAllMessages(e, executionLogCallback);
      throw e;
    } catch (Exception e) {
      log.error("Exception in KubernetesSwapServiceSelectors", e);
      Misc.logAllMessages(e, executionLogCallback);
      executionLogCallback.saveExecutionLog(
          "Exception occurred in kubernetesSwapServiceSelectors", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return KubernetesSwapServiceSelectorsResponse.builder().executionStatus(ExecutionStatus.FAILED).build();
    }
  }
}
