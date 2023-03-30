/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.openshift;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;

import java.util.List;
import javax.annotation.Nonnull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
public interface OpenShiftClient {
  @Nonnull
  String generateOcCommand(
      @NotEmpty String ocBinaryPath, @NotEmpty String templateFilePath, List<String> paramsFilePaths);

  @Nonnull
  CliResponse process(@NotEmpty String ocBinaryPath, @NotEmpty String templateFilePath, List<String> paramsFilePaths,
      @NotEmpty String manifestFilesDirectoryPath, LogCallback executionLogCallback);

  @Nonnull
  CliResponse process(
      @NotEmpty String processCommand, @NotEmpty String manifestFilesDirectoryPath, LogCallback executionLogCallback);
}
