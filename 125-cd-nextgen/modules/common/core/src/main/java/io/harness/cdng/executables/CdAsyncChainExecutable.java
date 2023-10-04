/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.CDStepHelper;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepHelper;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class CdAsyncChainExecutable<T extends CdTaskChainExecutable>
    implements AsyncChainExecutableWithRbac<StepParameters> {
  @Inject T cdTaskChainExecutable;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject protected StepHelper stepHelper;
  @Inject protected CDStepHelper cdStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") protected KryoSerializer kryoSerializer;

  @Override
  public Class<StepParameters> getStepParametersClass() {
    return StepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepParameters stepParameters) {
    cdTaskChainExecutable.validateResources(ambiance, (StepBaseParameters) stepParameters);
  }

  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepParameters stepParameters, StepInputPackage inputPackage) {
    return getAsyncChainExecutableResponse(
        cdTaskChainExecutable.startChainLinkAfterRbac(ambiance, (StepBaseParameters) stepParameters, inputPackage));
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepParameters stepParameters,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    return cdTaskChainExecutable.finalizeExecutionWithSecurityContext(
        ambiance, (StepBaseParameters) stepParameters, null, responseDataSupplier);
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepParameters stepParameters, StepInputPackage inputPackage, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return getAsyncChainExecutableResponse(cdTaskChainExecutable.executeNextLinkWithSecurityContext(
        ambiance, (StepBaseParameters) stepParameters, inputPackage, null, responseSupplier));
  }

  private AsyncChainExecutableResponse getAsyncChainExecutableResponse(TaskChainResponse taskChainResponse) {
    SubmitTaskRequest request = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest();
    TaskData taskData = extractTaskRequest(request.getDetails());
    Set<String> selectorsList =
        request.getSelectorsList().stream().map(TaskSelector::getSelector).collect(Collectors.toSet());
    DelegateTaskRequest delegateTaskRequest = cdStepHelper.mapTaskRequestToDelegateTaskRequest(
        taskChainResponse.getTaskRequest(), taskData, selectorsList, "", false);

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);

    return createAsyncChainExecutableResponse(taskId, taskChainResponse);
  }

  private AsyncChainExecutableResponse createAsyncChainExecutableResponse(
      String callbackId, TaskChainResponse taskChainResponse) {
    List<String> logKeysList = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getLogKeysList();
    List<String> units = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getUnitsList();
    boolean isChainEnd = taskChainResponse.isChainEnd();
    return AsyncChainExecutableResponse.newBuilder()
        .addAllLogKeys(logKeysList)
        .addAllUnits(units)
        .setCallbackId(callbackId)
        .setChainEnd(isChainEnd)
        // toDo do we need timeout
        .build();
  }

  private TaskData extractTaskRequest(TaskDetails taskDetails) {
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
}
