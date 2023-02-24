/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.lambda;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.lambda.AwsLambdaClient;
import io.harness.aws.v2.lambda.AwsLambdaCommandUnitConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelper;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaPrepareRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AwsLambdaPrepareRollbackTaskHandler {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsLambdaTaskHelper awsLambdaTaskHelper;

  public AwsLambdaPrepareRollbackResponse executeTaskInternal(AwsLambdaCommandRequest awsLambdaCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(awsLambdaCommandRequest instanceof AwsLambdaPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("awsLambdaCommandRequest",
          "Must be instance of "
              + "AwsLambdaPrepareRollbackRequest"));
    }

    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        (AwsLambdaPrepareRollbackRequest) awsLambdaCommandRequest;

    LogCallback executionLogCallback = new NGDelegateLogCallback(iLogStreamingTaskClient,
        AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        awsLambdaTaskHelper.parseYamlAsObject(awsLambdaPrepareRollbackRequest.getAwsLambdaDeployManifestContent(),
            CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = (CreateFunctionRequest) createFunctionRequestBuilder.build();

    String functionName = createFunctionRequest.functionName();
    GetFunctionRequest getFunctionRequest =
        (GetFunctionRequest) GetFunctionRequest.builder().functionName(functionName).build();

    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        (AwsLambdaFunctionsInfraConfig) awsLambdaPrepareRollbackRequest.getAwsLambdaInfraConfig();

    try {
      executionLogCallback.saveExecutionLog(
          format("Fetching Function Details for function: %s", createFunctionRequest.functionName()), LogLevel.INFO);

      Optional<GetFunctionResponse> existingFunctionOptional = awsLambdaClient.getFunction(
          awsLambdaTaskHelper.getAwsInternalConfig(
              awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
          getFunctionRequest);

      if (!existingFunctionOptional.isEmpty()) {
        // if function exist
        GetFunctionResponse function = existingFunctionOptional.get();
        executionLogCallback.saveExecutionLog(
            format("Fetched Function Details for function %s %n%n", function.configuration().functionName()),
            LogLevel.INFO);

        executionLogCallback.saveExecutionLog(
            format("Fetched Function Configuration:%s %n%n", function.configuration()), LogLevel.INFO);

        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return AwsLambdaPrepareRollbackResponse.builder()
            .manifestContent(awsLambdaPrepareRollbackRequest.getAwsLambdaDeployManifestContent())
            .functionName(functionName)
            .firstDeployment(false)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
      } else {
        // if function doesn't exist
        executionLogCallback.saveExecutionLog(format("Function %s doesn't exist. Skipping Prepare Rollback Data...",
            createFunctionRequest.functionName(), LogLevel.INFO));
        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return AwsLambdaPrepareRollbackResponse.builder()
            .manifestContent(awsLambdaPrepareRollbackRequest.getAwsLambdaDeployManifestContent())
            .firstDeployment(true)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
      }
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(color(format("%n Prepare Rollback Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException(e.getMessage());
    }
  }
}
