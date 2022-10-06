/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.reader.k8s;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.service.impl.AnomalyDetectionTimescaleDataServiceImpl;

import software.wings.graphql.datafetcher.billing.BillingDataTableSchema;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AnomalyDetectionTimescaleReader
    implements ItemReader<AnomalyDetectionTimeSeries>, StepExecutionListener {
  @Autowired AnomalyDetectionTimescaleDataServiceImpl dataService;
  BillingDataTableSchema tableSchema = new BillingDataTableSchema();
  List<AnomalyDetectionTimeSeries> listAnomalyDetectionTimeSeries;
  JobParameters parameters;
  String accountId;
  int timeSeriesIndex;

  @Override
  public AnomalyDetectionTimeSeries read() {
    AnomalyDetectionTimeSeries anomalyDetectionTimeSeries = null;
    if (timeSeriesIndex < listAnomalyDetectionTimeSeries.size()) {
      anomalyDetectionTimeSeries = listAnomalyDetectionTimeSeries.get(timeSeriesIndex);
      timeSeriesIndex++;
    }
    return anomalyDetectionTimeSeries;
  }

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    log.debug("Time Series Reader ended.");
    return ExitStatus.COMPLETED;
  }
}
