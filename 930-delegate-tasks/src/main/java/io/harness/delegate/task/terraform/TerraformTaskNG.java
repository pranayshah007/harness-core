/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraform.handlers.TerraformAbstractTaskHandler;
import io.harness.exception.UnexpectedTypeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformTaskNG extends AbstractDelegateRunnableTask {
  @Inject private Map<TFTaskType, TerraformAbstractTaskHandler> tfTaskTypeToHandlerMap;

  public TerraformTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    log.info("Started executing Terraform Task NG");
    TerraformTaskNGParameters taskParameters = (TerraformTaskNGParameters) parameters;

    CommandUnitsProgress commandUnitsProgress = taskParameters.getCommandUnitsProgress() != null
        ? taskParameters.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();

    LogCallback logCallback = getLogCallback(getLogStreamingTaskClient(),
        taskParameters.getTerraformCommandUnit().name(),
        shouldOpenStream(taskParameters.getTerraformCommandUnit().name(), commandUnitsProgress), commandUnitsProgress);

    if (!tfTaskTypeToHandlerMap.containsKey(taskParameters.getTaskType())) {
      throw new UnexpectedTypeException(
          String.format("Unexpected Terraform Task Type: [%s]", taskParameters.getTaskType()));
    }

    TerraformAbstractTaskHandler taskHandler = tfTaskTypeToHandlerMap.get(taskParameters.getTaskType());
    try {
      TerraformTaskNGResponse terraformTaskNGResponse =
          taskHandler.executeTask(taskParameters, getDelegateId(), getTaskId(), logCallback);
      terraformTaskNGResponse.setUnitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return terraformTaskNGResponse;
    } catch (Exception e) {
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress),
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private boolean shouldOpenStream(String commandUnitName, CommandUnitsProgress commandUnitsProgress) {
    if (commandUnitsProgress.getCommandUnitProgressMap() == null) {
      return true;
    }

    CommandUnitProgress unitProgress = commandUnitsProgress.getCommandUnitProgressMap().get(commandUnitName);
    if (unitProgress != null) {
      return false;
    }
    return true;
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
