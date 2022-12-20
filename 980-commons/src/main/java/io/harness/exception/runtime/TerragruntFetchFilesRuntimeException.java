/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = false)
public class TerragruntFetchFilesRuntimeException extends RuntimeException {
  String outputDirectory;

  public TerragruntFetchFilesRuntimeException(String message, String outputDirectory, Throwable cause) {
    super(message, cause);
    this.outputDirectory = outputDirectory;
  }

  public TerragruntFetchFilesRuntimeException(String message, String outputDirectory) {
    super(message);
    this.outputDirectory = outputDirectory;
  }
}
