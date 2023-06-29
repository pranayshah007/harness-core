/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.helpers.ext.ecr.EcrService.MAX_NO_OF_IMAGES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.ecr.EcrService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.ArtifactConfigMapper;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;
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
public class EcrBuildServiceImpl implements EcrBuildService {
  @Inject private EcrService ecrService;
  @Inject private EncryptionService encryptionService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private AwsEcrHelperServiceDelegate ecrServiceDelegate;

  @Override
  public List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    equalCheck(artifactStreamAttributes.getArtifactStreamType(), ECR.name());
    encryptionService.decrypt(awsConfig, encryptionDetails, false);

    return wrapNewBuildsWithLabels(
        ecrService
            .getBuilds(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), null,
                ecrServiceDelegate.getEcrImageUrl(awsConfig, encryptionDetails, artifactStreamAttributes.getRegion(),
                    artifactStreamAttributes.getImageName()),
                artifactStreamAttributes.getRegion(), artifactStreamAttributes.getImageName(), MAX_NO_OF_IMAGES)
            .stream()
            .map(ArtifactConfigMapper::toBuildDetails)
            .collect(Collectors.toList()),
        artifactStreamAttributes, awsConfig);
  }

  @Override
  public List<JobDetails> getJobs(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    List<String> regions = ecrService.listRegions(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return wrapJobNameWithJobDetails(regions);
  }

  @Override
  public List<String> getArtifactPaths(
      String region, String groupId, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return ecrService.listEcrRegistry(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region, null);
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Artifact Stream", SRE);
  }

  @Override
  public Map<String, String> getPlans(AwsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    return getJobs(config, encryptionDetails, Optional.empty())
        .stream()
        .collect(Collectors.toMap(JobDetails::getJobName, JobDetails::getJobName));
  }

  @Override
  public Map<String, String> getPlans(
      AwsConfig config, List<EncryptedDataDetail> encryptionDetails, ArtifactType artifactType, String repositoryType) {
    return getPlans(config, encryptionDetails);
  }

  @Override
  public List<String> getGroupIds(String jobName, AwsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Artifact Stream", USER);
  }

  @Override
  public boolean validateArtifactServer(AwsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Artifact Stream", USER);
  }

  @Override
  public boolean validateArtifactSource(AwsConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    encryptionService.decrypt(config, encryptionDetails, false);
    return ecrService.verifyRepository(AwsConfigToInternalMapper.toAwsInternalConfig(config),
        artifactStreamAttributes.getRegion(), null, artifactStreamAttributes.getImageName());
  }

  @Override
  public Map<String, String> getBuckets(
      AwsConfig config, String projectId, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Artifact Stream", USER);
  }

  @Override
  public List<String> getSmbPaths(AwsConfig config, List<EncryptedDataDetail> encryptionDetails) {
    throw new InvalidRequestException("Operation not supported by ECR Build Service", WingsException.USER);
  }

  @Override
  public List<String> getArtifactPathsByStreamType(
      AwsConfig config, List<EncryptedDataDetail> encryptionDetails, String streamType) {
    throw new InvalidRequestException("Operation not supported by ECR Build Service", WingsException.USER);
  }

  @Override
  public List<Map<String, String>> getLabels(ArtifactStreamAttributes artifactStreamAttributes, List<String> buildNos,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    return ecrService.getLabels(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), null,
        artifactStreamAttributes.getImageName(), artifactStreamAttributes.getRegion(), buildNos);
  }
}
