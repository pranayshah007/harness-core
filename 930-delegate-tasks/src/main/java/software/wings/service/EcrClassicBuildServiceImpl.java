/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.beans.artifact.ArtifactStreamType.ECR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.EcrConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.ecr.EcrClassicService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.EcrClassicBuildService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by brett on 7/16/17.
 */
@OwnedBy(CDC)
@Singleton
public class EcrClassicBuildServiceImpl implements EcrClassicBuildService {
  @Inject private EcrClassicService ecrClassicService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ECR.name());
    return wrapNewBuildsWithLabels(
        ecrClassicService.getBuilds(ecrConfig, encryptionDetails, artifactStreamAttributes.getImageName(), 50),
        artifactStreamAttributes, ecrConfig);
  }

  @Override
  public List<JobDetails> getJobs(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    List<String> strings = ecrClassicService.listEcrRegistry(ecrConfig, encryptionDetails);
    return wrapJobNameWithJobDetails(strings);
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, EcrConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream");
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream");
  }

  @Override
  public Map<String, String> getPlans(EcrConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return getJobs(config, encryptionDetails, Optional.empty())
        .stream()
        .collect(Collectors.toMap(JobDetails::getJobName, JobDetails::getJobName));
  }

  @Override
  public Map<String, String> getPlans(
      EcrConfig config, List<EncryptedDataDetail> encryptionDetails, ArtifactType artifactType, String repositoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(String jobName, EcrConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream");
  }

  @Override
  public boolean validateArtifactServer(EcrConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    if (!connectableHttpUrl(config.getEcrUrl())) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER)
          .addParam("message", "Could not reach Amazon EC2 Container Registry at : " + config.getEcrUrl());
    }
    return ecrClassicService.validateCredentials(config, encryptedDataDetails);
  }

  @Override
  public boolean validateArtifactSource(EcrConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    return ecrClassicService.verifyRepository(config, encryptionDetails, artifactStreamAttributes.getImageName());
  }

  @Override
  public Map<String, String> getBuckets(
      EcrConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR");
  }

  @Override
  public List<String> getSmbPaths(EcrConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      EcrConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by ECR Build Service", WingsException.USER);
  }
}
