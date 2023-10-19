/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.sdk.core.execution.invokers.StrategyHelper.buildResponseDataSupplier;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.delegate.AccountId;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class CdAsyncExecutable<R extends ResponseData, T extends CdTaskExecutable<R>>
    extends AsyncExecutableWithCapabilities {
  @Inject protected T cdTaskExecutable;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private AsyncExecutableTaskHelper asyncExecutableTaskHelper;
  @Inject private StrategyHelper strategyHelper;
  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      return cdTaskExecutable.handleTaskResult(ambiance, stepParameters, buildResponseDataSupplier(responseDataMap));
    } catch (Exception e) {
      log.error("Exception occurred while calling handleTaskResult", e);
      return strategyHelper.handleException(e);
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    cdTaskExecutable.validateResources(ambiance, stepParameters);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    return getAsyncExecutableResponse(
        cdTaskExecutable.obtainTaskAfterRbac(ambiance, stepParameters, inputPackage), ambiance);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return cdTaskExecutable.getStepParametersClass();
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    String taskId = executableResponse.getCallbackIdsList().iterator().next();
    String accountId = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
    delegateGrpcClientWrapper.cancelV2Task(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
  }

  private AsyncExecutableResponse getAsyncExecutableResponse(TaskRequest taskRequest, Ambiance ambiance) {
    SubmitTaskRequest request = taskRequest.getDelegateTaskRequest().getRequest();
    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(request.getDetails());
    Set<String> selectorsList =
        request.getSelectorsList().stream().map(TaskSelector::getSelector).collect(Collectors.toSet());
    DelegateTaskRequest delegateTaskRequest =
        asyncExecutableTaskHelper.mapTaskRequestToDelegateTaskRequest(taskRequest, taskData, selectorsList, "", false);

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    asyncExecutableTaskHelper.publishStepDelegateInfoStepDetails(
        ambiance, taskData, taskRequest.getDelegateTaskRequest().getTaskName(), taskId);
    return createAsyncExecutableResponse(taskId, taskRequest, taskData.getTimeout());
  }

  private AsyncExecutableResponse createAsyncExecutableResponse(
      String callbackId, TaskRequest taskRequest, long timeout) {
    List<String> logKeysList = taskRequest.getDelegateTaskRequest().getLogKeysList();
    List<String> units = taskRequest.getDelegateTaskRequest().getUnitsList();
    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(logKeysList)
        .addAllUnits(units)
        .addCallbackIds(callbackId)
        .setTimeout(Math.toIntExact(timeout))
        .build();
  }
}
