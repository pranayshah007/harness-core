/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.aws.cf.DeployStackResult;
import io.harness.aws.cf.Status;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.cloudformation.CloudFormationCreateStackNGResponse;
import io.harness.delegate.task.cloudformation.CloudFormationCreateStackNGResponse.CloudFormationCreateStackNGResponseBuilder;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse.CloudformationTaskNGResponseBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class CloudformationCreateStackTaskHandler extends CloudformationAbstractTaskHandler {
  private long remainingTimeoutMs;
  @Override
  public CloudformationTaskNGResponse executeTaskInternal(
      CloudformationTaskNGParameters taskNGParameters, String delegateId, String taskId, LogCallback logCallback)
      throws IOException, TimeoutException, InterruptedException {
    remainingTimeoutMs = System.currentTimeMillis() + taskNGParameters.getTimeoutInMs();

    AwsInternalConfig awsInternalConfig = cloudformationBaseHelper.getAwsInternalConfig(
        taskNGParameters.getAwsConnector(), taskNGParameters.getRegion(), taskNGParameters.getEncryptedDataDetails());

    logCallback.saveExecutionLog("# Checking if stack already exists...");
    Optional<Stack> stackOptional =
        getIfStackExists(taskNGParameters.getStackName(), awsInternalConfig, taskNGParameters.getRegion());

    if (!stackOptional.isPresent()) {
      logCallback.saveExecutionLog("# Stack does not exist, creating new stack");
      return createStack(taskNGParameters, awsInternalConfig, logCallback);
    } else {
      Stack stack = stackOptional.get();
      if (StackStatus.ROLLBACK_COMPLETE.name().equals(stack.getStackStatus())) {
        logCallback.saveExecutionLog(format("# Stack already exists and is in %s state.", stack.getStackStatus()));
        logCallback.saveExecutionLog(format("# Deleting stack %s", stack.getStackName()));
        CloudformationTaskNGResponse cloudformationTaskNGResponse =
            deleteStack(taskNGParameters, logCallback, awsInternalConfig, stack.getStackId(), stack.getStackName());
        if (SUCCESS.equals(cloudformationTaskNGResponse.getCommandExecutionStatus())) {
          logCallback.saveExecutionLog(
              format("# Stack %s deleted successfully now creating a new stack", stack.getStackName()));
          return createStack(taskNGParameters, awsInternalConfig, logCallback);
        }
        logCallback.saveExecutionLog(format(
            "# Stack %s deletion failed, stack creation/updation will not proceed.%n Go to Aws Console and delete the stack",
            stack.getStackName()));
        return cloudformationTaskNGResponse;
      } else {
        logCallback.saveExecutionLog("# Stack already exist, updating stack");
        return updateStack(taskNGParameters, awsInternalConfig, stack, logCallback);
      }
    }
  }

  private CloudformationTaskNGResponse createStack(CloudformationTaskNGParameters createStackParameters,
      AwsInternalConfig awsInternalConfig, LogCallback logCallback) {
    CloudformationTaskNGResponseBuilder builder = CloudformationTaskNGResponse.builder();
    String stackName = createStackParameters.getStackName();
    try {
      logCallback.saveExecutionLog(
          color(format("# Creating stack with name: %s", stackName), LogColor.Cyan, LogWeight.Bold));
      CreateStackRequest createStackRequest =
          new CreateStackRequest()
              .withStackName(stackName)
              .withParameters(getParameters(createStackParameters.getParameters()))
              .withCapabilities(createStackParameters.getCapabilities())
              .withTags(cloudformationBaseHelper.getCloudformationTags(createStackParameters.getTags()));
      if (EmptyPredicate.isNotEmpty(createStackParameters.getCloudFormationRoleArn())) {
        createStackRequest.withRoleARN(createStackParameters.getCloudFormationRoleArn());
      } else {
        logCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }
      if (isNotEmpty(createStackParameters.getTemplateBody())) {
        logCallback.saveExecutionLog("# Using Template Body to create Stack");
        createStackRequest.withTemplateBody(createStackParameters.getTemplateBody());
        createStackRequest.withCapabilities(
            cloudformationBaseHelper.getCapabilities(awsInternalConfig, createStackParameters.getRegion(),
                createStackParameters.getTemplateBody(), createStackParameters.getCapabilities(), "body"));
        createStackAndWaitWithEvents(
            createStackParameters, createStackRequest, awsInternalConfig, builder, logCallback);
      } else if (isNotEmpty(createStackParameters.getTemplateUrl())) {
        String normalizedS3TemplatePath =
            awsCFHelperServiceDelegate.normalizeS3TemplatePath(createStackParameters.getTemplateUrl());
        logCallback.saveExecutionLog(format("# Using Template Url: [%s] to Create Stack", normalizedS3TemplatePath));
        createStackRequest.withTemplateURL(normalizedS3TemplatePath);
        createStackRequest.withCapabilities(
            cloudformationBaseHelper.getCapabilities(awsInternalConfig, createStackParameters.getRegion(),
                normalizedS3TemplatePath, createStackParameters.getCapabilities(), "s3"));
        createStackAndWaitWithEvents(
            createStackParameters, createStackRequest, awsInternalConfig, builder, logCallback);
      }
    } catch (Exception ex) {
      String errorMessage = format("Exception: %s while creating stack: %s", ExceptionUtils.getMessage(ex), stackName);
      logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    return builder.build();
  }

  private CloudformationTaskNGResponse updateStack(CloudformationTaskNGParameters updateRequest,
      AwsInternalConfig awsInternalConfig, Stack stack, LogCallback executionLogCallback) {
    CloudformationTaskNGResponseBuilder builder = CloudformationTaskNGResponse.builder();
    try {
      executionLogCallback.saveExecutionLog(color(
          format("# Starting to Update stack with name: %s", stack.getStackName()), LogColor.Cyan, LogWeight.Bold));
      UpdateStackRequest updateStackRequest =
          new UpdateStackRequest()
              .withStackName(stack.getStackName())
              .withParameters(getParameters(updateRequest.getParameters()))
              .withCapabilities(updateRequest.getCapabilities())
              .withTags(cloudformationBaseHelper.getCloudformationTags(updateRequest.getTags()));
      if (EmptyPredicate.isNotEmpty(updateRequest.getCloudFormationRoleArn())) {
        updateStackRequest.withRoleARN(updateRequest.getCloudFormationRoleArn());
      } else {
        executionLogCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }
      if (isNotEmpty(updateRequest.getTemplateBody())) {
        executionLogCallback.saveExecutionLog("# Using Template Body to Update Stack");
        updateStackRequest.withTemplateBody(updateRequest.getTemplateBody());
        updateStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(awsInternalConfig,
            updateRequest.getRegion(), updateRequest.getTemplateBody(), updateRequest.getCapabilities(), "body"));
        updateStackAndWaitWithEvents(
            updateRequest, awsInternalConfig, updateStackRequest, builder, stack, executionLogCallback);
      } else if (isNotEmpty(updateRequest.getTemplateUrl())) {
        String normalizedS3TemplatePath =
            awsCFHelperServiceDelegate.normalizeS3TemplatePath(updateRequest.getTemplateUrl());
        executionLogCallback.saveExecutionLog(
            format("# Using Template Url: [%s] to Update Stack", normalizedS3TemplatePath));
        updateStackRequest.withTemplateURL(normalizedS3TemplatePath);
        updateStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(awsInternalConfig,
            updateRequest.getRegion(), normalizedS3TemplatePath, updateRequest.getCapabilities(), "s3"));
        updateStackAndWaitWithEvents(
            updateRequest, awsInternalConfig, updateStackRequest, builder, stack, executionLogCallback);
      }
    } catch (Exception ex) {
      String errorMessage =
          format("# Exception: %s while Updating stack: %s", ExceptionUtils.getMessage(ex), stack.getStackName());
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    CloudformationTaskNGResponse cloudformationTaskNGResponse = builder.build();
    if (!SUCCESS.equals(cloudformationTaskNGResponse.getCommandExecutionStatus())
        && cloudformationTaskNGResponse.getCloudFormationCommandNGResponse() != null) {
      String responseStackStatus =
          ((CloudFormationCreateStackNGResponse) cloudformationTaskNGResponse.getCloudFormationCommandNGResponse())
              .getStackStatus();
      if (responseStackStatus != null && isNotEmpty(updateRequest.getStackStatusesToMarkAsSuccess())) {
        boolean hasMatchingStatusToBeTreatedAsSuccess =
            updateRequest.getStackStatusesToMarkAsSuccess().stream().anyMatch(
                status -> status.name().equals(responseStackStatus));
        if (hasMatchingStatusToBeTreatedAsSuccess) {
          builder.commandExecutionStatus(SUCCESS);
          cloudformationTaskNGResponse.getCloudFormationCommandNGResponse().setCommandExecutionStatus(SUCCESS);
          builder.cloudFormationCommandNGResponse(cloudformationTaskNGResponse.getCloudFormationCommandNGResponse());
        }
      }
    }

    return builder.build();
  }

  private void createStackAndWaitWithEvents(CloudformationTaskNGParameters createRequest,
      CreateStackRequest createStackRequest, AwsInternalConfig awsInternalConfig,
      CloudformationTaskNGResponseBuilder builder, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("# Calling Aws API to Create stack: %s", createStackRequest.getStackName()));
    long stackEventsTs = System.currentTimeMillis();
    CreateStackResult result =
        awsCloudformationClient.createStack(createRequest.getRegion(), createStackRequest, awsInternalConfig);
    logCallback.saveExecutionLog(format(
        "# Create Stack request submitted for stack: %s. Now polling for status.", createStackRequest.getStackName()));
    long endTime = remainingTimeoutMs;
    String errorMsg;
    Stack stack = null;
    while (System.currentTimeMillis() < endTime) {
      DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(result.getStackId());
      List<Stack> stacks =
          awsCloudformationClient.getAllStacks(createRequest.getRegion(), describeStacksRequest, awsInternalConfig);
      if (stacks.size() < 1) {
        String errorMessage = "# Error: received empty stack list from AWS";
        logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
        builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
        return;
      }

      stack = stacks.get(0);
      stackEventsTs = cloudformationBaseHelper.printStackEvents(
          awsInternalConfig, createRequest.getRegion(), stackEventsTs, stack, logCallback);

      switch (stack.getStackStatus()) {
        case "CREATE_COMPLETE": {
          logCallback.saveExecutionLog("# Stack creation Successful");
          populateCloudformationTaskNGResponse(builder, stack, false);
          builder.commandExecutionStatus(SUCCESS);
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, logCallback);
          return;
        }
        case "CREATE_FAILED": {
          errorMsg = format("# Error: %s while creating stack: %s", stack.getStackStatusReason(), stack.getStackName());
          logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.cloudFormationCommandNGResponse(
              CloudFormationCreateStackNGResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, logCallback);
          return;
        }
        case "CREATE_IN_PROGRESS": {
          break;
        }
        case "ROLLBACK_IN_PROGRESS": {
          errorMsg = format("Creation of stack failed, Rollback in progress. Stack Name: %s : Reason: %s",
              stack.getStackName(), stack.getStackStatusReason());
          logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
          break;
        }
        case "ROLLBACK_FAILED": {
          errorMsg = format("# Creation of stack: %s failed, Rollback failed as well. Reason: %s", stack.getStackName(),
              stack.getStackStatusReason());
          logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.cloudFormationCommandNGResponse(
              CloudFormationCreateStackNGResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, logCallback);
          return;
        }
        case "ROLLBACK_COMPLETE": {
          errorMsg = format("# Creation of stack: %s failed, Rollback complete", stack.getStackName());
          logCallback.saveExecutionLog(errorMsg);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.cloudFormationCommandNGResponse(
              CloudFormationCreateStackNGResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, logCallback);
          return;
        }
        default: {
          String errorMessage = format("# Unexpected status: %s while Creating stack, Status reason: %s",
              stack.getStackStatus(), stack.getStackStatusReason());
          logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.cloudFormationCommandNGResponse(
              CloudFormationCreateStackNGResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, logCallback);
          return;
        }
      }
      sleep(ofSeconds(10));
    }
    String errorMessage = format("# Timing out while Creating stack: %s", createStackRequest.getStackName());
    logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
    builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    cloudformationBaseHelper.printStackResources(awsInternalConfig, createRequest.getRegion(), stack, logCallback);
  }

  private void updateStackAndWaitWithEvents(CloudformationTaskNGParameters request, AwsInternalConfig awsInternalConfig,
      UpdateStackRequest updateStackRequest, CloudformationTaskNGResponseBuilder builder, Stack originalStack,
      LogCallback logCallback) {
    logCallback.saveExecutionLog(format("# Calling Aws API to Update stack: %s", originalStack.getStackName()));
    long stackEventsTs = System.currentTimeMillis();

    DeployStackRequest deployStackRequest = cloudformationBaseHelper.transformToDeployStackRequest(updateStackRequest);
    long endTime = remainingTimeoutMs;
    DeployStackResult deployStackResult = awsCloudformationClient.deployStack(
        request.getRegion(), deployStackRequest, awsInternalConfig, Duration.ofMillis(endTime), logCallback);
    if (deployStackResult.getStatus() == Status.FAILURE) {
      throw new InvalidRequestException(format("# Error creating changeSet: %s", deployStackResult.getStatusReason()));
    }
    logCallback.saveExecutionLog(
        format("# Update Stack Request submitted for stack: %s. Now polling for status", originalStack.getStackName()));

    Stack stack = null;
    while (System.currentTimeMillis() < endTime) {
      DescribeStacksRequest describeStacksRequest =
          new DescribeStacksRequest().withStackName(originalStack.getStackId());
      List<Stack> stacks =
          awsCloudformationClient.getAllStacks(request.getRegion(), describeStacksRequest, awsInternalConfig);
      if (stacks.isEmpty()) {
        String errorMessage = "# Error: received empty stack list from AWS";
        logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
        builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
        return;
      }
      stack = stacks.get(0);

      if (deployStackResult.isNoUpdatesToPerform()) {
        switch (stack.getStackStatus()) {
          case "CREATE_COMPLETE":
          case "UPDATE_COMPLETE":
          case "UPDATE_ROLLBACK_COMPLETE": {
            logCallback.saveExecutionLog(format("# Stack is already in %s state.", stack.getStackStatus()));
            populateCloudformationTaskNGResponse(builder, stack, true);
            builder.commandExecutionStatus(SUCCESS);
            builder.updatedNotPerformed(true);
            cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
            return;
          }
          default: {
            String errorMessage =
                format("# Existing stack with name %s is already in status: %s, therefore exiting with failure",
                    stack.getStackName(), stack.getStackStatus());
            logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
            builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
            populateCloudformationTaskNGResponse(builder, stack, true);
            cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
            return;
          }
        }
      }

      stackEventsTs = cloudformationBaseHelper.printStackEvents(
          awsInternalConfig, request.getRegion(), stackEventsTs, stack, logCallback);

      switch (stack.getStackStatus()) {
        case "CREATE_COMPLETE":
        case "UPDATE_COMPLETE": {
          logCallback.saveExecutionLog("# Update Successful for stack");
          populateCloudformationTaskNGResponse(builder, stack, true);
          builder.commandExecutionStatus(SUCCESS);
          cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
          return;
        }
        case "UPDATE_COMPLETE_CLEANUP_IN_PROGRESS": {
          logCallback.saveExecutionLog("Update completed, cleanup in progress");
          break;
        }
        case "UPDATE_ROLLBACK_FAILED": {
          String errorMessage = format("# Error: %s when updating stack: %s, Rolling back stack update failed",
              stack.getStackStatusReason(), stack.getStackName());
          logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          populateCloudformationTaskNGResponse(builder, stack, true);
          cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
          return;
        }
        case "UPDATE_IN_PROGRESS": {
          break;
        }
        case "UPDATE_ROLLBACK_IN_PROGRESS": {
          logCallback.saveExecutionLog("Update of stack failed, , Rollback in progress");
          builder.commandExecutionStatus(CommandExecutionStatus.FAILURE);
          break;
        }
        case "UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS": {
          logCallback.saveExecutionLog(
              format("Rollback of stack update: %s completed, cleanup in progress", stack.getStackName()));
          break;
        }
        case "UPDATE_ROLLBACK_COMPLETE": {
          String errorMsg = format("# Rollback of stack update: %s completed", stack.getStackName());
          logCallback.saveExecutionLog(errorMsg);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          populateCloudformationTaskNGResponse(builder, stack, true);
          cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
          return;
        }
        default: {
          String errorMessage =
              format("# Unexpected status: %s while creating stack: %s ", stack.getStackStatus(), stack.getStackName());
          logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          populateCloudformationTaskNGResponse(builder, stack, true);
          cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
          return;
        }
      }
      sleep(ofSeconds(10));
    }
    String errorMessage = format("# Timing out while Updating stack: %s", originalStack.getStackName());
    logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
    populateCloudformationTaskNGResponse(builder, stack, true);
    builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, logCallback);
  }

  private void populateCloudformationTaskNGResponse(
      CloudformationTaskNGResponseBuilder builder, Stack stack, boolean existentStack) {
    CloudFormationCreateStackNGResponseBuilder cloudFormationCreateStackNGResponseBuilder =
        CloudFormationCreateStackNGResponse.builder().existentStack(existentStack);
    if (stack != null) {
      cloudFormationCreateStackNGResponseBuilder.stackId(stack.getStackId()).stackStatus(stack.getStackStatus());
      List<Output> outputs = stack.getOutputs();
      if (isNotEmpty(outputs)) {
        cloudFormationCreateStackNGResponseBuilder.cloudFormationOutputMap(
            outputs.stream().collect(toMap(Output::getOutputKey, Output::getOutputValue)));
      }
      builder.cloudFormationCommandNGResponse(cloudFormationCreateStackNGResponseBuilder.build());
    }
  }
}
