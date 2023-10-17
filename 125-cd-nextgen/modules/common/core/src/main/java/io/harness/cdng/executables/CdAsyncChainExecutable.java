/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.annotations.dev.HarnessTeam.CDP;

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
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class CdAsyncChainExecutable<T extends CdTaskChainExecutable>
    implements AsyncChainExecutableWithRbac<StepBaseParameters> {
  @Inject protected T cdTaskChainExecutable;
  @Inject protected DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject protected AsyncExecutableTaskHelper asyncExecutableTaskHelper;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return cdTaskChainExecutable.getStepParametersClass();
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    cdTaskChainExecutable.validateResources(ambiance, stepParameters);
  }

  @Override
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    return getAsyncChainExecutableResponse(
        cdTaskChainExecutable.startChainLinkAfterRbac(ambiance, stepParameters, inputPackage), ambiance);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    return cdTaskChainExecutable.finalizeExecutionWithSecurityContext(
        ambiance, stepParameters, passThroughData, responseDataSupplier);
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepBaseParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    return getAsyncChainExecutableResponse(cdTaskChainExecutable.executeNextLinkWithSecurityContext(ambiance,
                                               stepParameters, inputPackage, passThroughData, responseSupplier),
        ambiance);
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncChainExecutableResponse executableResponse, boolean userMarked) {
    String taskId = executableResponse.getCallbackId();
    String accountId = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
    delegateGrpcClientWrapper.cancelV2Task(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
  }

  private AsyncChainExecutableResponse getAsyncChainExecutableResponse(
      TaskChainResponse taskChainResponse, Ambiance ambiance) {
    SubmitTaskRequest request = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest();
    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(request.getDetails());
    Set<String> selectorsList =
        request.getSelectorsList().stream().map(TaskSelector::getSelector).collect(Collectors.toSet());
    DelegateTaskRequest delegateTaskRequest = asyncExecutableTaskHelper.mapTaskRequestToDelegateTaskRequest(
        taskChainResponse.getTaskRequest(), taskData, selectorsList, "", false);

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    asyncExecutableTaskHelper.publishStepDelegateInfoStepDetails(
        ambiance, taskData, taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName(), taskId);
    return createAsyncChainExecutableResponse(taskId, taskChainResponse, taskData.getTimeout());
  }

  private AsyncChainExecutableResponse createAsyncChainExecutableResponse(
      String callbackId, TaskChainResponse taskChainResponse, long timeout) {
    List<String> logKeysList = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getLogKeysList();
    List<String> units = taskChainResponse.getTaskRequest().getDelegateTaskRequest().getUnitsList();
    ByteString passThroughData =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(taskChainResponse.getPassThroughData()));
    return AsyncChainExecutableResponse.newBuilder()
        .addAllLogKeys(logKeysList)
        .addAllUnits(units)
        .setCallbackId(callbackId)
        .setChainEnd(taskChainResponse.isChainEnd())
        .setPassThroughData(passThroughData)
        .setTimeout(Math.toIntExact(timeout))
        .build();
  }
}
