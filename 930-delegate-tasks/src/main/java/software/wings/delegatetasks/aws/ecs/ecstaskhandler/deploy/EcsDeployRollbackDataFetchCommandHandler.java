/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.ContainerServiceData;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.ResizeCommandUnitExecutionData;
import software.wings.beans.command.ResizeCommandUnitExecutionData.ResizeCommandUnitExecutionDataBuilder;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHandler;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsDeployRollbackDataFetchRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployRollbackDataFetchCommandHandler extends EcsCommandTaskHandler {
  @Inject private EcsDeployCommandTaskHelper ecsDeployCommandTaskHelper;
  static final String DASH_STRING = "----------";

  @Override
  public EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    EcsCommandExecutionResponse executionResponse = EcsCommandExecutionResponse.builder().build();
    EcsDeployRollbackDataFetchResponse ecsServiceDeployResponse =
        ecsDeployCommandTaskHelper.getEmptyEcsDeployRollbackDataFetchResponse();
    executionResponse.setEcsCommandResponse(ecsServiceDeployResponse);

    ResizeCommandUnitExecutionDataBuilder executionDataBuilder = ResizeCommandUnitExecutionData.builder();

    try {
      EcsDeployRollbackDataFetchRequest request;
      if (ecsCommandRequest instanceof EcsDeployRollbackDataFetchRequest) {
        request = (EcsDeployRollbackDataFetchRequest) ecsCommandRequest;
      } else {
        ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
        ecsServiceDeployResponse.setOutput("Invalid request Type, expected EcsDeployRollbackFetchRequest");
        executionResponse.setErrorMessage("Invalid request Type, expected EcsDeployRollbackFetchRequest");
        return executionResponse;
      }

      EcsResizeParams resizeParams = request.getEcsResizeParams();

      final boolean deployingToHundredPercent = ecsDeployCommandTaskHelper.getDeployingToHundredPercent(resizeParams);
      ContextData contextData = ContextData.builder()
                                    .encryptedDataDetails(encryptedDataDetails)
                                    .deployingToHundredPercent(deployingToHundredPercent)
                                    .resizeParams(resizeParams)
                                    .awsConfig(request.getAwsConfig())
                                    .blueGreen(request.isBlueGreen())
                                    .build();

      List<ContainerServiceData> newInstanceDataList;
      List<ContainerServiceData> oldInstanceDataList;

      newInstanceDataList = new ArrayList<>();

      executionLogCallback.saveExecutionLog("Preparing rollback data", INFO);
      ContainerServiceData newInstanceData =
          ecsDeployCommandTaskHelper.getNewInstanceData(contextData, executionLogCallback);
      newInstanceDataList.add(newInstanceData);
      oldInstanceDataList = ecsDeployCommandTaskHelper.getOldInstanceData(contextData, newInstanceData);
      executionDataBuilder.newInstanceData(newInstanceDataList).oldInstanceData(oldInstanceDataList);
    } catch (TimeoutException ex) {
      log.error(ex.getMessage());
      log.error(ExceptionUtils.getMessage(ex), ex);
      executionLogCallback.saveExecutionLog(ex.getMessage(), ERROR);
      ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsServiceDeployResponse.setOutput(ex.getMessage());
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex), ex);
      Misc.logAllMessages(ex, executionLogCallback);
      log.error("Completed operation with errors");
      executionLogCallback.saveExecutionLog(format("Completed operation with errors%n%s%n", DASH_STRING), ERROR);
      ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsServiceDeployResponse.setOutput(ExceptionUtils.getMessage(ex));
    } finally {
      copyExecutionDataIntoResponse(executionResponse, executionDataBuilder);
    }
    return executionResponse;
  }

  private void copyExecutionDataIntoResponse(
      EcsCommandExecutionResponse executionResponse, ResizeCommandUnitExecutionDataBuilder executionDataBuilder) {
    ResizeCommandUnitExecutionData executionData = executionDataBuilder.build();
    EcsDeployRollbackDataFetchResponse ecsServiceDeployResponse =
        (EcsDeployRollbackDataFetchResponse) executionResponse.getEcsCommandResponse();
    ecsServiceDeployResponse.setNewInstanceData(executionData.getNewInstanceData());
    ecsServiceDeployResponse.setOldInstanceData(executionData.getOldInstanceData());
    executionResponse.setCommandExecutionStatus(ecsServiceDeployResponse.getCommandExecutionStatus());
    executionResponse.setErrorMessage(ecsServiceDeployResponse.getOutput());
  }
}
