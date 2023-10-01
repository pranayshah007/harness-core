/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.cloudformation.CloudformationBaseHelper;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public abstract class CloudformationAbstractTaskHandler {
  @Inject CloudformationBaseHelper cloudformationBaseHelper;
  @Inject protected AWSCloudformationClient awsCloudformationClient;
  @Inject protected AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;

  public abstract CloudformationTaskNGResponse executeTaskInternal(
      CloudformationTaskNGParameters taskNGParameters, String delegateId, String taskId, LogCallback logCallback)
      throws IOException, TimeoutException, InterruptedException;

  public CloudformationTaskNGResponse executeTask(CloudformationTaskNGParameters taskNGParameters, String delegateId,
      String taskId, LogCallback logCallback) throws Exception {
    try {
      CloudformationTaskNGResponse response = executeTaskInternal(taskNGParameters, delegateId, taskId, logCallback);
      if (SUCCESS.equals(response.getCommandExecutionStatus())) {
        logCallback.saveExecutionLog("Execution finished successfully.", LogLevel.INFO);
      } else {
        logCallback.saveExecutionLog("Execution has been failed.", LogLevel.ERROR);
      }
      return response;
    } catch (Exception e) {
      log.error(e.getMessage());
      return CloudformationTaskNGResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(e.getMessage())
          .build();
    }
  }

  CloudformationTaskNGResponse deleteStack(CloudformationTaskNGParameters taskNGParameters, LogCallback logCallback,
      AwsInternalConfig awsInternalConfig, String stackId, String stackName) {
    try {
      long stackEventsTs = System.currentTimeMillis();
      logCallback.saveExecutionLog(
          color(String.format("# Starting to delete stack: %s", stackName), LogColor.Cyan, LogWeight.Bold));
      DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackId);
      if (EmptyPredicate.isNotEmpty(taskNGParameters.getCloudFormationRoleArn())) {
        deleteStackRequest.withRoleARN(taskNGParameters.getCloudFormationRoleArn());
      } else {
        logCallback.saveExecutionLog(
            "No specific cloudformation role provided will use the default permissions on delegate.");
      }
      cloudformationBaseHelper.deleteStack(taskNGParameters.getRegion(), awsInternalConfig, stackId,
          taskNGParameters.getCloudFormationRoleArn(), (int) taskNGParameters.getTimeoutInMs());
      cloudformationBaseHelper.waitForStackToBeDeleted(
          taskNGParameters.getRegion(), awsInternalConfig, stackId, logCallback, stackEventsTs);
    } catch (Exception e) {
      String errorMessage = String.format("Stack deletion failed: %s", e.getMessage());
      logCallback.saveExecutionLog(errorMessage, ERROR);
      return CloudformationTaskNGResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(errorMessage)
          .build();
    }
    logCallback.saveExecutionLog("Stack deleted", INFO);
    return CloudformationTaskNGResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  protected List<Parameter> getParameters(Map<String, String> parameters) {
    return parameters.entrySet()
        .stream()
        .map(stringStringEntry
            -> new Parameter()
                   .withParameterKey(stringStringEntry.getKey())
                   .withParameterValue(stringStringEntry.getValue()))
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  protected Optional<Stack> getIfStackExists(String stackName, AwsInternalConfig awsConfig, String region) {
    List<Stack> stacks = awsCloudformationClient.getAllStacks(region, new DescribeStacksRequest(), awsConfig);
    if (isEmpty(stacks)) {
      return Optional.empty();
    }
    return stacks.stream().filter(stack -> stack.getStackName().equals(stackName)).findFirst();
  }
}
