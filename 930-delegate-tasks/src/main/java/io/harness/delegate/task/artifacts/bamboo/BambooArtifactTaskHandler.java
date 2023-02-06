/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.BambooRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.bamboo.BambooBuildTaskNGResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.delegatetasks.BambooTask.BambooExecutionResponse;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.Result;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.BambooBuildService;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class BambooArtifactTaskHandler extends DelegateArtifactTaskHandler<BambooArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  @Inject private BambooBuildService bambooBuildService;
  @Inject private BambooService bambooService;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(BambooArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = bambooBuildService.validateArtifactServer(
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(BambooArtifactDelegateRequest attributesRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName(attributesRequest.getPlanKey())
                                                            .artifactPaths(attributesRequest.getArtifactPaths())
                                                            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
                                                            .build();
    List<BuildDetails> buildDetails = bambooBuildService.getBuilds(null, artifactStreamAttributes,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().buildDetails(buildDetails).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(BambooArtifactDelegateRequest attributesRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName(attributesRequest.getPlanKey())
                                                            .artifactPaths(attributesRequest.getArtifactPaths())
                                                            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
                                                            .build();
    BuildDetails buildDetails = bambooBuildService.getLastSuccessfulBuild(null, artifactStreamAttributes,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    BambooArtifactDelegateResponse bambooArtifactDelegateResponse =
        BambooRequestResponseMapper.toBambooArtifactDelegateResponse(buildDetails, attributesRequest);
    return getSuccessTaskExecutionResponse(
        Collections.singletonList(bambooArtifactDelegateResponse), Collections.singletonList(buildDetails));
  }

  public ArtifactTaskExecutionResponse getPlans(BambooArtifactDelegateRequest attributesRequest) {
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName(attributesRequest.getPlanKey())
                                                            .artifactPaths(attributesRequest.getArtifactPaths())
                                                            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
                                                            .build();
    Map<String, String> plans = bambooBuildService.getPlans(
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().plans(plans).build();
  }

  public ArtifactTaskExecutionResponse triggerBuild(BambooArtifactDelegateRequest attributesRequest) {
    BambooBuildTaskNGResponse bambooBuildTaskNGResponse = new BambooBuildTaskNGResponse();
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    BambooConfig bambooConfig = BambooRequestResponseMapper.toBambooConfig(attributesRequest);
    String errorMessage = null;
    try {
      Map<String, String> evaluatedParameters = Maps.newLinkedHashMap();
      if (isNotEmpty(attributesRequest.getParameterEntries())) {
        attributesRequest.getParameterEntries().forEach(
            parameterEntry -> evaluatedParameters.put(parameterEntry.getKey(), parameterEntry.getValue()));
      }
      String buildResultKey = bambooService.triggerPlan(bambooConfig, attributesRequest.getEncryptedDataDetails(),
          attributesRequest.getPlanKey(), evaluatedParameters);
      Result result =
          waitForBuildExecutionToFinish(bambooConfig, attributesRequest.getEncryptedDataDetails(), buildResultKey);
      String buildState = result.getBuildState();
      if (result == null || buildState == null) {
        executionStatus = ExecutionStatus.FAILED;
        log.info("Bamboo execution failed for plan {}", attributesRequest.getPlanKey());
      } else {
        if (!"Successful".equalsIgnoreCase(buildState)) {
          executionStatus = ExecutionStatus.FAILED;
          log.info("Build result for Bamboo url {}, plan key {}, build key {} is Failed. Result {}",
              bambooConfig.getBambooUrl(), attributesRequest.getPlanKey(), buildResultKey, result);
        }
        bambooBuildTaskNGResponse.setProjectName(result.getProjectName());
        bambooBuildTaskNGResponse.setPlanName(result.getPlanName());
        bambooBuildTaskNGResponse.setBuildNumber(result.getBuildNumber());
        bambooBuildTaskNGResponse.setBuildStatus(result.getBuildState());
        bambooBuildTaskNGResponse.setBuildUrl(result.getBuildUrl());
        bambooBuildTaskNGResponse.setParameters(attributesRequest.getParameterEntries());
      }
    } catch (Exception e) {
      log.warn("Failed to execute Bamboo verification task: " + ExceptionUtils.getMessage(e), e);
      errorMessage = ExceptionUtils.getMessage(e);
      executionStatus = ExecutionStatus.FAILED;
    }
    bambooBuildTaskNGResponse.setErrorMessage(errorMessage);
    bambooBuildTaskNGResponse.setExecutionStatus(executionStatus);
    return ArtifactTaskExecutionResponse.builder().bambooBuildTaskNGResponse(bambooBuildTaskNGResponse).build();
  }

  private Result waitForBuildExecutionToFinish(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey) {
    Result result;
    do {
      log.info("Waiting for build execution {} to finish", buildResultKey);
      sleep(ofSeconds(5));
      result = bambooService.getBuildResult(bambooConfig, encryptionDetails, buildResultKey);
      log.info("Build result for build key {} is {}", buildResultKey, result);
    } while (result.getBuildState() == null || result.getBuildState().equalsIgnoreCase("Unknown"));

    // Get the build result
    log.info("Build execution for build key {} is finished. Result:{} ", buildResultKey, result);
    return result;
  }

  @Override
  public ArtifactTaskExecutionResponse getArtifactPaths(BambooArtifactDelegateRequest attributesRequest) {
    List<String> artifactPaths = bambooBuildService.getArtifactPaths(attributesRequest.getPlanKey(), null,
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().artifactPath(artifactPaths).build();
  }

  @Override
  public void decryptRequestDTOs(BambooArtifactDelegateRequest bambooArtifactDelegateRequest) {
    if (bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth().getCredentials(),
          bambooArtifactDelegateRequest.getEncryptedDataDetails());
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<BambooArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }
}
