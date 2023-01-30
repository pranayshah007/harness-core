/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType.CANARY;
import static io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType.NO_ANALYSIS;
import static io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType.ROLLING;
import static io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType.TEST;

import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostData;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesCustomThresholdActions;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdComparisonType;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.cdng.beans.v2.AnalysedDeploymentTestDataNode;
import io.harness.cvng.cdng.beans.v2.AnalysisReason;
import io.harness.cvng.cdng.beans.v2.AnalysisResult;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.ControlDataType;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricThreshold;
import io.harness.cvng.cdng.beans.v2.MetricThresholdCriteria;
import io.harness.cvng.cdng.beans.v2.MetricThresholdType;
import io.harness.cvng.cdng.beans.v2.MetricType;
import io.harness.cvng.cdng.beans.v2.MetricValue;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.ProviderType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricCustomThresholdActions;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.utils.URIBuilder;

public class VerifyStepMetricsAnalysisUtils {
  private static final long MILLIS_IN_MINUTE = 60000;
  private static final String DATE_FORMAT_STRING = "yyyy-MM-dd HH:mm";
  public static final String LOAD_TEST_CURRENT_NODE_IDENTIFIER = "Current-test";
  public static final String LOAD_TEST_BASELINE_NODE_IDENTIFIER = "Baseline-test";

  public static boolean isAnalysisResultExcluded(
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, AnalysisResult analysisResult) {
    return deploymentTimeSeriesAnalysisFilter.isAnomalousMetricsOnly() && AnalysisResult.UNHEALTHY != analysisResult;
  }

  private static AnalysisReason getAnalysisReason(HostData hostData, Map<String, MetricThreshold> metricThresholdMap) {
    switch (hostData.getRisk()) {
      case NO_DATA:
        return AnalysisReason.NO_TEST_DATA;
      case NO_ANALYSIS:
        return AnalysisReason.NO_CONTROL_DATA;
      case HEALTHY:
      case OBSERVE:
      case NEED_ATTENTION:
        return AnalysisReason.ML_ANALYSIS;
      case UNHEALTHY:
        return getReasonForFailure(hostData, metricThresholdMap);
      default:
        throw new IllegalArgumentException("Unhanded Risk " + hostData.getRisk());
    }
  }

  private static AnalysisReason getReasonForFailure(
      HostData hostData, Map<String, MetricThreshold> metricThresholdMap) {
    if (CollectionUtils.isEmpty(hostData.getAppliedThresholdIds())) {
      return AnalysisReason.ML_ANALYSIS;
    }
    List<String> appliedThresholds = hostData.getAppliedThresholdIds();
    for (String appliedThreshold : appliedThresholds) {
      if (metricThresholdMap.get(appliedThreshold).getThresholdType() == MetricThresholdType.FAIL_FAST) {
        return AnalysisReason.CUSTOM_FAIL_FAST_THRESHOLD;
      }
    }
    return AnalysisReason.ML_ANALYSIS;
  }

