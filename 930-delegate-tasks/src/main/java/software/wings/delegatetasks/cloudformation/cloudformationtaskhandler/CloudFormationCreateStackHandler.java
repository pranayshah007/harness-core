/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_GIT;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.aws.cf.DeployStackResult;
import io.harness.aws.cf.Status;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.NameValuePair;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse.CloudFormationCommandExecutionResponseBuilder;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse.CloudFormationCreateStackResponseBuilder;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo.CloudFormationRollbackInfoBuilder;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Singleton
@NoArgsConstructor
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationCreateStackHandler extends CloudFormationCommandTaskHandler {
  private int remainingTimeoutMs;

  @Override
  protected CloudFormationCommandExecutionResponse executeInternal(CloudFormationCommandRequest request,
      List<EncryptedDataDetail> details, ExecutionLogCallback executionLogCallback) {
    encryptionService.decrypt(request.getAwsConfig(), details, false);
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig());

    CloudFormationCreateStackRequest upsertRequest = (CloudFormationCreateStackRequest) request;

    remainingTimeoutMs = request.getTimeoutInMs() > 0 ? request.getTimeoutInMs() : DEFAULT_TIMEOUT_MS;

    executionLogCallback.saveExecutionLog("# Checking if stack already exists...");
    Optional<Stack> stackOptional = getIfStackExists(
        upsertRequest.getCustomStackName(), upsertRequest.getStackNameSuffix(), awsInternalConfig, request.getRegion());

    if (!stackOptional.isPresent()) {
      executionLogCallback.saveExecutionLog("# Stack does not exist, creating new stack");
      return createStack(upsertRequest, executionLogCallback);
    } else {
      Stack stack = stackOptional.get();
      if (StackStatus.ROLLBACK_COMPLETE.name().equals(stack.getStackStatus())) {
        executionLogCallback.saveExecutionLog(
            format("# Stack already exists and is in %s state.", stack.getStackStatus()));
        executionLogCallback.saveExecutionLog(format("# Deleting stack %s", stack.getStackName()));
        CloudFormationCommandExecutionResponse deleteStackCommandExecutionResponse =
            deleteStack(stack.getStackId(), stack.getStackName(), request, executionLogCallback);
        if (SUCCESS.equals(deleteStackCommandExecutionResponse.getCommandExecutionStatus())) {
          executionLogCallback.saveExecutionLog(
              format("# Stack %s deleted successfully now creating a new stack", stack.getStackName()));
          return createStack(upsertRequest, executionLogCallback);
        }
        executionLogCallback.saveExecutionLog(format(
            "# Stack %s deletion failed, stack creation/updation will not proceed.%n Go to Aws Console and delete the stack",
            stack.getStackName()));
        return deleteStackCommandExecutionResponse;
      } else {
        executionLogCallback.saveExecutionLog("# Stack already exist, updating stack");
        return updateStack(upsertRequest, stack, executionLogCallback);
      }
    }
  }

  private CloudFormationCommandExecutionResponse updateStack(
      CloudFormationCreateStackRequest updateRequest, Stack stack, ExecutionLogCallback executionLogCallback) {
    CloudFormationCommandExecutionResponseBuilder builder = CloudFormationCommandExecutionResponse.builder();
    try {
      executionLogCallback.saveExecutionLog(format("# Starting to Update stack with name: %s", stack.getStackName()));
      UpdateStackRequest updateStackRequest =
          new UpdateStackRequest()
              .withStackName(stack.getStackName())
              .withParameters(getCfParams(updateRequest))
              .withCapabilities(updateRequest.getCapabilities())
              .withTags(cloudformationBaseHelper.getCloudformationTags(updateRequest.getTags()));
      if (EmptyPredicate.isNotEmpty(updateRequest.getCloudFormationRoleArn())) {
        updateStackRequest.withRoleARN(updateRequest.getCloudFormationRoleArn());
      } else {
        executionLogCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }
      switch (updateRequest.getCreateType()) {
        case CLOUDFORMATION_STACK_CREATE_GIT: {
          executionLogCallback.saveExecutionLog("# Using Git Template Body to Update Stack");
          updateRequest.setCreateType(CLOUDFORMATION_STACK_CREATE_BODY);
          updateStackRequest.withTemplateBody(updateRequest.getData());
          updateStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(updateRequest.getAwsConfig()), updateRequest.getRegion(),
              updateRequest.getData(), updateRequest.getCapabilities(), "body"));
          updateStackAndWaitWithEvents(updateRequest, updateStackRequest, builder, stack, executionLogCallback);
          break;
        }
        case CLOUDFORMATION_STACK_CREATE_BODY: {
          executionLogCallback.saveExecutionLog("# Using Template Body to Update Stack");
          updateStackRequest.withTemplateBody(updateRequest.getData());
          updateStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(updateRequest.getAwsConfig()), updateRequest.getRegion(),
              updateRequest.getData(), updateRequest.getCapabilities(), "body"));
          updateStackAndWaitWithEvents(updateRequest, updateStackRequest, builder, stack, executionLogCallback);
          break;
        }
        case CLOUDFORMATION_STACK_CREATE_URL: {
          updateRequest.setData(awsCFHelperServiceDelegate.normalizeS3TemplatePath(updateRequest.getData()));
          executionLogCallback.saveExecutionLog(
              format("# Using Template Url: [%s] to Update Stack", updateRequest.getData()));
          updateStackRequest.withTemplateURL(updateRequest.getData());
          updateStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(updateRequest.getAwsConfig()), updateRequest.getRegion(),
              updateRequest.getData(), updateRequest.getCapabilities(), "s3"));
          updateStackAndWaitWithEvents(updateRequest, updateStackRequest, builder, stack, executionLogCallback);
          break;
        }
        default: {
          String errorMessage = format("# Unsupported stack create type: %s", updateRequest.getCreateType());
          executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
        }
      }
    } catch (Exception ex) {
      String errorMessage =
          format("# Exception: %s while Updating stack: %s", ExceptionUtils.getMessage(ex), stack.getStackName());
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    CloudFormationCommandExecutionResponse cloudFormationCommandExecutionResponse = builder.build();
    if (!SUCCESS.equals(cloudFormationCommandExecutionResponse.getCommandExecutionStatus())
        && cloudFormationCommandExecutionResponse.getCommandResponse() != null) {
      String responseStackStatus =
          ((CloudFormationCreateStackResponse) cloudFormationCommandExecutionResponse.getCommandResponse())
              .getStackStatus();
      if (responseStackStatus != null && isNotEmpty(updateRequest.getStackStatusesToMarkAsSuccess())) {
        boolean hasMatchingStatusToBeTreatedAsSuccess =
            updateRequest.getStackStatusesToMarkAsSuccess().stream().anyMatch(
                status -> status.name().equals(responseStackStatus));
        if (hasMatchingStatusToBeTreatedAsSuccess) {
          builder.commandExecutionStatus(SUCCESS);
          cloudFormationCommandExecutionResponse.getCommandResponse().setCommandExecutionStatus(SUCCESS);
          builder.commandResponse(cloudFormationCommandExecutionResponse.getCommandResponse());
        }
      }
    }
    return builder.build();
  }

  private CloudFormationCommandExecutionResponse createStack(
      CloudFormationCreateStackRequest createRequest, ExecutionLogCallback executionLogCallback) {
    CloudFormationCommandExecutionResponseBuilder builder = CloudFormationCommandExecutionResponse.builder();
    String stackName;
    if (isNotEmpty(createRequest.getCustomStackName())) {
      stackName = createRequest.getCustomStackName();
    } else {
      stackName = stackNamePrefix + createRequest.getStackNameSuffix();
    }
    try {
      executionLogCallback.saveExecutionLog(format("# Creating stack with name: %s", stackName));
      CreateStackRequest createStackRequest =
          new CreateStackRequest()
              .withStackName(stackName)
              .withParameters(getCfParams(createRequest))
              .withCapabilities(createRequest.getCapabilities())
              .withTags(cloudformationBaseHelper.getCloudformationTags(createRequest.getTags()));
      if (EmptyPredicate.isNotEmpty(createRequest.getCloudFormationRoleArn())) {
        createStackRequest.withRoleARN(createRequest.getCloudFormationRoleArn());
      } else {
        executionLogCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }
      switch (createRequest.getCreateType()) {
        case CLOUDFORMATION_STACK_CREATE_GIT: {
          executionLogCallback.saveExecutionLog("# Using Git Template Body to Create Stack");
          createRequest.setCreateType(CLOUDFORMATION_STACK_CREATE_BODY);
          createStackRequest.withTemplateBody(createRequest.getData());
          createStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(createRequest.getAwsConfig()), createRequest.getRegion(),
              createRequest.getData(), createRequest.getCapabilities(), "body"));
          createStackAndWaitWithEvents(createRequest, createStackRequest, builder, executionLogCallback);
          break;
        }
        case CLOUDFORMATION_STACK_CREATE_BODY: {
          executionLogCallback.saveExecutionLog("# Using Template Body to create Stack");
          createStackRequest.withTemplateBody(createRequest.getData());
          createStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(createRequest.getAwsConfig()), createRequest.getRegion(),
              createRequest.getData(), createRequest.getCapabilities(), "body"));
          createStackAndWaitWithEvents(createRequest, createStackRequest, builder, executionLogCallback);
          break;
        }
        case CLOUDFORMATION_STACK_CREATE_URL: {
          createRequest.setData(awsCFHelperServiceDelegate.normalizeS3TemplatePath(createRequest.getData()));
          executionLogCallback.saveExecutionLog(
              format("# Using Template Url: [%s] to Create Stack", createRequest.getData()));
          createStackRequest.withTemplateURL(createRequest.getData());
          createStackRequest.withCapabilities(cloudformationBaseHelper.getCapabilities(
              AwsConfigToInternalMapper.toAwsInternalConfig(createRequest.getAwsConfig()), createRequest.getRegion(),
              createRequest.getData(), createRequest.getCapabilities(), "s3"));
          createStackAndWaitWithEvents(createRequest, createStackRequest, builder, executionLogCallback);
          break;
        }
        default: {
          String errorMessage = format("Unsupported stack create type: %s", createRequest.getCreateType());
          executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
        }
      }
    } catch (Exception ex) {
      String errorMessage = format("Exception: %s while creating stack: %s", ExceptionUtils.getMessage(ex), stackName);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    return builder.build();
  }

  private void createStackAndWaitWithEvents(CloudFormationCreateStackRequest createRequest,
      CreateStackRequest createStackRequest, CloudFormationCommandExecutionResponseBuilder builder,
      ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(
        format("# Calling Aws API to Create stack: %s", createStackRequest.getStackName()));
    long stackEventsTs = System.currentTimeMillis();
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(createRequest.getAwsConfig());
    CreateStackResult result =
        awsHelperService.createStack(createRequest.getRegion(), createStackRequest, awsInternalConfig);
    executionLogCallback.saveExecutionLog(format(
        "# Create Stack request submitted for stack: %s. Now polling for status.", createStackRequest.getStackName()));
    int timeOutMs = remainingTimeoutMs;
    long endTime = System.currentTimeMillis() + timeOutMs;
    String errorMsg;
    Stack stack = null;
    while (System.currentTimeMillis() < endTime) {
      stack = getStack(createRequest, builder, result.getStackId(), executionLogCallback);
      if (stack == null) {
        return;
      }

      stackEventsTs = cloudformationBaseHelper.printStackEvents(
          awsInternalConfig, createRequest.getRegion(), stackEventsTs, stack, executionLogCallback);

      switch (stack.getStackStatus()) {
        case "CREATE_COMPLETE": {
          executionLogCallback.saveExecutionLog("# Stack creation Successful");
          populateInfraMappingPropertiesFromStack(builder, stack,
              ExistingStackInfo.builder().stackExisted(false).build(), executionLogCallback, createRequest);
          if (!createRequest.isSkipWaitForResources()) {
            executionLogCallback.saveExecutionLog("# Waiting 30 seconds for resources to come up");
            sleep(ofSeconds(30));
          }
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
          return;
        }
        case "CREATE_FAILED": {
          errorMsg = format("# Error: %s while creating stack: %s", stack.getStackStatusReason(), stack.getStackName());
          executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.commandResponse(
              CloudFormationCreateStackResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
          return;
        }
        case "CREATE_IN_PROGRESS": {
          break;
        }
        case "ROLLBACK_IN_PROGRESS": {
          errorMsg = format("Creation of stack failed, Rollback in progress. Stack Name: %s : Reason: %s",
              stack.getStackName(), stack.getStackStatusReason());
          executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          break;
        }
        case "ROLLBACK_FAILED": {
          errorMsg = format("# Creation of stack: %s failed, Rollback failed as well. Reason: %s", stack.getStackName(),
              stack.getStackStatusReason());
          executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.commandResponse(
              CloudFormationCreateStackResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
          return;
        }
        case "ROLLBACK_COMPLETE": {
          errorMsg = format("# Creation of stack: %s failed, Rollback complete", stack.getStackName());
          executionLogCallback.saveExecutionLog(errorMsg);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.commandResponse(
              CloudFormationCreateStackResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
          return;
        }
        default: {
          String errorMessage = format("# Unexpected status: %s while Creating stack, Status reason: %s",
              stack.getStackStatus(), stack.getStackStatusReason());
          executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          builder.commandResponse(
              CloudFormationCreateStackResponse.builder().stackStatus(stack.getStackStatus()).build());
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
          return;
        }
      }
      sleep(ofSeconds(10));
    }
    String errorMessage = format("# Timing out while Creating stack: %s", createStackRequest.getStackName());
    executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    cloudformationBaseHelper.printStackResources(
        awsInternalConfig, createRequest.getRegion(), stack, executionLogCallback);
  }

  private void updateStackAndWaitWithEvents(CloudFormationCreateStackRequest request,
      UpdateStackRequest updateStackRequest, CloudFormationCommandExecutionResponseBuilder builder, Stack originalStack,
      ExecutionLogCallback executionLogCallback) {
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig());
    ExistingStackInfo existingStackInfo =
        cloudformationBaseHelper.getExistingStackInfo(awsInternalConfig, request.getRegion(), originalStack);

    long stackEventsTs = System.currentTimeMillis();

    boolean noStackUpdated;

    if (request.isDeploy()) {
      noStackUpdated =
          deployStack(request, updateStackRequest, Duration.ofMillis(remainingTimeoutMs), executionLogCallback);
    } else {
      noStackUpdated = updateStack(request, updateStackRequest, originalStack, executionLogCallback);
    }

    if (noStackUpdated) {
      handleNoStackUpdate(request, builder, originalStack, executionLogCallback, existingStackInfo);
    } else {
      handleAndWaitForStackUpdate(
          request, builder, originalStack, executionLogCallback, existingStackInfo, stackEventsTs);
    }
  }

  private boolean deployStack(CloudFormationCreateStackRequest request, UpdateStackRequest updateStackRequest,
      Duration duration, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        color(format("\n# Using Change Set to update stack: %s\n", updateStackRequest.getStackName()), LogColor.White,
            LogWeight.Bold),
        LogLevel.INFO);

    DeployStackRequest deployStackRequest = cloudformationBaseHelper.transformToDeployStackRequest(updateStackRequest);
    DeployStackResult deployStackResult = awsHelperService.deployStack(request.getRegion(), deployStackRequest,
        AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()), duration, logCallback);

    if (deployStackResult.getStatus() == Status.FAILURE) {
      throw new InvalidRequestException(
          format("CF Change set creation failed: %s", deployStackResult.getStatusReason()));
    }

    return deployStackResult.isNoUpdatesToPerform();
  }

  private void handleAndWaitForStackUpdate(CloudFormationCreateStackRequest request,
      CloudFormationCommandExecutionResponseBuilder builder, Stack originalStack,
      ExecutionLogCallback executionLogCallback, ExistingStackInfo existingStackInfo, long stackEventsTs) {
    executionLogCallback.saveExecutionLog(
        format("# Update Stack Request submitted for stack: %s. Now polling for status", originalStack.getStackName()));

    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig());
    int timeOutMs = remainingTimeoutMs;
    long endTime = System.currentTimeMillis() + timeOutMs;

    while (System.currentTimeMillis() < endTime) {
      Stack stack = getStack(request, builder, originalStack.getStackId(), executionLogCallback);
      if (stack == null) {
        return;
      }

      stackEventsTs = cloudformationBaseHelper.printStackEvents(
          awsInternalConfig, request.getRegion(), stackEventsTs, stack, executionLogCallback);

      switch (stack.getStackStatus()) {
        case "CREATE_COMPLETE":
        case "UPDATE_COMPLETE": {
          executionLogCallback.saveExecutionLog("# Update Successful for stack");
          populateInfraMappingPropertiesFromStack(builder, stack, existingStackInfo, executionLogCallback, request);
          if (!request.isSkipWaitForResources()) {
            executionLogCallback.saveExecutionLog("# Waiting 30 seconds for resources to come up");
            sleep(ofSeconds(30));
          }
          CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
              getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
          builder.commandResponse(cloudFormationCreateStackResponse);
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, request.getRegion(), stack, executionLogCallback);
          return;
        }
        case "UPDATE_COMPLETE_CLEANUP_IN_PROGRESS": {
          executionLogCallback.saveExecutionLog("Update completed, cleanup in progress");
          break;
        }
        case "UPDATE_ROLLBACK_FAILED": {
          String errorMessage = format("# Error: %s when updating stack: %s, Rolling back stack update failed",
              stack.getStackStatusReason(), stack.getStackName());
          executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
              getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
          builder.commandResponse(cloudFormationCreateStackResponse);
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, request.getRegion(), stack, executionLogCallback);
          return;
        }
        case "UPDATE_IN_PROGRESS": {
          break;
        }
        case "UPDATE_ROLLBACK_IN_PROGRESS": {
          executionLogCallback.saveExecutionLog("Update of stack failed, , Rollback in progress");
          builder.commandExecutionStatus(CommandExecutionStatus.FAILURE);
          break;
        }
        case "UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS": {
          executionLogCallback.saveExecutionLog(
              format("Rollback of stack update: %s completed, cleanup in progress", stack.getStackName()));
          break;
        }
        case "UPDATE_ROLLBACK_COMPLETE": {
          String errorMsg = format("# Rollback of stack update: %s completed", stack.getStackName());
          executionLogCallback.saveExecutionLog(errorMsg);
          builder.errorMessage(errorMsg).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
              getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
          builder.commandResponse(cloudFormationCreateStackResponse);
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, request.getRegion(), stack, executionLogCallback);
          return;
        }
        default: {
          String errorMessage =
              format("# Unexpected status: %s while creating stack: %s ", stack.getStackStatus(), stack.getStackName());
          executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
          CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
              getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
          builder.commandResponse(cloudFormationCreateStackResponse);
          cloudformationBaseHelper.printStackResources(
              awsInternalConfig, request.getRegion(), stack, executionLogCallback);
          return;
        }
      }
      sleep(ofSeconds(10));
    }
    String errorMessage = format("# Timing out while Updating stack: %s", originalStack.getStackName());
    executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);

    Stack stack = getStack(request, builder, originalStack.getStackId(), executionLogCallback);
    if (null == stack) {
      return;
    }
    CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
        getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
    builder.commandResponse(cloudFormationCreateStackResponse);
    builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    cloudformationBaseHelper.printStackResources(awsInternalConfig, request.getRegion(), stack, executionLogCallback);
  }

  private void handleNoStackUpdate(CloudFormationCreateStackRequest request,
      CloudFormationCommandExecutionResponseBuilder builder, Stack originalStack,
      ExecutionLogCallback executionLogCallback, ExistingStackInfo existingStackInfo) {
    Stack stack = getStack(request, builder, originalStack.getStackId(), executionLogCallback);
    if (stack == null) {
      return;
    }
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig());

    switch (stack.getStackStatus()) {
      case "CREATE_COMPLETE":
      case "UPDATE_COMPLETE": {
        executionLogCallback.saveExecutionLog(format("# Stack is already in %s state.", stack.getStackStatus()));
        populateInfraMappingPropertiesFromStack(builder, stack, existingStackInfo, executionLogCallback, request);
        CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
            getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
        builder.commandResponse(cloudFormationCreateStackResponse);
        cloudformationBaseHelper.printStackResources(
            awsInternalConfig, request.getRegion(), stack, executionLogCallback);
        return;
      }
      case "UPDATE_ROLLBACK_COMPLETE": {
        executionLogCallback.saveExecutionLog(format("# Stack is already in %s state.", stack.getStackStatus()));
        CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
            getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
        builder.commandResponse(cloudFormationCreateStackResponse);
        builder.commandExecutionStatus(SUCCESS);
        cloudformationBaseHelper.printStackResources(
            awsInternalConfig, request.getRegion(), stack, executionLogCallback);
        return;
      }

      default: {
        String errorMessage =
            format("# Existing stack with name %s is already in status: %s, therefore exiting with failure",
                stack.getStackName(), stack.getStackStatus());
        executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
        CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
            getCloudFormationCreateStackResponse(builder, stack, existingStackInfo, request);
        builder.commandResponse(cloudFormationCreateStackResponse);
        cloudformationBaseHelper.printStackResources(
            awsInternalConfig, request.getRegion(), stack, executionLogCallback);
      }
    }
  }

  @Nullable
  private Stack getStack(CloudFormationCreateStackRequest request,
      CloudFormationCommandExecutionResponseBuilder builder, String stackId,
      ExecutionLogCallback executionLogCallback) {
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackId);
    Optional<Stack> stackOptional = awsHelperService.getStack(request.getRegion(), describeStacksRequest,
        AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()));
    if (!stackOptional.isPresent()) {
      String errorMessage = "# Error: received empty stack list from AWS";
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
      return null;
    }
    return stackOptional.get();
  }

  private boolean updateStack(CloudFormationCreateStackRequest request, UpdateStackRequest updateStackRequest,
      Stack originalStack, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(
        color(format("# Calling AWS API update to update stack: %s", originalStack.getStackName()), LogColor.White,
            LogWeight.Bold),
        LogLevel.INFO);

    UpdateStackResult updateStackResult = awsHelperService.updateStack(
        request.getRegion(), updateStackRequest, AwsConfigToInternalMapper.toAwsInternalConfig(request.getAwsConfig()));

    boolean noStackUpdated = false;
    if (updateStackResult == null || updateStackResult.getStackId() == null) {
      noStackUpdated = true;
      executionLogCallback.saveExecutionLog(
          format("# Update Stack Request Failed. There is nothing to be updated in the stack with name: %s",
              originalStack.getStackName()));
    }
    return noStackUpdated;
  }

  private void populateInfraMappingPropertiesFromStack(CloudFormationCommandExecutionResponseBuilder builder,
      Stack stack, ExistingStackInfo existingStackInfo, ExecutionLogCallback executionLogCallback,
      CloudFormationCreateStackRequest cloudFormationCreateStackRequest) {
    CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
        createCloudFormationCreateStackResponse(stack, existingStackInfo, cloudFormationCreateStackRequest);
    builder.commandExecutionStatus(SUCCESS).commandResponse(cloudFormationCreateStackResponse);
  }

  private CloudFormationCreateStackResponse getCloudFormationCreateStackResponse(
      CloudFormationCommandExecutionResponseBuilder builder, Stack stack, ExistingStackInfo existingStackInfo,
      CloudFormationCreateStackRequest request) {
    CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
        (CloudFormationCreateStackResponse) builder.build().getCommandResponse();
    if (cloudFormationCreateStackResponse == null) {
      cloudFormationCreateStackResponse = createCloudFormationCreateStackResponse(stack, existingStackInfo, request);
    } else {
      cloudFormationCreateStackResponse.setStackStatus(stack.getStackStatus());
    }
    return cloudFormationCreateStackResponse;
  }

  private CloudFormationCreateStackResponse createCloudFormationCreateStackResponse(Stack stack,
      ExistingStackInfo existingStackInfo, CloudFormationCreateStackRequest cloudFormationCreateStackRequest) {
    CloudFormationCreateStackResponseBuilder createBuilder = CloudFormationCreateStackResponse.builder();
    createBuilder.existingStackInfo(existingStackInfo);
    createBuilder.stackId(stack.getStackId());
    createBuilder.stackStatus(stack.getStackStatus());
    List<Output> outputs = stack.getOutputs();
    if (isNotEmpty(outputs)) {
      createBuilder.cloudFormationOutputMap(
          outputs.stream().collect(toMap(Output::getOutputKey, Output::getOutputValue)));
    }
    createBuilder.commandExecutionStatus(SUCCESS);
    createBuilder.rollbackInfo(getRollbackInfo(cloudFormationCreateStackRequest));
    return createBuilder.build();
  }

  private CloudFormationRollbackInfo getRollbackInfo(
      CloudFormationCreateStackRequest cloudFormationCreateStackRequest) {
    CloudFormationRollbackInfoBuilder builder = CloudFormationRollbackInfo.builder();

    builder.cloudFormationRoleArn(cloudFormationCreateStackRequest.getCloudFormationRoleArn());
    if (CLOUDFORMATION_STACK_CREATE_URL.equals(cloudFormationCreateStackRequest.getCreateType())) {
      cloudFormationCreateStackRequest.setData(
          awsCFHelperServiceDelegate.normalizeS3TemplatePath(cloudFormationCreateStackRequest.getData()));
      builder.url(cloudFormationCreateStackRequest.getData());
    } else {
      // handles the case of both Git and body
      builder.body(cloudFormationCreateStackRequest.getData());
    }
    builder.region(cloudFormationCreateStackRequest.getRegion());
    builder.customStackName(cloudFormationCreateStackRequest.getCustomStackName());
    List<NameValuePair> variables = newArrayList();
    if (isNotEmpty(cloudFormationCreateStackRequest.getVariables())) {
      for (Entry<String, String> variable : cloudFormationCreateStackRequest.getVariables().entrySet()) {
        variables.add(new NameValuePair(variable.getKey(), variable.getValue(), ServiceVariableType.TEXT.name()));
      }
    }
    if (isNotEmpty(cloudFormationCreateStackRequest.getEncryptedVariables())) {
      for (Entry<String, EncryptedDataDetail> encVariable :
          cloudFormationCreateStackRequest.getEncryptedVariables().entrySet()) {
        variables.add(new NameValuePair(encVariable.getKey(), encVariable.getValue().getEncryptedData().getUuid(),
            ServiceVariableType.ENCRYPTED_TEXT.name()));
      }
    }

    if (isNotEmpty(cloudFormationCreateStackRequest.getStackStatusesToMarkAsSuccess())) {
      builder.skipBasedOnStackStatus(true);
      builder.stackStatusesToMarkAsSuccess(cloudFormationCreateStackRequest.getStackStatusesToMarkAsSuccess()
                                               .stream()
                                               .map(status -> status.name())
                                               .collect(Collectors.toList()));
    } else {
      builder.skipBasedOnStackStatus(false);
      builder.stackStatusesToMarkAsSuccess(new ArrayList<>());
    }

    builder.variables(variables);
    return builder.build();
  }

  private List<Parameter> getCfParams(CloudFormationCreateStackRequest cloudFormationCreateStackRequest) {
    List<Parameter> allParams = newArrayList();
    if (isNotEmpty(cloudFormationCreateStackRequest.getVariables())) {
      cloudFormationCreateStackRequest.getVariables().forEach(
          (key, value) -> allParams.add(new Parameter().withParameterKey(key).withParameterValue(value)));
    }
    if (isNotEmpty(cloudFormationCreateStackRequest.getEncryptedVariables())) {
      for (Map.Entry<String, EncryptedDataDetail> entry :
          cloudFormationCreateStackRequest.getEncryptedVariables().entrySet()) {
        allParams.add(
            new Parameter()
                .withParameterKey(entry.getKey())
                .withParameterValue(String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false))));
      }
    }
    return allParams;
  }
}
