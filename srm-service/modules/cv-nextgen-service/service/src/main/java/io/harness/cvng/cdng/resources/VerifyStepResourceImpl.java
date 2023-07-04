/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.resources;

import static java.util.stream.Collectors.groupingBy;

import io.harness.beans.FeatureName;
import io.harness.cvng.CVConstants;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.HostSummaryInfo;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.v2.AbstractAnalysedNode;
import io.harness.cvng.cdng.beans.v2.AnalysedDeploymentNode;
import io.harness.cvng.cdng.beans.v2.AnalysedLoadTestNode;
import io.harness.cvng.cdng.beans.v2.AnalysedNodeOverview;
import io.harness.cvng.cdng.beans.v2.AnalysedNodeType;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.Baseline;
import io.harness.cvng.cdng.beans.v2.BaselineOverview;
import io.harness.cvng.cdng.beans.v2.BaselineType;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.ProviderType;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries.Metric;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries.Node;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries.TimeSeriesValue;
import io.harness.cvng.cdng.beans.v2.VerificationMetricsTimeSeries.TransactionGroup;
import io.harness.cvng.cdng.beans.v2.VerificationOverview;
import io.harness.cvng.cdng.beans.v2.VerificationResult;
import io.harness.cvng.cdng.beans.v2.VerificationSpec;
import io.harness.cvng.cdng.beans.v2.VerifyStepPathParams;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.resources.VerifyStepResource;
import io.harness.cvng.verificationjob.beans.AdditionalInfo;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
public class VerifyStepResourceImpl implements VerifyStepResource {
  @Inject private CVNGStepTaskService stepTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private TelemetryReporter telemetryReporter;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @Override
  public List<String> getTransactionGroupsForVerifyStepExecutionId(VerifyStepPathParams verifyStepPathParams) {
    return stepTaskService.getTransactionNames(
        verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId());
  }

  @Override
  public List<HealthSource> getHealthSourcesForVerifyStepExecutionId(VerifyStepPathParams verifyStepPathParams) {
    Set<HealthSourceDTO> healthSourceDtos = stepTaskService.healthSources(
        verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId());
    return CollectionUtils.emptyIfNull(healthSourceDtos)
        .stream()
        .map(dto
            -> HealthSource.builder()
                   .name(dto.getName())
                   .identifier(dto.getIdentifier())
                   .type(MonitoredServiceDataSourceType.getMonitoredServiceDataSourceType(dto.getType()))
                   .providerType(ProviderType.fromVerificationType(dto.getVerificationType()))
                   .build())
        .collect(Collectors.toList());
  }

