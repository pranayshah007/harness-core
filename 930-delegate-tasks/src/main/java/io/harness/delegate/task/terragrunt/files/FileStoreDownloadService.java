/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.logging.LogCallback;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@OwnedBy(CDP)
public interface FileStoreDownloadService {
  int MAX_LOGGABLE_PATH_DEPTH = 4;

  DownloadResult download(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException;

  FetchFilesResult fetchFiles(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException;

  default String getLoggablePath(String basePath) {
    Path path = Paths.get(basePath);
    int maxPathDepth = Math.min(path.getNameCount(), MAX_LOGGABLE_PATH_DEPTH);
    return path.subpath(path.getNameCount() - maxPathDepth, path.getNameCount() - 1).toString();
  }
}
