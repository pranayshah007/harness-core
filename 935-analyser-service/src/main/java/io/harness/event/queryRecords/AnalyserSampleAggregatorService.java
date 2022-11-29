/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.queryRecords;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.QueryRecordEntity;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.service.QueryRecordsService;
import io.harness.service.QueryStatsService;
import io.harness.service.beans.QueryRecordKey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AnalyserSampleAggregatorService implements Runnable {
  @Inject private QueryRecordsService queryRecordsService;
  @Inject private QueryStatsService queryStatsService;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      log.error("Exception happened in SampleAggregator execute", e);
    }
  }

  public void execute() {
    try {
      Map<QueryRecordKey, List<QueryRecordEntity>> allEntries = queryRecordsService.findAllEntries();
      queryStatsService.updateQueryStatsByAggregation(allEntries);
      log.info("Analyser Sample Aggregation done.");
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the SampleAggregator call", exception);
    }
  }
}
