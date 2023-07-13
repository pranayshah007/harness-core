/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class GcrRequestResponseMapper {
  public GcrInternalConfig toGcrInternalConfig(GcrArtifactDelegateRequest request, String basicAuthHeader) {
    return GcrInternalConfig.builder()
        .registryHostname(request.getRegistryHostname())
        .basicAuthHeader(basicAuthHeader)
        .build();
  }

  public GcrArtifactDelegateResponse toGcrResponse(
      BuildDetailsInternal buildDetailsInternal, GcrArtifactDelegateRequest request) {
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
    return GcrArtifactDelegateResponse.builder()
        .buildDetails(artifactBuildDetailsNG)
        .imagePath(request.getImagePath())
        .tag(buildDetailsInternal.getNumber())
        .sourceType(ArtifactSourceType.GCR)
        .label(label)
        .build();
  }

  public List<GcrArtifactDelegateResponse> toGcrResponse(
      List<Map<String, String>> labelsList, GcrArtifactDelegateRequest request) {
    return IntStream.range(0, request.getTagsList().size())
        .mapToObj(i
            -> GcrArtifactDelegateResponse.builder()
                   .buildDetails(
                       ArtifactBuildDetailsMapper.toBuildDetailsNG(labelsList.get(i), request.getTagsList().get(i)))
                   .imagePath(request.getImagePath())
                   .sourceType(ArtifactSourceType.GCR)
                   .build())
        .collect(Collectors.toList());
  }
}
