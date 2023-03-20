/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.aws.lambda;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.lambda.AwsLambdaCommandUnitConstants;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelper;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaDeployRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class AwsLambdaDeployTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock AwsLambdaTaskHelper awsLambdaTaskHelper;
  @InjectMocks @Spy AwsLambdaDeployTaskHandler awsLambdaDeployTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionDueToInvalidRequestType() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest = AwsLambdaRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    awsLambdaDeployTaskHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    AwsLambdaDeployRequest awsLambdaDeployRequest = AwsLambdaDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    CreateFunctionResponse createFunctionResponse = mock(CreateFunctionResponse.class);
    doReturn(createFunctionResponse).when(awsLambdaTaskHelper).deployFunction(any(), any(), any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    AwsLambdaDeployResponse awsLambdaDeployResponse = awsLambdaDeployTaskHandler.executeTaskInternal(
        awsLambdaDeployRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionDuringDeploying() throws Exception {
    AwsLambdaDeployRequest awsLambdaDeployRequest = AwsLambdaDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    CreateFunctionResponse createFunctionResponse = mock(CreateFunctionResponse.class);
    doThrow(InvalidRequestException.class).when(awsLambdaTaskHelper).deployFunction(any(), any(), any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    Mockito.mockStatic(ExceptionMessageSanitizer.class);
    PowerMockito.when(ExceptionMessageSanitizer.sanitizeException(any())).thenReturn(new Exception("exception"));
    AwsLambdaDeployResponse awsLambdaDeployResponse = awsLambdaDeployTaskHandler.executeTaskInternal(
        awsLambdaDeployRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
