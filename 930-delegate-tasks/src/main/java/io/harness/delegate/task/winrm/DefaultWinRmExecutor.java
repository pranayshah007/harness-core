/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.getScriptExecutingCommand;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.winrm.WinRmHelperUtils.buildErrorDetailsFromWinRmClientException;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ResponseMessage;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ExecuteCommandResponse.ExecuteCommandResponseBuilder;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.ShellExecutionData.ShellExecutionDataBuilder;

import software.wings.core.winrm.executors.WinRmExecutor;
import software.wings.utils.ExecutionLogWriter;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class DefaultWinRmExecutor implements WinRmExecutor {
  public static final String HARNESS_FILENAME_PREFIX = "\\harness-";
  public static final String WINDOWS_TEMPFILE_LOCATION = "%TEMP%";
  public static final String NOT_IMPLEMENTED = "Not implemented";
  private static final String ERROR_WHILE_EXECUTING_COMMAND = "Error while executing command";
  public static final String POWERSHELL_NO_PROFILE = "Powershell -NoProfile ";
  public static final String POWERSHELL = "Powershell ";
  public static final String SECRET_MASK = "************";

  protected LogCallback logCallback;
  private final WinRmSessionConfig config;
  private final boolean disableCommandEncoding;
  private final boolean shouldSaveExecutionLogs;
  private final String powershell;

  public DefaultWinRmExecutor(LogCallback logCallback, boolean shouldSaveExecutionLogs, WinRmSessionConfig config,
      boolean disableCommandEncoding) {
    this.logCallback = logCallback;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
    this.config = config;
    this.disableCommandEncoding = disableCommandEncoding;

    if (this.config.isUseNoProfile()) {
      powershell = POWERSHELL_NO_PROFILE;
    } else {
      powershell = POWERSHELL;
    }
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command) {
    return executeCommandString(command, null, false);
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, boolean displayCommand) {
    return executeCommandString(command, null, displayCommand);
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, StringBuffer output) {
    return executeCommandString(command, output, false);
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, StringBuffer output, boolean displayCommand) {
    CommandExecutionStatus commandExecutionStatus;
    saveExecutionLog(format("Initializing WinRM connection to %s ...", config.getHostname()), INFO);

    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      if (displayCommand) {
        saveExecutionLog(format("Executing command %s...", command), INFO);
      } else {
        saveExecutionLog(format("Executing command ...%n"), INFO);
      }

      int exitCode;
      String psScriptFile = null;
      if (disableCommandEncoding) {
        psScriptFile = getPSScriptFile();
        exitCode = session.executeCommandsList(WinRmExecutorHelper.constructPSScriptWithCommands(
                                                   command, psScriptFile, powershell, config.getCommandParameters()),
            outputWriter, errorWriter, false, getScriptExecutingCommand(psScriptFile, powershell));
      } else {
        exitCode = session.executeCommandString(
            WinRmExecutorHelper.psWrappedCommandWithEncoding(command, powershell, config.getCommandParameters()),
            outputWriter, errorWriter, false);
      }
      commandExecutionStatus = (exitCode == 0) ? SUCCESS : FAILURE;
      saveExecutionLog(format("%nCommand completed with ExitCode (%d)", exitCode), INFO, commandExecutionStatus);
      WinRmExecutorHelper.cleanupFiles(
          session, psScriptFile, powershell, disableCommandEncoding, config.getCommandParameters());
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      commandExecutionStatus = FAILURE;
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }
    return commandExecutionStatus;
  }

  private String addEnvVariablesCollector(
      String command, List<String> envVariablesToCollect, String envVariablesOutputFileName) {
    StringBuilder wrapperCommand = new StringBuilder(command);
    wrapperCommand.append('\n');
    for (String env : envVariablesToCollect) {
      wrapperCommand.append("$e = \"")
          .append(env)
          .append("=\"\n $e+=$Env:")
          .append(env)
          .append("\n Write-Output $e | Out-File -Encoding UTF8 -append -FilePath '")
          .append(envVariablesOutputFileName)
          .append("'\n");
    }
    return wrapperCommand.toString();
  }

  /*
  Keep the temp script in working directory or in Temp is working directory is not set.
   */
  @NotNull
  private String getPSScriptFile() {
    return config.getWorkingDirectory() == null
        ? WINDOWS_TEMPFILE_LOCATION
        : config.getWorkingDirectory() + HARNESS_FILENAME_PREFIX + this.config.getExecutionId() + ".ps1";
  }

  @Override
  public ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect) {
    return executeCommandString(command, envVariablesToCollect, Collections.emptyList(), null);
  }

  @Override
  public ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis) {
    ShellExecutionDataBuilder executionDataBuilder = ShellExecutionData.builder();
    ExecuteCommandResponseBuilder executeCommandResponseBuilder = ExecuteCommandResponse.builder();
    CommandExecutionStatus commandExecutionStatus;

    saveExecutionLog(format("Initializing WinRM connection to %s ...", config.getHostname()), INFO);
    String envVariablesOutputFile = null;
    String psScriptFile = null;

    // combine both variable types
    List<String> allVariablesToCollect =
        Stream.concat(envVariablesToCollect.stream(), secretEnvVariablesToCollect.stream())
            .collect(Collectors.toList());

    if (!allVariablesToCollect.isEmpty()) {
      envVariablesOutputFile = this.config.getWorkingDirectory() + "\\"
          + "harness-" + this.config.getExecutionId() + ".out";
    }

    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(format("Executing command ...%n"), INFO);

      command = addEnvVariablesCollector(command, allVariablesToCollect, envVariablesOutputFile);
      int exitCode;
      if (disableCommandEncoding) {
        psScriptFile = getPSScriptFile();

        exitCode = session.executeCommandsList(WinRmExecutorHelper.constructPSScriptWithCommands(
                                                   command, psScriptFile, powershell, config.getCommandParameters()),
            outputWriter, errorWriter, false, getScriptExecutingCommand(psScriptFile, powershell));
      } else {
        exitCode = session.executeCommandString(
            WinRmExecutorHelper.psWrappedCommandWithEncoding(command, powershell, config.getCommandParameters()),
            outputWriter, errorWriter, false);
      }
      commandExecutionStatus = (exitCode == 0) ? SUCCESS : FAILURE;

      if (commandExecutionStatus == SUCCESS && envVariablesOutputFile != null) {
        // If we are here, we will run another command to get the output variables. Make sure we delete the previous
        // script
        WinRmExecutorHelper.cleanupFiles(
            session, psScriptFile, powershell, disableCommandEncoding, config.getCommandParameters());
        executionDataBuilder.sweepingOutputEnvVariables(
            collectOutputEnvironmentVariables(session, envVariablesOutputFile, secretEnvVariablesToCollect));
      }

      saveExecutionLog(format("%nCommand completed with ExitCode (%d)", exitCode), INFO, commandExecutionStatus);
      executeCommandResponseBuilder.status(commandExecutionStatus);
      executeCommandResponseBuilder.commandExecutionData(executionDataBuilder.build());
      WinRmExecutorHelper.cleanupFiles(
          session, psScriptFile, powershell, disableCommandEncoding, config.getCommandParameters());
      return executeCommandResponseBuilder.build();
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      commandExecutionStatus = FAILURE;
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }
    executeCommandResponseBuilder.status(commandExecutionStatus);
    executeCommandResponseBuilder.commandExecutionData(executionDataBuilder.build());
    return executeCommandResponseBuilder.build();
  }

  private Map<String, String> collectOutputEnvironmentVariables(
      WinRmSession session, String envVariablesOutputFile, List<String> secretOutputVars) {
    Map<String, String> envVariablesMap = new HashMap<>();
    String command = "$base64string = [Convert]::ToBase64String([IO.File]::ReadAllBytes('" + envVariablesOutputFile
        + "'))\n"
        + "Write-Host $base64string -NoNewline";

    // Note we are already receving file contents in UTF-8
    String commandWithoutEncoding = "$envVarString = Get-Content -Path \"" + envVariablesOutputFile
        + "\" -Encoding UTF8 -Raw; Write-Host $envVarString -NoNewline ";

    String psScriptFile = null;

    try (StringWriter outputAccumulator = new StringWriter(1024);
         StringWriter errorAccumulator = new StringWriter(1024)) {
      int exitCode;
      if (disableCommandEncoding) {
        psScriptFile = getPSScriptFile();
        exitCode = session.executeCommandsList(WinRmExecutorHelper.constructPSScriptWithCommands(commandWithoutEncoding,
                                                   psScriptFile, powershell, config.getCommandParameters()),
            outputAccumulator, errorAccumulator, true, getScriptExecutingCommand(psScriptFile, powershell));
      } else {
        exitCode = session.executeCommandString(
            WinRmExecutorHelper.psWrappedCommandWithEncoding(command, powershell, config.getCommandParameters()),
            outputAccumulator, errorAccumulator, true);
      }
      if (exitCode != 0) {
        log.error("Powershell script collecting output environment Variables failed with exitCode {}", exitCode);
        return envVariablesMap;
      }

      String fileContents;
      if (disableCommandEncoding) {
        fileContents = outputAccumulator.toString();
      } else {
        byte[] decodedBytes = Base64.getDecoder().decode(outputAccumulator.getBuffer().toString());
        // removing UTF-8 and UTF-16 BOMs if any
        fileContents = new String(decodedBytes, StandardCharsets.UTF_8).replace("\uFEFF", "").replace("\uEFBBBF", "");
      }
      String[] lines = fileContents.split("\n");
      saveExecutionLog(color("\n\nScript Output: ", White, Bold), INFO);
      for (String line : lines) {
        int index = line.indexOf('=');
        if (index != -1) {
          String key = line.substring(0, index).trim();
          String value = line.substring(index + 1).trim().replace("\r", "");
          envVariablesMap.put(key, value);
          if (secretOutputVars.contains(key)) {
            saveExecutionLog(color(key + "=" + SECRET_MASK, Gray), INFO);
          } else {
            saveExecutionLog(color(key + "=" + value, Gray), INFO);
          }
        }
      }

      WinRmExecutorHelper.cleanupFiles(
          session, envVariablesOutputFile, powershell, disableCommandEncoding, config.getCommandParameters());
      WinRmExecutorHelper.cleanupFiles(
          session, psScriptFile, powershell, disableCommandEncoding, config.getCommandParameters());
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      log.error("Exception while trying to collectOutputEnvironmentVariables", e);
    }
    return envVariablesMap;
  }

  private void saveExecutionLog(String line, LogLevel level) {
    saveExecutionLog(line, level, RUNNING);
  }

  private void saveExecutionLog(String line, LogLevel level, CommandExecutionStatus commandExecutionStatus) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, level, commandExecutionStatus);
    }
  }

  private ExecutionLogWriter getExecutionLogWriter(LogLevel logLevel) {
    return ExecutionLogWriter.builder()
        .accountId(config.getAccountId())
        .appId(config.getAppId())
        .commandUnitName(config.getCommandUnitName())
        .executionId(config.getExecutionId())
        .logCallback(logCallback)
        .stringBuilder(new StringBuilder(1024))
        .logLevel(logLevel)
        .build();
  }
}
