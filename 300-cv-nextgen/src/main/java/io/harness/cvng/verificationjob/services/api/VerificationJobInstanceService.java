/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.services.api;

import io.harness.cvng.activity.beans.ActivityVerificationSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ProgressLog;

import java.util.List;
import java.util.Optional;

public interface VerificationJobInstanceService {
  String create(VerificationJobInstance verificationJobInstance);
  List<String> create(List<VerificationJobInstance> verificationJobInstances);
  List<VerificationJobInstance> get(List<String> verificationJobInstanceIds);
  VerificationJobInstance getVerificationJobInstance(String verificationJobInstanceId);
  void processVerificationJobInstance(VerificationJobInstance verificationJobInstance);
  void createDataCollectionTasks(VerificationJobInstance verificationJobInstance);
  void logProgress(ProgressLog progressLog);
  Optional<TimeRange> getPreDeploymentTimeRange(String verificationJobInstanceId);
  // TODO: we need more filters to select last successful execution. We should add monitored service identifier as a
  // filter.
  Optional<String> getLastSuccessfulTestVerificationJobExecutionId(
      String accountId, String orgIdentifier, String projectIdentifier);
  ActivityVerificationSummary getActivityVerificationSummary(List<VerificationJobInstance> verificationJobInstances);
  DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary getDeploymentVerificationJobInstanceSummary(
      List<String> verificationJobInstanceIds);

  List<CVConfig> getCVConfigsForVerificationJob(VerificationJob verificationJob);
  void markTimedOutIfNoProgress(VerificationJobInstance verificationJobInstance);
  CVConfig getEmbeddedCVConfig(String cvConfigId, String verificationJobInstanceId);

  void abort(List<String> verificationJobInstanceIds);
  List<String> getCVConfigIdsForVerificationJobInstance(
      String verificationJobInstanceId, List<String> filterIdentifiers);
  List<String> createDemoInstances(List<VerificationJobInstance> verificationJobInstances);
  List<ProgressLog> getProgressLogs(String verificationJobInstanceId);
}
