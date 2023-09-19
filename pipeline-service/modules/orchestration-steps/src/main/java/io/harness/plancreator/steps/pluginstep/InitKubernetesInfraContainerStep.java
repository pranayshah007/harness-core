/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.plancreator.steps.pluginstep.KubernetesInfraOutcome.KUBERNETES_INFRA_OUTCOME;

import io.harness.beans.FeatureName;
import io.harness.delegate.ComputingResource;
import io.harness.delegate.ContainerSpec;
import io.harness.delegate.ExecutionInfrastructure;
import io.harness.delegate.K8sInfraSpec;
import io.harness.delegate.LogConfig;
import io.harness.delegate.TaskSelector;
import io.harness.encryption.Scope;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.container.execution.ContainerStepRbacHelper;
import io.harness.steps.container.utils.ContainerSpecUtils;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.SubmitTaskResponseData;
import io.harness.utils.InitialiseTaskUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class InitKubernetesInfraContainerStep
    implements TaskExecutableWithRbac<StepElementParameters, SubmitTaskResponseData> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.INIT_KUBERNETES_INFRA_CONTAINER_STEP_TYPE;

  @Inject private ContainerStepRbacHelper containerStepRbacHelper;
  @Inject private InitialiseTaskUtils initialiseTaskUtils;
  @Inject private PmsFeatureFlagService featureFlagService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    ContainerStepSpec stepParameter = (ContainerStepSpec) stepParameters.getSpec();
    containerStepRbacHelper.validateResources(stepParameter, ambiance);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<SubmitTaskResponseData> responseDataSupplier) throws Exception {
    SubmitTaskResponseData submitTaskResponseData = responseDataSupplier.get();
    Status succeeded = Status.SUCCEEDED; // TODO need to check how to get status
    return StepResponse.builder()
        .status(succeeded)
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(KUBERNETES_INFRA_OUTCOME)
                .outcome(KubernetesInfraOutcome.builder().infraRefId(submitTaskResponseData.getInfraRefId()).build())
                .group(StepCategory.STEP_GROUP.name())
                .build())
        .build();
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    ContainerStepSpec containerStepSpec = (ContainerStepSpec) stepElementParameters.getSpec();
    final List<TaskSelector> delegateSelectors = getTaskSelectors(ambiance, containerStepSpec);

    ExecutionInfrastructure executionInfrastructure = buildExecutionInfrastructure(ambiance, stepElementParameters);
    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    return TaskRequestsUtils.prepareK8sInfraTaskRequest(ambiance, executionInfrastructure, timeout,
        TaskCategory.DELEGATE_TASK_V2, true, delegateSelectors, Scope.PROJECT);
  }

  private ExecutionInfrastructure buildExecutionInfrastructure(
      Ambiance ambiance, StepElementParameters stepElementParameters) {
    // get from stepElementParameters
    String cpu = "100m";
    String memory = "100Mi";
    String image = "imijailovic/shell-task-ng:1.0";
    List<Long> ports = List.of(20002L);

    // need to check
    String logKey = initialiseTaskUtils.getLogPrefix(ambiance, "STEP");
    String logToken = "logToken";

    K8sInfraSpec k8sInfraSpec =
        K8sInfraSpec.newBuilder()
            .addAllTasks(List.of(ContainerSpec.newBuilder()
                                     .setImage(image)
                                     .addAllPort(ports)
                                     .setResource(ComputingResource.newBuilder().setCpu(cpu).setMemory(memory).build())
                                     .build()))
            .build();

    return ExecutionInfrastructure.newBuilder()
        .setLogConfig(LogConfig.newBuilder().setLogKey(logKey).setToken(logToken).build())
        .setK8Infraspec(k8sInfraSpec)
        .build();
  }

  private List<TaskSelector> getTaskSelectors(Ambiance ambiance, ContainerStepSpec containerStepSpec) {
    return featureFlagService.isEnabled(
               AmbianceUtils.getAccountId(ambiance), FeatureName.CD_CONTAINER_STEP_DELEGATE_SELECTOR)
        ? ContainerSpecUtils.getStepDelegateSelectors(containerStepSpec)
        : new ArrayList<>();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
