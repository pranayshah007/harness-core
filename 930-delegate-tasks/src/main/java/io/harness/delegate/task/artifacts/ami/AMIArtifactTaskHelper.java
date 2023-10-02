/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ami;
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
import io.harness.exception.runtime.AMIServerRuntimeException;
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
public class AMIArtifactTaskHelper {
  private final AMIArtifactTaskHandler amiArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    AMIArtifactDelegateRequest attributes = (AMIArtifactDelegateRequest) artifactTaskParameters.getAttributes();

    amiArtifactTaskHandler.decryptRequestDTOs(attributes);

    ArtifactTaskResponse artifactTaskResponse;

    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_BUILDS:

          saveLogs(executionLogCallback, "Fetching AMI Artifacts Builds");
          artifactTaskResponse = getSuccessTaskResponse(amiArtifactTaskHandler.getBuilds(attributes));
          saveLogs(executionLogCallback, "Fetched AMI Artifacts Builds");

          break;

        case GET_LAST_SUCCESSFUL_BUILD:

          saveLogs(executionLogCallback, "Fetching Last Successful Build");
          artifactTaskResponse = getSuccessTaskResponse(amiArtifactTaskHandler.getLastSuccessfulBuild(attributes));
          saveLogs(executionLogCallback, "Fetched Last Successful Build");

          break;

        case GET_AMI_TAGS:

          saveLogs(executionLogCallback, "Fetching Last Successful Build");
          artifactTaskResponse = getSuccessTaskResponse(amiArtifactTaskHandler.listTags(attributes));
          saveLogs(executionLogCallback, "Fetched Last Successful Build");

          break;

        default:

          saveLogs(executionLogCallback,
              "No corresponding AMI artifact task type [{}]: " + artifactTaskParameters.toString());

          log.error("No corresponding AMI artifact task type [{}]", artifactTaskParameters.toString());

          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no such AMI Artifacts Delegate task - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (AMIServerRuntimeException ex) {
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

  protected void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
