/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;
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
import io.harness.exception.runtime.BambooServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manage.GlobalContextManager;

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
public class BambooArtifactTaskHelper {
  private final BambooArtifactTaskHandler bambooArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    BambooArtifactDelegateRequest attributes = (BambooArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getBambooConnectorDTO().getBambooUrl();
    bambooArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case VALIDATE_ARTIFACT_SERVER:
          saveLogs(executionLogCallback, "Validating  Artifact Server");
          artifactTaskResponse = getSuccessTaskResponse(bambooArtifactTaskHandler.validateArtifactServer(attributes));
          saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
          break;
        case GET_BUILDS:
          saveLogs(executionLogCallback, "Get the Bamboo Builds for Job");
          artifactTaskResponse = getSuccessTaskResponse(bambooArtifactTaskHandler.getBuilds(attributes));
          saveLogs(executionLogCallback, "Get the Bamboo Builds for Job " + registryUrl);
          break;
        case GET_LAST_SUCCESSFUL_BUILD:
          saveLogs(executionLogCallback, "Get the Bamboo Build");
          artifactTaskResponse = getSuccessTaskResponse(bambooArtifactTaskHandler.getLastSuccessfulBuild(attributes));
          saveLogs(executionLogCallback, "Get the Bamboo Build " + registryUrl);
          break;
        case GET_PLANS:
          saveLogs(executionLogCallback, "Get the Bamboo Plans");
          artifactTaskResponse = getSuccessTaskResponse(bambooArtifactTaskHandler.getPlans(attributes));
          saveLogs(executionLogCallback, "Get the Bamboo Plans " + registryUrl);
          break;
        case GET_ARTIFACT_PATH:
          saveLogs(executionLogCallback, "Get the Bamboo Artifact Path");
          artifactTaskResponse = getSuccessTaskResponse(bambooArtifactTaskHandler.getArtifactPaths(attributes));
          saveLogs(executionLogCallback, "Get the Bamboo Job " + registryUrl);
          break;
        case BAMBOO_BUILD:
          saveLogs(executionLogCallback, "Trigger the Bamboo Builds");
          artifactTaskResponse =
              getSuccessTaskResponse(bambooArtifactTaskHandler.triggerBuild(attributes, executionLogCallback));
          saveLogs(executionLogCallback, "Trigger the Bamboo Builds " + registryUrl);
          break;
        default:
          saveLogs(executionLogCallback, "No corresponding Bamboo artifact task type [{}]: " + artifactTaskParameters);
          log.error("No corresponding Bamboo artifact task type [{}]", artifactTaskParameters);
          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no Bamboo artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (BambooServerRuntimeException ex) {
      if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) == null) {
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(new HashMap<>()).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      }
      ((MdcGlobalContextData) GlobalContextManager.get(MdcGlobalContextData.MDC_ID))
          .getMap()
          .put(ExceptionMetadataKeys.CONNECTOR.name(), attributes.getConnectorRef());
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

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
