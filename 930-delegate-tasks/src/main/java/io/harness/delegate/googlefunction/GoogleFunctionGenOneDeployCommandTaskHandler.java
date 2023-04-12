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
import io.harness.delegate.task.googlefunction.GoogleFunctionGenOneCommandTaskHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionGenOneDeployRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionGenOneDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.cloud.functions.v1.CloudFunction;
import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionGenOneDeployCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionGenOneCommandTaskHelper googleFunctionGenOneCommandTaskHelper;
  private long timeoutInMillis;

  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(googleFunctionCommandRequest instanceof GoogleFunctionGenOneDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest",
          "Must be instance of "
              + "GoogleFunctionGenOneDeployRequest"));
    }
    GoogleFunctionGenOneDeployRequest googleFunctionDeployRequest =
        (GoogleFunctionGenOneDeployRequest) googleFunctionCommandRequest;

    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionDeployRequest.getGoogleFunctionInfraConfig();

    LogCallback executionLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, GoogleFunctionsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    try {
      timeoutInMillis = googleFunctionDeployRequest.getTimeoutIntervalInMin() * 60 * 1000;
      executionLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);
      CloudFunction function = googleFunctionGenOneCommandTaskHelper.deployFunction(googleFunctionInfraConfig,
          googleFunctionDeployRequest.getGoogleFunctionDeployManifestContent(),
          googleFunctionDeployRequest.getUpdateFieldMaskContent(),
          googleFunctionDeployRequest.getGoogleFunctionArtifactConfig(), timeoutInMillis, executionLogCallback, false);
      GoogleFunction googleFunction =
          googleFunctionGenOneCommandTaskHelper.getGoogleFunction(function, executionLogCallback);
      executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return GoogleFunctionGenOneDeployResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .function(googleFunction)
          .build();
    } catch (Exception exception) {
      executionLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new GoogleFunctionException(exception);
    }
  }
}
