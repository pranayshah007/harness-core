/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.context.MdcGlobalContextData;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.GithubPackagesServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manage.GlobalContextManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class GithubPackagesArtifactTaskHelper {
  private final GithubPackagesArtifactTaskHandler githubPackagesArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters taskParameters) {
    return getArtifactCollectResponse(taskParameters, null);
  }

  @VisibleForTesting
  protected void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  private ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    GithubPackagesArtifactDelegateRequest attributes =
        (GithubPackagesArtifactDelegateRequest) artifactTaskParameters.getAttributes();

    String registryUrl = attributes.getGithubConnectorDTO().getGitConnectionUrl();

    githubPackagesArtifactTaskHandler.decryptRequestDTOs(attributes);

    ArtifactTaskResponse artifactTaskResponse;

    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_BUILDS:

          saveLogs(executionLogCallback, "Fetching artifact details");

          artifactTaskResponse = getSuccessTaskResponse(githubPackagesArtifactTaskHandler.getBuilds(attributes));

          saveLogs(executionLogCallback,
              "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                  + " artifacts");

          break;
        case GET_LAST_SUCCESSFUL_BUILD:

          saveLogs(executionLogCallback, "Fetching last successful artifact details");

          artifactTaskResponse =
              getSuccessTaskResponse(githubPackagesArtifactTaskHandler.getLastSuccessfulBuild(attributes));

          saveLogs(executionLogCallback, "Fetched last successful artifact");

          break;
        case GET_GITHUB_PACKAGES:

          saveLogs(executionLogCallback, "Fetching list of Github Packages");

          artifactTaskResponse = getSuccessTaskResponse(githubPackagesArtifactTaskHandler.listPackages(attributes));

          saveLogs(executionLogCallback, "Fetched Github Packages");

          break;
        default:

          saveLogs(executionLogCallback,
              "No corresponding Github Package artifact task type [{}]: " + artifactTaskParameters.toString());

          log.error("No corresponding Github Package artifact task type [{}]", artifactTaskParameters.toString());

          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no Github Package artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (GithubPackagesServerRuntimeException ex) {
      if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) == null) {
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(new HashMap<>()).build();

        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      }

      if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) != null) {
        ((MdcGlobalContextData) GlobalContextManager.get(MdcGlobalContextData.MDC_ID))
            .getMap()
            .put(ExceptionMetadataKeys.CONNECTOR.name(), attributes.getConnectorRef());
      }

      throw ex;
    }

    return artifactTaskResponse;
  }
}
