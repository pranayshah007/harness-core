/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateResponse;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class GoogleCloudSourceRequestResponseMapper {
  public GoogleCloudSourceArtifactDelegateResponse toGoogleCloudSourceResponse(
      GoogleCloudSourceArtifactDelegateRequest request) {
    return GoogleCloudSourceArtifactDelegateResponse.builder()
        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT)
        .project(request.getProject())
        .repository(request.getRepository())
        .sourceDirectory(request.getSourceDirectory())
        .branch(request.getBranch())
        .commitId(request.getCommitId())
        .tag(request.getTag())
        .build();
  }
}
