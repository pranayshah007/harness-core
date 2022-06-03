/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toList;

import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class JenkinsArtifactTaskHandler extends DelegateArtifactTaskHandler<JenkinsArtifactDelegateRequest> {
  private static final int ARTIFACT_RETENTION_SIZE = 25;
  private final SecretDecryptionService secretDecryptionService;
  private final JenkinsRegistryService jenkinsRegistryService;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(JenkinsArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = jenkinsRegistryService.validateCredentials(
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getJob(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    List<JobDetails> jobDetails =
        jenkinsRegistryService.getJobs(JenkinsRequestResponseMapper.toJenkinsInternalConfig(artifactDelegateRequest),
            artifactDelegateRequest.getParentJobName());
    return ArtifactTaskExecutionResponse.builder().jobDetails(jobDetails).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getArtifactPaths(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    try {
      JobWithDetails jobDetails = jenkinsRegistryService.getJobWithDetails(
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(artifactDelegateRequest),
          artifactDelegateRequest.getJobName());
      List<String> artifactPath = Lists.newArrayList(jobDetails.getLastSuccessfulBuild()
                                                         .details()
                                                         .getArtifacts()
                                                         .stream()
                                                         .map(Artifact::getRelativePath)
                                                         .distinct()
                                                         .collect(toList()));
      return ArtifactTaskExecutionResponse.builder().artifactPath(artifactPath).build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception ex) {
      throw new ArtifactServerException(
          "Error in artifact paths from jenkins server. Reason:" + ExceptionUtils.getMessage(ex), ex, USER);
    }
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(JenkinsArtifactDelegateRequest attributesRequest) {
    List<BuildDetails> buildDetails =
        jenkinsRegistryService.getBuildsForJob(JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest),
            attributesRequest.getJobName(), attributesRequest.getArtifactPaths(), ARTIFACT_RETENTION_SIZE);
    return ArtifactTaskExecutionResponse.builder().buildDetails(buildDetails).build();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<JenkinsArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getBuildRegex());
  }

  @Override
  public void decryptRequestDTOs(JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest) {
    if (jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(
          jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO().getAuth().getCredentials(),
          jenkinsArtifactDelegateRequest.getEncryptedDataDetails());
    }
  }
}
