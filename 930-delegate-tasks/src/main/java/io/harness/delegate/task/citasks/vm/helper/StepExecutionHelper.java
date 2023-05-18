/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepResponse;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class StepExecutionHelper {
  @Inject private HttpHelper httpHelper;

  public VmTaskExecutionResponse callRunnerForStepExecution(ExecuteStepRequest request) {
    try {
      Response<ExecuteStepResponse> response = httpHelper.executeStepWithRetries(request);
      if (!response.isSuccessful()) {
        return VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
      }

      if (isEmpty(response.body().getError())) {
        return VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .outputVars(response.body().getOutputs())
            .artifact(response.body().getArtifact())
            .build();
      } else {
        return VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(response.body().getError())
            .build();
      }
    } catch (Exception e) {
      log.error("Failed to execute step in runner", e);
      return VmTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(e.toString())
          .build();
    }
  }
}
