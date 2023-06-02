/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.persistence.HIterator;

import software.wings.beans.dto.CVActivityLog.LogLevel;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.verification.CVActivityLog;
import software.wings.verification.CVActivityLog.CVActivityLogKeys;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVActivityLogServiceImpl implements CVActivityLogService {
  private static final String EXECUTION_LOGS_NOT_AVAILABLE = "Execution logs are not available for old executions";
  @Inject private WingsPersistence wingsPersistence;
  @Inject(optional = true) private WorkflowService workflowService; // Only available in manager

  @Override
  public List<CVActivityLog> findByCVConfigId(String cvConfigId, long startTimeEpochMinute, long endTimeEpochMinute) {
    List<CVActivityLog> cvActivityLogs = new ArrayList<>();
    try (HIterator<CVActivityLog> cvActivityLogHIterator =
             new HIterator<>(wingsPersistence.createQuery(CVActivityLog.class, excludeAuthority)
                                 .filter(CVActivityLogKeys.cvConfigId, cvConfigId)
                                 .filter(CVActivityLogKeys.dataCollectionMinute + " >=", startTimeEpochMinute)
                                 .filter(CVActivityLogKeys.dataCollectionMinute + " <=", endTimeEpochMinute)
                                 .order(Sort.ascending(CVActivityLogKeys.createdAt))
                                 .fetch())) {
      cvActivityLogHIterator.forEach(cvActivityLog -> cvActivityLogs.add(cvActivityLog));
    }
    return cvActivityLogs;
  }

  @Override
  public CVActivityLogger getLogger(
      String accountId, String cvConfigId, long dataCollectionMinute, String stateExecutionId) {
    return Strings.isNullOrEmpty(cvConfigId) ? getLoggerByStateExecutionId(accountId, stateExecutionId)
                                             : getLoggerByCVConfigId(accountId, cvConfigId, dataCollectionMinute);
  }

  @Override
  public CVActivityLogger getLoggerByCVConfigId(String accountId, String cvConfigId, long dataCollectionMinute) {
    return new LoggerImpl(accountId, cvConfigId, dataCollectionMinute);
  }

  @Override
  public CVActivityLogger getLoggerByStateExecutionId(String accountId, String stateExecutionId) {
    return new LoggerImpl(accountId, stateExecutionId);
  }

  @Override
  public List<CVActivityLog> findByStateExecutionId(String stateExecutionId) {
    List<CVActivityLog> cvActivityLogs = new ArrayList<>();
    try (HIterator<CVActivityLog> cvActivityLogHIterator =
             new HIterator<>(wingsPersistence.createQuery(CVActivityLog.class, excludeAuthority)
                                 .filter(CVActivityLogKeys.stateExecutionId, stateExecutionId)
                                 .order(Sort.ascending(CVActivityLogKeys.createdAt))
                                 .fetch())) {
      cvActivityLogHIterator.forEach(cvActivityLog -> cvActivityLogs.add(cvActivityLog));
    }
    return cvActivityLogs;
  }

  @Override
  public void saveActivityLogs(List<CVActivityLog> cvActivityLogs) {
    wingsPersistence.save(cvActivityLogs);
  }

  @Override
  public List<CVActivityLog> getActivityLogs(String accountId, String stateExecutionId, String cvConfigId,
      long startTimeEpochMinute, long endTimeEpochMinute) {
    List<CVActivityLog> cvActivityLogs;
    if (stateExecutionId != null) {
      AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                            .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId)
                                            .get();
      cvActivityLogs = findByStateExecutionId(stateExecutionId);
      if (cvActivityLogs.isEmpty()
          && (analysisContext == null
              || ExecutionStatus.isFinalStatus(workflowService.getExecutionStatus(
                  analysisContext.getAppId(), analysisContext.getStateExecutionId())))) {
        CVActivityLog placeholderActivityLog = CVActivityLog.builder()
                                                   .stateExecutionId(stateExecutionId)
                                                   .log(EXECUTION_LOGS_NOT_AVAILABLE)
                                                   .logLevel(LogLevel.INFO)
                                                   .createdAt(System.currentTimeMillis())
                                                   .lastUpdatedAt(System.currentTimeMillis())
                                                   .accountId(accountId)
                                                   .build();
        cvActivityLogs = new ArrayList<>();
        cvActivityLogs.add(placeholderActivityLog);
      }
    } else {
      cvActivityLogs = findByCVConfigId(cvConfigId, startTimeEpochMinute, endTimeEpochMinute);
    }
    return cvActivityLogs;
  }

  private class LoggerImpl implements CVActivityLogger {
    private String cvConfigId;
    private long dataCollectionMinute;
    private String stateExecutionId;
    private String accountId;
    LoggerImpl(String accountId, String cvConfigId, long dataCollectionMinute) {
      Preconditions.checkNotNull(cvConfigId);
      this.cvConfigId = cvConfigId;
      this.dataCollectionMinute = dataCollectionMinute;
      this.accountId = accountId;
    }

    LoggerImpl(String accountId, String stateExecutionId) {
      Preconditions.checkNotNull(stateExecutionId);
      this.stateExecutionId = stateExecutionId;
      this.accountId = accountId;
    }

    @Override
    public void appendLog(LogLevel logLevel, String log, long... timestampParams) {
      CVActivityLog cvActivityLog =
          CVActivityLog.builder()
              .cvConfigId(cvConfigId)
              .stateExecutionId(stateExecutionId)
              .dataCollectionMinute(dataCollectionMinute)
              .log(log)
              .logLevel(logLevel)
              .timestampParams(Arrays.stream(timestampParams).boxed().collect(Collectors.toList()))
              .accountId(accountId)
              .build();
      wingsPersistence.save(cvActivityLog);
    }
  }
}
