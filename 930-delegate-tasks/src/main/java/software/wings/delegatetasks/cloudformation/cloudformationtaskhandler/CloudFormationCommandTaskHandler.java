/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse.CloudFormationCommandExecutionResponseBuilder;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Stack;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public abstract class CloudFormationCommandTaskHandler {
  @Inject protected DelegateFileManager delegateFileManager;
  @Inject protected EncryptionService encryptionService;
  @Inject protected AWSCloudformationClient awsHelperService;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Inject private DelegateLogService delegateLogService;
  @Inject protected CloudformationBaseHelper cloudformationBaseHelper;

  protected static final String stackNamePrefix = "HarnessStack-";

  // ten minutes default timeout for polling stack operations
  static final int DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;

  public CloudFormationCommandExecutionResponse execute(
      CloudFormationCommandRequest request, List<EncryptedDataDetail> details) {
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(delegateLogService, request.getAccountId(),
        request.getAppId(), request.getActivityId(), request.getCommandName());
    try {
      CloudFormationCommandExecutionResponse result;
      result = executeInternal(request, details, executionLogCallback);
      logStatusMessage(executionLogCallback, result);
      return result;
    } catch (Exception ex) {
      String errorMessage = format("Exception: %s while executing CF task.", ExceptionUtils.getMessage(ex));
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, FAILURE);
      return CloudFormationCommandExecutionResponse.builder()
          .errorMessage(errorMessage)
          .commandExecutionStatus(FAILURE)
          .build();
    }
  }

  private void logStatusMessage(
      ExecutionLogCallback executionLogCallback, CloudFormationCommandExecutionResponse result) {
    final CommandExecutionStatus status = result.getCommandExecutionStatus();
    if (status == SUCCESS) {
      executionLogCallback.saveExecutionLog("Execution finished successfully.", LogLevel.INFO, status);
    } else if (status == FAILURE) {
      executionLogCallback.saveExecutionLog("Execution has been failed.", LogLevel.ERROR, status);
    }
  }

  protected CloudFormationCommandExecutionResponse deleteStack(String stackId, String stackName,
      CloudFormationCommandRequest request, ExecutionLogCallback executionLogCallback) {
    CloudFormationCommandExecutionResponseBuilder builder = CloudFormationCommandExecutionResponse.builder();
    Stack stack = null;
    try {
      long stackEventsTs = System.currentTimeMillis();
      executionLogCallback.saveExecutionLog(String.format("# Starting to delete stack: %s", stackName));
      DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackId);
      if (EmptyPredicate.isNotEmpty(request.getCloudFormationRoleArn())) {
        deleteStackRequest.withRoleARN(request.getCloudFormationRoleArn());
      } else {
        executionLogCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }

      awsHelperService.deleteStack(request.getRegion(), deleteStackRequest,
          AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()));
      if (!request.isSkipWaitForResources()) {
        sleep(ofSeconds(30));
      }

      executionLogCallback.saveExecutionLog(
          String.format("# Request to delete stack: %s submitted. Now beginning to poll.", stackName));
      int timeOutMs = request.getTimeoutInMs() > 0 ? request.getTimeoutInMs() : DEFAULT_TIMEOUT_MS;
      long endTime = System.currentTimeMillis() + timeOutMs;
      boolean done = false;

      while (System.currentTimeMillis() < endTime && !done) {
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackId);
        List<Stack> stacks = awsHelperService.getAllStacks(request.getRegion(), describeStacksRequest,
            AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()));
        if (stacks.size() < 1) {
          String message = String.format(
              "# Did not get any stacks with id: %s while querying stacks list. Deletion may have completed",
              stackName);
          executionLogCallback.saveExecutionLog(message);
          builder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
          done = true;
          break;
        }
        stack = stacks.get(0);
        stackEventsTs = cloudformationBaseHelper.printStackEvents(
            AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()), request.getRegion(), stackEventsTs,
            stack, executionLogCallback);

        switch (stack.getStackStatus()) {
          case "DELETE_COMPLETE": {
            executionLogCallback.saveExecutionLog("# Completed deletion of stack");
            builder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
            done = true;
            break;
          }
          case "DELETE_FAILED": {
            String errorMessage = String.format("# Error: %s when deleting stack", stack.getStackStatusReason());
            executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
            builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
            done = true;
            break;
          }
          case "DELETE_IN_PROGRESS": {
            break;
          }
          default: {
            String errorMessage = String.format(
                "# Unexpected status: %s while deleting stack: %s ", stack.getStackStatus(), stack.getStackName());
            executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
            builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
            done = true;
            break;
          }
        }
        sleep(ofSeconds(10));
      }
      if (!done) {
        String errorMessage = String.format("# Timing out while deleting stack: %s", stackName);
        executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
      } else {
        executionLogCallback.saveExecutionLog(
            "Completed deletion of stack", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
    } catch (Exception ex) {
      String errorMessage =
          String.format("# Exception: %s while deleting stack: %s", ExceptionUtils.getMessage(ex), stackName);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }

    cloudformationBaseHelper.printStackResources(AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()),
        request.getRegion(), stack, executionLogCallback);
    return builder.build();
  }

  protected Optional<Stack> getIfStackExists(
      String customStackName, String suffix, AwsInternalConfig awsConfig, String region) {
    List<Stack> stacks = awsHelperService.getAllStacks(region, new DescribeStacksRequest(), awsConfig);
    if (isEmpty(stacks)) {
      return Optional.empty();
    }

    if (isNotEmpty(customStackName)) {
      return stacks.stream().filter(stack -> stack.getStackName().equals(customStackName)).findFirst();
    } else {
      return stacks.stream().filter(stack -> stack.getStackName().endsWith(suffix)).findFirst();
    }
  }

  protected abstract CloudFormationCommandExecutionResponse executeInternal(CloudFormationCommandRequest request,
      List<EncryptedDataDetail> details, ExecutionLogCallback executionLogCallback);
}
