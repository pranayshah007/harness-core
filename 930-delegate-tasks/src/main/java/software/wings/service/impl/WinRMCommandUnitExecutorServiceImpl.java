/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.ReportTarget.REST_API;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.Log.Builder.aLog;
import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ShellExecutionException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.ExceptionLogger;

import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.InitPowerShellCommandUnit;
import software.wings.beans.command.ShellCommandExecutionContext;
import software.wings.core.ssh.executors.FileBasedWinRmExecutor;
import software.wings.core.winrm.executors.WinRmExecutor;
import software.wings.core.winrm.executors.WinRmExecutorFactory;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.CommandUnitExecutorService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.validation.executable.ValidateOnExecution;
import javax.xml.ws.soap.SOAPFaultException;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class WinRMCommandUnitExecutorServiceImpl implements CommandUnitExecutorService {
  @Inject private DelegateLogService logService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private Injector injector;
  @Inject private WinRmExecutorFactory winRmExecutorFactory;

  @Override
  public CommandExecutionStatus execute(CommandUnit commandUnit, CommandExecutionContext context) {
    String activityId = context.getActivityId();
    String publicDns = context.getHost().getPublicDns();
    logService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .hostName(publicDns)
            .activityId(activityId)
            .logLevel(INFO)
            .commandUnitName(commandUnit.getName())
            .logLine(format("Begin execution of command: %s", commandUnit.getName()))
            .executionResult(RUNNING)
            .build());

    CommandExecutionStatus commandExecutionStatus = FAILURE;

    String commandPath = getCommandPath(commandUnit, context);

    WinRmSessionConfig winRmSessionConfig = context.winrmSessionConfig(commandUnit.getName(), commandPath);
    WinRmExecutor winRmExecutor =
        winRmExecutorFactory.getExecutor(winRmSessionConfig, context.isDisableWinRMCommandEncodingFFSet());
    FileBasedWinRmExecutor fileBasedWinRmExecutor = winRmExecutorFactory.getFiledBasedWinRmExecutor(
        winRmSessionConfig, context.isDisableWinRMCommandEncodingFFSet());

    ShellCommandExecutionContext shellCommandExecutionContext = new ShellCommandExecutionContext(context);
    shellCommandExecutionContext.setExecutor(winRmExecutor);
    shellCommandExecutionContext.setFileBasedScriptExecutor(fileBasedWinRmExecutor);

    injector.injectMembers(commandUnit);

    try {
      long timeoutMs = context.getTimeout() == null ? TimeUnit.MINUTES.toMillis(10) : context.getTimeout().longValue();
      commandExecutionStatus = HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMillis(timeoutMs), () -> commandUnit.execute(shellCommandExecutionContext));
    } catch (InterruptedException | TimeoutException | UncheckedTimeoutException e) {
      logService.save(context.getAccountId(),
          aLog()
              .appId(context.getAppId())
              .activityId(activityId)
              .hostName(publicDns)
              .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
              .logLine("Command execution timed out")
              .commandUnitName(commandUnit.getName())
              .executionResult(commandExecutionStatus)
              .build());
      throw new WingsException(ErrorCode.SOCKET_CONNECTION_TIMEOUT);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof WingsException) {
        WingsException ex = (WingsException) e.getCause();
        String errorMessage = ExceptionUtils.getMessage(ex);
        logService.save(context.getAccountId(),
            aLog()
                .appId(context.getAppId())
                .activityId(activityId)
                .hostName(publicDns)
                .commandUnitName(commandUnit.getName())
                .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
                .logLine(errorMessage)
                .executionResult(commandExecutionStatus)
                .build());
        throw(WingsException) e.getCause();
      } else {
        logService.save(context.getAccountId(),
            aLog()
                .appId(context.getAppId())
                .activityId(activityId)
                .hostName(publicDns)
                .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
                .logLine("Unknown Error " + e.getCause().getMessage())
                .commandUnitName(commandUnit.getName())
                .executionResult(commandExecutionStatus)
                .build());

        throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
      }
    } catch (WingsException exception) {
      final List<ResponseMessage> messageList = ExceptionLogger.getResponseMessageList(exception, REST_API);
      if (!messageList.isEmpty()) {
        if (messageList.get(0).getCode() == ErrorCode.INVALID_KEY
            || messageList.get(0).getCode() == ErrorCode.INVALID_CREDENTIAL) {
          logService.save(context.getAccountId(),
              aLog()
                  .appId(context.getAppId())
                  .activityId(activityId)
                  .hostName(publicDns)
                  .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
                  .logLine("Command execution failed: invalid key")
                  .commandUnitName(commandUnit.getName())
                  .executionResult(commandExecutionStatus)
                  .build());
          throw exception;
        }
      } else {
        log.error("Error while executing command", exception);
        logService.save(context.getAccountId(),
            aLog()
                .appId(context.getAppId())
                .activityId(activityId)
                .hostName(publicDns)
                .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
                .logLine("Command execution failed")
                .commandUnitName(commandUnit.getName())
                .executionResult(commandExecutionStatus)
                .build());
        throw new WingsException(ErrorCode.UNKNOWN_ERROR, exception);
      }
    } catch (Exception e) {
      Exception ex = ExceptionUtils.cause(SOAPFaultException.class, e);
      if (ex != null) {
        String errorMessage = ExceptionUtils.getMessage(ex);
        logService.save(context.getAccountId(),
            aLog()
                .appId(context.getAppId())
                .activityId(activityId)
                .hostName(publicDns)
                .commandUnitName(commandUnit.getName())
                .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
                .logLine(errorMessage)
                .executionResult(commandExecutionStatus)
                .build());
        throw new ShellExecutionException("Script Execution Failed", e);
      }
      ex = ExceptionUtils.cause(SocketException.class, e);
      if (ex != null) {
        String errorMessage = ExceptionUtils.getMessage(ex);
        logService.save(context.getAccountId(),
            aLog()
                .appId(context.getAppId())
                .activityId(activityId)
                .hostName(publicDns)
                .commandUnitName(commandUnit.getName())
                .logLevel(ERROR)
                .logLine(errorMessage)
                .executionResult(commandExecutionStatus)
                .build());
        throw new ShellExecutionException("Unable to connect to remote host", e);
      }
      logService.save(context.getAccountId(),
          aLog()
              .appId(context.getAppId())
              .activityId(activityId)
              .hostName(publicDns)
              .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
              .logLine("Command execution failed")
              .commandUnitName(commandUnit.getName())
              .executionResult(commandExecutionStatus)
              .build());
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }

    logService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .activityId(activityId)
            .hostName(publicDns)
            .logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
            .logLine("Command execution finished with status " + commandExecutionStatus)
            .commandUnitName(commandUnit.getName())
            .executionResult(commandExecutionStatus)
            .build());

    commandUnit.setCommandExecutionStatus(commandExecutionStatus);
    return commandExecutionStatus;
  }

  @VisibleForTesting
  String getCommandPath(CommandUnit commandUnit, CommandExecutionContext context) {
    String commandPath =
        (commandUnit instanceof InitPowerShellCommandUnit) ? WINDOWS_HOME_DIR : context.getWindowsRuntimePath();

    if (commandUnit.getCommandUnitType() == CommandUnitType.EXEC
        || commandUnit.getCommandUnitType() == CommandUnitType.SETUP_ENV) {
      if (commandUnit instanceof ExecCommandUnit && ((ExecCommandUnit) commandUnit).getCommandPath() != null) {
        commandPath = ((ExecCommandUnit) commandUnit).getCommandPath();
      }
    }
    return commandPath;
  }
}
