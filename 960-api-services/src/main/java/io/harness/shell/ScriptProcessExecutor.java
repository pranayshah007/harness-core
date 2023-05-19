/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.azure.model.AzureConstants.AZURE_LOGIN_CONFIG_DIR_PATH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.K8sConstants.HARNESS_KUBE_CONFIG_PATH;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.shell.ExecuteCommandResponse.ExecuteCommandResponseBuilder;
import io.harness.shell.ShellExecutionData.ShellExecutionDataBuilder;

import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullOutputStream;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stop.ProcessStopper;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Created by anubhaw on 2019-03-11.
 */
@Slf4j
public class ScriptProcessExecutor extends AbstractScriptExecutor {
  private ShellExecutorConfig config;
  private ScriptType scriptType;
  /**
   * Instantiates a new abstract ssh executor.
   * @param logCallback          the log service
   */
  @Inject
  public ScriptProcessExecutor(
      LogCallback logCallback, boolean shouldSaveExecutionLogs, ScriptExecutionContext shellExecutorConfig) {
    super(logCallback, shouldSaveExecutionLogs);
    this.config = (ShellExecutorConfig) shellExecutorConfig;
    this.scriptType = ((ShellExecutorConfig) shellExecutorConfig).getScriptType();
  }

  private static final String defaultParentWorkingDirectory = "./local-scripts/";
  static {
    try {
      createDirectoryIfDoesNotExist(defaultParentWorkingDirectory);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create local-scripts directory", e);
    }
  }

  @Override
  public CommandExecutionStatus executeCommandString(String command, StringBuffer output, boolean displayCommand) {
    CommandExecutionStatus commandExecutionStatus = FAILURE;
    File workingDirectory;
    try {
      if (isEmpty(config.getWorkingDirectory())) {
        String directoryPath = defaultParentWorkingDirectory + config.getExecutionId();
        createDirectoryIfDoesNotExist(directoryPath);
        workingDirectory = new File(directoryPath);
      } else {
        workingDirectory = new File(config.getWorkingDirectory());
      }

      String scriptFilename = "harness-" + this.config.getExecutionId() + ".sh";
      String kubeConfigFilename = "kube-" + this.config.getExecutionId() + "-config";
      File scriptFile = new File(workingDirectory, scriptFilename);
      File kubeConfigFile = new File(workingDirectory, kubeConfigFilename);

      Map<String, String> environment = config.getEnvironment();

      if (!isEmpty(config.getKubeConfigContent())) {
        try (FileOutputStream outputStream = new FileOutputStream(kubeConfigFile)) {
          outputStream.write(config.getKubeConfigContent().getBytes(StandardCharsets.UTF_8));
          environment.put(HARNESS_KUBE_CONFIG_PATH, kubeConfigFile.getCanonicalPath());
        }
      }

      Optional<Path> gcpKeyFile = createGcpKeyFileIfNeeded(workingDirectory.toPath());
      gcpKeyFile.ifPresent(updateEnvironmentWithGcpPath(environment));

      try (FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
        outputStream.write(command.getBytes(StandardCharsets.UTF_8));

        String[] commandList = new String[] {"/bin/bash", scriptFilename};
        ProcessExecutor processExecutor =
            new ProcessExecutor()
                .command(commandList)
                .directory(workingDirectory)
                .environment(environment)
                .readOutput(true)
                .redirectOutput(new LogOutputStream() {
                  @Override
                  protected void processLine(String line) {
                    saveExecutionLog(line, INFO);
                  }
                })
                .redirectOutputAlsoTo(output != null ? new StringBufferOutputStream(output) : new NullOutputStream())
                .redirectError(new LogOutputStream() {
                  @Override
                  protected void processLine(String line) {
                    saveExecutionLog(line, ERROR);
                  }
                });

        ProcessResult processResult = processExecutor.execute();
        commandExecutionStatus = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
        if (commandExecutionStatus == SUCCESS) {
          saveExecutionLog(format("Command completed with ExitCode (%d)", processResult.getExitValue()), INFO, SUCCESS);
        } else {
          saveExecutionLog(format("CommandExecution failed with exit code: (%d)", processResult.getExitValue()), ERROR);
        }
      } catch (RuntimeException | InterruptedException | TimeoutException e) {
        saveExecutionLog(format("Exception: %s", e), ERROR);
      } finally {
        if (isEmpty(config.getWorkingDirectory())) {
          deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
        } else {
          deleteFileIfExists(scriptFile.getAbsolutePath());
          deleteFileIfExists(kubeConfigFile.getAbsolutePath());
          deleteDirectoryAndItsContentIfExists(Paths.get(String.valueOf(workingDirectory), AZURE_LOGIN_CONFIG_DIR_PATH)
                                                   .normalize()
                                                   .toAbsolutePath()
                                                   .toString());
          if (gcpKeyFile.isPresent()) {
            deleteFileIfExists(gcpKeyFile.get().toAbsolutePath().toString());
          }
        }
      }
    } catch (IOException e) {
      saveExecutionLog("IOException:" + e, ERROR, FAILURE);
    }
    return commandExecutionStatus;
  }

