/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.equalCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.GcrBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactConfigMapper;
import software.wings.service.mappers.artifact.GcrConfigToInternalMapper;
import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by brett on 8/2/17
 */
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
@Singleton
@Slf4j
public class GcrBuildServiceImpl implements GcrBuildService {
  @Inject private GcrApiService gcrApiService;
  @Inject private GcpHelperService gcpHelperService;
  @Inject private EncryptionService encryptionService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.GCR.name());
    log.info("[GCR Delegate Selection] Get builds for image " + artifactStreamAttributes.getImageName()
        + " with selectors " + gcpConfig.getDelegateSelectors());
    try {
      // Decrypt gcpConfig
      encryptionService.decrypt(gcpConfig, encryptionDetails, false);
      return wrapNewBuildsWithLabels(
          gcrApiService
              .getBuilds(GcrConfigToInternalMapper.toGcpInternalConfig(artifactStreamAttributes.getRegistryHostName(),
                             gcpHelperService.getBasicAuthHeader(
                                 gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors())),
                  artifactStreamAttributes.getImageName(), 50)
              .stream()
              .map(ArtifactConfigMapper::toBuildDetails)
              .collect(Collectors.toList()),
          artifactStreamAttributes, gcpConfig);
    } catch (IOException e) {
      log.error("", e);
    }
    return new ArrayList<>();
  }

  @Override
  public List<JobDetails> getJobs(
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    return Lists.newArrayList();
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, GcpConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by GCR Artifact Stream", USER);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      GcpConfig gcpConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by GCR Artifact Stream", USER);
  }

  @Override
  public Map<String, String> getPlans(GcpConfig config, List<EncryptedDataDetail> encryptionDetails) {
    log.info("[GCR Delegate Selection] Get Plans with selectors " + config.getDelegateSelectors());
    return getJobs(config, encryptionDetails, Optional.empty())
        .stream()
        .collect(Collectors.toMap(JobDetails::getJobName, JobDetails::getJobName));
  }

  @Override
  public Map<String, String> getPlans(GcpConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String reposiotoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(String jobName, GcpConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by GCR Artifact Stream", USER);
  }

  @Override
  public boolean validateArtifactSource(GcpConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    log.info("[GCR Delegate Selection] Validate artifact source for image " + artifactStreamAttributes.getImageName()
        + " with selectors " + config.getDelegateSelectors());
    try {
      // Decrypt gcpConfig
      encryptionService.decrypt(config, encryptionDetails, false);
      return gcrApiService.verifyImageName(
          GcrConfigToInternalMapper.toGcpInternalConfig(artifactStreamAttributes.getRegistryHostName(),
              gcpHelperService.getBasicAuthHeader(
                  config.getServiceAccountKeyFileContent(), config.isUseDelegateSelectors())),
          artifactStreamAttributes.getImageName());
    } catch (IOException e) {
      log.error("Could not verify Artifact source", e);
    }
    return false;
  }

  @Override
  public boolean validateArtifactServer(GcpConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      GcpConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by GCR Artifact Stream", USER);
  }

  @Override
  public List<String> getSmbPaths(GcpConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by GCR Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      GcpConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by GCR Build Service", WingsException.USER);
  }
}
