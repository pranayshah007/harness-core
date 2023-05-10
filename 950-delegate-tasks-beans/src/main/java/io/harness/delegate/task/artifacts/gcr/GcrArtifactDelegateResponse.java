/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gcr;

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
public class GcrArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** Images in repos need to be referenced via a path */
  String imagePath;
  /** Tag refers to exact tag number */
  String tag;

  Map<String, String> label;

  @Builder
  public GcrArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
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

    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null) + "\nimagePath: " + imagePath
        + "\ntag: " + getTag() + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "");
  }
}
