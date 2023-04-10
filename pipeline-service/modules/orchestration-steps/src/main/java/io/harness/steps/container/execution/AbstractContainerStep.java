/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.plancreator.NGCommonUtilPlanCreationConstants.STEP_GROUP;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AbstractContainerStep implements AsyncExecutableWithRbac<StepElementParameters> {
  @Inject private ContainerStepCleanupHelper containerStepCleanupHelper;
  @Inject private ContainerRunStepHelper containerRunStepHelper;

  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ContainerDelegateTaskHelper containerDelegateTaskHelper;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private ContainerStepBaseHelper containerStepBaseHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // done in last step
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting run in container step");
    ContainerStepSpec containerStepInfo = (ContainerStepSpec) stepElementParameters.getSpec();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    List<Level> levelsList = ambiance.getLevelsList();
    long startTs = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis(); // defaulting to 10 mins.
    for (int i = levelsList.size() - 1; i >= 0; i--) {
      if (levelsList.get(i).getGroup().equals(STEP_GROUP)) {
        startTs = levelsList.get(i).getStartTs();
        break;
      }
    }

    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis()
        - (System.currentTimeMillis() - startTs);
    timeout = Math.max(timeout, 100);
    log.info("Timeout for container step left {}", timeout);
    String parkedTaskId = containerDelegateTaskHelper.queueParkedDelegateTask(ambiance, timeout, accountId);
    String logPrefix = containerStepBaseHelper.getLogPrefix(ambiance);
    UnitStep unitStep =
        containerRunStepHelper.serialiseStep(ambiance, containerStepInfo, accountId, logPrefix, timeout, parkedTaskId);
    TaskData runStepTaskData = containerStepBaseHelper.getRunStepTask(ambiance, unitStep, timeout);
    String liteEngineTaskId = containerDelegateTaskHelper.queueTask(ambiance, runStepTaskData, accountId);
    log.info("Created parked task {} and lite engine task {} for  step {}", parkedTaskId, liteEngineTaskId,
        containerStepInfo.getIdentifier());

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(containerStepBaseHelper.getLogPrefix(ambiance))))
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    containerStepCleanupHelper.sendCleanupRequest(ambiance);
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, StepElementParameters containerStepInfo,
      List<String> allCallbackIds, String callbackId, ResponseData responseData) {
    containerStepBaseHelper.handleForCallbackId(ambiance, containerStepInfo, allCallbackIds, callbackId, responseData);
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    containerStepCleanupHelper.sendCleanupRequest(ambiance);
    Outcome outcome = produceOutcome(ambiance, stepParameters);
    return containerStepExecutionResponseHelper.handleAsyncResponseInternal(ambiance, responseDataMap, outcome);
  }

  public Outcome produceOutcome(Ambiance ambiance, StepElementParameters stepParameters) {
    return null;
  }
}
