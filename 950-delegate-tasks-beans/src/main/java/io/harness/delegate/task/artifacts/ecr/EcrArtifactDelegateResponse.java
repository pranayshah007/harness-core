/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

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
public class EcrArtifactDelegateResponse extends ArtifactDelegateResponse {
  String registryId;
  /**
   * Images in repos need to be referenced via a path
   */
  String imagePath;
  /** Tag refers to exact tag number */
  String tag;
  /** imageUrl refers to full path to image */
  String imageUrl;
  String authToken;

  Map<String, String> label;

  @Builder
  public EcrArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String registryId, String imagePath, String tag, String imageUrl, String authToken, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.registryId = registryId;
    this.imagePath = imagePath;
    this.tag = tag;
    this.imageUrl = imageUrl;
    this.authToken = authToken;
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

    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null) + "\nimagePath: " + imagePath
        + "\ntag: " + getTag() + "\nMetadata keys: " + (EmptyPredicate.isNotEmpty(metadataKeys) ? metadataKeys : "")
        + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "");
  }
}
