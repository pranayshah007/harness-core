/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azure;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class AcrArtifactTaskHelper {
  private final AcrArtifactTaskHandler acrArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    AcrArtifactDelegateRequest attributes = (AcrArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    acrArtifactTaskHandler.decryptRequestDTOs(attributes);

    ArtifactTaskResponse artifactTaskResponse;
    switch (artifactTaskParameters.getArtifactTaskType()) {
      case GET_BUILDS:
        saveLogs(executionLogCallback, "Fetching artifact details");
        artifactTaskResponse = getSuccessTaskResponse(acrArtifactTaskHandler.getBuilds(attributes));
        saveLogs(executionLogCallback,
            "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                + " artifacts");
        break;
      case GET_LAST_SUCCESSFUL_BUILD:
        saveLogs(executionLogCallback, "Fetching Artifact details");
        artifactTaskResponse = getSuccessTaskResponse(acrArtifactTaskHandler.getLastSuccessfulBuild(attributes));
        break;
      default:
        saveLogs(executionLogCallback, "No corresponding Acr artifact task type [{}]: " + artifactTaskParameters);
        log.error("No corresponding Acr artifact task type [{}]", artifactTaskParameters);
        return ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Acr artifact task type impl defined for - "
                + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();
    }
    return artifactTaskResponse;
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
