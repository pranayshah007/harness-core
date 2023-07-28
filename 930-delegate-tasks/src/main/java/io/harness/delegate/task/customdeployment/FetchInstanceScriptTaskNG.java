/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.customdeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class FetchInstanceScriptTaskNG extends AbstractDelegateRunnableTask {
  public static final String COMMAND_UNIT = "Fetch Instance Script";

  @Inject private ShellExecutorFactoryNG shellExecutorFactory;

  public FetchInstanceScriptTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public FetchInstanceScriptTaskNGResponse run(TaskParameters taskParameters) {
    String workingDir = null;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    LogCallback logCallback = getLogCallback(getLogStreamingTaskClient(), COMMAND_UNIT, true, commandUnitsProgress);
    try {
      FetchInstanceScriptTaskNGRequest parameters = (FetchInstanceScriptTaskNGRequest) taskParameters;
      String basePath = Paths.get("fetchInstanceScript").toAbsolutePath().toString();
      workingDir = Paths.get(basePath, parameters.getExecutionId()).toString();
      String outputPath = Paths.get(workingDir, "output.json").toString();

      logCallback.saveExecutionLog(
          "\"" + parameters.getOutputPathKey() + "\" has been initialized to \"" + outputPath + "\"", INFO);

      Map<String, String> variablesMap =
          isNotEmpty(parameters.getVariables()) ? parameters.getVariables() : new HashMap<>();
      variablesMap.put(parameters.getOutputPathKey(), outputPath);
      createNewFile(outputPath);

      ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                    .accountId(parameters.getAccountId())
                                                    .executionId(parameters.getExecutionId())
                                                    .commandUnitName(COMMAND_UNIT)
                                                    .environment(variablesMap)
                                                    .scriptType(ScriptType.BASH)
                                                    .build();
      ScriptProcessExecutor executor =
          shellExecutorFactory.getExecutor(shellExecutorConfig, getLogStreamingTaskClient(), commandUnitsProgress);
      ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(
          parameters.getScriptBody(), emptyList(), emptyList(), parameters.getTimeoutInMillis());

      String message = String.format("Execution finished with status: %s", executeCommandResponse.getStatus());
      logCallback.saveExecutionLog(message, INFO, SUCCESS);
      if (executeCommandResponse.getStatus() == CommandExecutionStatus.FAILURE) {
        return FetchInstanceScriptTaskNGResponse.builder()
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(message)
            .build();
      }

      try {
        return FetchInstanceScriptTaskNGResponse.builder()
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .commandExecutionStatus(SUCCESS)
            .output(new String(Files.readAllBytes(Paths.get(outputPath)), Charsets.UTF_8))
            .build();
      } catch (IOException e) {
        throw new InvalidRequestException("Error occurred while reading output file", e);
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Error occurred in the task", sanitizedException);
      Misc.logAllMessages(sanitizedException, logCallback);
      return FetchInstanceScriptTaskNGResponse.builder()
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    } finally {
      try {
        deleteDirectoryAndItsContentIfExists(workingDir);
      } catch (IOException e) {
        log.warn(String.format("Failed to delete working directory: %s", workingDir));
      }
    }
  }

  private File createNewFile(String path) {
    File file = new File(path);
    boolean mkdirs = file.getParentFile().mkdirs();
    if (!mkdirs && !file.getParentFile().exists()) {
      throw new InvalidRequestException(String.format("Unable to create directory for output file: %s", path));
    }
    try {
      file.createNewFile();
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred in creating output file", e);
    }
    return file;
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }
}
