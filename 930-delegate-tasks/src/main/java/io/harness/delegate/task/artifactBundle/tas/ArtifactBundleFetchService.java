/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.task.artifactBundle.ArtifactBundleConfig;
import io.harness.delegate.task.artifactBundle.TasArtifactBundleConfig;
import io.harness.logging.LogCallback;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
@OwnedBy(CDP)
public interface ArtifactBundleFetchService {
  File downloadArtifactFile(ArtifactBundleConfig tasArtifactConfig, LogCallback executionLogCallback)
      throws IOException;

  Map<String, List<FileData>> getManifestFilesFromArtifactBundle(
      File artifactBundleFile, ArtifactBundleConfig artifactBundleConfig, String activityId, LogCallback logCallback);
}
