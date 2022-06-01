/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.cloudformation.handlers.CloudformationAbstractTaskHandler;
import io.harness.exception.UnexpectedTypeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.lang.JoseException;

@OwnedBy(CDP)
@Slf4j
public class CloudformationTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<CloudformationTaskType, CloudformationAbstractTaskHandler> handlersMap;

  public CloudformationTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    log.info("Started executing Cloudformation Task NG");
    CloudformationTaskNGParameters taskParameters = (CloudformationTaskNGParameters) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    LogCallback logCallback = getLogCallback(
        getLogStreamingTaskClient(), taskParameters.getCfCommandUnit().name(), true, commandUnitsProgress);
    if (!handlersMap.containsKey(taskParameters.getTaskType())) {
      throw new UnexpectedTypeException(
          String.format("No handler found for task type %s", taskParameters.getTaskType()));
    }
    CloudformationAbstractTaskHandler handler = handlersMap.get(taskParameters.getTaskType());
    try {
      CloudformationTaskNGResponse response =
          handler.executeTask(taskParameters, getDelegateId(), getTaskId(), logCallback);
      if (response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
        logCallback.saveExecutionLog("Success", INFO, CommandExecutionStatus.SUCCESS);
      } else {
        logCallback.saveExecutionLog("Failure", ERROR, CommandExecutionStatus.FAILURE);
      }
      response.setUnitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return response;
    } catch (Exception e) {
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }
}
