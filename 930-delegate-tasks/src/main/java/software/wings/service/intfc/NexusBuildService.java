/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.NexusConfig;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by srinivas on 3/31/17.
 */
@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
public interface NexusBuildService extends BuildService<NexusConfig> {
  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_JOBS)
  List<JobDetails> getJobs(
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_PLANS)
  Map<String, String> getPlans(NexusConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_PLANS)
  Map<String, String> getPlans(NexusConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactType artifactType, String repositoryType);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_PLANS)
  Map<String, String> getPlans(
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails, RepositoryFormat repositoryFormat);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_ARTIFACT_PATHS)
  List<String> getArtifactPaths(
      String jobName, String groupId, NexusConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_ARTIFACT_PATHS)
  List<String> getArtifactPaths(String jobName, String groupId, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails, String repositoryFormat);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_GROUP_IDS)
  List<String> getGroupIds(String repositoryName, NexusConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_GROUP_IDS)
  List<String> getGroupIds(
      String repositoryName, String repositoryType, NexusConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_GET_BUILDS)
  BuildDetails getBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes, NexusConfig config,
      List<EncryptedDataDetail> encryptionDetails, String buildNo);

  @Override
  @DelegateTaskType(TaskType.NEXUS_LAST_SUCCESSFUL_BUILD)
  BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      NexusConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_VALIDATE_ARTIFACT_SERVER)
  boolean validateArtifactServer(NexusConfig config, List<EncryptedDataDetail> encryptedDataDetails);

  @Override
  @DelegateTaskType(TaskType.NEXUS_VALIDATE_ARTIFACT_STREAM)
  boolean validateArtifactSource(NexusConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes);
}
