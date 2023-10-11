/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class SyncToAsyncTaskHelper {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;

  public TaskData extractTaskRequest(TaskDetails taskDetails) {
    Object[] parameters = null;
    byte[] data;
    if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.KRYO_PARAMETERS)) {
      data = taskDetails.getKryoParameters().toByteArray();
      parameters = new Object[] {kryoSerializer.asInflatedObject(data)};
    } else if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.JSON_PARAMETERS)) {
      data = taskDetails.getJsonParameters().toStringUtf8().getBytes(StandardCharsets.UTF_8);
    } else {
      throw new InvalidRequestException("Invalid task response type.");
    }
    return TaskData.builder()
        .parameters(parameters)
        .data(data)
        .taskType(taskDetails.getType().getType())
        .timeout(taskDetails.getExecutionTimeout().getSeconds() * 1000)
        .async(true)
        .build();
  }

  // Copied method from CDStepHelper until we refactor it to make it easy to import
  public DelegateTaskRequest mapTaskRequestToDelegateTaskRequest(TaskRequest taskRequest, TaskData taskData,
      Set<String> taskSelectors, String baseLogKey, boolean shouldSkipOpenStream) {
    final SubmitTaskRequest submitTaskRequest = taskRequest.getDelegateTaskRequest().getRequest();
    return DelegateTaskRequest.builder()
        .taskParameters((TaskParameters) taskData.getParameters()[0])
        .taskType(taskData.getTaskType())
        .parked(taskData.isParked())
        .accountId(submitTaskRequest.getAccountId().getId())
        .taskSetupAbstractions(submitTaskRequest.getSetupAbstractions().getValuesMap())
        .taskSelectors(taskSelectors)
        .executionTimeout(Duration.ofMillis(taskData.getTimeout()))
        .logStreamingAbstractions(new LinkedHashMap<>(submitTaskRequest.getLogAbstractions().getValuesMap()))
        .forceExecute(submitTaskRequest.getForceExecute())
        .expressionFunctorToken(taskData.getExpressionFunctorToken())
        .eligibleToExecuteDelegateIds(submitTaskRequest.getEligibleToExecuteDelegateIdsList())
        .executeOnHarnessHostedDelegates(submitTaskRequest.getExecuteOnHarnessHostedDelegates())
        .emitEvent(submitTaskRequest.getEmitEvent())
        .stageId(submitTaskRequest.getStageId())
        .baseLogKey(baseLogKey)
        .shouldSkipOpenStream(shouldSkipOpenStream)
        .selectionLogsTrackingEnabled(true)
        .build();
  }
}