  private static MetricType getMetricTypeFromMetricDefinition(MetricDefinition metricDefinition) {
    TimeSeriesMetricType timeSeriesMetricType = metricDefinition.getType();
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

  private static MetricThreshold getMetricThresholdFromTimeSeriesThreshold(TimeSeriesThreshold timeSeriesThreshold) {
    return MetricThreshold.builder()
        .thresholdType(MetricThresholdType.fromTimeSeriesThresholdActionType(timeSeriesThreshold.getAction()))
        .action(MetricCustomThresholdActions.getMetricCustomThresholdActions(
            timeSeriesThreshold.getAction().equals(TimeSeriesThresholdActionType.IGNORE)
                ? TimeSeriesCustomThresholdActions.IGNORE
                : timeSeriesThreshold.getCriteria().getAction()))
        .criteria(getMetricThresholdCriteriafromTimeSeriesThresholdCriteria(timeSeriesThreshold))
        .isUserDefined(ThresholdConfigType.USER_DEFINED == timeSeriesThreshold.getThresholdConfigType())
        .id(timeSeriesThreshold.getUuid())
        .build();
  }

  private static MetricThresholdCriteria getMetricThresholdCriteriafromTimeSeriesThresholdCriteria(
      TimeSeriesThreshold timeSeriesThreshold) {
    MetricThresholdCriteria metricThresholdCriteria =
        MetricThresholdCriteria.builder()
            .actionableCount(timeSeriesThreshold.getCriteria().getOccurrenceCount())
            .measurementType(timeSeriesThreshold.getCriteria().getType())
            .build();
    Double thresholdValue = timeSeriesThreshold.getCriteria().getValue();
    if (timeSeriesThreshold.getCriteria().getType() == TimeSeriesThresholdComparisonType.RATIO) {
      thresholdValue *= 100;
    }
    if (timeSeriesThreshold.getAction() == TimeSeriesThresholdActionType.IGNORE) {
      if (timeSeriesThreshold.getCriteria().getThresholdType() == TimeSeriesThresholdType.ACT_WHEN_LOWER) {
        metricThresholdCriteria.setGreaterThanThreshold(thresholdValue);
      } else {
        metricThresholdCriteria.setLessThanThreshold(thresholdValue);
      }
    } else {
      if (timeSeriesThreshold.getCriteria().getThresholdType() == TimeSeriesThresholdType.ACT_WHEN_LOWER) {
        metricThresholdCriteria.setLessThanThreshold(thresholdValue);
      } else {
        metricThresholdCriteria.setGreaterThanThreshold(thresholdValue);
      }
    }
    return metricThresholdCriteria;
  }

  private static AnalysedDeploymentTestDataNode getAnalysedTestDataNodeFromHostData(
      HostData hostData, Map<String, MetricThreshold> metricThresholdMap) {
    AnalysisResult analysisResult = AnalysisResult.fromRisk(hostData.getRisk());
    ControlDataType controlDataType = null;
    if (analysisResult != AnalysisResult.NO_ANALYSIS) {
      controlDataType =
          (Objects.isNull(hostData.getNearestControlHost()) || hostData.getNearestControlHost().equals("None"))
          ? ControlDataType.AVERAGE
          : ControlDataType.MINIMUM_DEVIATION;
    }
    return AnalysedDeploymentTestDataNode.builder()
        .nodeIdentifier(hostData.getHostName().orElse(null))
        .analysisResult(analysisResult)
        .analysisReason(getAnalysisReason(hostData, metricThresholdMap))
        .controlDataType(controlDataType)
        .controlNodeIdentifier(hostData.getNearestControlHost())
        .normalisedControlData(getMetricValuesFromRawValues(hostData.getControlData()))
        .normalisedTestData(getMetricValuesFromRawValues(hostData.getTestData()))
        .appliedThresholds(hostData.getAppliedThresholdIds())
        .build();
  }

  private static List<MetricValue> getMetricValuesFromRawValues(List<Double> values) {
    return CollectionUtils.emptyIfNull(values)
        .stream()
        .map(data -> MetricValue.builder().value(data).build())
        .collect(Collectors.toList());
  }

  public static List<AnalysedDeploymentTestDataNode> getFilteredAnalysedTestDataNodes(
      TransactionMetricHostData transactionMetricHostData,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, List<MetricThreshold> thresholds) {
    Set<String> requestedTestNodes =
        new HashSet<>(CollectionUtils.emptyIfNull(deploymentTimeSeriesAnalysisFilter.getHostNames()));
    Map<String, MetricThreshold> metricThresholdMap =
        CollectionUtils.emptyIfNull(thresholds)
            .stream()
            .collect(Collectors.toMap(MetricThreshold::getId, threshold -> threshold, (existing, current) -> current));
    return transactionMetricHostData.getHostData()
        .stream()
        .filter((HostData host) -> {
          if (deploymentTimeSeriesAnalysisFilter.filterByHostNames()) {
            return host.getHostName().isPresent() && requestedTestNodes.contains(host.getHostName().get());
          } else {
            return true;
          }
        })
        .filter((HostData host) -> {
          if (deploymentTimeSeriesAnalysisFilter.isAnomalousNodesOnly()) {
            return Risk.UNHEALTHY == host.getRisk();
          } else {
            return true;
          }
        })
        .map(hostData -> getAnalysedTestDataNodeFromHostData(hostData, metricThresholdMap))
        .collect(Collectors.toList());
  }

  public static MetricType getMetricTypeFromCvConfigAndMetricDefinition(
      MetricCVConfig<? extends AnalysisInfo> cvConfig, MetricDefinition metricDefinition) {
    CVMonitoringCategory cvMonitoringCategory = cvConfig.getCategory();
    switch (cvMonitoringCategory) {
      case ERRORS:
        return MetricType.ERROR;
      case INFRASTRUCTURE:
        return MetricType.INFRASTRUCTURE;
      case PERFORMANCE:
        return getMetricTypeFromMetricDefinition(metricDefinition);
      default:
        throw new IllegalArgumentException("Urecognised CVMonitoringCategory " + cvMonitoringCategory);
    }
  }

  private static List<MetricValue> getMetricValuesFromTimeSeriesRecordDtos(
      List<TimeSeriesRecordDTO> timeSeriesRecordDtos) {
    return CollectionUtils.emptyIfNull(timeSeriesRecordDtos)
        .stream()
        .map(timeSeriesRecordDto
            -> MetricValue.builder()
                   .value(timeSeriesRecordDto.getMetricValue())
                   .timestampInMillis(getTimestampInMillisFromEpochMinute(timeSeriesRecordDto.getEpochMinute()))
                   .build())
        .collect(Collectors.toList());
  }

  public static AppliedDeploymentAnalysisType getAppliedDeploymentAnalysisTypeFromLearningEngineTaskType(
      LearningEngineTaskType learningEngineTaskType) {
    AppliedDeploymentAnalysisType appliedDeploymentAnalysisType = NO_ANALYSIS;
    if (Objects.nonNull(learningEngineTaskType)) {
      switch (learningEngineTaskType) {
        case CANARY_DEPLOYMENT_LOG:
        case CANARY_DEPLOYMENT_TIME_SERIES:
          appliedDeploymentAnalysisType = CANARY;
          break;
        case BEFORE_AFTER_DEPLOYMENT_LOG:
        case BEFORE_AFTER_DEPLOYMENT_TIME_SERIES:
          appliedDeploymentAnalysisType = ROLLING;
          break;
        case TIME_SERIES_LOAD_TEST:
          appliedDeploymentAnalysisType = TEST;
          break;
        default:
          throw new IllegalArgumentException("Unmapped LearningEngineTaskType " + learningEngineTaskType);
      }
    }
    return appliedDeploymentAnalysisType;
  }

  public static Map<String, Map<String, List<TimeSeriesRecordDTO>>> convertTimeSeriesRecordDtosListToMap(
      List<TimeSeriesRecordDTO> timeSeriesRecordDtos) {
    return CollectionUtils.emptyIfNull(timeSeriesRecordDtos)
        .stream()
        .collect(Collectors.groupingBy(
            TimeSeriesRecordDTO::getMetricIdentifier, Collectors.groupingBy(TimeSeriesRecordDTO::getHost)));
  }

  public static Set<String> parseTestNodeIdentifiersFromDeploymentTimeSeriesAnalysis(
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    return CollectionUtils.emptyIfNull(timeSeriesAnalysis.getHostSummaries())
        .stream()
        .filter(DeploymentTimeSeriesAnalysisDTO.HostInfo::isCanary)
        .map(DeploymentTimeSeriesAnalysisDTO.HostInfo::getHostName)
        .collect(Collectors.toSet());
  }

  public static Set<String> parseControlNodeIdentifiersFromDeploymentTimeSeriesAnalysis(
      DeploymentTimeSeriesAnalysis timeSeriesAnalysis) {
    return CollectionUtils.emptyIfNull(timeSeriesAnalysis.getHostSummaries())
        .stream()
        .filter(DeploymentTimeSeriesAnalysisDTO.HostInfo::isPrimary)
        .map(DeploymentTimeSeriesAnalysisDTO.HostInfo::getHostName)
        .collect(Collectors.toSet());
  }

  public static void populateRawMetricDataInMetricAnalysis(AppliedDeploymentAnalysisType appliedDeploymentAnalysisType,
      Map<String, List<TimeSeriesRecordDTO>> controlNodesRawData,
      Map<String, List<TimeSeriesRecordDTO>> testNodesRawData, MetricsAnalysis metricsAnalysis) {
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.TEST) {
      populateRawMetricDataInMetricAnalysisForLoadTestAnalysis(controlNodesRawData, testNodesRawData, metricsAnalysis);
    } else {
      populateRawMetricDataInMetricAnalysisForCanaryAndRollingAnalysis(
          controlNodesRawData, testNodesRawData, metricsAnalysis);
    }
  }

