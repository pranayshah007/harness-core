/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactBuildDetailsMapper {
  public ArtifactBuildDetailsNG toBuildDetailsNG(BuildDetailsInternal buildDetailsInternal) {
    return ArtifactBuildDetailsNG.builder()
        .buildUrl(buildDetailsInternal.getBuildUrl())
        .metadata(buildDetailsInternal.getMetadata())
        .number(buildDetailsInternal.getNumber())
        .uiDisplayName(buildDetailsInternal.getUiDisplayName())
        .build();
  }

  public ArtifactBuildDetailsNG toBuildDetailsNG(BuildDetails buildDetails) {
    return ArtifactBuildDetailsNG.builder()
        .buildUrl(buildDetails.getBuildUrl())
        .metadata(buildDetails.getMetadata())
        .number(buildDetails.getNumber())
        .uiDisplayName(buildDetails.getUiDisplayName())
        .build();
  }

  public ArtifactBuildDetailsNG toBuildDetailsNG(Map<String, String> labelsMap, String tag) {
    return ArtifactBuildDetailsNG.builder().number(tag).labelsMap(labelsMap).build();
  }
}
