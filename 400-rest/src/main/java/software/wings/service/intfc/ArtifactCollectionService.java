/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Map;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface ArtifactCollectionService {
  Artifact collectArtifact(String artifactStreamId, BuildDetails buildDetails);

  void collectNewArtifactsAsync(ArtifactStream artifactStream, String permitId);

  Artifact collectNewArtifacts(String appId, ArtifactStream artifactStream, String buildNumber);

  Artifact collectNewArtifacts(
      String appId, ArtifactStream artifactStream, String buildNumber, Map<String, Object> artifactVariables);

  void collectNewArtifacts(String appId, String artifactStreamId);
}
