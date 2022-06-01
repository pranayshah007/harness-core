/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.Log.Builder.aLog;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandUnit;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.CommandUnitExecutorService;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 6/23/17.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class CodeDeployCommandUnitExecutorServiceImpl implements CommandUnitExecutorService {
  /**
   * The Log service.
   */
  @Inject private DelegateLogService logService;

  @Inject private Injector injector;

  @Override
  public CommandExecutionStatus execute(CommandUnit commandUnit, CommandExecutionContext context) {
    String activityId = context.getActivityId();
    logService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .activityId(activityId)
            .logLevel(INFO)
            .commandUnitName(commandUnit.getName())
            .logLine(format("Begin execution of command: %s", commandUnit.getName()))
            .executionResult(RUNNING)
            .build());

    CommandExecutionStatus commandExecutionStatus = FAILURE;
    injector.injectMembers(commandUnit);

    try {
      commandExecutionStatus = commandUnit.execute(context);
    } catch (Exception ex) {
      log.error("Error while executing command", ex);
      logService.save(context.getAccountId(),
          aLog()
              .appId(context.getAppId())
              .activityId(activityId)
              .logLevel(ERROR)
              .logLine("Command execution failed")
              .commandUnitName(commandUnit.getName())
              .executionResult(commandExecutionStatus)
              .executionResult(FAILURE)
              .build());
    }

    logService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .activityId(activityId)
            .logLevel(INFO)
            .logLine("Command execution finished with status " + commandExecutionStatus)
            .commandUnitName(commandUnit.getName())
            .executionResult(commandExecutionStatus)
            .build());

    commandUnit.setCommandExecutionStatus(commandExecutionStatus);
    return commandExecutionStatus;
  }
}
