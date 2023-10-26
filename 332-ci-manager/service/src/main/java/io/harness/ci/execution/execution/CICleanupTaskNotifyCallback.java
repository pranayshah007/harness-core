/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Builder
@Slf4j
public class CICleanupTaskNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject private HPersistence persistence;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  private String stageExecutionID;

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Optional<String> optionalTaskID = response.keySet().stream().findFirst();
    try {
      if (optionalTaskID.isPresent()) {
        Supplier<ResponseData> responseSupplier = response.get(optionalTaskID.get());
        ResponseData responseData = responseSupplier.get();
        responseData = serializedResponseDataHelper.deserialize(responseData);
        CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.SUCCESS;
        if (responseData instanceof K8sTaskExecutionResponse) {
          K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
          commandExecutionStatus = k8sTaskExecutionResponse.getCommandExecutionStatus();
        } else if (responseData instanceof VmTaskExecutionResponse) {
          VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) responseData;
          commandExecutionStatus = vmTaskExecutionResponse.getCommandExecutionStatus();
        }
        log.info("Received cleanup response with status {} for stageExecutionId {}", commandExecutionStatus,
            stageExecutionID);
        if (commandExecutionStatus == CommandExecutionStatus.SUCCESS) {
          persistence.delete(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
                                 .filter(CIResourceCleanupResponseKeys.stageExecutionId, stageExecutionID));
        }
      }
    } catch (Exception exception) {
      log.error("Exception occurred during cleanup response consumption. Failure Message: {} and exception {}",
          exception.getMessage(), exception);
    }
  }
}
