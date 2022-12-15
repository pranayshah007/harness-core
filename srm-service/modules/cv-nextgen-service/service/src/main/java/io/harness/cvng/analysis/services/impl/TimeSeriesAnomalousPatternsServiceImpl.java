/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns.TimeSeriesAnomalousPatternsKeys;
import io.harness.cvng.analysis.services.api.TimeSeriesAnomalousPatternsService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class TimeSeriesAnomalousPatternsServiceImpl implements TimeSeriesAnomalousPatternsService {
  @Inject private HPersistence hPersistence;

  @Override
  public void saveAnomalousPatterns(ServiceGuardTimeSeriesAnalysisDTO analysis, String verificationTaskId) {
    TimeSeriesAnomalousPatterns patternsToSave = buildAnomalies(analysis);

    // change the filter to verificationTaskId

    if (isNotEmpty(patternsToSave.getAnomalies())) {
      int size = patternsToSave.getAnomalies().size();
      Instant startCompress = Instant.now();
      log.info("Start compress for verificationTaskId {}, size {}, time {}", verificationTaskId, size, startCompress);
      patternsToSave.compressAnomalies();
      Instant endCompress = Instant.now();
      log.info("End compress for verificationTaskId {}, size {}, time {}", verificationTaskId, size, endCompress);
      log.info("Time taken to compress anom patterns of size {} for verificationTaskId {} is {}", size,
          verificationTaskId, Duration.between(startCompress, endCompress));
      Query<TimeSeriesAnomalousPatterns> timeSeriesAnomalousPatternsQuery =
          hPersistence.createQuery(TimeSeriesAnomalousPatterns.class, excludeAuthority)
              .filter(TimeSeriesAnomalousPatternsKeys.verificationTaskId, verificationTaskId);
      UpdateOperations<TimeSeriesAnomalousPatterns> updateOperations =
          hPersistence.createUpdateOperations(TimeSeriesAnomalousPatterns.class)
              .setOnInsert(TimeSeriesAnomalousPatternsKeys.uuid, generateUuid())
              .setOnInsert(TimeSeriesAnomalousPatternsKeys.verificationTaskId, verificationTaskId)
              .set(TimeSeriesAnomalousPatternsKeys.compressedAnomalies, patternsToSave.getCompressedAnomalies());

      log.info("Start before saving anom patterns of size {} for verificationTaskId {}", size, verificationTaskId);
      Instant start = Instant.now();
      hPersistence.upsert(timeSeriesAnomalousPatternsQuery, updateOperations);
      Instant end = Instant.now();
      log.info("End after saving anom patterns of size {} for verificationTaskId {}", size, verificationTaskId);
      log.info("Time taken to save anom patterns of size {} for verificationTaskId {} is {}", size, verificationTaskId,
          Duration.between(start, end));
    }
  }

  @Override
  public Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> getLongTermAnomalies(String verificationTaskId) {
    log.info("Fetching longterm anomalies for config: {}", verificationTaskId);
    TimeSeriesAnomalousPatterns anomalousPatterns =
        hPersistence.createQuery(TimeSeriesAnomalousPatterns.class)
            .filter(TimeSeriesAnomalousPatternsKeys.verificationTaskId, verificationTaskId)
            .get();
    if (anomalousPatterns != null) {
      anomalousPatterns.deCompressAnomalies();
      return anomalousPatterns.convertToMap();
    }
    return Collections.emptyMap();
  }

  private TimeSeriesAnomalousPatterns buildAnomalies(ServiceGuardTimeSeriesAnalysisDTO analysisDTO) {
    Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> anomaliesMap = new HashMap<>();
    analysisDTO.getTxnMetricAnalysisData().forEach((txnName, metricMap) -> {
      anomaliesMap.put(txnName, new HashMap<>());
      metricMap.forEach(
          (metricIdentifier,
              txnMetricData) -> anomaliesMap.get(txnName).put(metricIdentifier, txnMetricData.getAnomalousPatterns()));
    });

    return TimeSeriesAnomalousPatterns.builder()
        .verificationTaskId(analysisDTO.getVerificationTaskId())
        .anomalies(TimeSeriesAnomalousPatterns.convertFromMap(anomaliesMap))
        .build();
  }
}
