/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.BuildExecutionSummary;
import software.wings.persistence.artifact.Artifact;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class ArtifactMetadata {
  String artifactSource;
  String buildNo;

  static List<ArtifactMetadata> fromArtifacts(List<Artifact> artifacts) {
    return MetadataUtils.map(artifacts, ArtifactMetadata::fromArtifact);
  }

  static List<ArtifactMetadata> fromBuildExecutionSummaries(List<BuildExecutionSummary> buildExecutionSummaries) {
    return MetadataUtils.map(buildExecutionSummaries, ArtifactMetadata::fromBuildExecutionSummary);
  }

  private static ArtifactMetadata fromArtifact(Artifact artifact) {
    if (artifact == null) {
      return null;
    }

    return ArtifactMetadata.builder()
        .artifactSource(artifact.getArtifactSourceName())
        .buildNo(artifact.getUiDisplayName())
        .build();
  }

  private static ArtifactMetadata fromBuildExecutionSummary(BuildExecutionSummary buildExecutionSummary) {
    if (buildExecutionSummary == null) {
      return null;
    }

    return ArtifactMetadata.builder()
        .artifactSource(buildExecutionSummary.getArtifactSource())
        .buildNo(buildExecutionSummary.getBuildName())
        .build();
  }
}
