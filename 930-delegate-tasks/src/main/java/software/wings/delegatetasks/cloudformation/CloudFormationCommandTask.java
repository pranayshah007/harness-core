/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationCommandTaskHandler;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationCreateStackHandler;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationDeleteStackHandler;
import software.wings.delegatetasks.cloudformation.cloudformationtaskhandler.CloudFormationListStacksHandler;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationCommandTask extends AbstractDelegateRunnableTask {
  @Inject private CloudFormationCreateStackHandler createStackHandler;
  @Inject private CloudFormationDeleteStackHandler deleteStackHandler;
  @Inject private CloudFormationListStacksHandler listStacksHandler;

  @Inject private TimeLimiter timeLimiter;

  public CloudFormationCommandTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public CloudFormationCommandExecutionResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public CloudFormationCommandExecutionResponse run(Object[] parameters) {
    CloudFormationCommandRequest request = (CloudFormationCommandRequest) parameters[0];
    List<EncryptedDataDetail> details = (List<EncryptedDataDetail>) parameters[1];

    CloudFormationCommandTaskHandler handler = null;
    switch (request.getCommandType()) {
      case GET_STACKS: {
        handler = listStacksHandler;
        break;
      }
      case CREATE_STACK: {
        handler = createStackHandler;
        break;
      }
      case DELETE_STACK: {
        handler = deleteStackHandler;
        break;
      }
      default: {
        return CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(String.format("Unidentified command task type: %s", request.getCommandType().name()))
            .build();
      }
    }
    try {
      if (request.isTimeoutSupported()) {
        CloudFormationCommandTaskHandler finalHandler = handler;
        return HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(request.getTimeoutInMs()),
            () -> { return finalHandler.execute(request, details); });
      } else {
        return handler.execute(request, details);
      }
    } catch (TimeoutException ex) {
      log.error("Timed out in processing cloud formation task [{}]", request, ex);
      return CloudFormationCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .isTimeoutError(true)
          .errorMessage("Timed out while waiting for task to complete")
          .build();
    } catch (Exception ex) {
      log.error("Exception in processing cloud formation task [{}]", request.toString(), ex);
      return CloudFormationCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
          .build();
    }
  }
}
