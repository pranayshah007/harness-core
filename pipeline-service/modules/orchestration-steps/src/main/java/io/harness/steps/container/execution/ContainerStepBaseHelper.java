/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepBaseHelper {
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  public void handleForCallbackId(Ambiance ambiance, StepElementParameters containerStepInfo,
      List<String> allCallbackIds, String callbackId, ResponseData responseData) {
    responseData = serializedResponseDataHelper.deserialize(responseData);
    Object response = responseData;
    if (responseData instanceof BinaryResponseData) {
      response = referenceFalseKryoSerializer.asInflatedObject(((BinaryResponseData) responseData).getData());
    }
    if (response instanceof K8sTaskExecutionResponse
        && (((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
            || ((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED)) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
    if (response instanceof ErrorNotifyResponseData) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
  }

  public String getLogPrefix(Ambiance ambiance) {
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
