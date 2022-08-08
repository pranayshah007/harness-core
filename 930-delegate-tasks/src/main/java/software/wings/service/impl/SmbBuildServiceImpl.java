/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SmbConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.smb.SmbService;
import software.wings.service.intfc.SmbBuildService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class SmbBuildServiceImpl implements SmbBuildService {
  @Inject private SmbService smbService;
  @Inject private SmbHelperService smbHelperService;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails) {
    String artifactName = artifactStreamAttributes.getArtifactName();
    return wrapNewBuildsWithLabels(smbService.getBuildDetails(smbConfig, encryptionDetails,
                                       Lists.newArrayList(artifactName), artifactName.contains("*")),
        artifactStreamAttributes, smbConfig);
  }

  @Override
  public List<String> getArtifactPaths(
      String jobName, String groupId, SmbConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by SMB Build Service", USER);
  }

  @Override
  public boolean validateArtifactServer(SmbConfig config, List<EncryptedDataDetail> encryptedDataDetails) {
    String smbConnectionHost = smbHelperService.getSMBConnectionHost(config.getSmbUrl());
    if (!smbHelperService.isConnectibleSOBServer(smbConnectionHost)) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER, USER)
          .addParam("message", "Could not reach SMB Server at : " + config.getSmbUrl());
    }
    return smbService.isRunning(config, encryptedDataDetails);
  }

  @Override
  public List<JobDetails> getJobs(
      SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public List<String> getSmbPaths(SmbConfig config, List<EncryptedDataDetail> encryptionDetails) {
    List<String> artifactPaths = smbService.getArtifactPaths(config, encryptionDetails);
    log.info("Retrieved {} artifact paths from SMB server.", artifactPaths.size());
    return artifactPaths;
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public Map<String, String> getPlans(SmbConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public Map<String, String> getBuckets(
      SmbConfig smbConfig, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public Map<String, String> getPlans(
      SmbConfig config, List<EncryptedDataDetail> encryptionDetails, ArtifactType artifactType, String repositoryType) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public List<String> getGroupIds(String repoType, SmbConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public boolean validateArtifactSource(SmbConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      SmbConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by SMB Artifact Stream", WingsException.USER);
  }
}
