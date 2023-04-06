/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class EcrRequestResponseMapper {
  public EcrArtifactDelegateResponse toEcrResponse(
      BuildDetailsInternal buildDetailsInternal, EcrArtifactDelegateRequest request) {
    ArtifactBuildDetailsNG artifactBuildDetailsNG;
    ArtifactMetaInfo artifactMetaInfo = buildDetailsInternal.getArtifactMetaInfo();
    Map<String, String> label = null;
    if (artifactMetaInfo != null) {
      artifactBuildDetailsNG = ArtifactBuildDetailsMapper.toBuildDetailsNG(
          buildDetailsInternal, artifactMetaInfo.getSha(), artifactMetaInfo.getShaV2());
      label = artifactMetaInfo.getLabels();
    } else {
      artifactBuildDetailsNG = ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal);
    }
    return EcrArtifactDelegateResponse.builder()
        .buildDetails(artifactBuildDetailsNG)
        .imagePath(request.getImagePath())
        .tag(buildDetailsInternal.getNumber())
        .label(label)
        .sourceType(ArtifactSourceType.ECR)
        .build();
  }
}
