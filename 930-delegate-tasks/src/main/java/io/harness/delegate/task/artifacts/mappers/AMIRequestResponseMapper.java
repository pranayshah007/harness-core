/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class AMIRequestResponseMapper {
  public static AMIArtifactDelegateResponse toAMIArtifactResponse(
      BuildDetails lastSuccessfulBuild, AMIArtifactDelegateRequest attributesRequest) {
    return AMIArtifactDelegateResponse.builder()
        .version(lastSuccessfulBuild.getNumber())
        .versionRegex(attributesRequest.getVersionRegex())
        .amiId(lastSuccessfulBuild.getRevision())
        .metadata(lastSuccessfulBuild.getMetadata())
        .sourceType(ArtifactSourceType.AMI)
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(lastSuccessfulBuild))
        .build();
  }
}
