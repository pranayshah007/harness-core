/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.CVConstants.DEPLOYMENT;
import static io.harness.cvng.CVConstants.LIVE_MONITORING;
import static io.harness.cvng.CVConstants.TAG_DATA_SOURCE;
import static io.harness.cvng.CVConstants.TAG_VERIFICATION_TYPE;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.cvng.CVConstants;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.CompositeSLOInfo;
import io.harness.cvng.core.entities.VerificationTask.CompositeSLOInfo.CompositeSLOInfoKeys;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo.DeploymentInfoKeys;
import io.harness.cvng.core.entities.VerificationTask.LiveMonitoringInfo;
import io.harness.cvng.core.entities.VerificationTask.LiveMonitoringInfo.LiveMonitoringInfoKeys;
import io.harness.cvng.core.entities.VerificationTask.SLIInfo;
import io.harness.cvng.core.entities.VerificationTask.SLIInfo.SLIInfoKeys;
import io.harness.cvng.core.entities.VerificationTask.TaskInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.entities.VerificationTask.VerificationTaskKeys;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery.QueryChecks;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.groovy.util.Maps;

@Singleton
public class VerificationTaskServiceImpl implements VerificationTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;
  // TODO: optimize this and add caching support. Since this collection is immutable
  @Override
  public String createLiveMonitoringVerificationTask(String accountId, String cvConfigId, DataSourceType provider) {
    return createLiveMonitoringVerificationTask(
        accountId, cvConfigId, Maps.of(TAG_DATA_SOURCE, provider.name(), TAG_VERIFICATION_TYPE, LIVE_MONITORING));
  }

  @Override
  public String createSLIVerificationTask(String accountId, String sliId) {
    return createSLIVerificationTask(accountId, sliId, Collections.emptyMap());
  }

  public String createDeploymentVerificationTask(
      String accountId, String cvConfigId, String verificationJobInstanceId, DataSourceType provider) {
    return createDeploymentVerificationTask(accountId, cvConfigId, verificationJobInstanceId,
        Maps.of(TAG_DATA_SOURCE, provider.name(), TAG_VERIFICATION_TYPE, DEPLOYMENT));
  }

  @Override
  public String createLiveMonitoringVerificationTask(String accountId, String cvConfigId, Map<String, String> tags) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(cvConfigId);
    // TODO: Change to new generated uuid in a separate PR since it needs more validation.
    VerificationTask verificationTask = VerificationTask.builder()
                                            .uuid(cvConfigId)
                                            .accountId(accountId)
                                            .taskInfo(LiveMonitoringInfo.builder().cvConfigId(cvConfigId).build())
                                            .tags(tags)
                                            .build();
    hPersistence.save(verificationTask);
    return verificationTask.getUuid();
  }

  @Override
  public String createSLIVerificationTask(String accountId, String sliId, Map<String, String> tags) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(sliId);
    // TODO: Change to new generated uuid in a separate PR since it needs more validation.
    VerificationTask verificationTask = VerificationTask.builder()
                                            .uuid(sliId)
                                            .accountId(accountId)
                                            .tags(tags)
                                            .taskInfo(SLIInfo.builder().sliId(sliId).build())
                                            .build();
    hPersistence.save(verificationTask);
    return verificationTask.getUuid();
  }

  @Override
  public String createCompositeSLOVerificationTask(String accountId, String sloId, Map<String, String> tags) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(sloId);
    // TODO: Change to new generated uuid in a separate PR since it needs more validation.
    VerificationTask verificationTask = VerificationTask.builder()
                                            .uuid(sloId)
                                            .accountId(accountId)
                                            .tags(tags)
                                            .taskInfo(CompositeSLOInfo.builder().sloId(sloId).build())
                                            .build();
    hPersistence.save(verificationTask);
    return verificationTask.getUuid();
  }

  public String createDeploymentVerificationTask(
      String accountId, String cvConfigId, String verificationJobInstanceId, Map<String, String> tags) {
    Preconditions.checkNotNull(accountId, "accountId can not be null");
    Preconditions.checkNotNull(cvConfigId, "cvConfigId can not be null");
    Preconditions.checkNotNull(verificationJobInstanceId, "verificationJobInstanceId can not be null");
    checkIfVerificationTaskAlreadyExists(accountId, cvConfigId, verificationJobInstanceId);
    VerificationTask verificationTask =
        VerificationTask.builder()
            .accountId(accountId)
            .taskInfo(DeploymentInfo.builder()
                          .cvConfigId(cvConfigId)
                          .verificationJobInstanceId(verificationJobInstanceId)
                          .build())
            .validUntil(Date.from(clock.instant().plus(CVConstants.MAX_DATA_RETENTION_DURATION)))
            .tags(tags)
            .build();
    hPersistence.save(verificationTask);
    return verificationTask.getUuid();
  }

  private void checkIfVerificationTaskAlreadyExists(
      String accountId, String cvConfigId, String verificationJobInstanceId) {
    Preconditions.checkState(getDeploymentTask(accountId, cvConfigId, verificationJobInstanceId) == null,
        "VerificationTask already exist for accountId %s, cvConfigId %s, verificationJobInstance %s", accountId,
        cvConfigId, verificationJobInstanceId);
  }

  @Override
  public String getCVConfigId(String verificationTaskId) {
    VerificationTask verificationTask = get(verificationTaskId);
    if (verificationTask.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT)) {
      return ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId();
    }
    if (verificationTask.getTaskInfo().getTaskType().equals(TaskType.LIVE_MONITORING)) {
      return ((LiveMonitoringInfo) verificationTask.getTaskInfo()).getCvConfigId();
    }
    throw new IllegalStateException("Verification task is of not the right type to have CVConfig, type : "
        + verificationTask.getTaskInfo().getTaskType());
  }

  @Override
  public String getSliId(String verificationTaskId) {
    VerificationTask verificationTask = get(verificationTaskId);
    if (verificationTask.getTaskInfo().getTaskType().equals(TaskType.SLI)) {
      return ((SLIInfo) verificationTask.getTaskInfo()).getSliId();
    }
    throw new IllegalStateException("Verification task is of not the right type to have ServiceLevelIndicator, type: "
        + verificationTask.getTaskInfo().getTaskType());
  }

  @Override
  public String getCompositeSLOId(String verificationTaskId) {
    VerificationTask verificationTask = get(verificationTaskId);
    if (verificationTask.getTaskInfo().getTaskType().equals(TaskType.COMPOSITE_SLO)) {
      return ((CompositeSLOInfo) verificationTask.getTaskInfo()).getSloId();
    }
    throw new IllegalStateException("Verification task is of not the right type to have Composite SLO, type: "
        + verificationTask.getTaskInfo().getTaskType());
  }

  @Override
  public String getVerificationJobInstanceId(String verificationTaskId) {
    VerificationTask verificationTask = get(verificationTaskId);
    Preconditions.checkNotNull(verificationTask.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT),
        "VerificationTask should be of Deployment type");
    return ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId();
  }

  @Override
  public VerificationTask get(String verificationTaskId) {
    return maybeGet(verificationTaskId)
        .orElseThrow(
            () -> new IllegalStateException("Invalid verificationTaskId. Verification mapping does not exist."));
  }
  @Override
  public Optional<VerificationTask> maybeGet(String verificationTaskId) {
    return Optional.ofNullable(hPersistence.get(VerificationTask.class, verificationTaskId));
  }

  @Override
  public String getVerificationTaskId(String accountId, String cvConfigId, String verificationJobInstanceId) {
    Optional<String> maybeVerificationTaskId =
        maybeGetVerificationTaskId(accountId, cvConfigId, verificationJobInstanceId);
    return maybeVerificationTaskId.orElseThrow(
        () -> new IllegalStateException("VerificationTask mapping does not exist"));
  }

  private Optional<String> maybeGetVerificationTaskId(
      String accountId, String cvConfigId, String verificationJobInstanceId) {
    Preconditions.checkNotNull(verificationJobInstanceId, "verificationJobInstanceId should not be null");
    return Optional.ofNullable(getDeploymentTask(accountId, cvConfigId, verificationJobInstanceId))
        .map(VerificationTask::getUuid);
  }

  @Override
  public Set<String> getVerificationTaskIds(String accountId, String verificationJobInstanceId) {
    Set<String> results = maybeGetVerificationTaskIds(accountId, verificationJobInstanceId);
    Preconditions.checkState(!results.isEmpty(), "No verification task mapping exist for verificationJobInstanceId %s",
        verificationJobInstanceId);
    return results;
  }

  @Override
  public Set<String> maybeGetVerificationTaskIds(String accountId, String verificationJobInstanceId) {
    Set<String> verificationTasksIds = new HashSet<>();
    verificationTasksIds.addAll(
        hPersistence.createQuery(VerificationTask.class, EnumSet.of(QueryChecks.COUNT))
            .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.DEPLOYMENT)
            .filter(VerificationTaskKeys.taskInfo + "." + DeploymentInfoKeys.verificationJobInstanceId,
                verificationJobInstanceId)
            .project(VerificationTaskKeys.uuid, true)
            .asList()
            .stream()
            .map(VerificationTask::getUuid)
            .collect(Collectors.toList()));

    return verificationTasksIds;
  }

  @Override
  public String getServiceGuardVerificationTaskId(String accountId, String cvConfigId) {
    VerificationTask result = getLiveMonitoringTask(accountId, cvConfigId);
    Preconditions.checkNotNull(
        result, "VerificationTask mapping does not exist for cvConfigId %s. Please check cvConfigId", cvConfigId);
    return result.getUuid();
  }

  @Override
  public String getSLIVerificationTaskId(String accountId, String sliId) {
    VerificationTask result = getSLITask(accountId, sliId);
    Preconditions.checkNotNull(
        result, "VerificationTask mapping does not exist for SLI Id %s. Please check sliId", sliId);
    return result.getUuid();
  }

  @Override
  public String getCompositeSLOVerificationTaskId(String accountId, String sloId) {
    VerificationTask result = getCompositeSLOTask(accountId, sloId);
    Preconditions.checkNotNull(
        result, "VerificationTask mapping does not exist for Composite SLO Id %s. Please check sloId", sloId);
    return result.getUuid();
  }

  @Override
  public List<String> getSLIVerificationTaskIds(String accountId, List<String> sliIds) {
    List<VerificationTask> result = getSLITasks(accountId, sliIds);
    Preconditions.checkNotNull(
        result, "VerificationTask mapping does not exist for SLI Id %s. Please check sliId", sliIds);
    return result.stream().map(VerificationTask::getUuid).collect(Collectors.toList());
  }

  @Override
  public List<String> getServiceGuardVerificationTaskIds(String accountId, List<String> cvConfigIds) {
    List<String> verificationTasksIds = new ArrayList<>();
    verificationTasksIds.addAll(
        hPersistence.createQuery(VerificationTask.class, EnumSet.of(QueryChecks.COUNT))
            .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.LIVE_MONITORING)
            .field(VerificationTaskKeys.taskInfo + "." + LiveMonitoringInfoKeys.cvConfigId)
            .in(cvConfigIds)
            .project(VerificationTaskKeys.uuid, true)
            .asList()
            .stream()
            .map(VerificationTask::getUuid)
            .collect(Collectors.toList()));

    return verificationTasksIds;
  }

  @Override
  public boolean isServiceGuardId(String verificationTaskId) {
    VerificationTask verificationTask = get(verificationTaskId);
    return VerificationTask.TaskType.LIVE_MONITORING.equals(verificationTask.getTaskInfo().getTaskType());
  }

  @Override
  public void removeLiveMonitoringMappings(String accountId, String cvConfigId) {
    hPersistence.delete(createQueryForLiveMonitoring(accountId, cvConfigId));
  }

  @Override
  public Optional<String> findBaselineVerificationTaskId(
      String currentVerificationTaskId, VerificationJobInstance verificationJobInstance) {
    Preconditions.checkState(verificationJobInstance.getResolvedJob() instanceof TestVerificationJob,
        "getResolvedJob has to be instance of TestVerificationJob");
    TestVerificationJob testVerificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = testVerificationJob.getBaselineVerificationJobInstanceId();
    if (baselineVerificationJobInstanceId == null) {
      return Optional.empty();
    }
    String cvConfigId = getCVConfigId(currentVerificationTaskId);
    return maybeGetVerificationTaskId(
        verificationJobInstance.getAccountId(), cvConfigId, baselineVerificationJobInstanceId);
  }

  @Override
  public List<String> getAllVerificationJobInstanceIdsForCVConfig(String cvConfigId) {
    List<String> verificationJobInstanceIds = new ArrayList<>();
    verificationJobInstanceIds.addAll(
        hPersistence.createQuery(VerificationTask.class, EnumSet.of(QueryChecks.COUNT))
            .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.DEPLOYMENT)
            .filter(VerificationTaskKeys.taskInfo + "." + DeploymentInfoKeys.cvConfigId, cvConfigId)
            .asList()
            .stream()
            .map(verificationTask -> ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId())
            .collect(Collectors.toList()));

    return verificationJobInstanceIds;
  }

  @Override
  public List<String> maybeGetVerificationTaskIds(List<String> verificationJobInstanceIds) {
    List<String> verificationTasksIds = new ArrayList<>();
    verificationTasksIds.addAll(
        hPersistence.createQuery(VerificationTask.class, EnumSet.of(QueryChecks.COUNT))
            .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.DEPLOYMENT)
            .field(VerificationTaskKeys.taskInfo + "." + DeploymentInfoKeys.verificationJobInstanceId)
            .in(verificationJobInstanceIds)
            .asList()
            .stream()
            .map(VerificationTask::getUuid)
            .collect(Collectors.toList()));

    return verificationTasksIds;
  }

  @Override
  public void deleteVerificationTask(String taskId) {
    hPersistence.delete(VerificationTask.class, taskId);
  }

  @Override
  public List<VerificationTask> getVerificationTasksForGivenIds(Set<String> verificationTaskIds) {
    return hPersistence.createQuery(VerificationTask.class, new HashSet<>())
        .field(VerificationTaskKeys.uuid)
        .in(verificationTaskIds)
        .asList();
  }

  private VerificationTask getDeploymentTask(String accountId, String cvConfigId, String verificationJobInstanceId) {
    return createQueryForDeploymentTasks(accountId, cvConfigId, verificationJobInstanceId).get();
  }

  @Override
  public VerificationTask getLiveMonitoringTask(String accountId, String cvConfigId) {
    return createQueryForLiveMonitoring(accountId, cvConfigId).get();
  }

  @Override
  public VerificationTask getSLITask(String accountId, String sliId) {
    return hPersistence.createQuery(VerificationTask.class, excludeValidate)
        .filter(VerificationTaskKeys.accountId, accountId)
        .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.SLI)
        .filter(VerificationTaskKeys.taskInfo + "." + SLIInfoKeys.sliId, sliId)
        .get();
  }

  @Override
  public VerificationTask getCompositeSLOTask(String accountId, String sloId) {
    return hPersistence.createQuery(VerificationTask.class, excludeValidate)
        .filter(VerificationTaskKeys.accountId, accountId)
        .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.COMPOSITE_SLO)
        .filter(VerificationTaskKeys.taskInfo + "." + CompositeSLOInfoKeys.sloId, sloId)
        .get();
  }

  private List<VerificationTask> getSLITasks(String accountId, List<String> sliIds) {
    return hPersistence.createQuery(VerificationTask.class, excludeValidate)
        .filter(VerificationTaskKeys.accountId, accountId)
        .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.SLI)
        .field(VerificationTaskKeys.taskInfo + "." + SLIInfoKeys.sliId)
        .in(sliIds)
        .asList();
  }

  private Query<VerificationTask> createQueryForLiveMonitoring(String accountId, String cvConfigId) {
    return hPersistence.createQuery(VerificationTask.class, excludeValidate)
        .filter(VerificationTaskKeys.accountId, accountId)
        .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.LIVE_MONITORING)
        .filter(VerificationTaskKeys.taskInfo + "." + LiveMonitoringInfoKeys.cvConfigId, cvConfigId);
  }

  private Query<VerificationTask> createQueryForDeploymentTasks(
      String accountId, String cvConfigId, String verificationJobInstanceId) {
    return hPersistence.createQuery(VerificationTask.class, excludeValidate)
        .filter(VerificationTaskKeys.accountId, accountId)
        .filter(VerificationTaskKeys.taskInfo + "." + TaskInfo.TASK_TYPE_FIELD_NAME, TaskType.DEPLOYMENT)
        .filter(VerificationTaskKeys.taskInfo + "." + DeploymentInfoKeys.cvConfigId, cvConfigId)
        .filter(VerificationTaskKeys.taskInfo + "." + DeploymentInfoKeys.verificationJobInstanceId,
            verificationJobInstanceId);
  }
}
