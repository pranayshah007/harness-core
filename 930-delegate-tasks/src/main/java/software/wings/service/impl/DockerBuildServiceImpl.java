/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.DockerConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactConfigMapper;
import software.wings.service.mappers.artifact.DockerConfigToInternalMapper;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 1/6/17.
 */
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerBuildServiceImpl implements DockerBuildService {
  @Inject private DockerRegistryService dockerRegistryService;
  @Inject private EncryptionService encryptionService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      DockerConfig dockerConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), DOCKER.name());
    encryptionService.decrypt(dockerConfig, encryptionDetails, false);
    List<BuildDetailsInternal> builds =
        dockerRegistryService.getBuilds(DockerConfigToInternalMapper.toDockerInternalConfig(dockerConfig),
            artifactStreamAttributes.getImageName(), 250);
    return wrapNewBuildsWithLabels(
        builds.stream().map(ArtifactConfigMapper::toBuildDetails).collect(Collectors.toList()),
        artifactStreamAttributes, dockerConfig);
  }

  @Override
  public List<JobDetails> getJobs(
      DockerConfig dockerConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", SRE);
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, DockerConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", SRE);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      DockerConfig dockerConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", SRE);
  }

  @Override
  public Map<String, String> getPlans(DockerConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", SRE);
  }

  @Override
  public Map<String, String> getPlans(DockerConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(String jobName, DockerConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", SRE);
  }

  @Override
  public boolean validateArtifactServer(DockerConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(config, encryptedDataDetails, false);
    return dockerRegistryService.validateCredentials(DockerConfigToInternalMapper.toDockerInternalConfig(config));
  }

  @Override
  public boolean validateArtifactSource(DockerConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    encryptionService.decrypt(config, encryptionDetails, false);
    return dockerRegistryService.verifyImageName(
        DockerConfigToInternalMapper.toDockerInternalConfig(config), artifactStreamAttributes.getImageName());
  }

  @Override
  public Map<String, String> getBuckets(
      DockerConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Artifact Stream", USER);
  }

  @Override
  public List<String> getSmbPaths(DockerConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Docker Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      DockerConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by Docker Build Service", WingsException.USER);
  }

  @Override
  public List<Map<String, String>> getLabels(ArtifactStreamAttributes artifactStreamAttributes, List<String> buildNos,
      DockerConfig dockerConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(dockerConfig, encryptionDetails, false);
    return dockerRegistryService.getLabels(DockerConfigToInternalMapper.toDockerInternalConfig(dockerConfig),
        artifactStreamAttributes.getImageName(), buildNos);
  }

  @Override
  public ArtifactMetaInfo getArtifactMetaInfo(ArtifactStreamAttributes artifactStreamAttributes, String buildNo,
      DockerConfig dockerConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(dockerConfig, encryptionDetails, false);
    return dockerRegistryService.getArtifactMetaInfo(DockerConfigToInternalMapper.toDockerInternalConfig(dockerConfig),
        artifactStreamAttributes.getImageName(), buildNo);
  }

  @Override
  public BuildDetails getBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes, DockerConfig config,
      List<EncryptedDataDetail> encryptionDetails, String buildNo) {
    BuildDetailsInternal build = dockerRegistryService.verifyBuildNumber(
        DockerConfigToInternalMapper.toDockerInternalConfig(config), artifactStreamAttributes.getImageName(), buildNo);
    if (build == null) {
      return null;
    }
    List<BuildDetails> buildDetails = wrapNewBuildsWithLabels(
        Collections.singletonList(ArtifactConfigMapper.toBuildDetails(build)), artifactStreamAttributes, config);
    if (isNotEmpty(buildDetails)) {
      return buildDetails.get(0);
    }
    return null;
  }
}