  @SuppressWarnings("PMD.AvoidStringBufferField")
  public static class StringBufferOutputStream extends OutputStream {
    protected StringBuffer buffer;

    public StringBufferOutputStream(StringBuffer out) {
      buffer = out;
    }

    @Override
    public void write(int ch) {
      buffer.append((char) ch);
    }
  }

  @Override
  public ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect) {
    return executeCommandString(command, envVariablesToCollect, Collections.emptyList(), null);
  }

  @Override
  public ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis) {
    return executeCommandString(command, envVariablesToCollect, secretEnvVariablesToCollect, timeoutInMillis, true);
  }

  public ExecuteCommandResponse executeCommandString(String command, List<String> envVariablesToCollect,
      List<String> secretEnvVariablesToCollect, Long timeoutInMillis, boolean denoteOverallSuccess) {
    try {
      ExecuteCommandResponse executeCommandResponse = null;

      saveExecutionLog("Executing command ...", INFO);

      switch (this.scriptType) {
        case POWERSHELL:
        case BASH:
          try {
            executeCommandResponse = executeBashScript(command,
                envVariablesToCollect == null ? Collections.emptyList() : envVariablesToCollect,
                secretEnvVariablesToCollect == null ? Collections.emptyList() : secretEnvVariablesToCollect,
                timeoutInMillis, denoteOverallSuccess);
          } catch (Exception e) {
            log.error("[ScriptProcessExecutor-01] Error while executing script on delegate: ", e);
            saveExecutionLog(format("Exception: %s", e), ERROR);
          }
          break;

        default:
          unhandled(this.scriptType);
      }

      return executeCommandResponse;
    } finally {
      logCallback.dispatchLogs();
    }
  }

  private ExecuteCommandResponse executeBashScript(String command, List<String> envVariablesToCollect,
      List<String> secretVariablesToCollect, Long timeoutInMillis, boolean denoteOverallSuccess) throws IOException {
    ShellExecutionDataBuilder executionDataBuilder = ShellExecutionData.builder();
    ExecuteCommandResponseBuilder executeCommandResponseBuilder = ExecuteCommandResponse.builder();
    CommandExecutionStatus commandExecutionStatus = FAILURE;
    File workingDirectory;

    log.info("Shell script task parameters: accountId - {}, appId - {}, workingDir - {}, activityId - {}",
        config.getAccountId(), config.getAppId(), config.getWorkingDirectory(), config.getExecutionId());
    if (isEmpty(config.getWorkingDirectory())) {
      String directoryPath = defaultParentWorkingDirectory + config.getExecutionId();
      createDirectoryIfDoesNotExist(directoryPath);
      workingDirectory = new File(directoryPath);
    } else {
      workingDirectory = new File(config.getWorkingDirectory());
    }

    String scriptFilename = "harness-" + this.config.getExecutionId() + ".sh";
    String kubeConfigFilename = "kube-" + this.config.getExecutionId() + "-config";
    File scriptFile = new File(workingDirectory, scriptFilename);
    File kubeConfigFile = new File(workingDirectory, kubeConfigFilename);

    Map<String, String> environment = config.getEnvironment();

    if (!isEmpty(config.getKubeConfigContent())) {
      try (FileOutputStream outputStream = new FileOutputStream(kubeConfigFile)) {
        outputStream.write(config.getKubeConfigContent().getBytes(Charset.forName("UTF-8")));
        environment.put(HARNESS_KUBE_CONFIG_PATH, kubeConfigFile.getCanonicalPath());
      }
    }

    Optional<Path> gcpKeyFile = createGcpKeyFileIfNeeded(workingDirectory.toPath());
    gcpKeyFile.ifPresent(updateEnvironmentWithGcpPath(environment));

    String envVariablesFilename;
    File envVariablesOutputFile = null;

    // combine both variable types
    List<String> allVariablesToCollect =
        Stream.concat(envVariablesToCollect.stream(), secretVariablesToCollect.stream())
            .filter(EmptyPredicate::isNotEmpty)
            .collect(Collectors.toList());

    if (!allVariablesToCollect.isEmpty()) {
      envVariablesFilename = "harness-" + this.config.getExecutionId() + ".out";
      envVariablesOutputFile = new File(workingDirectory, envVariablesFilename);
      command = addEnvVariablesCollector(
          command, allVariablesToCollect, envVariablesOutputFile.getAbsolutePath(), this.scriptType);
    }

    if (this.scriptType == ScriptType.POWERSHELL) {
      command = processPowerShellScript(command);
    }

    Map<String, String> envVariablesMap = new HashMap<>();
    try (FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
      outputStream.write(command.getBytes(StandardCharsets.UTF_8));
      Files.setPosixFilePermissions(scriptFile.toPath(),
          newHashSet(
              PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE));
      log.info("Done setting file permissions for script {}", scriptFile);

      String[] commandList = new String[] {"/bin/bash", scriptFilename};
      ProcessStopper processStopper = new ChildProcessStopper(
          scriptFilename, workingDirectory, new ProcessExecutor().environment(environment).directory(workingDirectory));

      StringBuilder errorLog = new StringBuilder();
      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .command(commandList)
                                            .directory(workingDirectory)
                                            .environment(environment)
                                            .readOutput(true)
                                            .stopper(processStopper)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                saveExecutionLog(line, INFO);
                                              }
                                            })
                                            .redirectError(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                errorLog.append(line);
                                                errorLog.append('\n');
                                                saveExecutionLog(line, ERROR);
                                              }
                                            });

      if (timeoutInMillis != null && timeoutInMillis > 0) {
        processExecutor.timeout(timeoutInMillis, TimeUnit.MILLISECONDS);
      }

      ProcessResult processResult = processExecutor.execute();

      if (errorLog.length() > 0) {
        log.error("[ScriptProcessExecutor-03] Error output stream:\n{}", errorLog);
      }

      commandExecutionStatus = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
      if (commandExecutionStatus == SUCCESS && envVariablesOutputFile != null) {
        try (BufferedReader br = new BufferedReader(
                 new InputStreamReader(new FileInputStream(envVariablesOutputFile), StandardCharsets.UTF_8))) {
          processScriptOutputFile(envVariablesMap, br, secretVariablesToCollect);
        } catch (FileNotFoundException e) {
          log.error("[ScriptProcessExecutor-02] Error in processing script output: ", e);
          saveExecutionLog(
              "Error while reading variables to process Script Output. Avoid exiting from script early. IOException: "
                  + e,
              ERROR);
        } catch (IOException e) {
          log.error("[ScriptProcessExecutor-02] Error in processing script output: ", e);
          saveExecutionLog("IOException:" + e, ERROR);
        }
      }

      validateExportedVariables(envVariablesMap);
      executionDataBuilder.sweepingOutputEnvVariables(envVariablesMap);

      commandExecutionStatus = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
      // TODO closeLogStream function is broken
      if (commandExecutionStatus == SUCCESS && denoteOverallSuccess) {
        saveExecutionLog(format("Command completed with ExitCode (%d)", processResult.getExitValue()), INFO, SUCCESS);
      } else if (commandExecutionStatus == SUCCESS) {
        saveExecutionLog(format("Command completed with ExitCode (%d)", processResult.getExitValue()), INFO, RUNNING);
      } else {
        saveExecutionLog(format("CommandExecution failed with exit code: (%d)", processResult.getExitValue()), ERROR);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handleException(executionDataBuilder, envVariablesMap, e, "Script execution interrupted");
    } catch (TimeoutException e) {
      executionDataBuilder.expired(true);
      handleException(executionDataBuilder, envVariablesMap, e, "Script execution timed out");
    } catch (RuntimeException e) {
      handleException(
          executionDataBuilder, envVariablesMap, e, format("Exception occurred in Script execution. Reason: %s", e));
    } catch (Exception e) {
      commandExecutionStatus = FAILURE;
      handleException(
          executionDataBuilder, envVariablesMap, e, format("Exception occurred while executing Script: %s", e));
    } finally {
      if (isEmpty(config.getWorkingDirectory())) {
        deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
      } else {
        deleteFileIfExists(scriptFile.getAbsolutePath());
        deleteFileIfExists(kubeConfigFile.getAbsolutePath());
        deleteDirectoryAndItsContentIfExists(Paths.get(String.valueOf(workingDirectory), AZURE_LOGIN_CONFIG_DIR_PATH)
                                                 .normalize()
                                                 .toAbsolutePath()
                                                 .toString());
        if (envVariablesOutputFile != null) {
          deleteFileIfExists(envVariablesOutputFile.getAbsolutePath());
        }
        if (gcpKeyFile.isPresent()) {
          deleteFileIfExists(gcpKeyFile.get().toAbsolutePath().toString());
        }
      }
    }
    executeCommandResponseBuilder.status(commandExecutionStatus);
    executeCommandResponseBuilder.commandExecutionData(executionDataBuilder.build());
    return executeCommandResponseBuilder.build();
  }

  private void handleException(ShellExecutionDataBuilder executionDataBuilder, Map<String, String> envVariablesMap,
      Exception e, String message) {
    executionDataBuilder.sweepingOutputEnvVariables(envVariablesMap);
    saveExecutionLog(message, ERROR);
    log.error("Exception in script execution ", e);
  }

  private void saveExecutionLog(String line, LogLevel level) {
    saveExecutionLog(line, level, RUNNING);
  }

  private void saveExecutionLog(String line, LogLevel level, CommandExecutionStatus commandExecutionStatus) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, level, commandExecutionStatus, false);
    }
  }

  private String processPowerShellScript(String scriptString) {
    StringBuilder scriptStringBuilder =
        new StringBuilder("pwsh -ExecutionPolicy \"$ErrorActionPreference=Stop\" -Command \" & {");
    scriptStringBuilder.append(scriptString.replace("$", "\\$").replace("\"", "\\\"")).append("}\"");
    return scriptStringBuilder.toString();
  }

  @Override
  public String getAccountId() {
    return config.getAccountId();
  }

  @Override
  public String getCommandUnitName() {
    return config.getCommandUnitName();
  }

  @Override
  public String getExecutionId() {
    return config.getExecutionId();
  }

  @Override
  public String getHost() {
    return null;
  }

  private Optional<Path> createGcpKeyFileIfNeeded(Path workingDir) throws IOException {
    if (isNotEmpty(config.getGcpKeyFileContent())) {
      Path gcpKeyFile = Files.createTempFile(workingDir, "gcpKey-", ".json");
      Files.write(gcpKeyFile, new String(config.getGcpKeyFileContent()).getBytes(StandardCharsets.UTF_8));
      return Optional.of(gcpKeyFile);
    } else {
      return Optional.empty();
    }
  }

  private Consumer<Path> updateEnvironmentWithGcpPath(Map<String, String> environment) {
    return path -> environment.put("GOOGLE_APPLICATION_CREDENTIALS", path.toAbsolutePath().toString());
  }
}