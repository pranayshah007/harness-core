/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class RunContainerStep implements AsyncExecutableWithRbac<ContainerStepInfo> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.RUN_CONTAINER_STEP_TYPE;
  private final ContainerStepCleanupHelper containerStepCleanupHelper;
  private final ContainerRunStepHelper containerRunStepHelper;
  private final SerializedResponseDataHelper serializedResponseDataHelper;
  private final WaitNotifyEngine waitNotifyEngine;
  private final ContainerDelegateTaskHelper containerDelegateTaskHelper;
  private final ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Override
  public void validateResources(Ambiance ambiance, ContainerStepInfo stepParameters) {
    // done in last step
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, ContainerStepInfo containerStepInfo, StepInputPackage inputPackage) {
    log.info("Starting run in container step");
    String accountId = AmbianceUtils.getAccountId(ambiance);
    List<Level> levelsList = ambiance.getLevelsList();
    long startTs = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis(); // defaulting to 10 mins.
    for (int i = levelsList.size() - 1; i >= 0; i--) {
      if (levelsList.get(i).getGroup().equals(STEP_GROUP)) {
        startTs = levelsList.get(i).getStartTs();
        break;
      }
    }
    long timeout = ((Timeout) containerStepInfo.getTimeout().fetchFinalValue()).getTimeoutInMillis()
        - (System.currentTimeMillis() - startTs * 1000);
    timeout = Math.max(timeout, 100);
    log.info("Timeout for container step left {}", timeout);
    String parkedTaskId = containerDelegateTaskHelper.queueParkedDelegateTask(ambiance, timeout, accountId);
    TaskData runStepTaskData = containerRunStepHelper.getRunStepTask(ambiance, containerStepInfo,
        AmbianceUtils.getAccountId(ambiance), getLogPrefix(ambiance), timeout, parkedTaskId);
    String liteEngineTaskId = containerDelegateTaskHelper.queueTask(ambiance, runStepTaskData, accountId);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(getLogPrefix(ambiance))))
        .build();
  }

  @Override
  public Class<ContainerStepInfo> getStepParametersClass() {
    return ContainerStepInfo.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, ContainerStepInfo stepParameters, AsyncExecutableResponse executableResponse) {
    containerStepCleanupHelper.sendCleanupRequest(ambiance);
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, ContainerStepInfo containerStepInfo, List<String> allCallbackIds,
      String callbackId, ResponseData responseData) {
    responseData = serializedResponseDataHelper.deserialize(responseData);
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
        || k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, ContainerStepInfo stepParameters, Map<String, ResponseData> responseDataMap) {
    return containerStepExecutionResponseHelper.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
  }
  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }
  private void abortTasks(List<String> allCallbackIds, String callbackId, Ambiance ambiance) {
    List<String> callBackIds =
        allCallbackIds.stream().filter(cid -> !cid.equals(callbackId)).collect(Collectors.toList());
    callBackIds.forEach(callbackId1
        -> waitNotifyEngine.doneWith(callbackId1,
            ErrorNotifyResponseData.builder()
                .errorMessage("Delegate is not able to connect to created build farm")
                .build()));
  }
}
