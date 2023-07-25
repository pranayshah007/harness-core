/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.constructPSScriptWithCommands;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.executablePSFilePath;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.getEncodedScriptFile;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.getScriptExecutingCommand;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.prepareCommandForCopyingToRemoteFile;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.splitCommandForCopyingToRemoteFile;
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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
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
  private static final String NEWLINE_MARK = "__NL";
  private static final String NEWLINE_SPLIT = NEWLINE_MARK + "\r\n";

  protected LogCallback logCallback;
  private final WinRmSessionConfig config;
  private final boolean disableCommandEncoding;
  private final boolean winrmScriptCommandSplit;
  private final boolean shouldSaveExecutionLogs;
  private final String powershell;

  public DefaultWinRmExecutor(LogCallback logCallback, boolean shouldSaveExecutionLogs, WinRmSessionConfig config,
      boolean disableCommandEncoding, boolean winrmScriptCommandSplit) {
    this.logCallback = logCallback;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
    this.config = config;
    this.disableCommandEncoding = disableCommandEncoding;
    this.winrmScriptCommandSplit = winrmScriptCommandSplit;

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
  public CommandExecutionStatus executeCommandString(
      String command, boolean displayCommand, boolean winrmScriptCommandSplit) {
    return executeCommandString(command, winrmScriptCommandSplit, null, displayCommand);
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, StringBuffer output) {
    return executeCommandString(command, output, false);
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, StringBuffer output, boolean displayCommand) {
    return executeCommandString(command, winrmScriptCommandSplit, output, displayCommand);
  }

  @Override
  public CommandExecutionStatus executeCommandString(
      String command, boolean winrmScriptCommandSplit, StringBuffer output, boolean displayCommand) {
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

      String psScriptFile = getPSScriptFile();
      int exitCode =
          executeCommand(command, session, outputWriter, errorWriter, psScriptFile, winrmScriptCommandSplit, false);
      commandExecutionStatus = getCommandExecutionStatus(exitCode);
      saveExecutionLog(format("%nCommand completed with ExitCode (%d)", exitCode), INFO, commandExecutionStatus);
      WinRmExecutorHelper.cleanupFiles(
          session, psScriptFile, powershell, disableCommandEncoding, config.getCommandParameters());
    } catch (RuntimeException re) {
      commandExecutionStatus = FAILURE;
      log.error(ERROR_WHILE_EXECUTING_COMMAND, re);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(re);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
      throw re;
    } catch (Exception e) {
      commandExecutionStatus = FAILURE;
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    } finally {
      logCallback.dispatchLogs();
    }
    return commandExecutionStatus;
  }

  private int executeCommand(String command, WinRmSession session, Writer outputWriter, Writer errorWriter,
      String psScriptFile, boolean winrmScriptCommandSplit, boolean isOutputWriter) throws IOException {
    int exitCode;
    if (disableCommandEncoding) {
      if (winrmScriptCommandSplit) {
        String encodedScriptFilePath = getEncodedScriptFile(config.getWorkingDirectory(), config.getExecutionId());
        exitCode = splitAndExecute(command, session, outputWriter, errorWriter, psScriptFile, encodedScriptFilePath);
      } else {
        List<List<String>> commandList =
            constructPSScriptWithCommands(command, psScriptFile, powershell, config.getCommandParameters());
        exitCode = session.executeCommandsList(commandList, outputWriter, errorWriter, isOutputWriter,
            getScriptExecutingCommand(psScriptFile, powershell));
      }
    } else {
      exitCode = session.executeCommandString(
          WinRmExecutorHelper.psWrappedCommandWithEncoding(command, powershell, config.getCommandParameters()),
          outputWriter, errorWriter, isOutputWriter);
    }
    return exitCode;
  }

  @NotNull
  private CommandExecutionStatus getCommandExecutionStatus(int exitCode) {
    return (exitCode == 0) ? SUCCESS : FAILURE;
  }

  @VisibleForTesting
  String addEnvVariablesCollector(
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
          .append("'\n Write-Output \"")
          .append(NEWLINE_MARK)
          .append("\" | Out-File -Encoding UTF8 -append -FilePath '")
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
    secretEnvVariablesToCollect =
        secretEnvVariablesToCollect == null ? Collections.emptyList() : secretEnvVariablesToCollect;
    ExecuteCommandResponseBuilder executeCommandResponseBuilder = ExecuteCommandResponse.builder();
    CommandExecutionStatus commandExecutionStatus;

    saveExecutionLog(format("Initializing WinRM connection to %s ...", config.getHostname()), INFO);
    String envVariablesOutputFile = null;

    // combine both variable types
    List<String> allVariablesToCollect =
        Stream.concat(envVariablesToCollect.stream(), secretEnvVariablesToCollect.stream())
            .filter(EmptyPredicate::isNotEmpty)
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
      String psScriptFile = getPSScriptFile();
      int exitCode =
          executeCommand(command, session, outputWriter, errorWriter, psScriptFile, winrmScriptCommandSplit, false);
      commandExecutionStatus = getCommandExecutionStatus(exitCode);

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
    } finally {
      logCallback.dispatchLogs();
    }
    executeCommandResponseBuilder.status(commandExecutionStatus);
    executeCommandResponseBuilder.commandExecutionData(executionDataBuilder.build());
    return executeCommandResponseBuilder.build();
  }

  private Map<String, String> collectOutputEnvironmentVariables(
      WinRmSession session, String envVariablesOutputFile, List<String> secretOutputVars) {
    Map<String, String> envVariablesMap = new HashMap<>();
    String command;
    if (disableCommandEncoding) {
      // Note we are already receving file contents in UTF-8
      command = "$envVarString = Get-Content -Path \"" + envVariablesOutputFile
          + "\" -Encoding UTF8 -Raw; Write-Host $envVarString -NoNewline ";
    } else {
      command = "$base64string = [Convert]::ToBase64String([IO.File]::ReadAllBytes('" + envVariablesOutputFile + "'))\n"
          + "Write-Host $base64string -NoNewline";
    }

    String psScriptFile;
    try (StringWriter outputAccumulator = new StringWriter(1024);
         StringWriter errorAccumulator = new StringWriter(1024)) {
      psScriptFile = getPSScriptFile();
      int exitCode = executeCommand(command, session, outputAccumulator, errorAccumulator, psScriptFile, false, true);
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
      String[] lines = fileContents.split(NEWLINE_SPLIT);
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

  private int splitAndExecute(String command, WinRmSession session, Writer outputWriter, Writer errorWriter,
      String psScriptFile, String encodedScriptFilePath) throws IOException {
    int exitCode;
    List<String> commandList =
        splitCommandForCopyingToRemoteFile(command, encodedScriptFilePath, powershell, config.getCommandParameters());
    exitCode = session.copyScriptToRemote(commandList, outputWriter, errorWriter);
    if (exitCode != 0) {
      log.error("Transferring encoded script data to remote file FAILED.");
      return exitCode;
    }

    String executablePSFilePath = executablePSFilePath(config.getWorkingDirectory(), config.getExecutionId());
    String psExecutionCommand = prepareCommandForCopyingToRemoteFile(
        encodedScriptFilePath, psScriptFile, powershell, config.getCommandParameters(), executablePSFilePath);
    exitCode = session.executeCommandString(psExecutionCommand, outputWriter, errorWriter, false);
    if (exitCode != 0) {
      log.error("Transferring script for decoding failed.");
      return exitCode;
    }

    String scriptFileExecutingCommand = getScriptExecutingCommand(psScriptFile, powershell);
    exitCode = session.executeScript(scriptFileExecutingCommand, outputWriter, errorWriter);
    if (exitCode != 0) {
      log.error("Decoding of the script on remote failed.");
      return exitCode;
    }

    String executeScript = getScriptExecutingCommand(executablePSFilePath, powershell);
    exitCode = session.executeScript(executeScript, outputWriter, errorWriter);

    WinRmExecutorHelper.cleanupFiles(
        session, executablePSFilePath, powershell, disableCommandEncoding, config.getCommandParameters());
    return exitCode;
  }

  @Override
  public LogCallback getLogCallback() {
    return logCallback;
  }
}
