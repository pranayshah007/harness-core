/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import software.wings.utils.RepositoryFormat;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactDelegateResponse extends ArtifactDelegateResponse {
  String repositoryName;
  /** Images in repos need to be referenced via a path */
  String artifactPath;
  String repositoryFormat;
  /** Tag refers to exact tag number */
  String tag;

  Map<String, String> label;
  @Builder
  public ArtifactoryArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String repositoryName, String artifactPath, String repositoryFormat, String tag, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.repositoryName = repositoryName;
    this.artifactPath = artifactPath;
    this.repositoryFormat = repositoryFormat;
    this.tag = tag;
    this.label = label;
  }

  @Override
  public String describe() {
    String dockerPullCommand = (RepositoryFormat.docker.name().equals(getRepositoryFormat())
                                   && getBuildDetails() != null && getBuildDetails().getMetadata() != null)
        ? "\nTo pull image use: docker pull " + getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
        : null;
    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null) + "\nrepository: "
        + getRepositoryName() + "\nimagePath: " + getArtifactPath() + "\ntag: " + getTag() + "\nrepository type: "
        + getRepositoryFormat() + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "");
  }
}
