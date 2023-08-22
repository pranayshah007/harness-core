/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import io.harness.batch.processing.anomalydetection.helpers.AnomalyDetectionHelper;
import io.harness.batch.processing.tasklet.ClusterDataToBigQueryTasklet;
import io.harness.batch.processing.tasklet.support.HarnessEntitiesService;
import io.harness.ccm.anomaly.entities.Anomaly;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Slf4j
@Singleton
public class AnomalyDetectionTimeSeries extends Anomaly {
  @Autowired private HarnessEntitiesService harnessEntitiesService;
  @Autowired private ClusterDataToBigQueryTasklet clusterDataToBigQueryTasklet;

  public static AnomalyDetectionTimeSeries initialiseNewTimeSeries(TimeSeriesMetaData timeSeriesMetaData) {
    AnomalyDetectionTimeSeries timeSeries = AnomalyDetectionTimeSeries.builder()
                                                .accountId(timeSeriesMetaData.getAccountId())
                                                .timeGranularity(timeSeriesMetaData.getTimeGranularity())
                                                .build();
    timeSeries.initialiseTrainData(
        timeSeriesMetaData.getTrainStart(), timeSeriesMetaData.getTrainEnd(), ChronoUnit.DAYS);
    timeSeries.initialiseTestData(timeSeriesMetaData.getTestStart(), timeSeriesMetaData.getTestEnd(), ChronoUnit.DAYS);
    return timeSeries;
  }
  private List<Instant> trainTimePointsList;
  private List<Double> trainDataPointsList;
  private List<Instant> testTimePointsList;
  private List<Double> testDataPointsList;

  public Double getValue(Instant instant) {
    Double value;

    if (trainTimePointsList.contains(instant)) {
      value = trainDataPointsList.get(trainTimePointsList.indexOf(instant));
    } else if (testTimePointsList.contains(instant)) {
      value = testDataPointsList.get(testTimePointsList.indexOf(instant));
    } else {
      log.debug("requested value for invalid timestamp");
      value = null;
    }
    return value;
  }

  public List<Double> getTrainDataPoints() {
    return trainDataPointsList;
  }
  public List<Instant> getTrainTimePointsList() {
    return trainTimePointsList;
  }

  public List<Instant> getTestTimePointsList() {
    return testTimePointsList;
  }

  public boolean insert(Instant instant, Double cost) {
    return insertTrain(instant, cost) || insertTest(instant, cost);
  }

  private boolean insertTrain(Instant instant, Double cost) {
    int index = trainTimePointsList.indexOf(instant);
    if (index > -1) {
      trainDataPointsList.set(trainTimePointsList.indexOf(instant), cost);
      log.info(" insertTrain currentTime : {} , currentValue : {}, index : {}, true", instant.toString(), cost, index);
      return true;
    }

    log.info("insertTrain currentTime : {} , currentValue : {}, index : {}, false", instant.toString(), cost, index);
    return false;
  }

  private boolean insertTest(Instant instant, Double cost) {
    int index = testTimePointsList.indexOf(instant);
    if (index > -1) {
      testDataPointsList.set(testTimePointsList.indexOf(instant), cost);
      log.info("insertTest currentTime : {} , currentValue : {}, index : {}, true", instant.toString(), cost, index);
      return true;
    }

    log.info("insertTest currentTime : {} , currentValue : {}, index : {}, false", instant.toString(), cost, index);
    return false;
  }

  public void initialiseTrainData(Instant start, Instant end, ChronoUnit unit) {
    trainTimePointsList = new ArrayList<>();
    trainDataPointsList = new ArrayList<>();
    while (start.isBefore(end)) {
      trainTimePointsList.add(start);
      log.info("initialiseTrainData trainTimePoints : {}", start.toString());
      trainDataPointsList.add(AnomalyDetectionConstants.DEFAULT_COST);
      log.info("initialiseTrainData trainDataPoints : {}", AnomalyDetectionConstants.DEFAULT_COST);
      start = start.plus(1, unit);
    }
  }

  public void initialiseTestData(Instant start, Instant end, ChronoUnit unit) {
    testTimePointsList = new ArrayList<>();
    testDataPointsList = new ArrayList<>();
    while (start.isBefore(end)) {
      testTimePointsList.add(start);
      log.info("initialiseTestData testTimePoints : {}", start.toString());
      testDataPointsList.add(AnomalyDetectionConstants.DEFAULT_COST);
      log.info("initialiseTestData testDataPoints : {}", AnomalyDetectionConstants.DEFAULT_COST);
      start = start.plus(1, unit);
    }
  }

  @Override
  public String getId() {
    return super.getId() == null ? getHash() : super.getId();
  }

  public String getHash() {
    return AnomalyDetectionHelper.generateHash(String.join(",", getAccountId(),
        getTestTimePointsList().get(0).toString(), getClusterId(), getNamespace(), getWorkloadName(), getService(),
        getGcpProject(), getGcpProduct(), getGcpSKUId(), getAwsAccount(), getAwsService(), getAwsUsageType(),
        getAzureSubscription(), getAzureResourceGroup(), getAzureMeterCategory()));
  }
}
