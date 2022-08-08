/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BambooConfig;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by peeyushaggarwal on 1/12/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface BambooBuildService extends BuildService<BambooConfig> {
  @Override
  @DelegateTaskType(TaskType.BAMBOO_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes, BambooConfig config,
      List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_GET_BUILDS)
  List<BuildDetails> getBuilds(String appId, ArtifactStreamAttributes artifactStreamAttributes, BambooConfig config,
      List<EncryptedDataDetail> encryptionDetails, int limit);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_GET_JOBS)
  List<JobDetails> getJobs(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, Optional<String> parentJobName);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_GET_ARTIFACT_PATHS)
  List<String> getArtifactPaths(
      String jobName, String groupId, BambooConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_LAST_SUCCESSFUL_BUILD)
  BuildDetails getLastSuccessfulBuild(String appId, ArtifactStreamAttributes artifactStreamAttributes,
      BambooConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_GET_PLANS)
  Map<String, String> getPlans(BambooConfig config, List<EncryptedDataDetail> encryptionDetails);

  @Override
  @DelegateTaskType(TaskType.BAMBOO_VALIDATE_ARTIFACT_SERVER)
  boolean validateArtifactServer(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails);
}
