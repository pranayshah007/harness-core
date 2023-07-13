/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.s3;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.context.MdcGlobalContextData;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manage.GlobalContextManager;

import com.amazonaws.services.s3.model.AmazonS3Exception;
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
public class S3ArtifactTaskHelper {
  private final S3ArtifactTaskHandler s3ArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    S3ArtifactDelegateRequest attributes = (S3ArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    s3ArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_LAST_SUCCESSFUL_BUILD:
          saveLogs(executionLogCallback, "Fetching S3 Artifact details");
          artifactTaskResponse = getSuccessTaskResponse(s3ArtifactTaskHandler.getLastSuccessfulBuild(attributes));
          S3ArtifactDelegateResponse s3ArtifactDelegateResponse =
              (S3ArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                .getArtifactDelegateResponses()
                                                .size()
                          != 0
                      ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                      : S3ArtifactDelegateResponse.builder().build());
          saveLogs(executionLogCallback,
              "Fetched S3 Artifact details \n  type: AmazonS3\n  bucketName: "
                  + s3ArtifactDelegateResponse.getBucketName()
                  + "\n  filePath: " + s3ArtifactDelegateResponse.getFilePath()
                  + "\n  filePathRegex: " + s3ArtifactDelegateResponse.getFilePathRegex());
          break;
        case GET_BUILDS:
          saveLogs(executionLogCallback, "Fetching list of S3 Artifacts");
          artifactTaskResponse = getSuccessTaskResponse(s3ArtifactTaskHandler.getBuilds(attributes));
          saveLogs(executionLogCallback,
              "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                  + "S3 artifacts");
          break;
        default:
          saveLogs(executionLogCallback,
              "No corresponding S3 artifact task type [{}]: " + artifactTaskParameters.toString());
          log.error("No corresponding S3 artifact task type [{}]", artifactTaskParameters.toString());
          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no S3 artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (AmazonS3Exception ex) {
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
