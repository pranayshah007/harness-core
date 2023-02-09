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
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionRollbackResponse;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.run.v2.Service;
import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    GoogleFunctionRollbackRequest googleFunctionRollbackRequest =
        (GoogleFunctionRollbackRequest) googleFunctionCommandRequest;
    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionRollbackRequest.getGoogleFunctionInfraConfig();
    LogCallback executionLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, GoogleFunctionsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    try {
      executionLogCallback.saveExecutionLog(format("Starting rollback..%n%n"), LogLevel.INFO);
      if (googleFunctionRollbackRequest.isFirstDeployment()) {
        CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
        googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
            googleFunctionRollbackRequest.getGoogleFunctionDeployManifestContent(), createFunctionRequestBuilder,
            "createFunctionRequest");
        String functionName = googleFunctionCommandTaskHelper.getFunctionName(googleFunctionInfraConfig.getProject(),
            googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());
        googleFunctionCommandTaskHelper.deleteFunction(functionName, googleFunctionInfraConfig.getGcpConnectorDTO(),
            googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), executionLogCallback);
        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionRollbackResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
      } else {
        Function.Builder functionBuilder = Function.newBuilder();
        googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
            googleFunctionRollbackRequest.getGoogleFunctionAsString(), functionBuilder, "cloudFunction");
        Service.Builder serviceBuilder = Service.newBuilder();
        googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
            googleFunctionRollbackRequest.getGoogleCloudRunServiceAsString(), serviceBuilder, "cloudRunService");
        String targetRevision = googleFunctionCommandTaskHelper.getCurrentRevision(serviceBuilder.build());
        googleFunctionCommandTaskHelper.updateFullTrafficToSingleRevision(serviceBuilder.getName(), targetRevision,
            googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
            googleFunctionInfraConfig.getRegion(), executionLogCallback);
        Function function = googleFunctionCommandTaskHelper
                                .getFunction(functionBuilder.getName(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion())
                                .get();
        GoogleFunction googleFunction = googleFunctionCommandTaskHelper.getGoogleFunction(
            function, googleFunctionInfraConfig, executionLogCallback);
        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .function(googleFunction)
            .build();
      }

    } catch (Exception exception) {
      executionLogCallback.saveExecutionLog(color(format("%n Rollback Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new GoogleFunctionException(exception);
    }
  }
}
