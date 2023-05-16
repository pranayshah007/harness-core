/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.jcraft.jsch.JSchException;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@OwnedBy(CDP)
public class SshHelperUtils {
  public static void generateTGT(String userPrincipal, String password, String keyTabFilePath, LogCallback logCallback,
      Map<String, String> env) throws JSchException {
    if (!isValidKeyTabFile(keyTabFilePath)) {
      logCallback.saveExecutionLog("Cannot proceed with Ticket Granting Ticket(TGT) generation.", ERROR);
      log.error("Cannot proceed with Ticket Granting Ticket(TGT) generation");
      throw new JSchException(
          "Failure: Invalid keytab file path. Cannot proceed with Ticket Granting Ticket(TGT) generation");
    }
    log.info("Generating Ticket Granting Ticket(TGT)...");
    logCallback.saveExecutionLog("Generating Ticket Granting Ticket(TGT) for principal: " + userPrincipal);
    String commandString = !StringUtils.isEmpty(password) ? format("echo \"%s\" | kinit %s", password, userPrincipal)
                                                          : format("kinit -k -t %s %s", keyTabFilePath, userPrincipal);
    WinRmCommandResult winRmCommandResult;
    synchronized (SshHelperUtils.class) {
      winRmCommandResult = executeLocalCommand(commandString, logCallback, null, false, env);
    }
    if (winRmCommandResult != null && winRmCommandResult.isSuccess()) {
      logCallback.saveExecutionLog("Ticket Granting Ticket(TGT) generated successfully for " + userPrincipal);
      log.info("Ticket Granting Ticket(TGT) generated successfully for " + userPrincipal);
    } else {
      StringBuilder errorMessage = new StringBuilder("Error: ");
      if (winRmCommandResult != null && EmptyPredicate.isNotEmpty(winRmCommandResult.getErrorMessage())) {
        errorMessage.append(winRmCommandResult.getErrorMessage());
      }
      log.error("Failure: could not generate Ticket Granting Ticket(TGT). " + errorMessage);
      throw new JSchException("Failure: could not generate Ticket Granting Ticket(TGT). " + errorMessage);
    }
  }

  private static boolean isValidKeyTabFile(String keyTabFilePath) {
    if (!StringUtils.isEmpty(keyTabFilePath)) {
      if (new File(keyTabFilePath).exists()) {
        log.info("Found keytab file at path: [{}]", keyTabFilePath);
        return true;
      } else {
        log.error("Invalid keytab file path: [{}].", keyTabFilePath);
        return false;
      }
    }
    return true;
  }

  public static WinRmCommandResult executeLocalCommand(
      String cmdString, LogCallback logCallback, Writer output, boolean isOutputWriter, Map<String, String> env) {
    String[] commandList = new String[] {"/bin/bash", "-c", cmdString};
    ProcessResult processResult = null;
    StringBuilder errorMessage = new StringBuilder();
    try {
      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .command(commandList)
                                            .directory(new File(System.getProperty("user.home")))
                                            .readOutput(true)
                                            .environment(env)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                if (isOutputWriter) {
                                                  try {
                                                    output.write(line);
                                                  } catch (IOException e) {
                                                    log.error("Failed to store the output to writer ", e);
                                                  }
                                                } else {
                                                  logCallback.saveExecutionLog(line, LogLevel.INFO);
                                                }
                                              }
                                            })
                                            .redirectError(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                logCallback.saveExecutionLog(line, ERROR);
                                                errorMessage.append(line);
                                              }
                                            });

      processResult = processExecutor.execute();
    } catch (IOException | InterruptedException | TimeoutException e) {
      log.error("Failed to execute command ", e);
    }
    return processResult != null && processResult.getExitValue() == 0
        ? WinRmCommandResult.builder().success(true).build()
        : WinRmCommandResult.builder().success(false).errorMessage(errorMessage.toString()).build();
  }
}
