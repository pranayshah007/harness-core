/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

@OwnedBy(CDP)
public interface KustomizeClient {
  @Nonnull
  CliResponse build(String manifestFilesDir, String kustomizeDirPath, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException;

  @Nonnull
  CliResponse buildWithPlugins(String manifestFilesDir, String kustomizeDirPath, @Nonnull String pluginPath,
      @Nonnull LogCallback callback) throws InterruptedException, TimeoutException, IOException;
}
