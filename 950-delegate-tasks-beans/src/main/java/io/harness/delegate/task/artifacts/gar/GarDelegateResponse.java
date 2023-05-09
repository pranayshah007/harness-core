/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gar;

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
public class GarDelegateResponse extends ArtifactDelegateResponse {
  String version;
  Map<String, String> label;

  @Builder
  public GarDelegateResponse(
      ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType, String version, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.version = version;
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

    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null) + "\nVersion: " + version
        + "\nMetadata keys: " + (EmptyPredicate.isNotEmpty(metadataKeys) ? metadataKeys : "")
        + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "");
  }
}
