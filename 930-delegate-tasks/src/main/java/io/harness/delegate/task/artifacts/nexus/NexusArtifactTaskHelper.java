/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class NexusArtifactTaskHelper {
  private final NexusArtifactTaskHandler nexusArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    NexusArtifactDelegateRequest attributes = (NexusArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getNexusConnectorDTO().getNexusServerUrl();
    nexusArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    switch (artifactTaskParameters.getArtifactTaskType()) {
      case GET_LAST_SUCCESSFUL_BUILD:
        saveLogs(executionLogCallback, "Fetching Artifact details");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.getLastSuccessfulBuild(attributes));
        NexusArtifactDelegateResponse nexusArtifactDelegateResponse =
            (NexusArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                 .getArtifactDelegateResponses()
                                                 .size()
                        != 0
                    ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                    : NexusArtifactDelegateResponse.builder().build());
        String buildMetadataUrl = nexusArtifactDelegateResponse.getBuildDetails() != null
            ? nexusArtifactDelegateResponse.getBuildDetails().getBuildUrl()
            : null;
        String dockerPullCommand =
            (RepositoryFormat.docker.name().equals(nexusArtifactDelegateResponse.getRepositoryFormat())
                && nexusArtifactDelegateResponse.getBuildDetails() != null
                && nexusArtifactDelegateResponse.getBuildDetails().getMetadata() != null)
            ? "\nTo pull image use: docker pull "
                + nexusArtifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
            : null;
        saveLogs(executionLogCallback,
            "Fetched Artifact details"
                + "\ntype: Nexus Artifact"
                + "\nbuild metadata url: " + buildMetadataUrl
                + "\nrepository: " + nexusArtifactDelegateResponse.getRepositoryName() + "\nartifactPath: "
                + nexusArtifactDelegateResponse.getArtifactPath() + "\ntag: " + nexusArtifactDelegateResponse.getTag()
                + "\nrepository type: " + nexusArtifactDelegateResponse.getRepositoryFormat()
                + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : ""));
        break;
      case GET_BUILDS:
        saveLogs(executionLogCallback, "Fetching artifact details");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.getBuilds(attributes));
        saveLogs(executionLogCallback,
            "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                + " artifacts");
        break;
      case VALIDATE_ARTIFACT_SERVER:
        saveLogs(executionLogCallback, "Validating  Artifact Server");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.validateArtifactServer(attributes));
        saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
        break;
      case GET_NEXUS_REPOSITORIES:
        saveLogs(executionLogCallback, "Validating  Artifact Server");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.getRepositories(attributes));
        saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
        break;
      case GET_NEXUS_GROUP_IDS:
        saveLogs(executionLogCallback, "Fetching GroupIds for Nexus");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.getGroupIds(attributes));
        saveLogs(executionLogCallback, "Fetched GroupIds for Nexus");
        break;
      case GET_NEXUS_ARTIFACTIDS:
        saveLogs(executionLogCallback, "Fetching ArtifactIds for Nexus");
        artifactTaskResponse = getSuccessTaskResponse(nexusArtifactTaskHandler.getArtifactIds(attributes));
        saveLogs(executionLogCallback, "Fetched ArtifactIds for Nexus");
        break;
      default:
        saveLogs(executionLogCallback,
            "No corresponding Nexus artifact task type [{}]: " + artifactTaskParameters.toString());
        log.error("No corresponding Nexus artifact task type [{}]", artifactTaskParameters.toString());
        return ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Nexus artifact task type impl defined for - "
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
