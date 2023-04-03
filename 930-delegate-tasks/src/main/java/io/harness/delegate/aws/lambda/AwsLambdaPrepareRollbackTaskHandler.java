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
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelper;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaPrepareRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionCodeLocation;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AwsLambdaPrepareRollbackTaskHandler {
  @Inject private AwsLambdaClient awsLambdaClient;
  @Inject private AwsLambdaTaskHelper awsLambdaTaskHelper;

  private static final String LATEST = "$LATEST";

  public AwsLambdaPrepareRollbackResponse executeTaskInternal(AwsLambdaCommandRequest awsLambdaCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(awsLambdaCommandRequest instanceof AwsLambdaPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("awsLambdaCommandRequest",
          "Must be instance of "
              + "AwsLambdaPrepareRollbackRequest"));
    }

    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        (AwsLambdaPrepareRollbackRequest) awsLambdaCommandRequest;

    LogCallback executionLogCallback = awsLambdaTaskHelper.getLogCallback(iLogStreamingTaskClient,
        AwsLambdaCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);

    CreateFunctionRequest.Builder createFunctionRequestBuilder =
        awsLambdaTaskHelper.parseYamlAsObject(awsLambdaPrepareRollbackRequest.getAwsLambdaDeployManifestContent(),
            CreateFunctionRequest.serializableBuilderClass());

    CreateFunctionRequest createFunctionRequest = createFunctionRequestBuilder.build();

    String functionName = createFunctionRequest.functionName();
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder().functionName(functionName).build();

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
            format("Fetched Function Details for most recent deployed function %s %n%n",
                function.configuration().functionName()),
            LogLevel.INFO);

        // Fetch FunctionConfiguration for latest published version of function
        Optional<FunctionConfiguration> fetchLatestFuncConfigOptional =
            getLatestFunctionConfiguration(functionName, awsLambdaFunctionsInfraConfig, executionLogCallback);

        // No function configuration exists, this can be treated as a first deployment.
        if (!fetchLatestFuncConfigOptional.isPresent()) {
          executionLogCallback.saveExecutionLog(color("Skipping Prepare Rollback Step.", Green, LogWeight.Bold),
              LogLevel.INFO, CommandExecutionStatus.SUCCESS);
          return AwsLambdaPrepareRollbackResponse.builder()
              .functionName(functionName)
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .build();
        }

        FunctionConfiguration fetchLatestFuncConfig = fetchLatestFuncConfigOptional.get();

        // Print the functionConfiguration Information
        printFunctionConfigurationDetails(fetchLatestFuncConfig, executionLogCallback);

        // Fetch Code location for the identified functionConfiguration
        GetFunctionRequest getFunctionRequestForLatestFuncVersion =
            GetFunctionRequest.builder().functionName(fetchLatestFuncConfig.functionArn()).build();

        Optional<GetFunctionResponse> fetchLatestFunctionResponseForLatestFuncVersion = awsLambdaClient.getFunction(
            awsLambdaTaskHelper.getAwsInternalConfig(
                awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
            getFunctionRequestForLatestFuncVersion);

        if (fetchLatestFunctionResponseForLatestFuncVersion.isEmpty()) {
          executionLogCallback.saveExecutionLog("Function with most latest version not found. Rollback may fail");
        }

        GetFunctionResponse functionResponseLatest = fetchLatestFunctionResponseForLatestFuncVersion.get();

        ObjectMapper mapper = awsLambdaTaskHelper.getObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String functionCodeLocationAsString =
            mapper.writeValueAsString(getFunctionCodeLocationBuilder(functionResponseLatest));
        String functionConfigurationAsString =
            mapper.writeValueAsString(getFunctionConfigurationBuilder(fetchLatestFuncConfig));

        executionLogCallback.saveExecutionLog(
            color("Prepare Rollback Done.", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

        return AwsLambdaPrepareRollbackResponse.builder()
            .manifestContent(awsLambdaPrepareRollbackRequest.getAwsLambdaDeployManifestContent())
            .functionCode(functionCodeLocationAsString)
            .functionConfiguration(functionConfigurationAsString)
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
            .functionName(functionName)
            .firstDeployment(true)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      executionLogCallback.saveExecutionLog(color(format("%n Prepare Rollback Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      return AwsLambdaPrepareRollbackResponse.builder()
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .build();
    }
  }

  protected Optional<FunctionConfiguration> getLatestFunctionConfiguration(
      String functionName, AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig, LogCallback logCallback) {
    ListVersionsByFunctionRequest.Builder listVersionsByFunctionBuilder =
        ListVersionsByFunctionRequest.builder().functionName(functionName);

    ListVersionsByFunctionResponse listVersionsByFunctionResponse = null;
    List<FunctionConfiguration> versionsList = new ArrayList<>();

    do {
      if (listVersionsByFunctionResponse != null) {
        listVersionsByFunctionBuilder.marker(listVersionsByFunctionResponse.nextMarker());
      }
      listVersionsByFunctionResponse = awsLambdaClient.listVersionsByFunction(
          awsLambdaTaskHelper.getAwsInternalConfig(
              awsLambdaFunctionsInfraConfig.getAwsConnectorDTO(), awsLambdaFunctionsInfraConfig.getRegion()),
          listVersionsByFunctionBuilder.build());
      versionsList.addAll(listVersionsByFunctionResponse.versions());
    } while (listVersionsByFunctionResponse.nextMarker() != null);

    // We remove $LATEST since it is the unpublished version
    List<FunctionConfiguration> listActualFunctionVersions =
        versionsList.stream().filter(v -> !v.version().contains(LATEST)).collect(Collectors.toList());

    if (listActualFunctionVersions.isEmpty()) {
      String msg = format("No published version found for the existing function: %s", functionName);
      logCallback.saveExecutionLog(color(msg, LogColor.Red, LogWeight.Bold));
      return Optional.empty();
    }

    // Sort/Filter and get the latest published version
    return listActualFunctionVersions.stream()
        .sorted(Comparator.comparing(FunctionConfiguration::version).reversed())
        .findFirst();
  }

  private FunctionConfiguration.Builder getFunctionConfigurationBuilder(FunctionConfiguration fetchLatestFuncConfig) {
    return FunctionConfiguration.builder()
        .runtime(fetchLatestFuncConfig.runtime())
        .memorySize(fetchLatestFuncConfig.memorySize())
        .lastModified(fetchLatestFuncConfig.lastModified())
        .packageType(fetchLatestFuncConfig.packageType())
        .deadLetterConfig(fetchLatestFuncConfig.deadLetterConfig())
        .architectures(fetchLatestFuncConfig.architectures())
        .description(fetchLatestFuncConfig.description())
        .functionArn(fetchLatestFuncConfig.functionArn())
        .handler(fetchLatestFuncConfig.handler())
        .functionName(fetchLatestFuncConfig.functionName())
        .vpcConfig(fetchLatestFuncConfig.vpcConfig())
        .codeSha256(fetchLatestFuncConfig.codeSha256())
        .codeSize(fetchLatestFuncConfig.codeSize())
        .description(fetchLatestFuncConfig.description());
  }

  private FunctionCodeLocation.Builder getFunctionCodeLocationBuilder(GetFunctionResponse functionResponseLatest) {
    return FunctionCodeLocation.builder()
        .location(functionResponseLatest.code().location())
        .imageUri(functionResponseLatest.code().imageUri())
        .repositoryType(functionResponseLatest.code().repositoryType())
        .resolvedImageUri(functionResponseLatest.code().resolvedImageUri());
  }

  private void printFunctionConfigurationDetails(
      FunctionConfiguration configuration, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(format("Function Version: %s %n", configuration.version()), LogLevel.INFO);
    executionLogCallback.saveExecutionLog(format("FunctionARN: %s %n", configuration.functionArn()), LogLevel.INFO);
    executionLogCallback.saveExecutionLog(format("CodeSha256: %s %n", configuration.codeSha256()), LogLevel.INFO);
    executionLogCallback.saveExecutionLog(format("Memory Size: %s %n", configuration.memorySize()), LogLevel.INFO);
    if (configuration.runtime() != null) {
      executionLogCallback.saveExecutionLog(format("Runtime: %s %n", configuration.runtime()), LogLevel.INFO);
    }
    if (configuration.codeSize() > 0) {
      executionLogCallback.saveExecutionLog(format("CodeSize: %s %n", configuration.codeSize()), LogLevel.INFO);
    }
    if (configuration.handler() != null) {
      executionLogCallback.saveExecutionLog(format("Handler: %s %n", configuration.handler()), LogLevel.INFO);
    }
    executionLogCallback.saveExecutionLog(
        format("Architecture: %s %n%n", configuration.architectures()), LogLevel.INFO);
  }
}
