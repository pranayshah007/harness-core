/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.docker;

import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class DockerArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Images in repos need to be referenced via a path */
  String imagePath;
  /** Tag refers to exact tag number */
  String tag;
  /** Label refers to tag */
  Map<String, String> label;

  @Builder
  public DockerArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String imagePath, String tag, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.imagePath = imagePath;
    this.tag = tag;
    this.label = label;
  }

  @Override
  public String describe() {
    String dockerPullCommand = (getBuildDetails() != null && getBuildDetails().getMetadata() != null)
        ? "\nTo pull image use: docker pull " + getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
        : null;
    String metadataKeys = (getBuildDetails() != null && getBuildDetails().getMetadata() != null)
        ? String.valueOf(getBuildDetails().getMetadata().keySet())
        : null;
    String sha256Digest =
        (getBuildDetails() != null && getBuildDetails().getMetadata() != null
            && getBuildDetails().getMetadata().containsKey(ArtifactMetadataKeys.SHAV2)
            && EmptyPredicate.isNotEmpty(getBuildDetails().getMetadata().get(ArtifactMetadataKeys.SHAV2)))
        ? "\nV1 SHA256 Digest: " + getBuildDetails().getMetadata().get(ArtifactMetadataKeys.SHA)
            + "\nV2 SHA256 Digest: " + getBuildDetails().getMetadata().get(ArtifactMetadataKeys.SHAV2)
        : null;

    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null)
        + "\nimagePath: " + getImagePath() + "\ntag: " + getTag()
        + "\nMetadata keys: " + (EmptyPredicate.isNotEmpty(metadataKeys) ? metadataKeys : "")
        + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "")
        + (EmptyPredicate.isNotEmpty(sha256Digest) ? sha256Digest : "");
  }
}
