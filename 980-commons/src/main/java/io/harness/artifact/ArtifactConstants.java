/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public interface ArtifactConstants {
  String ARTIFACT_REPO_BASE_DIR = "./repository/artifacts/";
  String ARTIFACT_REPO_TMP_DIR = "./repository/artifacts/tmp/";
  long ARTIFACT_FILE_SIZE_LIMIT = 4L * 1024L * 1024L * 1024L; // 4GB
  int DEFAULT_MAX_CACHED_ARTIFACT = 2;
}
