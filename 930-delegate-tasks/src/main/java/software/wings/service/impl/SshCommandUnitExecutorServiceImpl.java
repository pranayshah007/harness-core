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

import static software.wings.beans.dto.Log.Builder.aLog;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionManager;
import io.harness.shell.ssh.SshClientManager;

import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ShellCommandExecutionContext;
import software.wings.beans.dto.Log.Builder;
import software.wings.beans.infrastructure.Host;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.core.ssh.executors.FileBasedScriptExecutor;
import software.wings.core.ssh.executors.SshExecutorFactory;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.CommandUnitExecutorService;
import software.wings.utils.SshHelperUtils;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class SshCommandUnitExecutorServiceImpl.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class SshCommandUnitExecutorServiceImpl implements CommandUnitExecutorService {
  /**
   * The Log service.
   */
  @Inject private DelegateLogService logService;

  @Inject private TimeLimiter timeLimiter;

  @Inject private Injector injector;

  @Inject private SshExecutorFactory sshExecutorFactory;

  @Inject private ShellExecutorFactory shellExecutorFactory;

  @Override
  public void cleanup(String activityId, Host host) {
    SshSessionManager.evictAndDisconnectCachedSession(activityId, host.getHostName());
    SshClientManager.evictCacheAndDisconnect(activityId, host.getHostName());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CommandExecutionStatus execute(CommandUnit commandUnit, CommandExecutionContext context) {
    String activityId = context.getActivityId();

    final Builder logBuilder =
        aLog().appId(context.getAppId()).activityId(activityId).commandUnitName(commandUnit.getName());

    logService.save(context.getAccountId(),
        logBuilder.logLevel(INFO)
            .logLine(format("Begin execution of command: %s", commandUnit.getName()))
            .executionResult(RUNNING)
            .build());

    CommandExecutionStatus commandExecutionStatus = FAILURE;
    BaseScriptExecutor executor;
    FileBasedScriptExecutor fileBasedScriptExecutor;
    if (context.isExecuteOnDelegate()) {
      ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                    .accountId(context.getAccountId())
                                                    .appId(context.getAppId())
                                                    .executionId(context.getActivityId())
                                                    .commandUnitName(commandUnit.getName())
                                                    .environment(context.getEnvVariables())
                                                    .build();
      executor = shellExecutorFactory.getExecutor(shellExecutorConfig);
      fileBasedScriptExecutor = shellExecutorFactory.getFileBasedExecutor(shellExecutorConfig);
    } else {
      SshSessionConfig sshSessionConfig = SshHelperUtils.createSshSessionConfig(commandUnit.getName(), context);
      executor = sshExecutorFactory.getExecutor(sshSessionConfig);
      fileBasedScriptExecutor = sshExecutorFactory.getFileBasedExecutor(sshSessionConfig);
    }

    ShellCommandExecutionContext shellCommandExecutionContext = new ShellCommandExecutionContext(context);
    shellCommandExecutionContext.setExecutor(executor);
    shellCommandExecutionContext.setFileBasedScriptExecutor(fileBasedScriptExecutor);

    injector.injectMembers(commandUnit);

    try {
      long timeoutMs = context.getTimeout() == null ? TimeUnit.MINUTES.toMillis(10) : context.getTimeout().longValue();
      commandExecutionStatus = HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMillis(timeoutMs), () -> commandUnit.execute(shellCommandExecutionContext));
    } catch (InterruptedException | TimeoutException | UncheckedTimeoutException e) {
      logService.save(context.getAccountId(),
          logBuilder.logLevel(ERROR)
              .logLine("Command execution timed out")
              .executionResult(commandExecutionStatus)
              .build());
      throw new WingsException(ErrorCode.SOCKET_CONNECTION_TIMEOUT);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof WingsException) {
        WingsException ex = (WingsException) e.getCause();
        String errorMessage = ExceptionUtils.getMessage(ex);
        logService.save(context.getAccountId(),
            logBuilder.logLevel(ERROR).logLine(errorMessage).executionResult(commandExecutionStatus).build());
        throw(WingsException) e.getCause();
      } else {
        logService.save(context.getAccountId(),
            logBuilder.logLevel(ERROR)
                .logLine("Unknown Error " + e.getCause().getMessage())
                .executionResult(commandExecutionStatus)
                .build());

        throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
      }
    } catch (WingsException e) {
      final List<ResponseMessage> messageList = ExceptionLogger.getResponseMessageList(e, REST_API);
      if (!messageList.isEmpty()) {
        if (messageList.get(0).getCode() == ErrorCode.INVALID_KEY
            || messageList.get(0).getCode() == ErrorCode.INVALID_CREDENTIAL) {
          logService.save(context.getAccountId(),
              logBuilder.logLevel(ERROR)
                  .logLine("Command execution failed: invalid key")
                  .executionResult(commandExecutionStatus)
                  .build());
          throw e;
        }
        logService.save(context.getAccountId(),
            logBuilder.logLevel(ERROR)
                .logLine("Command execution failed: Reason:" + messageList.get(0).getMessage())
                .executionResult(commandExecutionStatus)
                .build());
        throw e;
      } else {
        logService.save(context.getAccountId(),
            logBuilder.logLevel(ERROR)
                .logLine("Command execution failed. Reason:" + e.getMessage())
                .executionResult(commandExecutionStatus)
                .build());
        throw e;
      }
    } catch (Exception e) {
      logService.save(context.getAccountId(),
          logBuilder.logLevel(ERROR)
              .logLine("Command execution failed. Reason: " + e.getMessage())
              .executionResult(commandExecutionStatus)
              .build());
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }

    logService.save(context.getAccountId(),
        logBuilder.logLevel(SUCCESS == commandExecutionStatus ? INFO : ERROR)
            .logLine("Command execution finished with status " + commandExecutionStatus)
            .executionResult(commandExecutionStatus)
            .build());

    commandUnit.setCommandExecutionStatus(commandExecutionStatus);
    return commandExecutionStatus;
  }
}
