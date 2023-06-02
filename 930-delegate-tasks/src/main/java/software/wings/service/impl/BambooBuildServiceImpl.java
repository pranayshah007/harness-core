/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.validation.Validator.equalCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.BambooBuildService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by anubhaw on 11/22/16.
 */
@OwnedBy(CDC)
@Singleton
public class BambooBuildServiceImpl implements BambooBuildService {
  @Inject private BambooService bambooService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.BAMBOO.name());
    return wrapNewBuildsWithLabels(
        bambooService.getBuilds(bambooConfig, encryptionDetails, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getArtifactPaths(), ARTIFACT_RETENTION_SIZE),
        artifactStreamAttributes, bambooConfig);
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, int limit) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.BAMBOO.name());
    return wrapNewBuildsWithLabels(
        bambooService.getBuilds(bambooConfig, encryptionDetails, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getArtifactPaths(), limit),
        artifactStreamAttributes, bambooConfig);
  }

  @Override
  public List<JobDetails> getJobs(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    List<String> strings = Lists.newArrayList(bambooService.getPlanKeys(bambooConfig, encryptionDetails).keySet());
    return wrapJobNameWithJobDetails(strings);
  }

  @Override
  public Map<String, String> getPlans(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    return bambooService.getPlanKeys(bambooConfig, encryptionDetails);
  }

  @Override
  public Map<String, String> getPlans(BambooConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    return bambooService.getArtifactPath(bambooConfig, encryptionDetails, jobName);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.BAMBOO.name());
    return wrapLastSuccessfulBuildWithLabels(
        bambooService.getLastSuccessfulBuild(bambooConfig, encryptionDetails, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getArtifactPaths()),
        artifactStreamAttributes, bambooConfig);
  }

  @Override
  public List<String> getGroupIds(
      String jobName, BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Bamboo Artifact Stream", USER);
  }

  @Override
  public boolean validateArtifactServer(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails) {
    if (!connectableHttpUrl(bambooConfig.getBambooUrl(), false)) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER, USER)
          .addParam("message", "Could not reach Bamboo Server at : " + bambooConfig.getBambooUrl());
    }
    // check for credentials
    return bambooService.isRunning(bambooConfig, encryptionDetails);
  }

  @Override
  public boolean validateArtifactSource(BambooConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      BambooConfig bambooConfig, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Bamboo Artifact Stream");
  }

  @Override
  public List<String> getSmbPaths(BambooConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Bamboo Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      BambooConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by Bamboo Build Service", WingsException.USER);
  }
}
