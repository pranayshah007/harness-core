/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.helpers;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.TimeSeriesMetaData;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

@OwnedBy(CE)
public class TimeSeriesUtils {
  private TimeSeriesUtils() {}

  public static List<Double> getStats(@NonNull AnomalyDetectionTimeSeries anomalyDetectionTimeSeries) {
    DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
    for (Double value : anomalyDetectionTimeSeries.getTrainDataPoints()) {
      descriptiveStatistics.addValue(value);
    }
    return Arrays.asList(descriptiveStatistics.getMean(), descriptiveStatistics.getStandardDeviation());
  }

  public static boolean validate(
      AnomalyDetectionTimeSeries anomalyDetectionTimeSeries, TimeSeriesMetaData timeSeriesMetaData) {
    return validateTimeSeriesTestData(anomalyDetectionTimeSeries)
        && validateTimeSeriesTrainData(anomalyDetectionTimeSeries, timeSeriesMetaData);
  }

  public static boolean validateTimeSeriesTestData(AnomalyDetectionTimeSeries anomalyDetectionTimeSeries) {
    return Collections.frequency(
               anomalyDetectionTimeSeries.getTestDataPointsList(), AnomalyDetectionConstants.DEFAULT_COST)
        == 0;
  }

  public static boolean validateTimeSeriesTrainData(
      AnomalyDetectionTimeSeries anomalyDetectionTimeSeries, TimeSeriesMetaData timeSeriesMetaData) {
    List<Double> pointsList = anomalyDetectionTimeSeries.getTrainDataPoints();
    int age = pointsList.size();
    Iterator<Double> iterator = pointsList.iterator();
    while (iterator.hasNext()) {
      if (iterator.next() != AnomalyDetectionConstants.DEFAULT_COST) {
        break;
      }
      age = age - 1;
    }
    if (age <= AnomalyDetectionConstants.MIN_DAYS_REQUIRED_DAILY) {
      anomalyDetectionTimeSeries.setNewEntity(true);
    }
    anomalyDetectionTimeSeries.setTrainDataPointsList(replaceInvalidPointsWithzeros(pointsList));
    return true;
  }

  public static int getValidPoints(List<Double> dataList) {
    return dataList.stream()
        .map(value -> {
          if (value >= 0) {
            return 1;
          } else {
            return 0;
          }
        })
        .mapToInt(Integer::intValue)
        .sum();
  }

  public static List<Double> replaceInvalidPointsWithzeros(List<Double> dataList) {
    return dataList.stream()
        .map(value -> {
          if (value >= 0) {
            return value;
          } else {
            return 0.0;
          }
        })
        .collect(Collectors.toList());
  }
}
