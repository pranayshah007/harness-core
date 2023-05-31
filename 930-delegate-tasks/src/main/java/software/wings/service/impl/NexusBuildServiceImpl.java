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
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.validation.Validator.equalCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.NexusConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.NexusConfigToNexusRequestMapper;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by srinivas on 3/31/17.
 */
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
@Singleton
@Slf4j
public class NexusBuildServiceImpl implements NexusBuildService {
  @Inject private NexusService nexusService;
  @Inject private EncryptionService encryptionService;
  @Inject private NexusClientImpl nexusClient;

  @Override
  public Map<String, String> getPlans(NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return nexusClient.getRepositories(
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails));
  }

  @Override
  public Map<String, String> getPlans(NexusConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryFormat) {
    if (artifactType != ArtifactType.DOCKER && repositoryFormat != null
        && repositoryFormat.equals(RepositoryFormat.docker.name())) {
      throw new WingsException(format("Not supported for Artifact Type %s", artifactType), USER);
    }
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    if (artifactType == ArtifactType.DOCKER) {
      return nexusClient.getRepositories(nexusRequest, RepositoryFormat.docker.name());
    }
    return nexusClient.getRepositories(nexusRequest, repositoryFormat);
  }

  @Override
  public Map<String, String> getPlans(
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails, RepositoryFormat repositoryFormat) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    return nexusClient.getRepositories(nexusRequest, repositoryFormat.name());
  }

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return wrapNewBuildsWithLabels(
        getBuildsInternal(artifactStreamAttributes, config, encryptionDetails), artifactStreamAttributes, config);
  }

  @Override
  public BuildDetails getBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails, String buildNo) {
    List<BuildDetails> buildDetails =
        wrapNewBuildsWithLabels(getBuildInternal(artifactStreamAttributes, config, encryptionDetails, buildNo),
            artifactStreamAttributes, config);
    if (isNotEmpty(buildDetails)) {
      return buildDetails.get(0);
    }
    return null;
  }

  private List<BuildDetails> getBuildsInternal(ArtifactStreamAttributes artifactStreamAttributes, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.NEXUS.name());
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    if (artifactStreamAttributes.getArtifactType() != null
            && artifactStreamAttributes.getArtifactType() == ArtifactType.DOCKER
        || (artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.docker.name()))) {
      return nexusService.getBuilds(nexusRequest, artifactStreamAttributes, 50);
    } else if (artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.nuget.name())
        || artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.npm.name())) {
      return nexusService.getVersions(artifactStreamAttributes.getRepositoryFormat(), nexusRequest,
          artifactStreamAttributes.getJobName(), artifactStreamAttributes.getNexusPackageName(),
          artifactStreamAttributes.getSavedBuildDetailsKeys());
    } else if (artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.raw.name())) {
      return nexusService.getPackageNames(nexusRequest, artifactStreamAttributes.getJobName(),
          artifactStreamAttributes.getNexusPackageName(), artifactStreamAttributes.getRepositoryFormat());
    } else {
      return nexusService.getVersions(nexusRequest, artifactStreamAttributes.getJobName(),
          artifactStreamAttributes.getGroupId(), artifactStreamAttributes.getArtifactName(),
          artifactStreamAttributes.getExtension(), artifactStreamAttributes.getClassifier());
    }
  }

  private List<BuildDetails> getBuildInternal(ArtifactStreamAttributes artifactStreamAttributes, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails, String buildNo) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.NEXUS.name());
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    if (artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.nuget.name())
        || artifactStreamAttributes.getRepositoryFormat().equals(RepositoryFormat.npm.name())) {
      return nexusService.getVersion(artifactStreamAttributes.getRepositoryFormat(), nexusRequest,
          artifactStreamAttributes.getJobName(), artifactStreamAttributes.getNexusPackageName(), buildNo);
    } else {
      return nexusService.getVersion(nexusRequest, artifactStreamAttributes.getJobName(),
          artifactStreamAttributes.getGroupId(), artifactStreamAttributes.getArtifactName(),
          artifactStreamAttributes.getExtension(), artifactStreamAttributes.getClassifier(), buildNo);
    }
  }

  @Override
  public List<JobDetails> getJobs(
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    List<String> jobNames = Lists.newArrayList(nexusClient.getRepositories(nexusRequest).keySet());
    return wrapJobNameWithJobDetails(jobNames);
  }

  @Override
  public List<String> getArtifactPaths(
      String repoId, String groupId, NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    if (isBlank(groupId)) {
      return nexusService.getArtifactPaths(nexusRequest, repoId);
    }
    return nexusService.getArtifactNames(nexusRequest, repoId, groupId);
  }

  @Override
  public List<String> getArtifactPaths(String repoId, String groupId, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails, String repositoryFormat) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    if (isBlank(groupId)) {
      return nexusService.getArtifactPaths(nexusRequest, repoId);
    }
    return nexusService.getArtifactNames(nexusRequest, repoId, groupId, repositoryFormat);
  }

  @Override
  public List<String> getGroupIds(
      String repositoryName, NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    return nexusService.getGroupIdPaths(nexusRequest, repositoryName, null);
  }

  @Override
  public List<String> getGroupIds(
      String repositoryName, String repositoryFormat, NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    return nexusService.getGroupIdPaths(nexusRequest, repositoryName, repositoryFormat);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ArtifactStreamType.NEXUS.name());
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
    return wrapLastSuccessfulBuildWithLabels(
        nexusService.getLatestVersion(nexusRequest, artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getGroupId(), artifactStreamAttributes.getArtifactName()),
        artifactStreamAttributes, config);
  }

  @Override
  public boolean validateArtifactServer(NexusConfig nexusConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    if (!connectableHttpUrl(nexusConfig.getNexusUrl(), false)) {
      throw new InvalidArtifactServerException("Could not reach Nexus Server at : " + nexusConfig.getNexusUrl(), USER);
    }
    NexusRequest nexusRequest =
        NexusConfigToNexusRequestMapper.toNexusRequest(nexusConfig, encryptionService, encryptedDataDetails);
    return nexusClient.isRunning(nexusRequest);
  }

  @Override
  public boolean validateArtifactSource(NexusConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    if (isNotEmpty(artifactStreamAttributes.getExtension()) || isNotEmpty(artifactStreamAttributes.getClassifier())) {
      NexusRequest nexusRequest =
          NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, encryptionDetails);
      return nexusService.existsVersion(nexusRequest, artifactStreamAttributes.getJobName(),
          artifactStreamAttributes.getGroupId(), artifactStreamAttributes.getArtifactName(),
          artifactStreamAttributes.getExtension(), artifactStreamAttributes.getClassifier());
    }
    return true;
  }

  @Override
  public Map<String, String> getBuckets(
      NexusConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Nexus Artifact Stream");
  }

  @Override
  public List<String> getSmbPaths(NexusConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by Nexus Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by Nexus Build Service", WingsException.USER);
  }
}
