/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by sgurubelli on 6/20/17.
 */
@OwnedBy(CDC)
public interface ArtifactoryBuildService extends BuildService<ArtifactoryConfig> {
  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_BUILDS)
  List<Map<String, String>> getLabels(ArtifactStreamAttributes artifactStreamAttributes, List<String> buildNos,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, int limit);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_JOBS)
  List<JobDetails> getJobs(
      ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_PLANS)
  Map<String, String> getPlans(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_PLANS)
  Map<String, String> getPlans(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_PLANS)
  Map<String, String> getPlans(
      ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails, RepositoryType repositoryType);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_ARTIFACTORY_PATHS)
  List<String> getArtifactPaths(
      String jobName, String groupId, ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_GROUP_IDS)
  List<String> getGroupIds(
      String repoType, ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_GET_GROUP_IDS)
  List<String> getGroupIds(String repositoryName, String repositoryType, ArtifactoryConfig config,
      List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_LAST_SUCCSSFUL_BUILD)
  BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_VALIDATE_ARTIFACT_SERVER)
  boolean validateArtifactServer(ArtifactoryConfig artifactoryConfig, List<EncryptedDataDetail> encryptedDataDetails);

  @Override
  @DelegateTaskType(TaskType.ARTIFACTORY_VALIDATE_ARTIFACT_STREAM)
  boolean validateArtifactSource(ArtifactoryConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes);
}
