package io.harness.delegate.googlefunction;

import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

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
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.cloud.functions.v2.Function;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class GoogleFunctionDeployCommandTaskHandler extends GoogleFunctionCommandTaskHandler {
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
  @Override
  protected GoogleFunctionCommandResponse executeTaskInternal(GoogleFunctionCommandRequest googleFunctionCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(googleFunctionCommandRequest instanceof GoogleFunctionDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("googleFunctionCommandRequest",
          "Must be instance of "
              + "GoogleFunctionCommandRequest"));
    }
    GoogleFunctionDeployRequest googleFunctionDeployRequest =
        (GoogleFunctionDeployRequest) googleFunctionCommandRequest;

    GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionDeployRequest.getGoogleFunctionInfraConfig();

    try {
      LogCallback executionLogCallback = new NGDelegateLogCallback(
          iLogStreamingTaskClient, GoogleFunctionsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
      executionLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);
      Function function = googleFunctionCommandTaskHelper.deployFunction(googleFunctionInfraConfig,
          googleFunctionDeployRequest.getGoogleFunctionDeployManifestContent(),
          googleFunctionDeployRequest.getUpdateFieldMaskContent(),
          googleFunctionDeployRequest.getGoogleFunctionArtifactConfig(), true, executionLogCallback);

      GoogleFunction googleFunction =
          googleFunctionCommandTaskHelper.getGoogleFunction(function, googleFunctionInfraConfig, executionLogCallback);
      executionLogCallback.saveExecutionLog(color("Done", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return GoogleFunctionDeployResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .function(googleFunction)
          .build();
    } catch (Exception exception) {
      throw new GoogleFunctionException(exception);
    }
  }
}
