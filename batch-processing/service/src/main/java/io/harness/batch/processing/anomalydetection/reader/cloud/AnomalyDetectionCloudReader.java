/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.reader.cloud;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.TimeSeriesMetaData;
import io.harness.batch.processing.anomalydetection.service.impl.AnomalyDetectionBigQueryServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AnomalyDetectionCloudReader
    implements ItemReader<AnomalyDetectionTimeSeries>, StepExecutionListener {
  @Autowired AnomalyDetectionBigQueryServiceImpl dataService;

  TimeSeriesMetaData timeSeriesMetaData;
  JobParameters parameters;
  String accountId;

  List<String> hashCodesList;
  ListIterator<String> hashCodeIterator;
  List<AnomalyDetectionTimeSeries> listAnomalyDetectionTimeSeries;
  ListIterator<AnomalyDetectionTimeSeries> timeSeriesIterator;

  @Override
  public AnomalyDetectionTimeSeries read() {
    // while next batch has valid data keep reading batches
    while (!this.timeSeriesIterator.hasNext()) {
      if (!loadNextBatch()) {
        return null;
      }
    }
    return this.timeSeriesIterator.next();
  }

  public boolean loadNextBatch() {
    if (hashCodeIterator.hasNext()) {
      List<String> currentBatch = new ArrayList<>();

      int count = 0;
      while (hashCodeIterator.hasNext() && count < AnomalyDetectionConstants.BATCH_SIZE) {
        currentBatch.add(hashCodeIterator.next());
        count++;
      }
      listAnomalyDetectionTimeSeries = dataService.readBatchData(timeSeriesMetaData, currentBatch);
      timeSeriesIterator = listAnomalyDetectionTimeSeries.listIterator();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    log.debug("Time Series Reader ended.");
    return ExitStatus.COMPLETED;
  }
}
