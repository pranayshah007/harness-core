/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filesystem;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AutoCloseableWorkingDirectory implements AutoCloseable {
  private final String workingDirectory;

  public AutoCloseableWorkingDirectory(final String workingDirectory, int maxRetries) throws IOException {
    this.workingDirectory = workingDirectory;
    FileIo.createDirectoryIfDoesNotExist(workingDirectory);
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, maxRetries);
  }

  @Override
  public void close() {
    if (isEmpty(workingDirectory)) {
      return;
    }

    try {
      FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (IOException e) {
      log.error("Unable to delete working directory {}", workingDirectory);
    }
  }
}
