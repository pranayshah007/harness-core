/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.migrations.Migration;
import io.harness.time.Timestamp;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLMetricSummary;
import software.wings.service.impl.analysis.TimeSeriesMLTxnSummary;
import software.wings.service.impl.analysis.TimeSeriesRiskData;
import software.wings.service.impl.analysis.TimeSeriesRiskSummary;
import software.wings.verification.CVConfiguration;

import com.google.common.collect.TreeBasedTable;
import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Praveen
 */
@Slf4j
public class TimeSeriesRiskSummaryMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  private int completedCount;

  @Override
  public void migrate() {
    // Fetch the list of current valid cvConfigurations
    List<CVConfiguration> cvConfigurations =
        wingsPersistence.createQuery(CVConfiguration.class, excludeAuthority).filter("enabled24x7", true).asList();

    log.info("Starting TimeSeriesRiskSummaryMigration. Total CV Configs to migrate: {}", cvConfigurations.size());
    for (CVConfiguration config : cvConfigurations) {
      log.info("Currently migrating cvConfig: {}", config.getUuid());
      PageRequest<TimeSeriesMLAnalysisRecord> recordPageRequest =
          PageRequestBuilder.aPageRequest()
              .withLimit("999")
              .addFilter("cvConfigId", Operator.EQ, config.getUuid())
              .addFilter("analysisMinute", Operator.GE,
                  TimeUnit.MILLISECONDS.toMinutes(Timestamp.currentMinuteBoundary()) - TimeUnit.DAYS.toMinutes(30))
              .withOffset("0")
              .build();

      PageResponse<TimeSeriesMLAnalysisRecord> response =
          wingsPersistence.query(TimeSeriesMLAnalysisRecord.class, recordPageRequest, excludeAuthority);

      log.info("The total number of records for cvConfigId {} is {}", config.getUuid(), response.getTotal());
      int previousOffset = 0;
      while (!response.isEmpty()) {
        List<TimeSeriesMLAnalysisRecord> records = response.getResponse();
        log.info("Currently Migrating for cvConfigId {} and batchsize {}", config.getUuid(), records.size());
        saveRiskSummaries(records);
        previousOffset += response.size();
        recordPageRequest.setOffset(String.valueOf(previousOffset));
        response = wingsPersistence.query(TimeSeriesMLAnalysisRecord.class, recordPageRequest, excludeAuthority);
      }
      sleep(ofMillis(1000));
    }
    log.info("Completed TimeSeriesRiskSummaryMigration after migrating {} records", completedCount);
  }

  private void saveRiskSummaries(List<TimeSeriesMLAnalysisRecord> timeSeriesMLAnalysisRecords) {
    List<TimeSeriesRiskSummary> riskSummaries = new ArrayList<>();

    timeSeriesMLAnalysisRecords.forEach(mlAnalysisResponse -> {
      log.info("In TimeSeriesRiskSummaryMigration, processing record for config {} and minute {}",
          mlAnalysisResponse.getCvConfigId(), mlAnalysisResponse.getAnalysisMinute());
      mlAnalysisResponse.decompress(false);
      TimeSeriesRiskSummary riskSummary = TimeSeriesRiskSummary.builder()
                                              .analysisMinute(mlAnalysisResponse.getAnalysisMinute())
                                              .cvConfigId(mlAnalysisResponse.getCvConfigId())
                                              .accountId(mlAnalysisResponse.getAccountId())
                                              .build();

      riskSummary.setAppId(mlAnalysisResponse.getAppId());
      TreeBasedTable<String, String, TimeSeriesRiskData> risks = TreeBasedTable.create();

      if (isNotEmpty(mlAnalysisResponse.getTransactions())) {
        for (TimeSeriesMLTxnSummary txnSummary : mlAnalysisResponse.getTransactions().values()) {
          if (isNotEmpty(txnSummary.getMetrics())) {
            for (TimeSeriesMLMetricSummary mlMetricSummary : txnSummary.getMetrics().values()) {
              if (mlMetricSummary.getResults() != null) {
                int maxRisk = mlMetricSummary.getMax_risk();
                int longTermPattern = mlMetricSummary.getLong_term_pattern();
                long lastSeenTime = mlMetricSummary.getLast_seen_time();
                risks.put(txnSummary.getTxn_name(), mlMetricSummary.getMetric_name(),
                    TimeSeriesRiskData.builder()
                        .metricRisk(maxRisk)
                        .lastSeenTime(lastSeenTime)
                        .longTermPattern(longTermPattern)
                        .build());
              }
            }
          }
        }
      }

      riskSummary.setTxnMetricRiskData(risks.rowMap());
      log.info("Done creating the riskSummary for config {} and minute {}", mlAnalysisResponse.getCvConfigId(),
          mlAnalysisResponse.getAnalysisMinute());
      riskSummary.compressMaps();
      Query<TimeSeriesRiskSummary> riskSummaryQuery =
          wingsPersistence.createAuthorizedQuery(TimeSeriesRiskSummary.class)
              .filter("cvConfigId", riskSummary.getCvConfigId())
              .filter("analysisMinute", riskSummary.getAnalysisMinute());

      // update
      UpdateOperations<TimeSeriesRiskSummary> updateOperations =
          wingsPersistence.createUpdateOperations(TimeSeriesRiskSummary.class)
              .set("compressedRiskData", riskSummary.getCompressedRiskData());
      wingsPersistence.findAndModify(riskSummaryQuery, updateOperations, new FindAndModifyOptions());
      sleep(ofMillis(100));
      riskSummaries.add(riskSummary);
    });

    completedCount += timeSeriesMLAnalysisRecords.size();
    log.info("So far, Completed Migrating {} records", completedCount);
  }
}
