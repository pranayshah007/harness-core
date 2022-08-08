/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.delegatetasks.spotinst.taskhandler.SpotInstDeployTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotInstSetupTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotInstSwapRoutesTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotInstSyncTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotInstTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotinstTrafficShiftAlbDeployTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotinstTrafficShiftAlbSetupTaskHandler;
import software.wings.delegatetasks.spotinst.taskhandler.SpotinstTrafficShiftAlbSwapRoutesTaskHandler;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstTask extends AbstractDelegateRunnableTask {
  @Inject private EncryptionService encryptionService;
  @Inject private SpotInstSyncTaskHandler syncTaskHandler;
  @Inject private SpotInstSetupTaskHandler setupTaskHandler;
  @Inject private SpotInstDeployTaskHandler deployTaskHandler;
  @Inject private SpotInstSwapRoutesTaskHandler swapRoutesTaskHandler;
  @Inject private SpotinstTrafficShiftAlbSetupTaskHandler shiftAlbSetupTaskHandler;
  @Inject private SpotinstTrafficShiftAlbDeployTaskHandler shiftAlbDeployTaskHandler;
  @Inject private SpotinstTrafficShiftAlbSwapRoutesTaskHandler shiftAlbSwapRoutesTaskHandler;

  public SpotInstTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public SpotInstTaskExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented.");
  }

  @Override
  public SpotInstTaskExecutionResponse run(TaskParameters parameters) {
    if (!(parameters instanceof SpotInstCommandRequest)) {
      String message =
          format("Unrecognized task params while running spot inst task: [%s]", parameters.getClass().getSimpleName());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }

    SpotInstCommandRequest spotInstCommandRequest = (SpotInstCommandRequest) parameters;
    SpotInstTaskParameters spotInstTaskParameters = spotInstCommandRequest.getSpotInstTaskParameters();

    if (spotInstCommandRequest.getAwsConfig() != null) {
      encryptionService.decrypt(
          spotInstCommandRequest.getAwsConfig(), spotInstCommandRequest.getAwsEncryptionDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          spotInstCommandRequest.getAwsConfig(), spotInstCommandRequest.getAwsEncryptionDetails());
    }

    encryptionService.decrypt(
        spotInstCommandRequest.getSpotInstConfig(), spotInstCommandRequest.getSpotinstEncryptionDetails(), false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        spotInstCommandRequest.getAwsConfig(), spotInstCommandRequest.getAwsEncryptionDetails());

    SpotInstTaskHandler handler;
    if (spotInstTaskParameters.isSyncTask()) {
      handler = syncTaskHandler;
    } else {
      switch (spotInstTaskParameters.getCommandType()) {
        case SPOT_INST_SETUP: {
          handler = setupTaskHandler;
          break;
        }
        case SPOT_INST_DEPLOY: {
          handler = deployTaskHandler;
          break;
        }
        case SPOT_INST_SWAP_ROUTES: {
          handler = swapRoutesTaskHandler;
          break;
        }
        case SPOT_INST_ALB_SHIFT_SETUP:
          handler = shiftAlbSetupTaskHandler;
          break;
        case SPOT_INST_ALB_SHIFT_DEPLOY:
          handler = shiftAlbDeployTaskHandler;
          break;
        case SPOT_INST_ALB_SHIFT_SWAP_ROUTES:
          handler = shiftAlbSwapRoutesTaskHandler;
          break;
        default: {
          String message =
              format("Unrecognized task params type running spot inst task: [%s]. Workflow execution: [%s]",
                  spotInstTaskParameters.getCommandType().name(), spotInstTaskParameters.getWorkflowExecutionId());
          log.error(message);
          return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
        }
      }
    }
    return handler.executeTask(
        spotInstTaskParameters, spotInstCommandRequest.getSpotInstConfig(), spotInstCommandRequest.getAwsConfig());
  }
}
