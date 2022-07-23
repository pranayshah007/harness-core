/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.deleteFileIfExists;

import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.CustomRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.ShellScriptExecutionOnDelegateNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArtifactServerException;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class CustomArtifactTaskHandler extends DelegateArtifactTaskHandler<CustomArtifactDelegateRequest> {
  @Inject private ShellScriptExecutionOnDelegateNG shellScriptExecutionOnDelegateNG;
  private static final String ARTIFACT_RESULT_PATH = "ARTIFACT_RESULT_PATH";

  @Override
  public void decryptRequestDTOs(CustomArtifactDelegateRequest dto) {}

  public ArtifactTaskExecutionResponse getBuilds(
      CustomArtifactDelegateRequest attributesRequest, ILogStreamingTaskClient logStreamingTaskClient) {
    String script = attributesRequest.getScript();
    List<BuildDetails> buildDetails = getBuildDetails(attributesRequest, logStreamingTaskClient);
    List<CustomArtifactDelegateResponse> customArtifactDelegateResponseList =
        buildDetails.stream()
            .sorted(new BuildDetailsComparatorDescending())
            .map(build -> CustomRequestResponseMapper.toCustomArtifactDelegateResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(customArtifactDelegateResponseList, buildDetails);
  }

  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(
      CustomArtifactDelegateRequest attributesRequest, ILogStreamingTaskClient logStreamingTaskClient) {
    String script = attributesRequest.getScript();
    List<BuildDetails> buildDetails = new ArrayList<>();
    buildDetails = getBuildDetails(attributesRequest, logStreamingTaskClient);
    if(filterVersion(buildDetails, attributesRequest) != null && EmptyPredicate.isNotEmpty(filterVersion(buildDetails, attributesRequest))){
      CustomArtifactDelegateResponse customArtifactDelegateResponse =
              CustomRequestResponseMapper.toCustomArtifactDelegateResponse(filterVersion(buildDetails, attributesRequest).get(0), attributesRequest);
      return getSuccessTaskExecutionResponse(
              Collections.singletonList(customArtifactDelegateResponse), filterVersion(buildDetails, attributesRequest));
    }
    else {
      log.error("Artifact Version Not found");
      throw new InvalidArtifactServerException("Artifact version not found", Level.INFO, USER);
    }
  }

  private List<BuildDetails> getBuildDetails(
      CustomArtifactDelegateRequest attributesRequest, ILogStreamingTaskClient logStreamingTaskClient) {
    String script = attributesRequest.getScript();
    List<BuildDetails> buildDetails = new ArrayList<>();
    UUID uuid = UUID.randomUUID();
    String scriptOutputFilename = "harness-" + uuid + ".out";
    File workingDirectory = new File(attributesRequest.getWorkingDirectory());
    File scriptOutputFile = new File(workingDirectory, scriptOutputFilename);
    try {
      script = addEnvVariablesCollector(script, scriptOutputFile.getAbsolutePath());
      ShellScriptTaskParametersNG shellScriptTaskParametersNG =
          getShellScriptTaskParametersNG(script, attributesRequest);
      ShellScriptTaskResponseNG shellScriptTaskResponseNG =
          shellScriptExecutionOnDelegateNG.executeOnDelegate(shellScriptTaskParametersNG, logStreamingTaskClient);
      if (shellScriptTaskResponseNG.getStatus().name().equals("SUCCESS")) {
        return shellScriptExecutionOnDelegateNG.getBuildDetails(scriptOutputFile.getAbsolutePath(), attributesRequest);
      } else {
        String msg = "No Artifact found in ARTIFACT_RESULT_PATH.";
        log.error(msg);
        throw new InvalidArtifactServerException(msg, Level.INFO, USER);
      }

    } finally {
      try {
        deleteFileIfExists(scriptOutputFile.getAbsolutePath());
      } catch (IOException e) {
        log.warn("Failed to delete file: {} ", scriptOutputFile.getAbsolutePath(), e);
      }
    }
  }

  private String addEnvVariablesCollector(String command, String scriptOutputFilePath) {
    StringBuilder wrapperCommand = new StringBuilder();
    wrapperCommand.append("export " + ARTIFACT_RESULT_PATH + "=" + scriptOutputFilePath + "\n" + command);
    return wrapperCommand.toString();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<CustomArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  private ShellScriptTaskParametersNG getShellScriptTaskParametersNG(
      String script, CustomArtifactDelegateRequest attributesRequest) {
    return ShellScriptTaskParametersNG.builder()
        .script(script)
        .scriptType(attributesRequest.getScriptType())
        .environmentVariables(attributesRequest.getInputs())
        .executionId(attributesRequest.getExecutionId())
        .workingDirectory(attributesRequest.getWorkingDirectory())
        .outputVars(new ArrayList<>())
        .executeOnDelegate(attributesRequest.isExecuteOnDelegate())
        .build();
  }

  private List<BuildDetails> filterVersion(
      List<BuildDetails> buildDetails, CustomArtifactDelegateRequest attributesRequest) {
    return buildDetails.stream()
        .filter(build -> build.getNumber().equals(attributesRequest.getVersion()))
        .collect(Collectors.toList());
  }
}
