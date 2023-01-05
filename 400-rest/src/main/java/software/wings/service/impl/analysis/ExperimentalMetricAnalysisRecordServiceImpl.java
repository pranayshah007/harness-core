/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.persistence.HQuery.excludeAuthority;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.ExperimentalMetricAnalysisRecord.ExperimentalMetricAnalysisRecordKeys;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.intfc.analysis.ExperimentalMetricAnalysisRecordService;

import com.google.inject.Inject;
import dev.morphia.query.Sort;

public class ExperimentalMetricAnalysisRecordServiceImpl implements ExperimentalMetricAnalysisRecordService {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public ExperimentalMetricAnalysisRecord getLastAnalysisRecord(String stateExecutionId, String experimentName) {
    return wingsPersistence.createQuery(ExperimentalMetricAnalysisRecord.class, excludeAuthority)
        .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
        .order(Sort.descending(MetricAnalysisRecordKeys.analysisMinute))
        .filter(ExperimentalMetricAnalysisRecordKeys.experimentName, experimentName)
        .get();
  }

  @Override
  public ExperimentalMetricAnalysisRecord getAnalysisRecordForMinute(String stateExecutionId, Integer analysisMinute) {
    return wingsPersistence.createQuery(ExperimentalMetricAnalysisRecord.class)
        .filter(MetricAnalysisRecordKeys.stateExecutionId, stateExecutionId)
        .filter(MetricAnalysisRecordKeys.analysisMinute, analysisMinute)
        .get();
  }
}
