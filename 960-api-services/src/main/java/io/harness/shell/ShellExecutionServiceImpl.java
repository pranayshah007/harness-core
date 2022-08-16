/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteFileIfExists;

import io.harness.exception.ExceptionUtils;
import io.harness.exception.ShellExecutionException;
import io.harness.shell.ShellExecutionResponse.ShellExecutionResponseBuilder;

import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
public class ShellExecutionServiceImpl implements ShellExecutionService {
  private static final String defaultParentWorkingDirectory = "./local-scripts/";
  private static final String ARTIFACT_RESULT_PATH = "HARNESS_ARTIFACT_RESULT_PATH";
  private static final Pattern pattern = Pattern.compile("harness-(.*).sh: line ([0-9]*):");

  @Override
  public ShellExecutionResponse execute(ShellExecutionRequest shellExecutionRequest) {
    ShellExecutionResponseBuilder shellExecutionResponseBuilder = ShellExecutionResponse.builder();
    File workingDirectory;

    UUID uuid = UUID.randomUUID();
    if (isEmpty(shellExecutionRequest.getWorkingDirectory())) {
      String directoryPath = defaultParentWorkingDirectory + uuid.toString();
      try {
        createDirectoryIfDoesNotExist(directoryPath);
      } catch (IOException e) {
        log.warn("Exception occurred while creating the directory {}. Returning ", directoryPath);
        shellExecutionResponseBuilder.exitValue(1);
        return shellExecutionResponseBuilder.build();
      }
      workingDirectory = new File(directoryPath);
    } else {
      workingDirectory = new File(shellExecutionRequest.getWorkingDirectory());
    }
    String scriptFilename = "harness-" + uuid.toString() + ".sh";
    File scriptFile = new File(workingDirectory, scriptFilename);

    String scriptOutputFilename = "harness-" + uuid.toString() + ".out";
    File scriptOutputFile = new File(workingDirectory, scriptOutputFilename);

    String command =
        addEnvVariablesCollector(shellExecutionRequest.getScriptString(), scriptOutputFile.getAbsolutePath());
    final String[] message = new String[1];
    Arrays.fill(message, "");
    try (FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
      outputStream.write(command.getBytes(StandardCharsets.UTF_8));
      String[] commandList = new String[] {"/bin/bash", scriptFilename};

      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .timeout(shellExecutionRequest.getTimeoutSeconds(), TimeUnit.SECONDS)
                                            .command(commandList)
                                            .directory(workingDirectory)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                if (log.isTraceEnabled()) {
                                                  log.trace("std: " + line);
                                                }
                                              }
                                            })
                                            .redirectError(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                if (message[0].equals("") && line != null) {
                                                  Matcher matcher = pattern.matcher(line);
                                                  String trimmed = matcher.replaceAll("");
                                                  message[0] = trimmed;
                                                }
                                                if (log.isTraceEnabled()) {
                                                  log.trace("err:" + line);
                                                }
                                              }
                                            });
      log.info("Executing the script ");
      ProcessResult processResult = processExecutor.execute();
      shellExecutionResponseBuilder.exitValue(processResult.getExitValue());
      if (processResult.getExitValue() == 0) {
        if (scriptOutputFile != null && scriptOutputFile.length() == 0) {
          throw new ShellExecutionException(
              "The script executed successfully but no artifact file was downloaded in the target directory ${ARTIFACT_RESULT_PATH}.");
        }
        Map<String, String> scriptData = new HashMap<>();
        scriptData.put(ARTIFACT_RESULT_PATH, scriptOutputFile.getAbsolutePath());
        shellExecutionResponseBuilder.shellExecutionData(scriptData);
        log.info("The script execution succeeded");
      } else {
        throw new ShellExecutionException("Error occurred during script execution, Reason: " + message[0]);
      }
    } catch (IOException | InterruptedException | TimeoutException e) {
      log.error("Exception in Script execution ", e);
      shellExecutionResponseBuilder.message(ExceptionUtils.getMessage(e));
      shellExecutionResponseBuilder.exitValue(1);
    } finally {
      try {
        deleteFileIfExists(scriptFile.getAbsolutePath());
      } catch (IOException e) {
        log.warn("Failed to delete file: {} ", scriptFile.getAbsolutePath(), e);
      }
    }

    return shellExecutionResponseBuilder.build();
  }

  private String addEnvVariablesCollector(String command, String scriptOutputFilePath) {
    StringBuilder wrapperCommand = new StringBuilder();
    wrapperCommand.append("export " + ARTIFACT_RESULT_PATH + "=\"" + scriptOutputFilePath + "\"\n" + command);
    return wrapperCommand.toString();
  }
}
