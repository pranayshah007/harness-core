/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.cdng.beans.v2.AnalysedDeploymentTestDataNode;
import io.harness.cvng.cdng.beans.v2.AnalysisReason;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
import io.harness.cvng.cdng.beans.v2.ControlDataType;
import io.harness.cvng.cdng.beans.v2.MetricThreshold;
import io.harness.cvng.cdng.beans.v2.MetricThresholdCriteria;
import io.harness.cvng.cdng.beans.v2.MetricType;
import io.harness.cvng.cdng.beans.v2.MetricValue;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricCustomThresholdActions;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class VerifyStepMetricsAnalysisUtils {
  public static boolean isAnalysisResultExcluded(
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, AnalysisResult analysisResult) {
    return deploymentTimeSeriesAnalysisFilter.isAnomalousMetricsOnly() && AnalysisResult.UNHEALTHY != analysisResult;
  }

  public static boolean isTransactionGroupExcluded(
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, Set<String> requestedTransactionGroups,
      String transactionGroup) {
    return deploymentTimeSeriesAnalysisFilter.filterByTransactionNames()
        && !requestedTransactionGroups.contains(transactionGroup);
  }

  public static AnalysisReason getAnalysisReason(DeploymentTimeSeriesAnalysisDTO.HostData hostData) {
    // TODO: As of now LE doesnt provide any info if a threshold was used. When that info is available, use is to refine
    // below logic.
    switch (hostData.getRisk()) {
      case NO_DATA:
        return AnalysisReason.NO_TEST_DATA;
      case NO_ANALYSIS:
        return AnalysisReason.NO_CONTROL_DATA;
      case HEALTHY:
      case OBSERVE:
      case NEED_ATTENTION:
      case UNHEALTHY:
        return AnalysisReason.ML_ANALYSIS;
      default:
        throw new IllegalArgumentException("Unhanded Risk " + hostData.getRisk());
    }
  }

  public static MetricType getMetricTypeFromTimeSeriesMetricType(TimeSeriesMetricType timeSeriesMetricType) {
    switch (timeSeriesMetricType) {
      case RESP_TIME:
        return MetricType.PERFORMANCE_RESPONSE_TIME;
      case THROUGHPUT:
        return MetricType.PERFORMANCE_THROUGHPUT;
      case ERROR:
      case APDEX:
      case INFRA:
      case OTHER:
        return MetricType.PERFORMANCE_OTHER;
      default:
        throw new IllegalArgumentException("Unhanded TimeSeriesMetricType " + timeSeriesMetricType);
    }
  }

  public static MetricThreshold getMetricThresholdFromTimeSeriesThreshold(TimeSeriesThreshold timeSeriesThreshold) {
    // TODO: Add thresholdIdentifier once LE provides threshold details.
    return MetricThreshold.builder()
        .thresholdType(MetricThresholdActionType.getMetricThresholdActionType(timeSeriesThreshold.getAction()))
        .action(
            MetricCustomThresholdActions.getMetricCustomThresholdActions(timeSeriesThreshold.getCriteria().getAction()))
        .criteria(getMetricThresholdCriteriafromTimeSeriesThresholdCriteria(timeSeriesThreshold.getCriteria()))
        .isUserDefined(ThresholdConfigType.USER_DEFINED == timeSeriesThreshold.getThresholdConfigType())
        .build();
  }

  private static MetricThresholdCriteria getMetricThresholdCriteriafromTimeSeriesThresholdCriteria(
      TimeSeriesThresholdCriteria timeSeriesThresholdCriteria) {
    // TODO: Add less than and more than values.
    return MetricThresholdCriteria.builder()
        .actionableCount(timeSeriesThresholdCriteria.getOccurrenceCount())
        .measurementType(timeSeriesThresholdCriteria.getType())
        .build();
  }

  public static AnalysedDeploymentTestDataNode getAnalysedTestDataNodeFromHostData(
      DeploymentTimeSeriesAnalysisDTO.HostData hostData) {
    ControlDataType controlDataType =
        Objects.isNull(hostData.getNearestControlHost()) ? ControlDataType.AVERAGE : ControlDataType.MINIMUM_DEVIATION;
    // TODO: Add appliedThresholds[] once LE is able to provide that info.
    return AnalysedDeploymentTestDataNode.builder()
        .nodeIdentifier(hostData.getHostName().orElse(null))
        .analysisResult(AnalysisResult.fromRisk(hostData.getRisk()))
        .analysisReason(getAnalysisReason(hostData))
        .controlDataType(controlDataType)
        .controlNodeIdentifier(hostData.getNearestControlHost())
        .controlData(getMetricValuesFromRawValues(hostData.getControlData()))
        .testData(getMetricValuesFromRawValues(hostData.getTestData()))
        .build();
  }

  private static List<MetricValue> getMetricValuesFromRawValues(List<Double> values) {
    return CollectionUtils.emptyIfNull(values)
        .stream()
        .map(data -> MetricValue.builder().value(data).build())
        .collect(Collectors.toList());
  }

  public static List<AnalysedDeploymentTestDataNode> getFilteredAnalysedTestDataNodes(
      DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData transactionMetricHostData,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    Set<String> requestedTestNodes = new HashSet<>(deploymentTimeSeriesAnalysisFilter.getHostNames());
    return transactionMetricHostData.getHostData()
        .stream()
        .filter((DeploymentTimeSeriesAnalysisDTO.HostData host) -> {
          if (deploymentTimeSeriesAnalysisFilter.filterByHostNames()) {
            return host.getHostName().isPresent() && requestedTestNodes.contains(host.getHostName().get());
          } else {
            return true;
          }
        })
        .filter((DeploymentTimeSeriesAnalysisDTO.HostData host) -> {
          if (deploymentTimeSeriesAnalysisFilter.isAnomalousNodesOnly()) {
            return Risk.UNHEALTHY == host.getRisk();
          } else {
            return true;
          }
        })
        .map(VerifyStepMetricsAnalysisUtils::getAnalysedTestDataNodeFromHostData)
        .collect(Collectors.toList());
  }

  public static MetricType getMetricTypeFromCvConfigAndMetricDefinition(
      MetricCVConfig<? extends AnalysisInfo> cvConfig, MetricPack.MetricDefinition metricDefinition) {
    CVMonitoringCategory cvMonitoringCategory = cvConfig.getMetricPack().getCategory();
    TimeSeriesMetricType timeSeriesMetricType = metricDefinition.getType();
    switch (cvMonitoringCategory) {
      case ERRORS:
        return MetricType.ERROR;
      case INFRASTRUCTURE:
        return MetricType.INFRASTRUCTURE;
      case PERFORMANCE:
        return getMetricTypeFromTimeSeriesMetricType(timeSeriesMetricType);
      default:
        throw new IllegalArgumentException("Urecognised CVMonitoringCategory " + cvMonitoringCategory);
    }
  }

  private VerifyStepMetricsAnalysisUtils() {}
}
