/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.pythonserviceendpoint;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.ccm.anomaly.entities.Anomaly;
import io.harness.ccm.anomaly.entities.AnomalyDetectionModel;
import io.harness.ccm.anomaly.entities.AnomalyType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PythonMappers {
  public static Anomaly toAnomaly(PythonResponse pythonResponse, AnomalyDetectionTimeSeries timeSeries) {
    return Anomaly.builder()
        .id(pythonResponse.getId())
        .accountId(timeSeries.getAccountId())
        .actualCost(pythonResponse.getY())
        .expectedCost(pythonResponse.getYHat())
        .anomalyScore(pythonResponse.getAnomalyScore())
        .isAnomaly(pythonResponse.getIsAnomaly())
        .anomalyScore(pythonResponse.getAnomalyScore())
        .anomalyType(pythonResponse.getY() > pythonResponse.getYHat() ? AnomalyType.SPIKE : AnomalyType.DROP)
        .anomalyTime(timeSeries.getTestTimePointsList().get(0))
        .reportedBy(AnomalyDetectionModel.FBPROPHET)
        .timeGranularity(timeSeries.getTimeGranularity())
        .clusterId(timeSeries.getClusterId())
        .clusterName(timeSeries.getClusterName())
        .namespace(timeSeries.getNamespace())
        .workloadType(timeSeries.getWorkloadType())
        .workloadName(timeSeries.getWorkloadName())
        .gcpProject(timeSeries.getGcpProject())
        .gcpProduct(timeSeries.getGcpProduct())
        .gcpSKUId(timeSeries.getGcpSKUId())
        .gcpSKUDescription(timeSeries.getGcpSKUDescription())
        .awsAccount(timeSeries.getAwsAccount())
        .awsService(timeSeries.getAwsService())
        .awsInstanceType(timeSeries.getAwsInstanceType())
        .awsUsageType(timeSeries.getAwsUsageType())
        .azureSubscription(timeSeries.getAzureSubscription())
        .azureResourceGroup(timeSeries.getAzureResourceGroup())
        .azureMeterCategory(timeSeries.getAzureMeterCategory())
        .build();
  }

  public static PythonInput fromTimeSeries(AnomalyDetectionTimeSeries source) {
    PythonInput pythonInput = PythonInput.builder().build();
    pythonInput.setId(source.getId());
    pythonInput.setAccountId(source.getAccountId());
    pythonInput.setClusterId(source.getClusterId());
    pythonInput.setClusterName(source.getClusterName());
    pythonInput.setNamespace(source.getNamespace());
    pythonInput.setWorkloadName(source.getWorkloadName());
    pythonInput.setWorkloadType(source.getWorkloadType());
    pythonInput.setAwsAccount(source.getAwsAccount());
    pythonInput.setAwsService(source.getAwsService());
    pythonInput.setAwsUsageType(source.getAwsUsageType());
    pythonInput.setGcpProject(source.getGcpProject());
    pythonInput.setGcpProduct(source.getGcpProduct());
    pythonInput.setGcpSKUId(source.getGcpSKUId());
    pythonInput.setGcpSKUDescription(source.getGcpSKUDescription());
    pythonInput.setAzureSubscription(source.getAzureSubscription());
    pythonInput.setAzureResourceGroup(source.getAzureResourceGroup());
    pythonInput.setAzureMeterCategory(source.getAzureMeterCategory());

    PythonInput.APITimeSeries apiTimeSeries =
        PythonInput.APITimeSeries.builder()
            .test(convertListToAPITimeSeries(source.getTestTimePointsList(), source.getTestDataPointsList()))
            .train(convertListToAPITimeSeries(source.getTrainTimePointsList(), source.getTrainDataPointsList()))
            .build();

    pythonInput.setData(apiTimeSeries);

    return pythonInput;
  }

  private static List<PythonInput.DataPoint> convertListToAPITimeSeries(
      List<Instant> timePoints, List<Double> dataPoints) {
    List<PythonInput.DataPoint> list = new ArrayList<>();
    for (int i = 0; i < dataPoints.size(); i++) {
      list.add(PythonInput.DataPoint.builder().time(timePoints.get(i).toEpochMilli()).y(dataPoints.get(i)).build());
    }
    return list;
  }
}
