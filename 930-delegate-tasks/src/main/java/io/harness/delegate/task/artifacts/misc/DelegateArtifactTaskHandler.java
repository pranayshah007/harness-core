/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;

import java.net.URISyntaxException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class DelegateArtifactTaskHandler<T extends ArtifactSourceDelegateRequest> {
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getBuilds(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getLabels(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse validateArtifactServer(T attributesRequest) throws URISyntaxException {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse validateArtifactImage(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getImages(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getJob(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getJobWithParamters(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }

  public ArtifactTaskExecutionResponse getArtifactPaths(T attributesRequest) {
    throw new InvalidRequestException("Operation not supported");
  }
  public abstract void decryptRequestDTOs(T dto);
}
