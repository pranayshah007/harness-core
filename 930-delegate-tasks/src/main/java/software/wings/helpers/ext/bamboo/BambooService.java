/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.bamboo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by anubhaw on 11/29/16.
 */
@OwnedBy(CDC)
public interface BambooService {
  /**
   * Gets job keys.
   *
   * @param bambooConfig the bamboo config
   * @param planKey      the plan key
   * @return the job keys
   */
  List<String> getJobKeys(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey);

  /**
   * Gets plan keys.
   *
   * @param bambooConfig the bamboo config
   * @return the plan keys
   */
  Map<String, String> getPlanKeys(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * Gets last successful build.
   *
   * @param bambooConfig the bamboo config
   * @param planKey      the jobname
   * @return the last successful build
   */
  BuildDetails getLastSuccessfulBuild(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey);

  BuildDetails getLastSuccessfulBuild(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String planKey, List<String> artifactPaths);
  /**
   * Gets builds for job.
   * @param bambooConfig
   * @param encryptionDetails
   * @param planKey
   * @param artifactPaths
   * @param maxNumberOfBuilds
   * @return
   */
  List<BuildDetails> getBuilds(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey,
      List<String> artifactPaths, int maxNumberOfBuilds);

  /**
   * Gets artifact path.
   *
   * @param bambooConfig the bamboo config
   * @param planKey      the job name
   * @return the artifact path
   */
  List<String> getArtifactPath(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey);

  /**
   * Download artifacts for given buildNumber
   * @param bambooConfig
   * @param encryptionDetails
   * @param artifactStreamAttributes
   * @param buildNo
   * @param delegateId
   * @param taskId
   * @param accountId
   * @param res
   * @return
   */
  @SuppressWarnings("squid:S00107")
  Pair<String, InputStream> downloadArtifacts(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, String buildNo, String delegateId, String taskId,
      String accountId, ListNotifyResponseData res);

  /**
   * Is running boolean.
   *
   * @param bambooConfig the bamboo config
   * @return the boolean
   */
  boolean isRunning(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * Triggers Project Plan
   *
   * @param planKey    the plankey
   * @param parameters the parameters
   * @return Build Result Key {projectKey}-{buildKey}-{buildNumber}
   */
  String triggerPlan(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String planKey,
      Map<String, String> parameters);

  /**
   * Retrieves the bamboo build result
   *
   * @param bambooConfig   BambooConfig
   * @param buildResultKey Build result key {projectKey}-{buildKey}-{buildNumber}
   * @return
   */
  Result getBuildResult(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey);

  /**
   * Retrieves the bamboo build running result status
   *
   * @param bambooConfig   BambooConfig
   * @param buildResultKey Build result key {projectKey}-{buildKey}-{buildNumber}
   * @return
   */
  Status getBuildResultStatus(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey);

  long getFileSize(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String artifactFileName,
      String artifactFilePath);

  Pair<String, InputStream> downloadArtifact(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String artifactFileName, String artifactFilePath);
}
