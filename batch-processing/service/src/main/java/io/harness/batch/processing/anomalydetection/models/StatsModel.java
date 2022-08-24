/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.models;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.helpers.TimeSeriesUtils;
import io.harness.ccm.anomaly.entities.Anomaly;
import io.harness.ccm.anomaly.entities.AnomalyDetectionModel;
import io.harness.ccm.anomaly.entities.AnomalyType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StatsModel {
  public Anomaly detectAnomaly(AnomalyDetectionTimeSeries data) {
    List<Double> stats = TimeSeriesUtils.getStats(data);
    Double mean = stats.get(0);
    Double standardDeviation = stats.get(1);

    List<Anomaly> anomaliesList = new ArrayList<>();

    for (Instant current : data.getTestTimePointsList()) {
      Double currentValue = data.getValue(current);

      boolean probabilityThreshold = true;
      if (standardDeviation > 0) {
        probabilityThreshold = probabilityThreshold(currentValue, mean, standardDeviation);
      }

      Anomaly currentAnomaly = Anomaly.builder()
                                   .id(data.getHash())
                                   .accountId(data.getAccountId())
                                   .actualCost(currentValue)
                                   .expectedCost(mean)
                                   .timeGranularity(data.getTimeGranularity())
                                   .clusterId(data.getClusterId())
                                   .clusterName(data.getClusterName())
                                   .workloadType(data.getWorkloadType())
                                   .workloadName(data.getWorkloadName())
                                   .namespace(data.getNamespace())
                                   .region(data.getRegion())
                                   .cloudProvider(data.getCloudProvider())
                                   .gcpProduct(data.getGcpProduct())
                                   .gcpProject(data.getGcpProject())
                                   .gcpSKUId(data.getGcpSKUId())
                                   .gcpSKUDescription(data.getGcpSKUDescription())
                                   .awsAccount(data.getAwsAccount())
                                   .awsInstanceType(data.getAwsInstanceType())
                                   .awsService(data.getAwsService())
                                   .awsUsageType(data.getAwsUsageType())
                                   .azureSubscription(data.getAzureSubscription())
                                   .azureResourceGroup(data.getAzureResourceGroup())
                                   .azureMeterCategory(data.getAzureMeterCategory())
                                   .anomalyTime(current)
                                   .relativeThreshold(relativityThreshold(currentValue, mean))
                                   .absoluteThreshold(absoluteThreshold(currentValue, mean))
                                   .probabilisticThreshold(probabilityThreshold)
                                   .reportedBy(AnomalyDetectionModel.STATISTICAL)
                                   .build();
      if (currentValue > mean) {
        currentAnomaly.setAnomalyType(AnomalyType.SPIKE);
      } else {
        currentAnomaly.setAnomalyType(AnomalyType.DROP);
      }

      boolean isAnomaly = currentAnomaly.isRelativeThreshold() && currentAnomaly.isProbabilisticThreshold()
          && currentAnomaly.isAbsoluteThreshold();
      currentAnomaly.setAnomaly(isAnomaly);
      anomaliesList.add(currentAnomaly);
      log.info(
          "statistics : predicted : [{}] , actual : [{}] , STD : [{}] , absolute Threshold : [{}] , relative Threshold : [{}] , probabilistic Threshold : [{}] , isAnomaly : [{}] ",
          mean, currentValue, standardDeviation, currentAnomaly.isAbsoluteThreshold(),
          currentAnomaly.isRelativeThreshold(), currentAnomaly.isProbabilisticThreshold(), currentAnomaly.isAnomaly());
    }
    return anomaliesList.get(0);
  }

  private static boolean relativityThreshold(Double original, Double expected) {
    return original > AnomalyDetectionConstants.STATS_MODEL_RELATIVITY_THRESHOLD * expected;
  }

  private static boolean absoluteThreshold(Double original, Double expected) {
    return original > expected + AnomalyDetectionConstants.STATS_MODEL_ABSOLUTE_THRESHOLD;
  }

  private static boolean probabilityThreshold(Double original, Double mean, Double standardDeviation) {
    NormalDistribution normal = new NormalDistribution(mean, standardDeviation);
    return normal.cumulativeProbability(original) > AnomalyDetectionConstants.STATS_MODEL_PROBABILITY_THRESHOLD;
  }
}
