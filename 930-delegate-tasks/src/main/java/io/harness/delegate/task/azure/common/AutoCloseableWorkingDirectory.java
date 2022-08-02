/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.UNIX_SEPARATOR;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;

@OwnedBy(CDP)
public class AutoCloseableWorkingDirectory implements AutoCloseable {
  private File workingDir;

  public AutoCloseableWorkingDirectory(final String repositoryPath, final String rootWorkingDirPath) {
    initialize(repositoryPath, rootWorkingDirPath);
  }

  private void initialize(final String repositoryPath, final String rootWorkingDirPath) {
    String workingDirPath = FilenameUtils.concat(rootWorkingDirPath, UUIDGenerator.generateUuid() + UNIX_SEPARATOR);
    try {
      createDirectoryIfDoesNotExist(repositoryPath);
      createDirectoryIfDoesNotExist(rootWorkingDirPath);
      createDirectoryIfDoesNotExist(workingDirPath);
    } catch (IOException ex) {
      throw new InvalidRequestException(format("Unable to create directory, directoryPath: %s", workingDirPath), ex);
    }

    workingDir = new File(workingDirPath);
  }

  @Override
  public void close() {
    if (workingDir == null) {
      return;
    }

    try {
      FileIo.deleteDirectoryAndItsContentIfExists(workingDir.getAbsolutePath());
    } catch (IOException ex) {
      throw new InvalidRequestException(
          format("Failed to delete directory, directoryAbsolutePath: %s", workingDir.getAbsolutePath()), ex);
    }
  }

  public File workingDir() {
    return this.workingDir;
  }
}
