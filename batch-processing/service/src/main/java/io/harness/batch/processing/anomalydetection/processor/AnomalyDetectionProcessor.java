/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.processor;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.helpers.AnomalyDetectionHelper;
import io.harness.batch.processing.anomalydetection.models.StatsModel;
import io.harness.batch.processing.anomalydetection.pythonserviceendpoint.AnomalyDetectionPythonService;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.impl.AnomalyDetectionLogContext;
import io.harness.ccm.anomaly.entities.Anomaly;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.logging.AutoLogContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AnomalyDetectionProcessor
    implements ItemProcessor<AnomalyDetectionTimeSeries, Anomaly>, StepExecutionListener {
  @Autowired StatsModel statsModel;
  @Autowired AnomalyDetectionPythonService pythonService;
  @Autowired BatchMainConfig mainConfig;
  @Autowired AnomalyService anomalyService;

  // TODO: make this class stateless
  Set<String> anomalyHashSet;
  JobParameters parameters;
  String accountId;
  Instant endTime;

  @Override
  public void beforeStep(StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
    accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    endTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE);

    anomalyHashSet =
        anomalyService.list(accountId, endTime.minus(6, ChronoUnit.DAYS), endTime.minus(2, ChronoUnit.DAYS))
            .stream()
            .map(anomalyEntity -> AnomalyDetectionHelper.getHash(anomalyEntity, false))
            .collect(Collectors.toSet());

    log.info("TimeSeries Processor initialized.");
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    log.info("TimeSeries Processor ended.");
    return ExitStatus.COMPLETED;
  }

  @Override
  public Anomaly process(AnomalyDetectionTimeSeries timeSeries) throws Exception {
    Anomaly returnAnomaly;
    try (AutoLogContext ignore = new AnomalyDetectionLogContext(timeSeries.getId(), OVERRIDE_ERROR)) {
      AnomalyDetectionHelper.logProcessingTimeSeries("Stats Model");
      returnAnomaly = statsModel.detectAnomaly(timeSeries);
      if (mainConfig.getCePythonServiceConfig().isUseProphet() && returnAnomaly.isAnomaly()) {
        AnomalyDetectionHelper.logProcessingTimeSeries("Prophet Model");
        Anomaly anomalyFromService = pythonService.process(timeSeries);
        if (anomalyFromService != null) {
          returnAnomaly = anomalyFromService;
        }
      }
      log.debug("finally after processing, isAnomaly : [{}]", returnAnomaly.isAnomaly());

      if (returnAnomaly.isAnomaly()) {
        if (anomalyHashSet.contains(AnomalyDetectionHelper.getHash(returnAnomaly, false))) {
          log.info("same entity is present as anomaly in last 5 days");
          Double maxValue = Collections.max(timeSeries.getTrainDataPointsList().subList(
              timeSeries.getTrainDataPointsList().size() - 5, timeSeries.getTrainDataPointsList().size()));
          Double currentValue = timeSeries.getTestDataPointsList().get(0);
          log.info(String.format("Current value : %1$s , max value : %2$s", currentValue, maxValue));
          if (currentValue < AnomalyDetectionConstants.NEARBY_ANOMALIES_THRESHOLD * maxValue) {
            log.info("not writing into db since a significant anomaly is present in last 5 days");
            /*Near by duplicates are marked with actual cost as negative value, removed in duplicate anomalies step*/
            returnAnomaly.setActualCost(-10.0);
          } else {
            log.debug("writing anomaly into db ");
          }
        } else {
          log.debug("writing anomaly into db ");
        }
      } else {
        log.debug("not writing anomaly into db since it is not anomaly");
      }
    }

    return returnAnomaly;
  }
}