  private static void populateRawMetricDataInMetricAnalysisForLoadTestAnalysis(
      Map<String, List<TimeSeriesRecordDTO>> controlNodesRawData,
      Map<String, List<TimeSeriesRecordDTO>> testNodesRawData, MetricsAnalysis metricsAnalysis) {
    if (CollectionUtils.isNotEmpty(metricsAnalysis.getTestDataNodes())) {
      // In case of Load-test there will be a single node.
      AnalysedDeploymentTestDataNode analysedDeploymentTestDataNode = metricsAnalysis.getTestDataNodes().get(0);
      analysedDeploymentTestDataNode.setNodeIdentifier(LOAD_TEST_CURRENT_NODE_IDENTIFIER);
      analysedDeploymentTestDataNode.setTestData(
          getMetricValuesFromTimeSeriesRecordDtos(testNodesRawData.get(LOAD_TEST_CURRENT_NODE_IDENTIFIER)));
      if (controlNodesRawData.containsKey(LOAD_TEST_BASELINE_NODE_IDENTIFIER)) {
        analysedDeploymentTestDataNode.setControlNodeIdentifier(LOAD_TEST_BASELINE_NODE_IDENTIFIER);
        analysedDeploymentTestDataNode.setControlData(
            getMetricValuesFromTimeSeriesRecordDtos(controlNodesRawData.get(LOAD_TEST_BASELINE_NODE_IDENTIFIER)));
      }
    }
  }

