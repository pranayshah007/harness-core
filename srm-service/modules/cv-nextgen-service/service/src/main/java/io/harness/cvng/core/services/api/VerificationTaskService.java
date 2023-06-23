/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface VerificationTaskService {
  // use create with Map as input for tags.
  @Deprecated String createLiveMonitoringVerificationTask(String accountId, String cvConfigId, DataSourceType provider);
  @Deprecated String createSLIVerificationTask(String accountId, String sliId);
  @Deprecated
  String createDeploymentVerificationTask(
      String accountId, String cvConfigId, String verificationJobInstanceId, DataSourceType provider);
  String createLiveMonitoringVerificationTask(String accountId, String cvConfigId, Map<String, String> tags);
  String createSLIVerificationTask(String accountId, String sliId, Map<String, String> tags);
  String createCompositeSLOVerificationTask(String accountId, String sloId, Map<String, String> tags);
  String createDeploymentVerificationTask(
      String accountId, String cvConfigId, String verificationJobInstanceId, Map<String, String> tags);

  String getCVConfigId(String verificationTaskId);
  String getSliId(String verificationTaskId);
  String getCompositeSLOId(String verificationTaskId);
  String getVerificationJobInstanceId(String verificationTaskId);

  VerificationTask get(String verificationTaskId);
  Optional<VerificationTask> maybeGet(String verificationTaskId);
  String getVerificationTaskId(String accountId, String cvConfigId, String verificationJobInstanceId);
  Set<String> getVerificationTaskIds(String accountId, String verificationJobInstanceId);

  /**
   * This can return empty if mapping does not exist. Only use this if know that the mapping might not exist. Use
   * #getVerificationTasks otherwise.
   */
  Set<String> maybeGetVerificationTaskIds(String accountId, String verificationJobInstanceId);

  String getServiceGuardVerificationTaskId(String accountId, String cvConfigId);
  String getSLIVerificationTaskId(String accountId, String sliId);
  String getCompositeSLOVerificationTaskId(String accountId, String sloId);
  List<String> getSLIVerificationTaskIds(String accountId, List<String> sliIds);
  VerificationTask getLiveMonitoringTask(String accountId, String cvConfigId);
  VerificationTask getSLITask(String accountId, String sliId);
  VerificationTask getCompositeSLOTask(String accountId, String sloId);
  List<String> getServiceGuardVerificationTaskIds(String accountId, List<String> cvConfigIds);

  boolean isServiceGuardId(String verificationTaskId);
  void removeLiveMonitoringMappings(String accountId, String cvConfigId);
  Optional<String> findBaselineVerificationTaskId(
      String currentVerificationTaskId, VerificationJobInstance verificationJobInstance);
  List<String> getAllVerificationJobInstanceIdsForCVConfig(String cvConfigId);
  List<String> maybeGetVerificationTaskIds(List<String> verificationJobInstanceIds);
  void deleteVerificationTask(String taskId);
  List<VerificationTask> getVerificationTasksForGivenIds(Set<String> verificationTaskIds);
}
