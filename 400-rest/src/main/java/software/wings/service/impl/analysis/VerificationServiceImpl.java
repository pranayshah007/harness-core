/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.SearchFilter.Operator.LT_EQ;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.service.impl.analysis.MLAnalysisType.LOG_ML;
import static software.wings.service.impl.analysis.MLAnalysisType.TIME_SERIES;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidArgumentsException;

import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.metrics.TimeSeriesDataRecord.TimeSeriesMetricRecordKeys;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.VerificationService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Sort;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by rsingh on 1/9/18.
 */
@Singleton
@Slf4j
public class VerificationServiceImpl implements VerificationService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DataStoreService dataStoreService;

  @Override
  public Optional<LearningEngineAnalysisTask> getLatestTaskForCvConfigIds(List<String> cvConfigIds) {
    if (isEmpty(cvConfigIds)) {
      return Optional.empty();
    }
    LearningEngineAnalysisTask task = wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                                          .field(LearningEngineAnalysisTaskKeys.cvConfigId)
                                          .in(cvConfigIds)
                                          .field(LearningEngineAnalysisTaskKeys.executionStatus)
                                          .in(ExecutionStatus.finalStatuses())
                                          .field(LearningEngineAnalysisTaskKeys.ml_analysis_type)
                                          .in(Lists.newArrayList(LOG_ML, TIME_SERIES))
                                          .order(Sort.descending(LearningEngineAnalysisTask.BaseKeys.createdAt))
                                          .get();
    return Optional.ofNullable(task);
  }

  @Override
  public boolean checkIfAnalysisHasData(String cvConfigId, MLAnalysisType mlAnalysisType, long minute) {
    long analysisStartMinute = minute - VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
    switch (mlAnalysisType) {
      case LOG_ML:
      case LOG_CLUSTER:
        PageRequest<LogDataRecord> logRequest = PageRequestBuilder.aPageRequest()
                                                    .addFilter(LogDataRecordKeys.cvConfigId, EQ, cvConfigId)
                                                    .addFilter(LogDataRecordKeys.timeStamp, GE, analysisStartMinute)
                                                    .addFilter(LogDataRecordKeys.timeStamp, LT_EQ, minute)
                                                    .addFilter(LogDataRecordKeys.clusterLevel, IN, "L0", "L1", "L2")
                                                    .withLimit("1")
                                                    .build();
        PageResponse<LogDataRecord> logDataRecord = dataStoreService.list(LogDataRecord.class, logRequest);
        return isNotEmpty(logDataRecord.getResponse());
      case TIME_SERIES:
        PageRequest<TimeSeriesDataRecord> pageRequest =
            PageRequestBuilder.aPageRequest()
                .addFilter(TimeSeriesMetricRecordKeys.cvConfigId, EQ, cvConfigId)
                .addFilter(TimeSeriesMetricRecordKeys.timeStamp, GE, analysisStartMinute)
                .addFilter(TimeSeriesMetricRecordKeys.timeStamp, LT_EQ, minute)
                .addFilter(TimeSeriesMetricRecordKeys.level, NOT_EXISTS)
                .withLimit("1")
                .build();
        PageResponse<TimeSeriesDataRecord> metricRecord =
            dataStoreService.list(TimeSeriesDataRecord.class, pageRequest);
        return isNotEmpty(metricRecord.getResponse());
      default:
        throw new InvalidArgumentsException(Pair.of(mlAnalysisType.name(), "Invalid data check for analysis type"));
    }
  }
}
