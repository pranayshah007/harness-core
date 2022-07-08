/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationListStacksRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse.CloudFormationCommandExecutionResponseBuilder;
import software.wings.helpers.ext.cloudformation.response.CloudFormationListStacksResponse;
import software.wings.helpers.ext.cloudformation.response.StackSummaryInfo;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.Stack;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@Singleton
@NoArgsConstructor
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationListStacksHandler extends CloudFormationCommandTaskHandler {
  @Override
  protected CloudFormationCommandExecutionResponse executeInternal(CloudFormationCommandRequest request,
      List<EncryptedDataDetail> details, ExecutionLogCallback executionLogCallback) {
    CloudFormationListStacksRequest cloudFormationListStacksRequest = (CloudFormationListStacksRequest) request;
    CloudFormationCommandExecutionResponseBuilder builder = CloudFormationCommandExecutionResponse.builder();
    AwsInternalConfig awsInternalConfig =
        AwsConfigToInternalMapper.toAwsInternalConfig(cloudFormationListStacksRequest.getAwsConfig());
    encryptionService.decrypt(awsInternalConfig, details, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsInternalConfig, details);
    try {
      DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
      String stackId = cloudFormationListStacksRequest.getStackId();
      if (isNotEmpty(stackId)) {
        describeStacksRequest.withStackName(stackId);
      }
      executionLogCallback.saveExecutionLog("Sending list stacks call to Aws");
      List<Stack> stacks = awsHelperService.getAllStacks(request.getRegion(), describeStacksRequest, awsInternalConfig);
      executionLogCallback.saveExecutionLog("Completed list stacks call to Aws");
      List<StackSummaryInfo> summaryInfos = Collections.emptyList();
      if (isNotEmpty(stacks)) {
        summaryInfos = stacks.stream()
                           .map(stack
                               -> StackSummaryInfo.builder()
                                      .stackId(stack.getStackId())
                                      .stackName(stack.getStackName())
                                      .stackStatus(stack.getStackStatus())
                                      .stackStatusReason(stack.getStackStatusReason())
                                      .build())
                           .collect(Collectors.toList());
      }
      builder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .commandResponse(CloudFormationListStacksResponse.builder().stackSummaryInfos(summaryInfos).build());
    } catch (Exception ex) {
      String errorMessage = String.format("Exception: %s while getting stacks list: %s",
          ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)),
          cloudFormationListStacksRequest.getStackId());
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      builder.errorMessage(errorMessage).commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    return builder.build();
  }
}
