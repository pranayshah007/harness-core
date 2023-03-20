/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.openshift;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.openshift.OpenShiftConstants.COMMAND_TIMEOUT;
import static io.harness.openshift.OpenShiftConstants.OC_BINARY_PATH;
import static io.harness.openshift.OpenShiftConstants.PROCESS_COMMAND;
import static io.harness.openshift.OpenShiftConstants.TEMPLATE_FILE_PATH;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
public class OpenShiftClientImpl implements OpenShiftClient {
  @Inject CliHelper cliHelper;

  @NotNull
  @Override
  public String generateOcCommand(String ocBinaryPath, String templateFilePath, List<String> paramsFilePaths) {
    StringBuilder processCommand = new StringBuilder(
        PROCESS_COMMAND.replace(OC_BINARY_PATH, ocBinaryPath).replace(TEMPLATE_FILE_PATH, templateFilePath));

    if (isNotEmpty(paramsFilePaths)) {
      for (String paramsFilePath : paramsFilePaths) {
        processCommand.append(" --param-file ").append(paramsFilePath);
      }
    }

    return processCommand.toString();
  }

  @Override
  @Nonnull
  public CliResponse process(@NotEmpty String ocBinaryPath, @NotEmpty String templateFilePath,
      List<String> paramsFilePaths, @NotEmpty String manifestFilesDirectoryPath, LogCallback executionLogCallback) {
    return process(generateOcCommand(ocBinaryPath, templateFilePath, paramsFilePaths), manifestFilesDirectoryPath,
        executionLogCallback);
  }

  @NotNull
  @Override
  public CliResponse process(
      String processCommand, String manifestFilesDirectoryPath, LogCallback executionLogCallback) {
    try {
      return cliHelper.executeCliCommand(
          processCommand, COMMAND_TIMEOUT, Collections.emptyMap(), manifestFilesDirectoryPath, executionLogCallback);
    } catch (IOException e) {
      throw new InvalidRequestException(
          "IO Failure occurred while running oc process. " + e.getMessage(), e, WingsException.USER);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(
          "Thread interrupted while running oc process. Try again.", e, WingsException.USER);
    } catch (TimeoutException e) {
      throw new InvalidRequestException("Timed out while running oc process.", e, WingsException.USER);
    }
  }
}
