/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.plugin.ContainerStepOutcome;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepExecutionResponseHelper {
  @Inject private ExceptionManager exceptionManager;

  public StepResponse finalizeStepResponse(
      Ambiance ambiance, StepElementParameters stepParameters, ResponseData responseData) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}, response {}", stepIdentifier, responseData);

    if (responseData instanceof ErrorNotifyResponseData) {
      FailureData failureData =
          FailureData.newBuilder()
              .addFailureTypes(FailureType.APPLICATION_FAILURE)
              .setLevel(Level.ERROR.name())
              .setCode(GENERAL_ERROR.name())
              .setMessage(emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
                  new ContainerStepExecutionException(((ErrorNotifyResponseData) responseData).getErrorMessage())))))
              .build();

      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .setErrorMessage("Delegate is not able to connect to created build farm")
                           .addFailureData(failureData)
                           .build())
          .build();
    }

    if (responseData instanceof StepStatusTaskResponseData) {
      return buildAndReturnStepResponse(
          ((StepStatusTaskResponseData) responseData), ambiance, stepParameters, stepIdentifier);
    }
    if (responseData instanceof K8sTaskExecutionResponse) {
      K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
      UnitProgressData commandUnitsProgress = k8sTaskExecutionResponse.getCommandUnitsProgress();
      //      StepResponse.StepOutcome stepOutcome =
      //          StepResponse.StepOutcome
      //              .builder()
      //              //                      .outcome(ContainerStepOutcome.builder()
      //              //                              .outputVariables(((StepMapOutput)
      //              stepStatus.getOutput()).getMap())
      //              //                              .build())
      //                                    .name("output")
      //              .build();
      //      stepResponseBuilder.stepOutcome(stepOutcome);
      StepResponseBuilder stepResponseBuilder = StepResponse.builder();

      stepResponseBuilder.unitProgressList(
          commandUnitsProgress != null ? commandUnitsProgress.getUnitProgresses() : null);
      if (k8sTaskExecutionResponse.getCommandExecutionStatus().getUnitStatus() == UnitStatus.SUCCESS) {
        stepResponseBuilder.status(Status.SUCCEEDED);
      } else if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
        stepResponseBuilder.status(Status.FAILED);
      }
      return stepResponseBuilder.build();
    }
    log.error("Reached unkown place");
    log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE)).build())
        .build();
  }

  private StepResponse buildAndReturnStepResponse(StepStatusTaskResponseData stepStatusTaskResponseData,
      Ambiance ambiance, StepElementParameters stepParameters, String stepIdentifier) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();

    StepStatus stepStatus = stepStatusTaskResponseData.getStepStatus();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    log.info("Received step {} response {} in {} milliseconds ", stepIdentifier, stepStatus.getStepExecutionStatus(),
        (currentTime - startTime) / 1000);

    if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      if (stepStatus.getOutput() != null) {
        StepResponse.StepOutcome stepOutcome =
            StepResponse.StepOutcome.builder()
                .outcome(ContainerStepOutcome.builder()
                             .outputVariables(((StepMapOutput) stepStatus.getOutput()).getMap())
                             .build())
                .name("output")
                .build();
        stepResponseBuilder.stepOutcome(stepOutcome);
      }

      return stepResponseBuilder.status(Status.SUCCEEDED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SKIPPED) {
      return stepResponseBuilder.status(Status.SKIPPED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.ABORTED) {
      return stepResponseBuilder.status(Status.ABORTED).build();
    } else {
      String maskedError = maskTransportExceptionError(stepStatus.getError());
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .setErrorMessage(maskedError)
                           .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                           .build())
          .build();
    }
  }

  private String maskTransportExceptionError(String errorMessage) {
    final String defaultTransportExceptionMessage =
        "Communication between Container and Lite-engine seems to be broken. Please review the resources allocated to the Step";
    final String transportExceptionString = "connection error: desc = \"transport: Error while dialing dial tcp";
    if (errorMessage != null && errorMessage.contains(transportExceptionString)) {
      return defaultTransportExceptionMessage;
    } else {
      return errorMessage;
    }
  }
}
