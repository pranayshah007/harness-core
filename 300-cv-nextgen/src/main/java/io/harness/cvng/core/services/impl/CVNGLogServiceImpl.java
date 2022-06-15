/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.CVConstants.TAG_ACCOUNT_ID;
import static io.harness.cvng.CVConstants.TAG_ONBOARDING;
import static io.harness.cvng.CVConstants.TAG_VERIFICATION_TYPE;
import static io.harness.cvng.beans.cvnglog.CVNGLogType.EXECUTION_LOG;
import static io.harness.cvng.beans.cvnglog.TraceableType.ONBOARDING;
import static io.harness.cvng.beans.cvnglog.TraceableType.VERIFICATION_TASK;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.beans.cvnglog.CVNGLogTag.TagType;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.logsFilterParams.DeploymentLogsFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.TimeRangeLogsFilter;
import io.harness.cvng.core.entities.CVNGLog;
import io.harness.cvng.core.entities.CVNGLog.CVNGLogKeys;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.cvnglogs.CVNGLogRecord;
import io.harness.cvng.core.entities.cvnglogs.CVNGLogRecord.CVNGLogRecordComparator;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.UpdateOptions;
import org.mongodb.morphia.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class CVNGLogServiceImpl implements CVNGLogService {
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private MetricService metricService;

  @Override
  public void save(List<CVNGLogDTO> callLogs) {
    UpdateOptions options = new UpdateOptions();
    options.upsert(true);

    callLogs.forEach(logRecord -> {
      CVNGLogRecord cvngLogRecord = CVNGLog.toCVNGLogRecord(logRecord);
      List<CVNGLogTag> cvngLogTags = new ArrayList<>();
      TagResult tagResult = getTags(logRecord);
      cvngLogTags.addAll(logRecord.getTags());
      cvngLogTags.addAll(getCommonLogTags(logRecord));
      cvngLogTags.addAll(tagResult.getCvngLogTags());
      cvngLogRecord.setTags(cvngLogTags);
      cvngLogRecord.recordsMetrics(metricService, tagResult.getTagsMap());
      cvngLogRecord.setCreatedAt(clock.instant().toEpochMilli());
      Instant startTime = Instant.ofEpochMilli(logRecord.getStartTime());
      Instant endTime = Instant.ofEpochMilli(logRecord.getEndTime());
      Query<CVNGLog> cvngLogQuery = hPersistence.createQuery(CVNGLog.class)
                                        .filter(CVNGLogKeys.accountId, logRecord.getAccountId())
                                        .filter(CVNGLogKeys.logType, logRecord.getType())
                                        .filter(CVNGLogKeys.traceableType, logRecord.getTraceableType())
                                        .filter(CVNGLogKeys.traceableId, logRecord.getTraceableId())
                                        .filter(CVNGLogKeys.endTime, endTime)
                                        .filter(CVNGLogKeys.startTime, startTime);

      hPersistence.getDatastore(CVNGLog.class)
          .update(cvngLogQuery,
              hPersistence.createUpdateOperations(CVNGLog.class)
                  .setOnInsert(CVNGLogKeys.accountId, logRecord.getAccountId())
                  .setOnInsert(CVNGLogKeys.traceableId, logRecord.getTraceableId())
                  .setOnInsert(CVNGLogKeys.startTime, startTime)
                  .setOnInsert(CVNGLogKeys.endTime, endTime)
                  .setOnInsert(CVNGLogKeys.traceableType, logRecord.getTraceableType())
                  .setOnInsert(CVNGLogKeys.logType, logRecord.getType())
                  .setOnInsert(CVNGLogKeys.validUntil, CVNGLog.builder().build().getValidUntil())
                  .addToSet(CVNGLogKeys.logRecords, cvngLogRecord),
              options);
    });
  }

  private List<CVNGLogTag> getCommonLogTags(CVNGLogDTO cvngLogDTO) {
    List<CVNGLogTag> cvngLogTags = new ArrayList<>();
    cvngLogTags.add(CVNGLogTag.builder()
                        .key("startTime")
                        .value(Long.toString(cvngLogDTO.getStartTime()))
                        .type(TagType.TIMESTAMP)
                        .build());
    cvngLogTags.add(CVNGLogTag.builder()
                        .key("endTime")
                        .value(Long.toString(cvngLogDTO.getEndTime()))
                        .type(TagType.TIMESTAMP)
                        .build());
    cvngLogTags.add(
        CVNGLogTag.builder().key("traceableId").value(cvngLogDTO.getTraceableId()).type(TagType.DEBUG).build());
    cvngLogTags.add(CVNGLogTag.builder()
                        .key("traceableType")
                        .value(cvngLogDTO.getTraceableType().name())
                        .type(TagType.DEBUG)
                        .build());
    return cvngLogTags;
  }

  @Override
  public PageResponse<CVNGLogDTO> getOnboardingLogs(
      String accountId, String traceableId, CVNGLogType cvngLogType, int offset, int pageSize) {
    List<CVNGLog> cvngLogs = hPersistence.createQuery(CVNGLog.class, excludeAuthority)
                                 .filter(CVNGLogKeys.accountId, accountId)
                                 .filter(CVNGLogKeys.logType, CVNGLogType.API_CALL_LOG)
                                 .filter(CVNGLogKeys.traceableType, TraceableType.ONBOARDING)
                                 .filter(CVNGLogKeys.traceableId, traceableId)
                                 .asList();

    List<CVNGLogDTO> cvngLogDTOs = new ArrayList<>();
    cvngLogs.forEach(log -> {
      Collections.sort(log.getLogRecords(), new CVNGLogRecordComparator());
      cvngLogDTOs.addAll(log.toCVNGLogDTOs());
    });
    return PageUtils.offsetAndLimit(cvngLogDTOs, offset, pageSize);
  }

  @Override
  public List<CVNGLog> getCompleteCVNGLog(String accountId, String verificationTaskId, CVNGLogType cvngLogType) {
    return hPersistence.createQuery(CVNGLog.class)
        .filter(CVNGLogKeys.accountId, accountId)
        .filter(CVNGLogKeys.traceableType, VERIFICATION_TASK)
        .filter(CVNGLogKeys.traceableId, verificationTaskId)
        .filter(CVNGLogKeys.logType, cvngLogType)
        .asList();
  }

  @Override
  public List<ExecutionLogDTO> getExecutionLogDTOs(String accountId, String verificationTaskId) {
    return hPersistence.createQuery(CVNGLog.class)
        .filter(CVNGLogKeys.accountId, accountId)
        .filter(CVNGLogKeys.traceableId, verificationTaskId)
        .filter(CVNGLogKeys.traceableType, VERIFICATION_TASK)
        .filter(CVNGLogKeys.logType, EXECUTION_LOG)
        .asList()
        .stream()
        .map(cvngLog -> (List<ExecutionLogDTO>) (List<?>) cvngLog.toCVNGLogDTOs())
        .flatMap(List<ExecutionLogDTO>::stream)
        .collect(Collectors.toList());
  }

  @Override
  public PageResponse<CVNGLogDTO> getCVNGLogs(String accountId, String verificationJobInstanceId,
      DeploymentLogsFilter deploymentLogsFilter, PageParams pageParams) {
    List<CVNGLog> cvngLogs = getCVNGLogs(accountId, verificationJobInstanceId, deploymentLogsFilter);
    List<CVNGLogDTO> cvngLogDTOs = new ArrayList<>();
    cvngLogs.forEach(cvngLog -> {
      Collections.sort(cvngLog.getLogRecords(), new CVNGLogRecordComparator());
      List<CVNGLogRecord> cvngLogRecords =
          cvngLog.getLogRecords()
              .stream()
              .filter(cvngLogRecord -> !deploymentLogsFilter.isErrorLogsOnly() || cvngLogRecord.isErrorLog())
              .collect(Collectors.toList());
      cvngLog.setLogRecords(cvngLogRecords);
      cvngLogDTOs.addAll(cvngLog.toCVNGLogDTOs());
    });

    return PageUtils.offsetAndLimit(cvngLogDTOs, pageParams.getPage(), pageParams.getSize());
  }

  @Override
  public PageResponse<CVNGLogDTO> getCVNGLogs(String accountId, List<String> verificationTaskIds,
      TimeRangeLogsFilter timeRangeLogsFilter, PageParams pageParams) {
    List<CVNGLog> cvngLogs = getCVNGLogs(accountId, verificationTaskIds, timeRangeLogsFilter);
    List<CVNGLogDTO> cvngLogDTOs = new ArrayList<>();
    cvngLogs.forEach(cvngLog -> {
      Collections.sort(cvngLog.getLogRecords(), new CVNGLogRecordComparator());
      List<CVNGLogRecord> cvngLogRecords =
          cvngLog.getLogRecords()
              .stream()
              .filter(cvngLogRecord -> !timeRangeLogsFilter.isErrorLogsOnly() || cvngLogRecord.isErrorLog())
              .collect(Collectors.toList());
      cvngLog.setLogRecords(cvngLogRecords);
      cvngLogDTOs.addAll(cvngLog.toCVNGLogDTOs());
    });

    return PageUtils.offsetAndLimit(cvngLogDTOs, pageParams.getPage(), pageParams.getSize());
  }

  private List<CVNGLog> getCVNGLogs(
      String accountId, List<String> verificationTaskIds, TimeRangeLogsFilter timeRangeLogsFilter) {
    Preconditions.checkArgument(
        Duration.between(timeRangeLogsFilter.getStartTime(), timeRangeLogsFilter.getEndTime()).toDays() <= 1,
        "Logs can be fetched for maximum 1 day");
    return hPersistence.createQuery(CVNGLog.class, excludeAuthority)
        .filter(CVNGLogKeys.accountId, accountId)
        .filter(CVNGLogKeys.logType, timeRangeLogsFilter.getLogType())
        .filter(CVNGLogKeys.traceableType, VERIFICATION_TASK)
        .field(CVNGLogKeys.traceableId)
        .in(verificationTaskIds)
        .field(CVNGLogKeys.endTime)
        .greaterThanOrEq(timeRangeLogsFilter.getStartTime())
        .field(CVNGLogKeys.startTime)
        .lessThan(timeRangeLogsFilter.getEndTime())
        .asList();
  }

  private List<CVNGLog> getCVNGLogs(
      String accountId, String verificationJobInstanceId, DeploymentLogsFilter deploymentLogsFilter) {
    Set<String> verificationTaskIds =
        verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstanceId);
    if (deploymentLogsFilter.filterByHealthSourceIdentifiers()) {
      List<String> cvConfigIds = verificationJobInstanceService.getCVConfigIdsForVerificationJobInstance(
          verificationJobInstanceId, deploymentLogsFilter.getHealthSourceIdentifiers());
      verificationTaskIds =
          verificationTaskIds.stream()
              .filter(
                  verificationTaskId -> cvConfigIds.contains(verificationTaskService.getCVConfigId(verificationTaskId)))
              .collect(Collectors.toSet());
    }
    return hPersistence.createQuery(CVNGLog.class, excludeAuthority)
        .filter(CVNGLogKeys.accountId, accountId)
        .filter(CVNGLogKeys.logType, deploymentLogsFilter.getLogType())
        .filter(CVNGLogKeys.traceableType, VERIFICATION_TASK)
        .field(CVNGLogKeys.traceableId)
        .in(verificationTaskIds)
        .asList();
  }

  private TagResult getTags(CVNGLogDTO cvngLogDTO) {
    List<CVNGLogTag> cvngLogTags = new ArrayList<>();
    Map<String, String> tagsMap = new HashMap<>();
    tagsMap.put(TAG_ACCOUNT_ID, cvngLogDTO.getAccountId());

    if (cvngLogDTO.getType() == CVNGLogType.API_CALL_LOG) {
      if (cvngLogDTO.getTraceableType() == VERIFICATION_TASK) {
        Optional<VerificationTask> verificationTask = verificationTaskService.maybeGet(cvngLogDTO.getTraceableId());
        if (verificationTask.isPresent()) {
          tagsMap.putAll(verificationTask.get().getTags());
          verificationTask.get().getTags().forEach(
              (key, value) -> cvngLogTags.add(CVNGLogTag.builder().key(key).value(value).type(TagType.STRING).build()));
        }
      } else if (cvngLogDTO.getTraceableType() == ONBOARDING) {
        tagsMap.put(TAG_VERIFICATION_TYPE, TAG_ONBOARDING);
      }
    } else {
      Optional<VerificationTask> verificationTask = verificationTaskService.maybeGet(cvngLogDTO.getTraceableId());
      if (verificationTask.isPresent()) {
        verificationTask.get().getTags().forEach(
            (key, value) -> cvngLogTags.add(CVNGLogTag.builder().key(key).value(value).type(TagType.STRING).build()));
      }
    }
    return TagResult.builder().tagsMap(tagsMap).cvngLogTags(cvngLogTags).build();
  }
  @Value
  @Builder
  private static class TagResult {
    Map<String, String> tagsMap;
    List<CVNGLogTag> cvngLogTags;
  }
}
