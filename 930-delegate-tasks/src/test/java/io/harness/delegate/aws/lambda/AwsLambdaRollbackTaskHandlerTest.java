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
import io.harness.delegate.exception.AwsLambdaException;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelper;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaDeployRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaRollbackResponse;
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
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class AwsLambdaRollbackTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock AwsLambdaTaskHelper awsLambdaTaskHelper;
  @InjectMocks @Spy AwsLambdaRollbackTaskCommandHandler awsLambdaRollbackTaskCommandHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionDueToInvalidRequestType() throws Exception {
    AwsLambdaDeployRequest awsLambdaDeployRequest = AwsLambdaDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaDeployRequest, logStreamingTaskClient, commandUnitsProgress);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenNotFirstDeployment() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder().firstDeployment(false).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    CreateFunctionResponse createFunctionResponse = mock(CreateFunctionResponse.class);
    doReturn(createFunctionResponse)
        .when(awsLambdaTaskHelper)
        .rollbackFunction(any(), any(), any(), any(), any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsLambdaRollbackResponse awsLambdaDeployResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeployment() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder().firstDeployment(true).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    DeleteFunctionResponse deleteFunctionResponse = mock(DeleteFunctionResponse.class);
    doReturn(deleteFunctionResponse).when(awsLambdaTaskHelper).deleteFunction(any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsLambdaRollbackResponse awsLambdaDeployResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionDuringDeploying() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder().firstDeployment(false).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    CreateFunctionResponse createFunctionResponse = mock(CreateFunctionResponse.class);
    doThrow(InvalidRequestException.class)
        .when(awsLambdaTaskHelper)
        .rollbackFunction(any(), any(), any(), any(), any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    Mockito.mockStatic(ExceptionMessageSanitizer.class);
    PowerMockito.when(ExceptionMessageSanitizer.sanitizeException(any())).thenReturn(new Exception("exception"));
    AwsLambdaRollbackResponse awsLambdaRollbackResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionWhenFunctionIsNotFoundWhileDoingRollback() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder().firstDeployment(false).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    CreateFunctionResponse createFunctionResponse = mock(CreateFunctionResponse.class);
    doThrow(AwsLambdaException.class)
        .when(awsLambdaTaskHelper)
        .rollbackFunction(any(), any(), any(), any(), any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    Mockito.mockStatic(ExceptionMessageSanitizer.class);
    PowerMockito.when(ExceptionMessageSanitizer.sanitizeException(any())).thenReturn(new Exception("exception"));
    AwsLambdaRollbackResponse awsLambdaRollbackResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionWhenFunctionIsNotFoundWhileDeleting() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder().firstDeployment(true).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doThrow(AwsLambdaException.class).when(awsLambdaTaskHelper).deleteFunction(any(), any(), any());
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(
            logStreamingTaskClient, AwsLambdaCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    Mockito.mockStatic(ExceptionMessageSanitizer.class);
    PowerMockito.when(ExceptionMessageSanitizer.sanitizeException(any())).thenReturn(new Exception("exception"));
    AwsLambdaRollbackResponse awsLambdaRollbackResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