  private static void populateRawMetricDataInMetricAnalysisForCanaryAndRollingAnalysis(
      Map<String, List<TimeSeriesRecordDTO>> controlNodesRawData,
      Map<String, List<TimeSeriesRecordDTO>> testNodesRawData, MetricsAnalysis metricsAnalysis) {
    metricsAnalysis.getTestDataNodes().forEach(analysedDeploymentTestDataNode -> {
      populateRawControlDataForAnalysedDeploymentTestDataNode(analysedDeploymentTestDataNode, controlNodesRawData);
      populateRawTestDataForAnalysedDeploymentTestDataNode(analysedDeploymentTestDataNode, testNodesRawData);
    });
  }

  private static void populateRawTestDataForAnalysedDeploymentTestDataNode(
      AnalysedDeploymentTestDataNode analysedDeploymentTestDataNode,
      Map<String, List<TimeSeriesRecordDTO>> mapOfHostIdentifierAndTimeSeriesRecordDtos) {
    analysedDeploymentTestDataNode.setTestData(getMetricValuesFromTimeSeriesRecordDtos(
        mapOfHostIdentifierAndTimeSeriesRecordDtos.get(analysedDeploymentTestDataNode.getNodeIdentifier())));
  }

  private static void populateRawControlDataForAnalysedDeploymentTestDataNode(
      AnalysedDeploymentTestDataNode analysedDeploymentTestDataNode,
      Map<String, List<TimeSeriesRecordDTO>> mapOfHostIdentifierAndTimeSeriesRecordDtos) {
    analysedDeploymentTestDataNode.setControlData(getMetricValuesFromTimeSeriesRecordDtos(
        mapOfHostIdentifierAndTimeSeriesRecordDtos.get(analysedDeploymentTestDataNode.getControlNodeIdentifier())));
  }

  private static long getTimestampInMillisFromEpochMinute(long epochMinute) {
    return MILLIS_IN_MINUTE * epochMinute;
  }

  public static List<MetricCVConfig<? extends AnalysisInfo>> getFilteredMetricCVConfigs(
      VerificationJobInstance verificationJobInstance,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter) {
    Set<String> requestedHealthSources =
        new HashSet<>(CollectionUtils.emptyIfNull(deploymentTimeSeriesAnalysisFilter.getHealthSourceIdentifiers()));
    Set<String> requestedTransactionGroups =
        new HashSet<>(CollectionUtils.emptyIfNull(deploymentTimeSeriesAnalysisFilter.getTransactionNames()));

    List<CVConfig> cvConfigs = verificationJobInstance.getResolvedJob().getCvConfigs();

    return cvConfigs.stream()
        .filter(VerifyStepMetricsAnalysisUtils::isMetricCVConfig)
        .map(cvConfig -> (MetricCVConfig<? extends AnalysisInfo>) cvConfig)
        .filter(cvConfig -> isHealthSourceIncluded(requestedHealthSources, cvConfig))
        .filter(cvConfig -> isTransactionGroupIncluded(requestedTransactionGroups, cvConfig))
        .collect(Collectors.toList());
  }

