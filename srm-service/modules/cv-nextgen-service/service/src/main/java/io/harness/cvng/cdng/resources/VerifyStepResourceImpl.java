/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.resources;

import io.harness.beans.FeatureName;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.HostSummaryInfo;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.v2.AbstractAnalysedNode;
import io.harness.cvng.cdng.beans.v2.AnalysedDeploymentNode;
import io.harness.cvng.cdng.beans.v2.AnalysedLoadTestNode;
import io.harness.cvng.cdng.beans.v2.AnalysedNodeOverview;
import io.harness.cvng.cdng.beans.v2.AnalysedNodeType;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.HealthSource;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.ProviderType;
import io.harness.cvng.cdng.beans.v2.VerificationOverview;
import io.harness.cvng.cdng.beans.v2.VerificationResult;
import io.harness.cvng.cdng.beans.v2.VerificationSpec;
import io.harness.cvng.cdng.beans.v2.VerifyStepPathParams;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.services.api.FeatureFlagService;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
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
    return VerificationOverview.builder()
        .spec(getVerificationSpec(verificationJobInstance, deploymentVerificationJobInstanceSummary))
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
        .build();
  }

  private static AppliedDeploymentAnalysisType getAppliedDeploymentAnalysisType(
      DeploymentVerificationJobInstanceSummary summary) {
    if (summary.getRisk() == Risk.NO_DATA || summary.getRisk() == Risk.NO_ANALYSIS) {
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
}
