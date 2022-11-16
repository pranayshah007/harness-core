/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filesystem;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;

@OwnedBy(CDP)
public class LazyAutoCloseableWorkingDirectory implements AutoCloseable {
  private String workingDirPath;
  private String repositoryPath;
  private String rootWorkingDirPath;
  private File workingDir;

  public LazyAutoCloseableWorkingDirectory(final String repositoryPath, final String rootWorkingDirPath) {
    this.repositoryPath = repositoryPath;
    this.rootWorkingDirPath = rootWorkingDirPath;
    this.workingDirPath = FilenameUtils.concat(rootWorkingDirPath, format("%s/", UUIDGenerator.generateUuid()));
  }

  public LazyAutoCloseableWorkingDirectory createDirectory() {
    try {
      createDirectoryIfDoesNotExist(repositoryPath);
      createDirectoryIfDoesNotExist(rootWorkingDirPath);
      createDirectoryIfDoesNotExist(workingDirPath);
    } catch (IOException ex) {
      throw new InvalidRequestException(format("Unable to create directory, directoryPath: %s", workingDirPath), ex);
    }

    workingDir = new File(workingDirPath);
    return this;
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
