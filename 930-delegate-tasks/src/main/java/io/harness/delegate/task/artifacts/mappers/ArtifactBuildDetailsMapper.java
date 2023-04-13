/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactBuildDetailsMapper {
  public ArtifactBuildDetailsNG toBuildDetailsNG(BuildDetailsInternal buildDetailsInternal) {
    Map<String, String> metadata = new HashMap<>();
    if (buildDetailsInternal != null && EmptyPredicate.isNotEmpty(buildDetailsInternal.getMetadata())) {
      metadata = buildDetailsInternal.getMetadata();
    }
    metadata.put(ArtifactMetadataKeys.url, buildDetailsInternal.getBuildUrl());
    metadata.put(ArtifactMetadataKeys.artifactName, buildDetailsInternal.getBuildFullDisplayName());
    metadata.put(ArtifactMetadataKeys.artifactPath, buildDetailsInternal.getArtifactPath());
    if (buildDetailsInternal.getArtifactFileMetadataList() != null
        && EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList())
        && buildDetailsInternal.getArtifactFileMetadataList().get(0) != null) {
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getFileName())) {
        metadata.put(
            ArtifactMetadataKeys.FILE_NAME, buildDetailsInternal.getArtifactFileMetadataList().get(0).getFileName());
      }
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getImagePath())) {
        metadata.put(
            ArtifactMetadataKeys.IMAGE_PATH, buildDetailsInternal.getArtifactFileMetadataList().get(0).getImagePath());
      }
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getUrl())) {
        metadata.put(ArtifactMetadataKeys.url, buildDetailsInternal.getArtifactFileMetadataList().get(0).getUrl());
      }
    }

    return ArtifactBuildDetailsNG.builder()
        .buildUrl(buildDetailsInternal.getBuildUrl())
        .metadata(metadata)
        .number(buildDetailsInternal.getNumber())
        .uiDisplayName(buildDetailsInternal.getUiDisplayName())
        .build();
  }

  public ArtifactBuildDetailsNG toBuildDetailsNG(BuildDetailsInternal buildDetailsInternal, String sha, String shaV2) {
    Map<String, String> metadata = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getMetadata())) {
      metadata = buildDetailsInternal.getMetadata();
    }
    metadata.put(ArtifactMetadataKeys.url, buildDetailsInternal.getBuildUrl());
    metadata.put(ArtifactMetadataKeys.artifactName, buildDetailsInternal.getBuildFullDisplayName());
    metadata.put(ArtifactMetadataKeys.artifactPath, buildDetailsInternal.getArtifactPath());
    metadata.put(ArtifactMetadataKeys.SHA, sha);
    metadata.put(ArtifactMetadataKeys.SHAV2, shaV2);
    if (buildDetailsInternal.getArtifactFileMetadataList() != null
        && EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList())
        && buildDetailsInternal.getArtifactFileMetadataList().get(0) != null) {
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getFileName())) {
        metadata.put(
            ArtifactMetadataKeys.FILE_NAME, buildDetailsInternal.getArtifactFileMetadataList().get(0).getFileName());
      }
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getImagePath())) {
        metadata.put(
            ArtifactMetadataKeys.IMAGE_PATH, buildDetailsInternal.getArtifactFileMetadataList().get(0).getImagePath());
      }
      if (EmptyPredicate.isNotEmpty(buildDetailsInternal.getArtifactFileMetadataList().get(0).getUrl())) {
        metadata.put(ArtifactMetadataKeys.url, buildDetailsInternal.getArtifactFileMetadataList().get(0).getUrl());
      }
    }

    return ArtifactBuildDetailsNG.builder()
        .buildUrl(buildDetailsInternal.getBuildUrl())
        .metadata(metadata)
        .number(buildDetailsInternal.getNumber())
        .uiDisplayName(buildDetailsInternal.getUiDisplayName())
        .build();
  }

  public ArtifactBuildDetailsNG toBuildDetailsNG(BuildDetails buildDetails) {
    if (buildDetails == null) {
      return ArtifactBuildDetailsNG.builder().build();
    }
    Map<String, String> metadata = buildDetails.getMetadata() == null ? new HashMap<>() : buildDetails.getMetadata();
    if (buildDetails.getBuildFullDisplayName() != null) {
      metadata.put(ArtifactMetadataKeys.SHA, buildDetails.getBuildFullDisplayName());
      metadata.put(ArtifactMetadataKeys.SHAV2, buildDetails.getBuildFullDisplayName());
    }

    return ArtifactBuildDetailsNG.builder()
        .buildUrl(buildDetails.getBuildUrl())
        .metadata(metadata)
        .number(buildDetails.getNumber())
        .uiDisplayName(buildDetails.getUiDisplayName())
        .build();
  }

  public ArtifactBuildDetailsNG toBuildDetailsNG(Map<String, String> labelsMap, String tag) {
    return ArtifactBuildDetailsNG.builder().number(tag).labelsMap(labelsMap).build();
  }
}