  private void sendTelemetryEvent(String verifyStepResult, String projectId, String orgId) {
    UserPrincipal userPrincipal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("verifyStepResult", verifyStepResult);
    properties.put("projectId", projectId);
    properties.put("orgId", orgId);
    properties.put("userId", userPrincipal.getEmail());
    telemetryReporter.sendTrackEvent("Verify Step Result", properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.AMPLITUDE, true).build(), Category.GLOBAL);
  }

  @Override
  public VerificationOverview getVerificationOverviewForVerifyStepExecutionId(
      VerifyStepPathParams verifyStepPathParams) {
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        stepTaskService.getByCallBackId(verifyStepPathParams.getVerifyStepExecutionId())
            .getVerificationJobInstanceId());
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        stepTaskService.getDeploymentSummary(verifyStepPathParams.getVerifyStepExecutionId())
            .getDeploymentVerificationJobInstanceSummary();
    AdditionalInfo additionalInfo = deploymentVerificationJobInstanceSummary.getAdditionalInfo();
    // TODO: Currently we do not persist the analysis type used. Need a common voted upon analysis type for a given
    // verification.

    // Send telemetry event
    AppliedDeploymentAnalysisType appliedDeploymentAnalysisType =
        getAppliedDeploymentAnalysisType(deploymentVerificationJobInstanceSummary);
    if (featureFlagService.isFeatureFlagEnabled(
            verifyStepPathParams.getAccountIdentifier(), FeatureName.SRM_TELEMETRY.toString())) {
      sendTelemetryEvent(deploymentVerificationJobInstanceSummary.getStatus().toString(),
          verifyStepPathParams.getProjectIdentifier(), verifyStepPathParams.getOrgIdentifier());
    }
    VerificationSpec verificationSpec =
        getVerificationSpec(verificationJobInstance, deploymentVerificationJobInstanceSummary);

    return VerificationOverview.builder()
        .spec(verificationSpec)
        .appliedDeploymentAnalysisType(appliedDeploymentAnalysisType)
        .verificationStartTimestamp(deploymentVerificationJobInstanceSummary.getActivityStartTime())
        .verificationProgressPercentage(deploymentVerificationJobInstanceSummary.getProgressPercentage())
        .verificationStatus(deploymentVerificationJobInstanceSummary.getStatus())
        .controlNodes(getControlNodesOverview(additionalInfo))
        .testNodes(getTestNodesOverview(additionalInfo))
        .metricsAnalysis(deploymentTimeSeriesAnalysisService.getMetricsAnalysisOverview(
            verifyStepPathParams.getVerifyStepExecutionId()))
        .logClusters(deploymentLogAnalysisService.getLogsAnalysisOverview(
            verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId()))
        .errorClusters(deploymentLogAnalysisService.getErrorsAnalysisOverview(
            verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId()))
        .baselineOverview(getBaselineOverview(verificationSpec, verifyStepPathParams, appliedDeploymentAnalysisType,
            verificationJobInstance, deploymentVerificationJobInstanceSummary))
        .controlDataStartTimestamp(getControlDataStartTimestamp(verificationJobInstance, appliedDeploymentAnalysisType))
        .testDataStartTimestamp(getTestDataStartTimestamp(verificationJobInstance))
        .build();
  }

  public BaselineOverview getBaselineOverview(VerificationSpec verificationSpec,
      VerifyStepPathParams verifyStepPathParams, AppliedDeploymentAnalysisType appliedDeploymentAnalysisType,
      VerificationJobInstance verificationJobInstance,
      DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary) {
    Optional<VerificationJobInstance> baselineVerificationJobInstance =
        verificationJobInstanceService.getPinnedBaselineVerificationJobInstance(
            ServiceEnvironmentParams.builder()
                .environmentIdentifier(verificationSpec.getAnalysedEnvIdentifier())
                .serviceIdentifier(verificationSpec.getAnalysedServiceIdentifier())
                .accountIdentifier(verifyStepPathParams.getAccountIdentifier())
                .projectIdentifier(verifyStepPathParams.getProjectIdentifier())
                .orgIdentifier(verifyStepPathParams.getOrgIdentifier())
                .build());
    boolean isExpired = false;
    BaselineOverview baselineOverview = null;
    if (verificationJobInstance.getResolvedJob().getType() == VerificationJobType.TEST) {
      String baselineVerificationJobInstanceId = null;
      long baselineExpiry = 1L;
      String planExecutionId = "";
      if (baselineVerificationJobInstance.isPresent()) {
        baselineVerificationJobInstanceId = baselineVerificationJobInstance.get().getUuid();
        baselineExpiry = baselineVerificationJobInstance.get().getValidUntil().getTime();
        planExecutionId = baselineVerificationJobInstance.get().getPlanExecutionId();
      }
      baselineOverview = BaselineOverview.builder()
                             .baselineVerificationJobInstanceId(baselineVerificationJobInstanceId)
                             .isApplicableForBaseline(deploymentVerificationJobInstanceSummary.getStatus()
                                 == ActivityVerificationStatus.VERIFICATION_PASSED)
                             .isBaselineExpired(isExpired)
                             .baselineExpiryTimestamp(baselineExpiry)
                             .planExecutionId(planExecutionId)
                             .isBaseline(verificationJobInstance.getUuid().equals(baselineVerificationJobInstanceId))
                             .build();
    }
    return baselineOverview;
  }

  @Override
  public Baseline updateBaseline(VerifyStepPathParams verifyStepPathParams, Baseline isBaseline) {
    return verificationJobInstanceService.pinOrUnpinBaseline(verifyStepPathParams, isBaseline.isBaseline());
  }

  private static AppliedDeploymentAnalysisType getAppliedDeploymentAnalysisType(
      DeploymentVerificationJobInstanceSummary summary) {
    if (summary.getRisk() == Risk.NO_DATA
        || (summary.getRisk() == Risk.NO_ANALYSIS
            && summary.getAdditionalInfo().getType() != VerificationJobType.SIMPLE)) {
      return AppliedDeploymentAnalysisType.NO_ANALYSIS;
    } else {
      return AppliedDeploymentAnalysisType.fromVerificationJobType(summary.getAdditionalInfo().getType());
    }
  }

  private static VerificationSpec getVerificationSpec(VerificationJobInstance verificationJobInstance,
      DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary) {
    return VerificationSpec.builder()
        .analysedServiceIdentifier(verificationJobInstance.getResolvedJob().getServiceIdentifier())
        .analysedEnvIdentifier(verificationJobInstance.getResolvedJob().getEnvIdentifier())
        .monitoredServiceIdentifier(verificationJobInstance.getResolvedJob().getMonitoredServiceIdentifier())
        .monitoredServiceTemplateIdentifier(
            verificationJobInstance.getResolvedJob().getMonitoredServiceTemplateIdentifier())
        .monitoredServiceTemplateVersionLabel(
            verificationJobInstance.getResolvedJob().getMonitoredServiceTemplateVersionLabel())
        .monitoredServiceType(
            StringUtils.isBlank(verificationJobInstance.getResolvedJob().getMonitoredServiceTemplateIdentifier())
                ? MonitoredServiceSpecType.DEFAULT
                : MonitoredServiceSpecType.TEMPLATE)
        .analysisType(verificationJobInstance.getResolvedJob().getType())
        .durationInMinutes(Duration.ofMillis(deploymentVerificationJobInstanceSummary.getDurationMs()).toMinutes())
        .sensitivity(verificationJobInstance.getResolvedJob().getSensitivity())
        .isFailOnNoAnalysis(verificationJobInstance.getResolvedJob().isFailOnNoAnalysis())
        .baselineType(verificationJobInstance.getBaselineType() == null ? BaselineType.LAST
                                                                        : verificationJobInstance.getBaselineType())
        .build();
  }

  private AnalysedNodeOverview getControlNodesOverview(AdditionalInfo additionalInfo) {
    AnalysedNodeOverview analysedNodeOverview;
    switch (additionalInfo.getType()) {
      case CANARY:
        CanaryBlueGreenAdditionalInfo canaryAdditionalInfo = (CanaryBlueGreenAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.PRIMARY)
                                   .nodes(getControlNodesForCanaryOrRollingAnalysisType(canaryAdditionalInfo))
                                   .build();
        break;
      case BLUE_GREEN:
      case AUTO:
      case ROLLING:
        CanaryBlueGreenAdditionalInfo blueGreenAdditionalInfo = (CanaryBlueGreenAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.PRE_DEPLOYMENT)
                                   .nodes(getControlNodesForCanaryOrRollingAnalysisType(blueGreenAdditionalInfo))
                                   .build();
        break;
      case TEST:
        LoadTestAdditionalInfo loadTestAdditionalInfo = (LoadTestAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.BASELINE_TEST)
                                   .nodes(getControlNodesForLoadTestAnalysisType(loadTestAdditionalInfo))
                                   .build();
        break;
      case SIMPLE:
        analysedNodeOverview = null;
        break;
      default:
        throw new IllegalArgumentException("Unrecognised VerificationJobType " + additionalInfo.getType());
    }
    return analysedNodeOverview;
  }

  private static List<AbstractAnalysedNode> getControlNodesForCanaryOrRollingAnalysisType(
      CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo) {
    Set<HostSummaryInfo> hostSummaryInfos = canaryBlueGreenAdditionalInfo.getPrimary();
    return hostSummaryInfos.stream()
        .map(hostSummaryInfo -> AnalysedDeploymentNode.builder().nodeIdentifier(hostSummaryInfo.getHostName()).build())
        .collect(Collectors.toList());
  }

  private boolean isExpired(VerificationJobInstance baselineVerificationJobInstance) {
    long createdAt = baselineVerificationJobInstance.getCreatedAt();
    // If difference between createdAt and currentTime is greater than 180 days, then baseline is expired.
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdAt)
        > CVConstants.MAX_DATA_RETENTION_DURATION.toDays();
  }

  private AnalysedNodeOverview getTestNodesOverview(AdditionalInfo additionalInfo) {
    AnalysedNodeOverview analysedNodeOverview;
    switch (additionalInfo.getType()) {
      case CANARY:
        CanaryBlueGreenAdditionalInfo canaryAdditionalInfo = (CanaryBlueGreenAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.CANARY)
                                   .nodes(getTestNodesForCanaryOrRollingAnalysisType(canaryAdditionalInfo))
                                   .build();
        break;
      case BLUE_GREEN:
      case AUTO:
      case ROLLING:
        CanaryBlueGreenAdditionalInfo blueGreenAdditionalInfo = (CanaryBlueGreenAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.POST_DEPLOYMENT)
                                   .nodes(getTestNodesForCanaryOrRollingAnalysisType(blueGreenAdditionalInfo))
                                   .build();
        break;
      case TEST:
        LoadTestAdditionalInfo loadTestAdditionalInfo = (LoadTestAdditionalInfo) additionalInfo;
        analysedNodeOverview = AnalysedNodeOverview.builder()
                                   .nodeType(AnalysedNodeType.CURRENT_TEST)
                                   .nodes(getTestNodesForLoadTestAnalysisType(loadTestAdditionalInfo))
                                   .build();
        break;
      case SIMPLE:
        analysedNodeOverview = null;
        break;
      default:
        throw new IllegalArgumentException("Unrecognised VerificationJobType " + additionalInfo.getType());
    }
    return analysedNodeOverview;
  }

  private static List<AbstractAnalysedNode> getTestNodesForCanaryOrRollingAnalysisType(
      CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo) {
    Set<HostSummaryInfo> hostSummaryInfos = canaryBlueGreenAdditionalInfo.getCanary();
    return hostSummaryInfos.stream()
        .map(hostSummaryInfo
            -> AnalysedDeploymentNode.builder()
                   .nodeIdentifier(hostSummaryInfo.getHostName())
                   .failedMetrics(hostSummaryInfo.getAnomalousMetricsCount())
                   .failedLogClusters(hostSummaryInfo.getAnomalousLogClustersCount())
                   .verificationResult(VerificationResult.fromRisk(hostSummaryInfo.getRisk()))
                   .build())
        .collect(Collectors.toList());
  }

  private static List<AbstractAnalysedNode> getTestNodesForLoadTestAnalysisType(
      LoadTestAdditionalInfo loadTestAdditionalInfo) {
    return Collections.singletonList(AnalysedLoadTestNode.builder()
                                         .deploymentTag(loadTestAdditionalInfo.getCurrentDeploymentTag())
                                         .testStartTimestamp(loadTestAdditionalInfo.getCurrentStartTime())
                                         .build());
  }

  private static List<AbstractAnalysedNode> getControlNodesForLoadTestAnalysisType(
      LoadTestAdditionalInfo loadTestAdditionalInfo) {
    return Collections.singletonList(AnalysedLoadTestNode.builder()
                                         .deploymentTag(loadTestAdditionalInfo.getBaselineDeploymentTag())
                                         .testStartTimestamp(loadTestAdditionalInfo.getBaselineStartTime())
                                         .build());
  }

  @Override
  public PageResponse<MetricsAnalysis> getMetricsAnalysisForVerifyStepExecutionId(
      VerifyStepPathParams verifyStepPathParams, boolean anomalousMetricsOnly, List<String> healthSources,
      List<String> transactionGroups, List<String> nodes, PageRequest pageRequest) {
    List<MetricsAnalysis> metricsAnalyses =
        deploymentTimeSeriesAnalysisService.getFilteredMetricAnalysesForVerifyStepExecutionId(
            verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId(),
            DeploymentTimeSeriesAnalysisFilter.builder()
                .healthSourceIdentifiers(healthSources)
                .transactionNames(transactionGroups)
                .hostNames(nodes)
                .anomalousMetricsOnly(anomalousMetricsOnly)
                .anomalousNodesOnly(anomalousMetricsOnly)
                .build());

    return PageUtils.offsetAndLimit(metricsAnalyses, pageRequest.getPageIndex(), pageRequest.getPageSize());
  }

  @Override
  public VerificationMetricsTimeSeries getMetricsTimeSeriesForVerifyStepExecutionId(
      VerifyStepPathParams verifyStepPathParams) {
    Map<String, Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>> groupedTimeSeriesRecords =
        getTimeSeriesRecordsGroupedByHealthSourceIds(verifyStepPathParams);
    return VerificationMetricsTimeSeries.builder()
        .verificationId(verifyStepPathParams.getVerifyStepExecutionId())
        .healthSources(getHealthSources(groupedTimeSeriesRecords))
        .build();
  }

  private Map<String, Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>>
  getTimeSeriesRecordsGroupedByHealthSourceIds(VerifyStepPathParams verifyStepPathParams) {
    Map<String, Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>> groupedTimeSeriesRecords =
        new HashMap<>();
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verifyStepPathParams.getVerifyStepExecutionId());
    Map<String, String> cvConfigIdToHealthSourceIdMap =
        verificationJobInstance.getCvConfigMap().values().stream().collect(Collectors.toMap(CVConfig::getUuid,
            cvConfig
            -> cvConfig.getFullyQualifiedIdentifier().substring(
                cvConfig.getFullyQualifiedIdentifier().indexOf("/") + 1),
            (u, v) -> v));
    Set<String> verificationTaskIds = verificationTaskService.getVerificationTaskIds(
        verifyStepPathParams.getAccountIdentifier(), verifyStepPathParams.getVerifyStepExecutionId());
    Map<String, String> verificationTaskIdToHealthSourceNameMap =
        getVerificationTaskIdToHealthSourceIdMap(verificationTaskIds, cvConfigIdToHealthSourceIdMap);
    for (String verificationTaskId : verificationTaskIds) {
      List<TimeSeriesRecordDTO> timeSeriesRecords =
          new ArrayList<>(timeSeriesRecordService.getTimeSeriesRecordsForVerificationTaskId(verificationTaskId));
      groupedTimeSeriesRecords.put(verificationTaskIdToHealthSourceNameMap.getOrDefault(verificationTaskId, ""),
          getGroupedTimeSeriesRecordDtos(timeSeriesRecords));
    }
    return groupedTimeSeriesRecords;
  }

  private Map<String, String> getVerificationTaskIdToHealthSourceIdMap(
      Set<String> verificationTaskIds, Map<String, String> cvConfigIdToHealthSourceNameMap) {
    return verificationTaskService.getVerificationTasksForGivenIds(verificationTaskIds)
        .stream()
        .filter(
            verificationTask -> verificationTask.getTaskInfo().getTaskType() == VerificationTask.TaskType.DEPLOYMENT)
        .collect(Collectors.toMap(VerificationTask::getUuid,
            verificationTask
            -> cvConfigIdToHealthSourceNameMap.getOrDefault(
                ((VerificationTask.DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId(), ""),
            (u, v) -> v));
  }

  private static Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>> getGroupedTimeSeriesRecordDtos(
      List<TimeSeriesRecordDTO> allTimeSeriesRecords) {
    return allTimeSeriesRecords.stream().collect(groupingBy(
        dto -> Objects.nonNull(dto.getGroupName()) ? dto.getGroupName() : "", groupByMetricNameAndHostIdentifier()));
  }

  private static List<VerificationMetricsTimeSeries.HealthSource> getHealthSources(
      Map<String, Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>> healthSourceNameMappedDtos) {
    List<VerificationMetricsTimeSeries.HealthSource> healthSources = new ArrayList<>();
    for (Map.Entry<String, Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>> healthSourceEntry :
        healthSourceNameMappedDtos.entrySet()) {
      healthSources.add(VerificationMetricsTimeSeries.HealthSource.builder()
                            .healthSourceIdentifier(healthSourceEntry.getKey())
                            .transactionGroups(getTransactionGroups(healthSourceEntry.getValue()))
                            .build());
    }
    return healthSources;
  }
  private static List<TransactionGroup> getTransactionGroups(
      Map<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>> transactionGroupMappedDtos) {
    List<TransactionGroup> transactionGroups = new ArrayList<>();
    for (Map.Entry<String, Map<String, Map<String, List<TimeSeriesRecordDTO>>>> transactionGroupEntry :
        transactionGroupMappedDtos.entrySet()) {
      transactionGroups.add(TransactionGroup.builder()
                                .metrics(getMetrics(transactionGroupEntry.getValue()))
                                .transactionGroupName(transactionGroupEntry.getKey())
                                .build());
    }
    return transactionGroups;
  }

  private static List<Metric> getMetrics(Map<String, Map<String, List<TimeSeriesRecordDTO>>> metricMappedDtos) {
    List<Metric> metrics = new ArrayList<>();
    for (Map.Entry<String, Map<String, List<TimeSeriesRecordDTO>>> metricEntry : metricMappedDtos.entrySet()) {
      metrics.add(Metric.builder().metricName(metricEntry.getKey()).nodes(getNodes(metricEntry.getValue())).build());
    }
    return metrics;
  }

  private static List<Node> getNodes(Map<String, List<TimeSeriesRecordDTO>> nodeMappedDtos) {
    List<Node> nodes = new ArrayList<>();
    for (Map.Entry<String, List<TimeSeriesRecordDTO>> nodeEntry : nodeMappedDtos.entrySet()) {
      List<TimeSeriesValue> sortedTimeSeries =
          nodeEntry.getValue()
              .stream()
              .sorted(Comparator.comparing(TimeSeriesRecordDTO::getEpochMinute))
              .map(timeSeriesRecordDTO
                  -> TimeSeriesValue.builder()
                         .metricValue(timeSeriesRecordDTO.getMetricValue())
                         .epochSecond(TimeUnit.MINUTES.toSeconds(timeSeriesRecordDTO.getEpochMinute()))
                         .build())
              .collect(Collectors.toList());
      nodes.add(Node.builder().nodeIdentifier(nodeEntry.getKey()).timeSeries(sortedTimeSeries).build());
    }
    return nodes;
  }

  private static Collector<TimeSeriesRecordDTO, ?, Map<String, Map<String, List<TimeSeriesRecordDTO>>>>
  groupByMetricNameAndHostIdentifier() {
    return groupingBy(dto
        -> Objects.nonNull(dto.getMetricName()) ? dto.getMetricName() : "",
        groupingBy(dto -> Objects.nonNull(dto.getHost()) ? dto.getHost() : ""));
  }

  private Long getTestDataStartTimestamp(VerificationJobInstance verificationJobInstance) {
    Long testDataStartTimestamp = null;
    TimeRange testDataStartTimerange =
        deploymentTimeSeriesAnalysisService.getTestDataTimeRange(verificationJobInstance, null);
    if (Objects.nonNull(testDataStartTimerange)) {
      testDataStartTimestamp = testDataStartTimerange.getStartTime().toEpochMilli();
    }
    return testDataStartTimestamp;
  }

  private Long getControlDataStartTimestamp(
      VerificationJobInstance verificationJobInstance, AppliedDeploymentAnalysisType appliedDeploymentAnalysisType) {
    Long controlDataStartTimestamp = null;
    TimeRange controlDataStartTimerange =
        deploymentTimeSeriesAnalysisService
            .getControlDataTimeRange(appliedDeploymentAnalysisType, verificationJobInstance, null)
            .orElse(null);
    if (Objects.nonNull(controlDataStartTimerange)) {
      controlDataStartTimestamp = controlDataStartTimerange.getStartTime().toEpochMilli();
    }
    return controlDataStartTimestamp;
  }
}