  public static String setDeeplinkURLWithRange(DeploymentTimeSeriesAnalysis timeSeriesAnalysis, String deepLinkURL) {
    LocalDateTime endTime = timeSeriesAnalysis.getEndTime().atZone(ZoneId.systemDefault()).toLocalDateTime();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_STRING);
    long diffMinutes = timeSeriesAnalysis.getStartTime().until(
        timeSeriesAnalysis.getEndTime(), ChronoUnit.MINUTES); // this is a diff in min
    String rangeInput = Math.max(diffMinutes, 1) + "m";
    URIBuilder uriBuilder;
    try {
      uriBuilder = new URIBuilder(deepLinkURL);
      uriBuilder.addParameter("g0.range_input", rangeInput); // eg. 10s or 10m or 1h
      uriBuilder.addParameter("g0.g0.end_input", endTime.format(formatter)); // eg. YYYY-MM-DD HH:MM
      return uriBuilder.build().toString();
    } catch (URISyntaxException ignored) {
    }
    return null;
  }

  public static HealthSource getHealthSourceFromCVConfig(MetricCVConfig<? extends AnalysisInfo> metricCVConfig) {
    return HealthSource.builder()
        .identifier(metricCVConfig.getFullyQualifiedIdentifier())
        .name(metricCVConfig.getMonitoringSourceName())
        .type(MonitoredServiceDataSourceType.dataSourceTypeMonitoredServiceDataSourceTypeMap.get(
            metricCVConfig.getType()))
        .providerType(ProviderType.METRICS)
        .build();
  }

  public static String getTransactionGroupFromCVConfig(MetricCVConfig<? extends AnalysisInfo> metricCVConfig) {
    String transactionGroup;
    if (metricCVConfig.maybeGetGroupName().isPresent()) {
      transactionGroup = metricCVConfig.maybeGetGroupName().get();
    } else {
      transactionGroup = null;
    }
    return transactionGroup;
  }

  public static boolean isDeploymentVerificationEnabledForThisMetric(AnalysisInfo analysisInfo) {
    return analysisInfo.getDeploymentVerification().isEnabled();
  }

  public static boolean isMetricCVConfig(CVConfig cvConfig) {
    return cvConfig instanceof MetricCVConfig;
  }

  public static boolean isTransactionGroupIncluded(
      Set<String> requestedTransactionGroups, MetricCVConfig<? extends AnalysisInfo> cvConfig) {
    return CollectionUtils.isEmpty(requestedTransactionGroups)
        || (cvConfig.maybeGetGroupName().isPresent()
            && requestedTransactionGroups.contains(cvConfig.maybeGetGroupName().get()));
  }

  public static boolean isHealthSourceIncluded(
      Set<String> requestedHealthSources, MetricCVConfig<? extends AnalysisInfo> cvConfig) {
    return CollectionUtils.isEmpty(requestedHealthSources)
        || requestedHealthSources.contains(cvConfig.getFullyQualifiedIdentifier());
  }

  public static List<MetricThreshold> getThresholdsFromDefinition(MetricDefinition metricDefinition) {
    return CollectionUtils.emptyIfNull(metricDefinition.getThresholds())
        .stream()
        .map(VerifyStepMetricsAnalysisUtils::getMetricThresholdFromTimeSeriesThreshold)
        .collect(Collectors.toList());
  }

  public static boolean areMetricsFromCVConfigFilteredOut(Map<String, MetricsAnalysis> metricsForThisAnalysis) {
    return Objects.isNull(metricsForThisAnalysis);
  }

  public static void removeMetricFromResult(Map<String, MetricsAnalysis> resultMap, String metricIdentifier) {
    resultMap.remove(metricIdentifier);
  }

  public static boolean isTransactionGroupExcluded(
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, String transactionGroup) {
    return deploymentTimeSeriesAnalysisFilter.filterByTransactionNames()
        && !deploymentTimeSeriesAnalysisFilter.getTransactionNames().contains(transactionGroup);
  }

  private VerifyStepMetricsAnalysisUtils() {}
}
