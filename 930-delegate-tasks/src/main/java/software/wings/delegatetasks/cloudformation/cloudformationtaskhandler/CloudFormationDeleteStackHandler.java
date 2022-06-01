/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cloudformation.cloudformationtaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse.CloudFormationCommandExecutionResponseBuilder;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.services.cloudformation.model.Stack;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;

@Singleton
@NoArgsConstructor
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class CloudFormationDeleteStackHandler extends CloudFormationCommandTaskHandler {
  @Override
  protected CloudFormationCommandExecutionResponse executeInternal(CloudFormationCommandRequest request,
      List<EncryptedDataDetail> details, ExecutionLogCallback executionLogCallback) {
    CloudFormationDeleteStackRequest cloudFormationDeleteStackRequest = (CloudFormationDeleteStackRequest) request;
    CloudFormationCommandExecutionResponseBuilder builder = CloudFormationCommandExecutionResponse.builder();
    AwsConfig awsConfig = cloudFormationDeleteStackRequest.getAwsConfig();
    encryptionService.decrypt(awsConfig, details, false);
    Optional<Stack> existingStack = getIfStackExists(cloudFormationDeleteStackRequest.getCustomStackName(),
        cloudFormationDeleteStackRequest.getStackNameSuffix(),
        AwsConfigToInternalMapper.toAwsInternalConfig(cloudFormationDeleteStackRequest.getAwsConfig()),
        request.getRegion());
    String stackId;
    String stackName;
    if (existingStack.isPresent()) {
      stackId = existingStack.get().getStackId();
      stackName = existingStack.get().getStackName();
    } else {
      String message = "# No stack found. Returning";
      executionLogCallback.saveExecutionLog(message);
      builder.errorMessage(message).commandExecutionStatus(CommandExecutionStatus.SUCCESS);
      return builder.build();
    }

    return deleteStack(stackId, stackName, request, executionLogCallback);
  }
}
