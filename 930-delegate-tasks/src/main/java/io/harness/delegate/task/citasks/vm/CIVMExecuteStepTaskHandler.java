/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.SecretSpecBuilder;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest.ExecuteStepRequestBuilder;
import io.harness.delegate.task.citasks.CIExecuteStepTaskHandler;
import io.harness.delegate.task.citasks.vm.helper.StepExecutionHelper;
import io.harness.vm.VmExecuteStepUtils;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVMExecuteStepTaskHandler implements CIExecuteStepTaskHandler {
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @NotNull private Type type = Type.VM;
  @Inject private StepExecutionHelper stepExecutionHelper;

  @Inject private VmExecuteStepUtils vmExecuteStepUtils;
  private static final String DOCKER_REGISTRY_ENV = "PLUGIN_REGISTRY";

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public VmTaskExecutionResponse executeTaskInternal(CIExecuteStepTaskParams ciExecuteStepTaskParams, String taskId) {
    CIVmExecuteStepTaskParams ciVmExecuteStepTaskParams = (CIVmExecuteStepTaskParams) ciExecuteStepTaskParams;
    log.info(
        "Received request to execute step with stage runtime ID {}", ciVmExecuteStepTaskParams.getStageRuntimeId());
    return stepExecutionHelper.callRunnerForStepExecution(convert(ciVmExecuteStepTaskParams, taskId));
  }

  private ExecuteStepRequest convert(CIVmExecuteStepTaskParams params, String taskId) {
    ExecuteStepRequestBuilder builder = vmExecuteStepUtils.convertStep(params);
    builder.correlationID(taskId);
    return builder.build();
  }
}
