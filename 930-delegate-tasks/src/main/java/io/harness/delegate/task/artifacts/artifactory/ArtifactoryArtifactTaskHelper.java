/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.artifactory.service.ArtifactoryRegistryService.DEFAULT_ARTIFACT_DIRECTORY;
import static io.harness.artifactory.service.ArtifactoryRegistryService.MAX_NO_OF_TAGS_PER_ARTIFACT;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.mappers.ArtifactoryRequestResponseMapper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class ArtifactoryArtifactTaskHelper {
  @Inject ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject ArtifactoryNgService artifactoryNgService;
  private final ArtifactoryArtifactTaskHandler artifactoryArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    ArtifactoryArtifactDelegateRequest attributes =
        (ArtifactoryArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getArtifactoryConnectorDTO().getArtifactoryServerUrl();
    artifactoryArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    switch (artifactTaskParameters.getArtifactTaskType()) {
      case GET_LAST_SUCCESSFUL_BUILD:
        saveLogs(executionLogCallback, "Fetching Artifact details");
        artifactTaskResponse =
            getSuccessTaskResponse(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(attributes));
        ArtifactoryArtifactDelegateResponse artifactoryArtifactDelegateResponse =
            (ArtifactoryArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                       .getArtifactDelegateResponses()
                                                       .size()
                        != 0
                    ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                    : ArtifactoryArtifactDelegateResponse.builder().build());
        String buildMetadataUrl = artifactoryArtifactDelegateResponse.getBuildDetails() != null
            ? artifactoryArtifactDelegateResponse.getBuildDetails().getBuildUrl()
            : null;
        String dockerPullCommand =
            (RepositoryFormat.docker.name().equals(artifactoryArtifactDelegateResponse.getRepositoryFormat())
                && artifactoryArtifactDelegateResponse.getBuildDetails() != null
                && artifactoryArtifactDelegateResponse.getBuildDetails().getMetadata() != null)
            ? "\nTo pull image use: docker pull "
                + artifactoryArtifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
            : null;
        saveLogs(executionLogCallback,
            "Fetched Artifact details"
                + "\ntype: Artifactory Artifact"
                + "\nbuild metadata url: " + buildMetadataUrl
                + "\nrepository: " + artifactoryArtifactDelegateResponse.getRepositoryName()
                + "\nartifactPath: " + artifactoryArtifactDelegateResponse.getArtifactPath()
                + "\ntag: " + artifactoryArtifactDelegateResponse.getTag()
                + "\nrepository type: " + artifactoryArtifactDelegateResponse.getRepositoryFormat()
                + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : ""));
        break;
      case GET_BUILDS:
        saveLogs(executionLogCallback, "Fetching artifact details");
        artifactTaskResponse = getSuccessTaskResponse(artifactoryArtifactTaskHandler.getBuilds(attributes));
        saveLogs(executionLogCallback,
            "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                + " artifacts");
        break;
      case VALIDATE_ARTIFACT_SERVER:
        saveLogs(executionLogCallback, "Validating  Artifact Server");
        artifactTaskResponse =
            getSuccessTaskResponse(artifactoryArtifactTaskHandler.validateArtifactServer(attributes));
        saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
        break;
      default:
        saveLogs(executionLogCallback,
            "No corresponding Artifactory artifact task type [{}]: " + artifactTaskParameters.toString());
        log.error("No corresponding Artifactory artifact task type [{}]", artifactTaskParameters.toString());
        return ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Artifactory artifact task type impl defined for - "
                + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();
    }
    return artifactTaskResponse;
  }

  public ArtifactTaskResponse getGenericArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    ArtifactoryGenericArtifactDelegateRequest attributes =
        (ArtifactoryGenericArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    ArtifactTaskResponse artifactTaskResponse;
    switch (artifactTaskParameters.getArtifactTaskType()) {
      case GET_LAST_SUCCESSFUL_BUILD:
        saveLogs(executionLogCallback, "Fetching Artifact details");
        artifactTaskResponse = getSuccessTaskResponse(getLatestArtifact(artifactTaskParameters, executionLogCallback));
        ArtifactoryGenericArtifactDelegateResponse artifactoryGenericArtifactDelegateResponse =
            (ArtifactoryGenericArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                              .getArtifactDelegateResponses()
                                                              .size()
                        != 0
                    ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                    : ArtifactoryGenericArtifactDelegateResponse.builder().build());
        String buildMetadataUrl = artifactoryGenericArtifactDelegateResponse.getBuildDetails() != null
            ? artifactoryGenericArtifactDelegateResponse.getBuildDetails().getBuildUrl()
            : null;
        saveLogs(executionLogCallback,
            "Fetched Artifact details"
                + "\ntype: Artifactory Artifact"
                + "\nbuild metadata url: " + buildMetadataUrl
                + "\nrepository: " + artifactoryGenericArtifactDelegateResponse.getRepositoryName()
                + "\nartifactPath: " + artifactoryGenericArtifactDelegateResponse.getArtifactPath()
                + "\nrepository type: " + artifactoryGenericArtifactDelegateResponse.getRepositoryFormat());
        break;
      case GET_BUILDS:
        saveLogs(executionLogCallback, "Fetching artifact details");
        artifactTaskResponse = getSuccessTaskResponse(artifactoryArtifactTaskHandler.getBuilds(attributes));
        saveLogs(executionLogCallback,
            "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                + " artifacts");
        break;
      default:
        saveLogs(executionLogCallback,
            "No corresponding Artifactory artifact task type [{}]: " + artifactTaskParameters.toString());
        log.error("No corresponding Artifactory artifact task type [{}]", artifactTaskParameters.toString());
        return ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Artifactory artifact task type impl defined for - "
                + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();
    }
    return artifactTaskResponse;
  }

  public ArtifactTaskExecutionResponse getLatestArtifact(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    ArtifactoryGenericArtifactDelegateRequest artifactoryGenericArtifactDelegateRequest =
        (ArtifactoryGenericArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    artifactoryArtifactTaskHandler.decryptRequestDTOs(artifactoryGenericArtifactDelegateRequest);

    String artifactDirectory = artifactoryGenericArtifactDelegateRequest.getArtifactDirectory();
    ArtifactoryGenericArtifactDelegateResponse artifactoryGenericArtifactDelegateResponse;

    if (EmptyPredicate.isEmpty(artifactDirectory)) {
      saveLogs(executionLogCallback,
          "Artifact Directory is Empty, assuming Artifacts are present in root of the repository");
      artifactDirectory = DEFAULT_ARTIFACT_DIRECTORY;
    }

    ArtifactoryConfigRequest artifactoryConfigRequest = artifactoryRequestMapper.toArtifactoryRequest(
        artifactoryGenericArtifactDelegateRequest.getArtifactoryConnectorDTO());
    BuildDetails buildDetails = artifactoryNgService.getLatestArtifact(artifactoryConfigRequest,
        artifactoryGenericArtifactDelegateRequest.getRepositoryName(), artifactDirectory,
        artifactoryGenericArtifactDelegateRequest.getArtifactPathFilter(),
        artifactoryGenericArtifactDelegateRequest.getArtifactPath(), MAX_NO_OF_TAGS_PER_ARTIFACT);
    artifactoryGenericArtifactDelegateResponse = ArtifactoryRequestResponseMapper.toArtifactoryGenericResponse(
        buildDetails, artifactoryGenericArtifactDelegateRequest);
    addArtifactNameToMetaData(artifactoryGenericArtifactDelegateResponse);
    return artifactoryArtifactTaskHandler.getSuccessTaskExecutionResponseGeneric(
        Collections.singletonList(artifactoryGenericArtifactDelegateResponse));
  }

  private void addArtifactNameToMetaData(
      ArtifactoryGenericArtifactDelegateResponse artifactoryGenericArtifactDelegateResponse) {
    if (artifactoryGenericArtifactDelegateResponse.getBuildDetails() != null) {
      Map<String, String> metadata = artifactoryGenericArtifactDelegateResponse.getBuildDetails().getMetadata();
      if (metadata == null) {
        metadata = new HashMap<>();
      }
      String name = "";
      if (artifactoryGenericArtifactDelegateResponse.getArtifactPath() != null) {
        name = StringUtils.substringAfterLast(artifactoryGenericArtifactDelegateResponse.getArtifactPath(), "/");
        if (name.equals("")) {
          name = artifactoryGenericArtifactDelegateResponse.getArtifactPath();
        }
      }
      metadata.put(ArtifactMetadataKeys.FILE_NAME, name);
    }
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  protected void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    if (artifactTaskParameters.getAttributes() instanceof ArtifactoryGenericArtifactDelegateRequest) {
      return getGenericArtifactCollectResponse(artifactTaskParameters, null);
    }
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}