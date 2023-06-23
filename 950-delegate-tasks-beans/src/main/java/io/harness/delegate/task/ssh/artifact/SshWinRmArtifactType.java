/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum SshWinRmArtifactType {
  ARTIFACTORY,
  JENKINS,
  CUSTOM_ARTIFACT,
  NEXUS,
  AWS_S3,
  NEXUS_PACKAGE,
  AZURE,
  ECR,
  ACR,
  GCR,
  DOCKER,
  GITHUB_PACKAGE
}
