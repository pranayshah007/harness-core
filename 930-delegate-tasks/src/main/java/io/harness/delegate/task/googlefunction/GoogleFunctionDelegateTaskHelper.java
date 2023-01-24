package io.harness.delegate.task.googlefunction;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.googlefunction.GoogleFunctionCommandTaskHandler;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionDelegateTaskHelper {
  @Inject private Map<String, GoogleFunctionCommandTaskHandler> commandTaskTypeToTaskHandlerMap;
  @Inject private GoogleFunctionInfraConfigHelper googleFunctionInfraConfigHelper;

  public GoogleFunctionCommandResponse getCommandResponse(
      GoogleFunctionCommandRequest googleFunctionCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = googleFunctionCommandRequest.getCommandUnitsProgress() != null
        ? googleFunctionCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info(
        "Starting task execution for command: {}", googleFunctionCommandRequest.getGoogleFunctionCommandType().name());
    decryptRequestDTOs(googleFunctionCommandRequest);

    GoogleFunctionCommandTaskHandler commandTaskHandler =
        commandTaskTypeToTaskHandlerMap.get(googleFunctionCommandRequest.getGoogleFunctionCommandType().name());
    try {
      GoogleFunctionCommandResponse googleFunctionCommandResponse =
          commandTaskHandler.executeTask(googleFunctionCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
      googleFunctionCommandResponse.setCommandUnitsProgress(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return googleFunctionCommandResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing google function task [{}]",
          googleFunctionCommandRequest.getCommandName() + ":"
              + googleFunctionCommandRequest.getGoogleFunctionCommandType(),
          sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private void decryptRequestDTOs(GoogleFunctionCommandRequest googleFunctionCommandRequest) {
    googleFunctionInfraConfigHelper.decryptInfraConfig(googleFunctionCommandRequest.getGoogleFunctionInfraConfig());
  }
}