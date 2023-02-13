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
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionTrafficShiftRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionTrafficShiftResponse;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.cloud.functions.v2.Function;
import com.google.cloud.run.v2.Service;
import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionTrafficShiftCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    GoogleFunctionTrafficShiftRequest googleFunctionTrafficShiftRequest =
        (GoogleFunctionTrafficShiftRequest) googleFunctionCommandRequest;
    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionTrafficShiftRequest.getGoogleFunctionInfraConfig();
    LogCallback executionLogCallback = new NGDelegateLogCallback(iLogStreamingTaskClient,
        GoogleFunctionsCommandUnitConstants.trafficShift.toString(), true, commandUnitsProgress);
    try {
      if (!googleFunctionTrafficShiftRequest.isFirstDeployment()) {
        executionLogCallback.saveExecutionLog(format("Starting traffic shift..%n%n"), LogLevel.INFO);
        Function.Builder functionBuilder = Function.newBuilder();
        googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
            googleFunctionTrafficShiftRequest.getGoogleFunctionAsString(), functionBuilder, executionLogCallback,
            "cloudFunction");
        Service.Builder serviceBuilder = Service.newBuilder();
        googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
            googleFunctionTrafficShiftRequest.getGoogleCloudRunServiceAsString(), serviceBuilder, executionLogCallback,
            "cloudRunService");
        String existingRevision = googleFunctionCommandTaskHelper.getCurrentRevision(serviceBuilder.build());

        googleFunctionCommandTaskHelper.updateTraffic(serviceBuilder.getName(),
            googleFunctionTrafficShiftRequest.getTargetTrafficPercent(),
            googleFunctionTrafficShiftRequest.getTargetRevision(), existingRevision,
            googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
            googleFunctionInfraConfig.getRegion(), executionLogCallback);
        Function function =
            googleFunctionCommandTaskHelper
                .getFunction(functionBuilder.getName(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), executionLogCallback)
                .get();
        GoogleFunction googleFunction = googleFunctionCommandTaskHelper.getGoogleFunction(
            function, googleFunctionInfraConfig, executionLogCallback);
        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionTrafficShiftResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .function(googleFunction)
            .build();
      }
      return GoogleFunctionTrafficShiftResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage("traffic shift not allowed with first deployment")
          .build();
    } catch (Exception exception) {
      executionLogCallback.saveExecutionLog(color(format("%n Traffic Shift Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new GoogleFunctionException(exception);
    }
  }
}
