/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.utils.ArtifactType.DOCKER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactoryConfigToArtifactoryRequestMapper;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotSupportedException;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sgurubelli on 6/28/17.
 */
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactoryBuildServiceImpl implements ArtifactoryBuildService {
  public static final int MANUAL_PULL_ARTIFACTORY_LIMIT = 1000;
  @Inject private ArtifactoryService artifactoryService;
  @Inject private ArtifactoryClientImpl artifactoryClient;
  @Inject private EncryptionService encryptionService;

  @Override
  public List<Map<String, String>> getLabels(ArtifactStreamAttributes artifactStreamAttributes, List<String> buildNos,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails) {
    if (artifactStreamAttributes.getArtifactType() != DOCKER) {
      return List.of();
    }
    ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
        artifactoryConfig, encryptionService, encryptionDetails);
    return artifactoryClient.getLabels(artifactoryRequest, artifactStreamAttributes.getImageName(),
        artifactStreamAttributes.getJobName(), buildNos.get(0));
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.ARTIFACTORY.name());
    return wrapNewBuildsWithLabels(
        getBuilds(appId, artifactStreamAttributes, artifactoryConfig, encryptionDetails, MANUAL_PULL_ARTIFACTORY_LIMIT),
        artifactStreamAttributes, artifactoryConfig);
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, int limit) {
    return wrapNewBuildsWithLabels(
        getBuildsInternal(appId, artifactStreamAttributes, artifactoryConfig, encryptionDetails, limit),
        artifactStreamAttributes, artifactoryConfig);
  }

  private List<BuildDetails> getBuildsInternal(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, int limit) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.ARTIFACTORY.name());
    ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
        artifactoryConfig, encryptionService, encryptionDetails);
    if (!appId.equals(GLOBAL_APP_ID)) {
      if (artifactStreamAttributes.getArtifactType() == DOCKER
          || RepositoryType.docker.name().equalsIgnoreCase(artifactStreamAttributes.getRepositoryType())) {
        return artifactoryService.getBuilds(artifactoryRequest, artifactStreamAttributes, limit == -1 ? 1000 : limit);
      } else {
        return artifactoryService.getFilePaths(artifactoryRequest, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getArtifactPattern(), artifactStreamAttributes.getRepositoryType(),
            limit == -1 ? ARTIFACT_RETENTION_SIZE : limit);
      }
    } else {
      if (artifactStreamAttributes.getRepositoryType().equals(RepositoryType.docker.name())) {
        return artifactoryService.getBuilds(artifactoryRequest, artifactStreamAttributes, limit == -1 ? 1000 : limit);
      } else {
        return artifactoryService.getFilePaths(artifactoryRequest, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getArtifactPattern(), artifactStreamAttributes.getRepositoryType(),
            limit == -1 ? ARTIFACT_RETENTION_SIZE : limit);
      }
    }
  }

  @Override
  public List<JobDetails> getJobs(
      ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    throw new NotSupportedException();
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails) {
    if (isEmpty(groupId)) {
      ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
          config, encryptionService, encryptionDetails);
      log.info("Retrieving {} repo paths.", jobName);
      List<String> repoPaths = artifactoryService.getRepoPaths(artifactoryRequest, jobName);
      log.info("Retrieved {} repo paths.", repoPaths.size());
      return repoPaths;
    }
    return new ArrayList<>();
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails) {
    return null;
  }

  @Override
  public Map<String, String> getPlans(ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails) {
    ArtifactoryConfigRequest artifactoryRequest =
        ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(config, encryptionService, encryptionDetails);
    return artifactoryService.getRepositories(artifactoryRequest);
  }

  @Override
  public Map<String, String> getPlans(ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType) {
    ArtifactoryConfigRequest artifactoryRequest =
        ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(config, encryptionService, encryptionDetails);
    if (RepositoryType.docker.name().equalsIgnoreCase(repositoryType) || artifactType == DOCKER) {
      return artifactoryService.getRepositories(artifactoryRequest, RepositoryType.docker);
    }
    return artifactoryService.getRepositories(artifactoryRequest, repositoryType);
  }

  @Override
  public Map<String, String> getPlans(
      ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails, RepositoryType repositoryType) {
    ArtifactoryConfigRequest artifactoryRequest =
        ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(config, encryptionService, encryptionDetails);
    return artifactoryService.getRepositories(artifactoryRequest, repositoryType);
  }

  @Override
  public List<String> getGroupIds(
      String repoType, ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails) {
    log.info("Retrieving {} docker images.", repoType);
    ArtifactoryConfigRequest artifactoryRequest =
        ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(config, encryptionService, encryptionDetails);
    List<String> repoPaths = artifactoryService.getRepoPaths(artifactoryRequest, repoType);
    log.info("Retrieved {} docker images.", repoPaths.size());
    return repoPaths;
  }

  @Override
  public List<String> getGroupIds(String repositoryName, String repositoryType, ArtifactoryConfig config,
      List<EncryptedDataDetail> encryptionDetails) {
    log.info("Retrieving {} docker images.", repositoryName);
    ArtifactoryConfigRequest artifactoryRequest =
        ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(config, encryptionService, encryptionDetails);
    List<String> repoPaths = artifactoryService.getRepoPaths(artifactoryRequest, repositoryName);
    log.info("Retrieved {} docker images.", repoPaths.size());
    return repoPaths;
  }

  @Override
  public boolean validateArtifactServer(ArtifactoryConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    if (!connectableHttpUrl(config.getArtifactoryUrl(), false)) {
      throw new InvalidArtifactServerException(
          "Could not reach Artifactory Server at : " + config.getArtifactoryUrl(), USER);
    }
    ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
        config, encryptionService, encryptedDataDetails);
    return artifactoryClient.isRunning(artifactoryRequest);
  }

  @Override
  public boolean validateArtifactSource(ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    if (artifactStreamAttributes.getArtifactPattern() != null) {
      ArtifactoryConfigRequest artifactoryRequest = ArtifactoryConfigToArtifactoryRequestMapper.toArtifactoryRequest(
          config, encryptionService, encryptionDetails);
      return artifactoryService.validateArtifactPath(artifactoryRequest, artifactStreamAttributes.getJobName(),
          artifactStreamAttributes.getArtifactPattern(), artifactStreamAttributes.getRepositoryType());
    }
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      ArtifactoryConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Artifactory");
  }

  @Override
  public List<String> getSmbPaths(ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Artifactory Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by Artifactory Build Service", WingsException.USER);
  }
}
