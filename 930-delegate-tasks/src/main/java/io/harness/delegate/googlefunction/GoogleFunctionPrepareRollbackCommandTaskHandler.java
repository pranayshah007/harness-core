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
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionPrepareRollbackResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
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
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionPrepareRollbackCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(googleFunctionCommandRequest instanceof GoogleFunctionPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest",
          "Must be instance of "
              + "GoogleFunctionPrepareRollbackRequest"));
    }
    GoogleFunctionPrepareRollbackRequest googleFunctionPrepareRollbackRequest =
        (GoogleFunctionPrepareRollbackRequest) googleFunctionCommandRequest;
    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionPrepareRollbackRequest.getGoogleFunctionInfraConfig();
    LogCallback executionLogCallback = new NGDelegateLogCallback(iLogStreamingTaskClient,
        GoogleFunctionsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);
    try {
      executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);
      CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
      googleFunctionCommandTaskHelper.parseStringContentAsClassBuilder(
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

      Optional<Function> existingFunctionOptional =
          googleFunctionCommandTaskHelper.getFunction(functionName, googleFunctionInfraConfig.getGcpConnectorDTO(),
              googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), executionLogCallback);
      if (existingFunctionOptional.isPresent()) {
        // if function exist
        executionLogCallback.saveExecutionLog(format("Fetched Function Details for function %s %n%n",
                                                  createFunctionRequestBuilder.getFunction().getName()),
            LogLevel.INFO);
        executionLogCallback.saveExecutionLog(JsonFormat.printer().print(existingFunctionOptional.get()));
        Function existingFunction = existingFunctionOptional.get();
        Optional<String> cloudRunServiceNameOptional =
            googleFunctionCommandTaskHelper.getCloudRunServiceName(existingFunction);
        if (cloudRunServiceNameOptional.isEmpty()) {
          throw NestedExceptionUtils.hintWithExplanationException("Please make sure Google Function should be 2nd Gen.",
              "Cloud Run Service doesn't exist with Cloud Function. Harness supports 2nd Gen Google Functions which "
                  + "are integrated with cloud run",
              new InvalidRequestException("Cloud Run Service doesn't exist with Cloud Function."));
        }
        executionLogCallback.saveExecutionLog(
            format("Fetching Service Details for Cloud-Run service: %s in project: %s and region: %s ..",
                googleFunctionCommandTaskHelper.getResourceName(cloudRunServiceNameOptional.get()),
                googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
            LogLevel.INFO);
        Service existingService = googleFunctionCommandTaskHelper.getCloudRunService(cloudRunServiceNameOptional.get(),
            googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
            googleFunctionInfraConfig.getRegion(), executionLogCallback);

        executionLogCallback.saveExecutionLog(
            format("Fetched Service Details for Cloud-Run service %s ..%n%n",
                googleFunctionCommandTaskHelper.getResourceName(cloudRunServiceNameOptional.get())),
            LogLevel.INFO);
        executionLogCallback.saveExecutionLog(JsonFormat.printer().print(existingService));

        if (!googleFunctionCommandTaskHelper.validateTrafficInExistingRevisions(
                existingService.getTrafficStatusesList())) {
          googleFunctionCommandTaskHelper.printExistingRevisionsTraffic(
              existingService.getTrafficStatusesList(), executionLogCallback, cloudRunServiceNameOptional.get());
          executionLogCallback.saveExecutionLog("Only one revision of Cloud-Run service should have 100% traffic"
                  + " before deployment",
              LogLevel.WARN);
          throw NestedExceptionUtils.hintWithExplanationException(
              "Please make sure that one revision cloud run service is serving full traffic. Please check execution logs"
                  + "to see present traffic split among revisions.",
              "Only one revision of cloud run service is expected to serve full traffic before new deployment.",
              new InvalidRequestException("More than one Revision of cloud run service is serving traffic."));
        }
        executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionPrepareRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .isFirstDeployment(false)
            .cloudFunctionAsString(JsonFormat.printer().print(existingFunction))
            .cloudRunServiceAsString(JsonFormat.printer().print(existingService))
            .build();
      } else {
        // if function doesn't exist
        executionLogCallback.saveExecutionLog(
            format("Function %s doesn't exist in project: %s and region: %s. "
                    + "Skipping Prepare Rollback Data..",
                createFunctionRequestBuilder.getFunction().getName(), googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion()),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return GoogleFunctionPrepareRollbackResponse.builder()
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
