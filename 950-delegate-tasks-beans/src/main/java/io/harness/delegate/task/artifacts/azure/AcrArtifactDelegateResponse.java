/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@EqualsAndHashCode(callSuper = false)
public class AcrArtifactDelegateResponse extends ArtifactDelegateResponse {
  String subscription;
  String registry;
  /** Images in repos need to be referenced via a path */
  String repository;
  /** Tag refers to exact tag number */
  String tag;

  Map<String, String> label;

  @Builder
  public AcrArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String subscription, String registry, String repository, String tag, Map<String, String> label) {
    super(buildDetails, sourceType);
    this.subscription = subscription;
    this.registry = registry;
    this.repository = repository;
    this.tag = tag;
    this.label = label;
  }

  @Override
  public String describe() {
    String buildMetadataUrl = getBuildDetails() != null ? getBuildDetails().getBuildUrl() : null;
    String dockerPullCommand = (getBuildDetails() != null && getBuildDetails().getMetadata() != null)
        ? "\nTo pull image use: docker pull " + getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
        : null;
    return "type: " + (getSourceType() != null ? getSourceType().getDisplayName() : null)
        + "\nsubscription: " + getSubscription() + "\nregistry: " + getRegistry() + "\nrepository: " + getRepository()
        + "\ntag: " + getTag() + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : "");
  }
}
