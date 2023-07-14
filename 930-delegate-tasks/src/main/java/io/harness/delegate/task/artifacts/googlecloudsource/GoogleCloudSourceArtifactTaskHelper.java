/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudsource;
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
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
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
public class GoogleCloudSourceArtifactTaskHelper {
  private final GoogleCloudSourceArtifactTaskHandler googleCloudSourceArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    GoogleCloudSourceArtifactDelegateRequest attributes =
        (GoogleCloudSourceArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    ArtifactTaskResponse artifactTaskResponse;
    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_LAST_SUCCESSFUL_BUILD:
          saveLogs(executionLogCallback, "Google Cloud Source Artifact details");
          artifactTaskResponse =
              getSuccessTaskResponse(googleCloudSourceArtifactTaskHandler.getLastSuccessfulBuild(attributes));
          GoogleCloudSourceArtifactDelegateResponse artifactDelegateResponse =
              (GoogleCloudSourceArtifactDelegateResponse) artifactTaskResponse.getArtifactTaskExecutionResponse()
                  .getArtifactDelegateResponses()
                  .get(0);
          String branchFormat;
          String branchValue;
          if (artifactDelegateResponse.getBranch() != null) {
            branchFormat = "\n  branch: ";
            branchValue = artifactDelegateResponse.getBranch();
          } else if (artifactDelegateResponse.getCommitId() != null) {
            branchFormat = "\n  commitId: ";
            branchValue = artifactDelegateResponse.getCommitId();
          } else {
            branchFormat = "\n  tag: ";
            branchValue = artifactDelegateResponse.getTag();
          }
          saveLogs(executionLogCallback,
              "Google Cloud Source Artifact details \n  type: GoogleCloudSource\n  projectId: "
                  + artifactDelegateResponse.getProject()
                  + "\n  repositoryName: " + artifactDelegateResponse.getRepository() + branchFormat + branchValue
                  + "\n  sourceDirectory: " + artifactDelegateResponse.getSourceDirectory());
          break;
        default:
          saveLogs(executionLogCallback,
              "No corresponding GCS artifact task type [{}]: " + artifactTaskParameters.toString());
          log.error("No corresponding GCS artifact task type [{}]", artifactTaskParameters.toString());
          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no GCS artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (WingsException ex) {
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

  private void saveLogs(LogCallback executionLogCallback, String message) {
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
}
