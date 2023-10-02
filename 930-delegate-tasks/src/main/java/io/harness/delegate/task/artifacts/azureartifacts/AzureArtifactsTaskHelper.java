/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;
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
import io.harness.exception.runtime.AzureArtifactsServerRuntimeException;
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
public class AzureArtifactsTaskHelper {
  private final AzureArtifactsTaskHandler azureArtifactsTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    AzureArtifactsDelegateRequest attributes = (AzureArtifactsDelegateRequest) artifactTaskParameters.getAttributes();

    String registryUrl = attributes.getAzureArtifactsConnectorDTO().getAzureArtifactsUrl();

    azureArtifactsTaskHandler.decryptRequestDTOs(attributes);

    ArtifactTaskResponse artifactTaskResponse;

    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case VALIDATE_ARTIFACT_SERVER:

          saveLogs(executionLogCallback, "Validating  Artifact Server");
          artifactTaskResponse = getSuccessTaskResponse(azureArtifactsTaskHandler.validateArtifactServer(attributes));
          saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);

          break;

        case GET_BUILDS:

          saveLogs(executionLogCallback, "Fetching Azure Artifacts Builds");
          artifactTaskResponse = getSuccessTaskResponse(azureArtifactsTaskHandler.getBuilds(attributes));
          saveLogs(executionLogCallback, "Fetched Azure Artifacts Builds " + registryUrl);

          break;

        case GET_AZURE_PACKAGES:

          saveLogs(executionLogCallback, "Fetching Azure Artifacts Packages");
          artifactTaskResponse =
              getSuccessTaskResponse(azureArtifactsTaskHandler.getAzureArtifactsPackages(attributes));
          saveLogs(executionLogCallback, "Fetched Azure Artifacts Packages: " + registryUrl);

          break;

        case GET_AZURE_PROJECTS:

          saveLogs(executionLogCallback, "Fetching Azure Artifacts Projects");
          artifactTaskResponse =
              getSuccessTaskResponse(azureArtifactsTaskHandler.getAzureArtifactsProjects(attributes));
          saveLogs(executionLogCallback, "Fetched Azure Artifacts Projects: " + registryUrl);

          break;

        case GET_AZURE_FEEDS:

          saveLogs(executionLogCallback, "Fetching Azure Artifacts Feeds");
          artifactTaskResponse = getSuccessTaskResponse(azureArtifactsTaskHandler.getAzureArtifactsFeeds(attributes));
          saveLogs(executionLogCallback, "Fetched Azure Artifacts Feeds: " + registryUrl);

          break;

        case GET_LAST_SUCCESSFUL_BUILD:

          saveLogs(executionLogCallback, "Fetching Last Successful Build");
          artifactTaskResponse = getSuccessTaskResponse(azureArtifactsTaskHandler.getLastSuccessfulBuild(attributes));
          saveLogs(executionLogCallback, "Fetched Last Successful Build: " + registryUrl);

          break;

        default:

          saveLogs(executionLogCallback,
              "No corresponding Azure artifact task type [{}]: " + artifactTaskParameters.toString());

          log.error("No corresponding Azure artifact task type [{}]", artifactTaskParameters.toString());

          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no such Azure Artifacts Delegate task - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (AzureArtifactsServerRuntimeException ex) {
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

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  @VisibleForTesting
  protected void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
