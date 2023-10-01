/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.CleanupStepInfo;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * State sends cleanup task to finish CI build job. It has to be executed in the end once all steps are complete
 * This is not used currently because clean up is implemented via event handler
 */

@OwnedBy(CI)
@Slf4j
public class CleanupStep implements TaskExecutable<CleanupStepInfo, K8sTaskExecutionResponse> {
  public static final StepType STEP_TYPE = CleanupStepInfo.STEP_TYPE;
  public static final String TASK_TYPE = "CI_CLEANUP";
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private KryoSerializer kryoSerializer;
  @Override
  public Class<CleanupStepInfo> getStepParametersClass() {
    return CleanupStepInfo.class;
  }

  @Override
  public TaskRequest obtainTask(Ambiance ambiance, CleanupStepInfo stepParameters, StepInputPackage inputPackage) {
    Infrastructure infrastructure = stepParameters.getInfrastructure();

    if (infrastructure == null || ((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    PodCleanupDetails podCleanupDetails = (PodCleanupDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(CLEANUP_DETAILS));

    // It should always resolved to K8sDirectInfraYaml
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;

    final String clusterName = (String) k8sDirectInfraYaml.getSpec().getConnectorRef().fetchFinalValue();
    final String namespace = (String) k8sDirectInfraYaml.getSpec().getNamespace().fetchFinalValue();
    final List<String> podNames = new ArrayList<>();
    podNames.add(stepParameters.getPodName());

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, clusterName);

    CIK8CleanupTaskParams cik8CleanupTaskParams =
        CIK8CleanupTaskParams.builder()
            .k8sConnector(connectorDetails)
            .cleanupContainerNames(podCleanupDetails.getCleanUpContainerNames())
            .namespace(namespace)
            .podNameList(podNames)
            .serviceNameList(new ArrayList<>())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(stepParameters.getTimeout())
                                  .taskType(TASK_TYPE)
                                  .parameters(new Object[] {cik8CleanupTaskParams})
                                  .build();

    return StepUtils.prepareTaskRequest(
        ambiance, taskData, kryoSerializer, false, Collections.emptyList(), false, null);
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, CleanupStepInfo stepParameters,
      ThrowingSupplier<K8sTaskExecutionResponse> responseSupplier) throws Exception {
    K8sTaskExecutionResponse k8sTaskExecutionResponse = responseSupplier.get();
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      log.info("Cleanup of K8 pod, secret is successful for pod name {} ", stepParameters.getPodName());
      return StepResponse.builder().status(Status.SUCCEEDED).build();

    } else {
      log.error("Failed to clean K8 pod and secret for pod name {} ", stepParameters.getPodName());

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED);
      if (k8sTaskExecutionResponse.getErrorMessage() != null) {
        stepResponseBuilder.failureInfo(
            FailureInfo.newBuilder().setErrorMessage(k8sTaskExecutionResponse.getErrorMessage()).build());
      }
      return stepResponseBuilder.build();
    }
  }
}
