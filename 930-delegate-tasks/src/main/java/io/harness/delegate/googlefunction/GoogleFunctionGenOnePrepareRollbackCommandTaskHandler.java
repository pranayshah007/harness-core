/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.googlefunction;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.exception.GoogleFunctionException;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunction.GoogleFunctionGenOneCommandTaskHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionGenOnePrepareRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionGenOnePrepareRollbackResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionGenOnePrepareRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionGenOneCommandTaskHelper googleFunctionGenOneCommandTaskHelper;
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(googleFunctionCommandRequest instanceof GoogleFunctionGenOnePrepareRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest",
          "Must be instance of "
              + "GoogleFunctionGenOnePrepareRollbackRequest"));
    }
    GoogleFunctionGenOnePrepareRollbackRequest googleFunctionPrepareRollbackRequest =
        (GoogleFunctionGenOnePrepareRollbackRequest) googleFunctionCommandRequest;
    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionPrepareRollbackRequest.getGoogleFunctionInfraConfig();
    LogCallback executionLogCallback = new NGDelegateLogCallback(iLogStreamingTaskClient,
        GoogleFunctionsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);
    try {
      executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);
      CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
      googleFunctionGenOneCommandTaskHelper.parseStringContentAsClassBuilder(
          googleFunctionPrepareRollbackRequest.getGoogleFunctionDeployManifestContent(), createFunctionRequestBuilder,
          executionLogCallback, "createFunctionRequest");

      // get function name
      String functionName = googleFunctionCommandTaskHelper.getFunctionName(googleFunctionInfraConfig.getProject(),
          googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());

      executionLogCallback.saveExecutionLog(
          format("Fetching Function Details for function: %s in project: %s and region: %s ..",
              createFunctionRequestBuilder.getFunction().getName(), googleFunctionInfraConfig.getProject(),
              googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);

      Optional<CloudFunction> existingFunctionOptional = googleFunctionGenOneCommandTaskHelper.getFunction(
          functionName, googleFunctionInfraConfig, executionLogCallback);
      if (existingFunctionOptional.isPresent()) {
        // if function exist
        executionLogCallback.saveExecutionLog(format("Fetched Function Details for function %s %n%n",
                                                  createFunctionRequestBuilder.getFunction().getName()),
            LogLevel.INFO);
        executionLogCallback.saveExecutionLog(JsonFormat.printer().print(existingFunctionOptional.get()));
        CreateFunctionRequest.Builder existingCreateFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
        CloudFunction existingFunction = existingFunctionOptional.get();

        // set location
        createFunctionRequestBuilder.setLocation(googleFunctionCommandTaskHelper.getFunctionParent(
            googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()));
        createFunctionRequestBuilder.setFunction(existingFunction);

        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionGenOnePrepareRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .isFirstDeployment(false)
            .createFunctionRequestAsString(JsonFormat.printer().print(existingCreateFunctionRequestBuilder))
            .build();
      } else {
        // if function doesn't exist
        executionLogCallback.saveExecutionLog(
            format("Function %s doesn't exist in project: %s and region: %s. "
                    + "Skipping Prepare Rollback Data..",
                createFunctionRequestBuilder.getFunction().getName(), googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion()),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionGenOnePrepareRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .isFirstDeployment(true)
            .build();
      }
    } catch (Exception exception) {
      executionLogCallback.saveExecutionLog(color(format("%n Prepare Rollback Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new GoogleFunctionException(exception);
    }
  }
}
