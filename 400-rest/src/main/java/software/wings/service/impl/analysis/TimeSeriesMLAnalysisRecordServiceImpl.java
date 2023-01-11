/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.persistence.HQuery.excludeAuthority;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.intfc.analysis.TimeSeriesMLAnalysisRecordService;

import com.google.inject.Inject;
import dev.morphia.query.Sort;

public class TimeSeriesMLAnalysisRecordServiceImpl implements TimeSeriesMLAnalysisRecordService {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public TimeSeriesMLAnalysisRecord getLastAnalysisRecord(String stateExecutionId) {
    return wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
        .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
        .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
        .get();
  }

  @Override
  public TimeSeriesMLAnalysisRecord getAnalysisRecordForMinute(String stateExecutionId, Integer analysisMinute) {
    return wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
        .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
        .filter(MetricAnalysisRecordKeys.analysisMinute, analysisMinute)
        .get();
  }
}
