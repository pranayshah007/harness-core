/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDP)
public interface CustomManifestService {
  void downloadCustomSource(@NotNull CustomManifestSource source, String outputDirectory, LogCallback logCallback)
      throws IOException;

  Collection<CustomSourceFile> fetchValues(@NotNull CustomManifestSource source, String workingDirectory,
      String activityId, LogCallback logCallback, boolean closeLogStream) throws IOException;

  String getWorkingDirectory() throws IOException;

  @NotNull
  String executeCustomSourceScript(String activityId, LogCallback logCallback,
      CustomManifestSource customManifestSource, boolean closeLogStream) throws IOException;

  Collection<CustomSourceFile> readFilesContent(String parentDirectory, List<String> filesPath) throws IOException;

  void cleanup(String path);
}
