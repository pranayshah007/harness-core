/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Created by anubhaw on 2019-03-11.
 */

@ValidateOnExecution
@Slf4j
public abstract class AbstractScriptExecutor implements BaseScriptExecutor {
  static final String UUID = generateUuid();
  static final String HARNESS_START_TOKEN = "harness_start_token_" + UUID;
  static final String HARNESS_END_TOKEN = "harness_end_token_" + UUID;

  /**
   * The Config.
   */

  /**
   * The Log service.
   */
  protected LogCallback logCallback;
  protected boolean shouldSaveExecutionLogs;

  /**
   * Instantiates a new abstract ssh executor.
   * @param logCallback          the log service
   */
  @Inject
  public AbstractScriptExecutor(LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    this.logCallback = logCallback;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
  }

  @Override
  public abstract CommandExecutionStatus executeCommandString(
      String command, StringBuffer output, boolean displayCommand);

  @Override
  public abstract ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect);

  @Override
  public abstract ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis);

  public abstract String getAccountId();

  public abstract String getCommandUnitName();

  public abstract String getExecutionId();

  public abstract String getHost();

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
  public CommandExecutionStatus executeCommandString(
      String command, boolean displayCommand, boolean winrmScriptCommandSplit) {
    return executeCommandString(command, null, displayCommand);
  }

  @Override
  public CommandExecutionStatus executeCommandString(
      String command, boolean winrmScriptCommandSplit, StringBuffer output, boolean displayComman) {
    return executeCommandString(command, output, displayComman);
  }

  protected String addEnvVariablesCollector(
      String command, List<String> envVariablesToCollect, String envVariablesOutputFilePath, ScriptType scriptType) {
    StringBuilder wrapperCommand = new StringBuilder(command);
    wrapperCommand.append('\n');
    String redirect = ">";
    for (String env : envVariablesToCollect) {
      wrapperCommand.append("echo ")
          .append(HARNESS_START_TOKEN)
          .append(' ')
          .append(env)
          .append("=\"$")
          .append(scriptType == ScriptType.POWERSHELL ? "env:" : "")
          .append(env)
          .append("\" ")
          .append(HARNESS_END_TOKEN)
          .append(' ')
          .append(redirect)
          .append(envVariablesOutputFilePath)
          .append('\n');
      redirect = ">>";
    }
    return wrapperCommand.toString();
  }

  protected void saveExecutionLog(String line) {
    saveExecutionLog(line, RUNNING);
  }

  protected void processScriptOutputFile(@NotNull Map<String, String> envVariablesMap, @NotNull BufferedReader br,
      List<String> secretVariables) throws IOException {
    saveExecutionLog("Script Output: ");
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = br.readLine()) != null) {
      sb.append(line);
      sb.append('\n');
      if (line.endsWith(HARNESS_END_TOKEN)) {
        String envVar = sb.toString();
        envVar = StringUtils.substringBetween(envVar, HARNESS_START_TOKEN, HARNESS_END_TOKEN);
        int index = envVar.indexOf('=');
        if (index != -1) {
          String key = envVar.substring(0, index).trim();
          String value = envVar.substring(index + 1).trim();
          if (StringUtils.isNotBlank(key)) {
            envVariablesMap.put(key, value);
            if (secretVariables.contains(key)) {
              saveExecutionLog(key + "="
                  + "************");
            } else {
              saveExecutionLog(key + "=" + value);
            }
          }
          sb = new StringBuilder();
        }
      }
    }
  }

  protected void saveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, INFO, commandExecutionStatus);
    }
  }

  protected void saveExecutionLogError(String line) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, ERROR, RUNNING);
    }
  }

  protected void saveExecutionLogWarn(String line) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, WARN, RUNNING);
    }
  }

  protected void validateExportedVariables(@NotNull Map<String, String> envVariablesMap) {
    StringBuilder emptySb = new StringBuilder();
    StringBuilder hyphenSb = new StringBuilder();

    for (Map.Entry<String, String> variable : envVariablesMap.entrySet()) {
      if (isEmpty(variable.getValue())) {
        emptySb.append(variable.getKey()).append(',');
      }
      if (variable.getKey().contains("-")) {
        hyphenSb.append(variable.getKey()).append(',');
      }
    }

    if (isNotEmpty(emptySb.toString())) {
      saveExecutionLogWarn("Warning: following variables have resolved to empty values: "
          + emptySb.substring(0, emptySb.length() - 1) + "\nCheck if these are assigned correctly in the script.");
    }

    if (isNotEmpty(hyphenSb.toString())) {
      saveExecutionLogWarn("Warning: following variables have hyphens in variable values: "
          + hyphenSb.substring(0, hyphenSb.length() - 1) + "\nBash does not support hyphen(-) in variable names.");
    }
  }

  /**
   * The interface File provider.
   */
  public interface FileProvider {
    /**
     * Gets info.
     *
     * @return the info
     * @throws IOException the io exception
     */
    Pair<String, Long> getInfo() throws IOException;

    /**
     * Download to stream.
     *
     * @param outputStream the output stream
     * @throws IOException the io exception
     */
    void downloadToStream(OutputStream outputStream) throws IOException, ExecutionException;
  }

  @Override
  public LogCallback getLogCallback() {
    return this.logCallback;
  }
}
