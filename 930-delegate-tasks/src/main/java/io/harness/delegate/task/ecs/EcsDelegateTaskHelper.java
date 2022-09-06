package io.harness.delegate.task.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.ecs.EcsCommandTaskNGHandler;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class EcsDelegateTaskHelper {
  @Inject private Map<String, EcsCommandTaskNGHandler> commandTaskTypeToTaskHandlerMap;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;

  public EcsCommandResponse getEcsCommandResponse(
      EcsCommandRequest ecsCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = ecsCommandRequest.getCommandUnitsProgress() != null
        ? ecsCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
    decryptRequestDTOs(ecsCommandRequest);

    EcsCommandTaskNGHandler commandTaskHandler =
        commandTaskTypeToTaskHandlerMap.get(ecsCommandRequest.getEcsCommandType().name());
    try {
      EcsCommandResponse ecsCommandResponse =
          commandTaskHandler.executeTask(ecsCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
      ecsCommandResponse.setCommandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return ecsCommandResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing ecs task [{}]",
          ecsCommandRequest.getCommandName() + ":" + ecsCommandRequest.getEcsCommandType(), sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private void decryptRequestDTOs(EcsCommandRequest ecsCommandRequest) {
    ecsInfraConfigHelper.decryptEcsInfraConfig(ecsCommandRequest.getEcsInfraConfig());
  }
}
