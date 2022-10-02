/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ArtifactRepositoryServiceImpl {
  @Inject ArtifactCollectionServiceRegistryNg serviceRegistry;

  public ArtifactTaskExecutionResponse collectBuilds(ArtifactTaskParameters params) {
    ArtifactSourceDelegateRequest attributes = params.getAttributes();
    DelegateArtifactTaskHandler handler = serviceRegistry.getBuildService(attributes.getSourceType());
    handler.decryptRequestDTOs(params.getAttributes());
    if (params.getArtifactTaskType().equals(ArtifactTaskType.GET_BUILDS)) {
      return handler.getBuilds(attributes);
    }
    return null;
  }
}
