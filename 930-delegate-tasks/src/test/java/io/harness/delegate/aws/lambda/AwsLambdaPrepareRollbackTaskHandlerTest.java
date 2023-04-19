/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.aws.lambda;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.aws.v2.lambda.AwsLambdaCommandUnitConstants;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelper;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaPrepareRollbackRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCodeLocation;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class AwsLambdaPrepareRollbackTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock AwsLambdaTaskHelper awsLambdaTaskHelper;
  @Mock AwsLambdaClient awsLambdaClient;
  @InjectMocks @Spy AwsLambdaPrepareRollbackTaskHandler awsLambdaPrepareRollbackTaskHandler;

  private static final String LATEST = "$LATEST";

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionDueToInvalidRequestType() throws Exception {
    AwsLambdaRollbackRequest awsLambdaRollbackRequest = AwsLambdaRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    awsLambdaPrepareRollbackTaskHandler.executeTaskInternal(
        awsLambdaRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        AwsLambdaPrepareRollbackRequest.builder().awsLambdaInfraConfig(awsLambdaFunctionsInfraConfig).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    String functionName = "name";
    CreateFunctionRequest.Builder createFunctionBuilder = CreateFunctionRequest.builder().functionName(functionName);
    doReturn(createFunctionBuilder).when(awsLambdaTaskHelper).parseYamlAsObject(any(), any());
    AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
    doReturn(awsInternalConfig).when(awsLambdaTaskHelper).getAwsInternalConfig(any(), any());
    FunctionConfiguration functionConfiguration1 = mock(FunctionConfiguration.class);
    FunctionCodeLocation functionCodeLocation = mock(FunctionCodeLocation.class);
    GetFunctionResponse getFunctionResponse =
        GetFunctionResponse.builder().code(functionCodeLocation).configuration(functionConfiguration1).build();
    doReturn(Optional.of(getFunctionResponse)).when(awsLambdaClient).getFunction(any(), any());

    String version1 = "version1";
    String functionArn = "arn";
    String codeSha56 = "code";
    Integer memorySize = 10;
    FunctionConfiguration functionConfiguration2 = mock(FunctionConfiguration.class);
    doReturn(functionName).when(functionConfiguration1).functionName();
    doReturn(functionName).when(functionConfiguration2).functionName();
    doReturn(version1).when(functionConfiguration1).version();
    doReturn(AwsLambdaPrepareRollbackTaskHandlerTest.LATEST).when(functionConfiguration2).version();
    doReturn(functionArn).when(functionConfiguration1).functionArn();
    doReturn(functionArn).when(functionConfiguration2).functionArn();
    doReturn(codeSha56).when(functionConfiguration1).codeSha256();
    doReturn(codeSha56).when(functionConfiguration2).codeSha256();
    doReturn(memorySize).when(functionConfiguration1).memorySize();
    doReturn(memorySize).when(functionConfiguration2).memorySize();

    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder()
            .versions(Arrays.asList(functionConfiguration1, functionConfiguration2))
            .build();
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    ObjectMapper objectMapper = mock(ObjectMapper.class);
    doReturn(objectMapper).when(awsLambdaTaskHelper).getObjectMapper();
    doReturn("conf").when(objectMapper).writeValueAsString(any());

    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    AwsLambdaPrepareRollbackResponse awsLambdaDeployResponse = awsLambdaPrepareRollbackTaskHandler.executeTaskInternal(
        awsLambdaPrepareRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFunctionNotAvailable() throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        AwsLambdaPrepareRollbackRequest.builder().awsLambdaInfraConfig(awsLambdaFunctionsInfraConfig).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    String functionName = "name";
    CreateFunctionRequest.Builder createFunctionBuilder = CreateFunctionRequest.builder().functionName(functionName);
    doReturn(createFunctionBuilder).when(awsLambdaTaskHelper).parseYamlAsObject(any(), any());
    AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
    doReturn(awsInternalConfig).when(awsLambdaTaskHelper).getAwsInternalConfig(any(), any());
    FunctionConfiguration functionConfiguration1 = mock(FunctionConfiguration.class);
    FunctionCodeLocation functionCodeLocation = mock(FunctionCodeLocation.class);
    GetFunctionResponse getFunctionResponse =
        GetFunctionResponse.builder().code(functionCodeLocation).configuration(functionConfiguration1).build();
    doReturn(Optional.empty()).when(awsLambdaClient).getFunction(any(), any());

    String version1 = "version1";
    String functionArn = "arn";
    String codeSha56 = "code";
    Integer memorySize = 10;
    FunctionConfiguration functionConfiguration2 = mock(FunctionConfiguration.class);
    doReturn(functionName).when(functionConfiguration1).functionName();
    doReturn(functionName).when(functionConfiguration2).functionName();
    doReturn(version1).when(functionConfiguration1).version();
    doReturn(AwsLambdaPrepareRollbackTaskHandlerTest.LATEST).when(functionConfiguration2).version();
    doReturn(functionArn).when(functionConfiguration1).functionArn();
    doReturn(functionArn).when(functionConfiguration2).functionArn();
    doReturn(codeSha56).when(functionConfiguration1).codeSha256();
    doReturn(codeSha56).when(functionConfiguration2).codeSha256();
    doReturn(memorySize).when(functionConfiguration1).memorySize();
    doReturn(memorySize).when(functionConfiguration2).memorySize();

    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder().versions(Collections.emptyList()).build();
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    ObjectMapper objectMapper = mock(ObjectMapper.class);
    doReturn(objectMapper).when(awsLambdaTaskHelper).getObjectMapper();
    doReturn("conf").when(objectMapper).writeValueAsString(any());

    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    AwsLambdaPrepareRollbackResponse awsLambdaDeployResponse = awsLambdaPrepareRollbackTaskHandler.executeTaskInternal(
        awsLambdaPrepareRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test()
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFunctionConfigurationNotAvailable() throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        AwsLambdaPrepareRollbackRequest.builder().awsLambdaInfraConfig(awsLambdaFunctionsInfraConfig).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    String functionName = "name";
    CreateFunctionRequest.Builder createFunctionBuilder = CreateFunctionRequest.builder().functionName(functionName);
    doReturn(createFunctionBuilder).when(awsLambdaTaskHelper).parseYamlAsObject(any(), any());
    AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
    doReturn(awsInternalConfig).when(awsLambdaTaskHelper).getAwsInternalConfig(any(), any());
    FunctionConfiguration functionConfiguration1 = mock(FunctionConfiguration.class);
    FunctionCodeLocation functionCodeLocation = mock(FunctionCodeLocation.class);
    GetFunctionResponse getFunctionResponse =
        GetFunctionResponse.builder().code(functionCodeLocation).configuration(functionConfiguration1).build();
    doReturn(Optional.of(getFunctionResponse)).when(awsLambdaClient).getFunction(any(), any());

    String version1 = "version1";
    String functionArn = "arn";
    String codeSha56 = "code";
    Integer memorySize = 10;
    FunctionConfiguration functionConfiguration2 = mock(FunctionConfiguration.class);
    doReturn(functionName).when(functionConfiguration1).functionName();
    doReturn(functionName).when(functionConfiguration2).functionName();
    doReturn(version1).when(functionConfiguration1).version();
    doReturn(AwsLambdaPrepareRollbackTaskHandlerTest.LATEST).when(functionConfiguration2).version();
    doReturn(functionArn).when(functionConfiguration1).functionArn();
    doReturn(functionArn).when(functionConfiguration2).functionArn();
    doReturn(codeSha56).when(functionConfiguration1).codeSha256();
    doReturn(codeSha56).when(functionConfiguration2).codeSha256();
    doReturn(memorySize).when(functionConfiguration1).memorySize();
    doReturn(memorySize).when(functionConfiguration2).memorySize();

    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder()
            .versions(Arrays.asList(FunctionConfiguration.builder().version("$LATEST").build()))
            .build();

    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    AwsLambdaPrepareRollbackResponse awsLambdaDeployResponse = awsLambdaPrepareRollbackTaskHandler.executeTaskInternal(
        awsLambdaPrepareRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsLambdaDeployResponse.isFirstDeployment()).isFalse();
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExceptionThrown() throws Exception {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder().build();
    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        AwsLambdaPrepareRollbackRequest.builder().awsLambdaInfraConfig(awsLambdaFunctionsInfraConfig).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    String functionName = "name";
    CreateFunctionRequest.Builder createFunctionBuilder = CreateFunctionRequest.builder().functionName(functionName);
    doReturn(createFunctionBuilder).when(awsLambdaTaskHelper).parseYamlAsObject(any(), any());
    AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
    doReturn(awsInternalConfig).when(awsLambdaTaskHelper).getAwsInternalConfig(any(), any());
    FunctionConfiguration functionConfiguration1 = mock(FunctionConfiguration.class);
    FunctionCodeLocation functionCodeLocation = mock(FunctionCodeLocation.class);
    GetFunctionResponse getFunctionResponse =
        GetFunctionResponse.builder().code(functionCodeLocation).configuration(functionConfiguration1).build();
    doThrow(InvalidRequestException.class).when(awsLambdaClient).getFunction(any(), any());

    String version1 = "version1";
    String functionArn = "arn";
    String codeSha56 = "code";
    Integer memorySize = 10;
    FunctionConfiguration functionConfiguration2 = mock(FunctionConfiguration.class);
    doReturn(functionName).when(functionConfiguration1).functionName();
    doReturn(functionName).when(functionConfiguration2).functionName();
    doReturn(version1).when(functionConfiguration1).version();
    doReturn(AwsLambdaPrepareRollbackTaskHandlerTest.LATEST).when(functionConfiguration2).version();
    doReturn(functionArn).when(functionConfiguration1).functionArn();
    doReturn(functionArn).when(functionConfiguration2).functionArn();
    doReturn(codeSha56).when(functionConfiguration1).codeSha256();
    doReturn(codeSha56).when(functionConfiguration2).codeSha256();
    doReturn(memorySize).when(functionConfiguration1).memorySize();
    doReturn(memorySize).when(functionConfiguration2).memorySize();

    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder().versions(Collections.emptyList()).build();
    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    ObjectMapper objectMapper = mock(ObjectMapper.class);
    doReturn(objectMapper).when(awsLambdaTaskHelper).getObjectMapper();
    doReturn("conf").when(objectMapper).writeValueAsString(any());

    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    Mockito.mockStatic(ExceptionMessageSanitizer.class);
    PowerMockito.when(ExceptionMessageSanitizer.sanitizeException(any())).thenReturn(new Exception("exception"));

    AwsLambdaPrepareRollbackResponse awsLambdaDeployResponse = awsLambdaPrepareRollbackTaskHandler.executeTaskInternal(
        awsLambdaPrepareRollbackRequest, logStreamingTaskClient, commandUnitsProgress);
    assertThat(awsLambdaDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test()
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetLatestFunctionConfigurationReturnsEmpty() {
    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder()
            .versions(Arrays.asList(FunctionConfiguration.builder().version("$LATEST").build()))
            .build();

    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);

    Optional<FunctionConfiguration> functionConfiguration =
        awsLambdaPrepareRollbackTaskHandler.getLatestFunctionConfiguration(
            "functionName", AwsLambdaFunctionsInfraConfig.builder().build(), executionLogCallback);

    assertThat(functionConfiguration.isPresent()).isFalse();
  }

  @Test()
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetLatestFunctionConfiguration() {
    List<FunctionConfiguration> functionConfigurationList = new ArrayList<>();

    functionConfigurationList.add(FunctionConfiguration.builder().version("$LATEST").build());
    functionConfigurationList.add(FunctionConfiguration.builder().version("1").build());
    functionConfigurationList.add(FunctionConfiguration.builder().version("2").build());

    ListVersionsByFunctionResponse listVersionsByFunctionResponse =
        ListVersionsByFunctionResponse.builder().versions(functionConfigurationList).build();

    doReturn(listVersionsByFunctionResponse).when(awsLambdaClient).listVersionsByFunction(any(), any());

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(executionLogCallback)
        .when(awsLambdaTaskHelper)
        .getLogCallback(logStreamingTaskClient, AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);

    Optional<FunctionConfiguration> functionConfiguration =
        awsLambdaPrepareRollbackTaskHandler.getLatestFunctionConfiguration(
            "functionName", AwsLambdaFunctionsInfraConfig.builder().build(), executionLogCallback);

    assertThat(functionConfiguration.isPresent()).isTrue();
  }
}
