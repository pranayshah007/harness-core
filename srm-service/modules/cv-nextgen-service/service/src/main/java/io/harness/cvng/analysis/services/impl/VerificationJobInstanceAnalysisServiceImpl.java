/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.beans.BlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.HostSummaryInfo;
import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo.TrafficSplitPercentage;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.HostSummary;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO.HostInfo;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TransactionMetricInfo;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.analysis.services.api.VerificationJobInstanceAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.HostRecordDTO;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo.LoadTestAdditionalInfoBuilder;
import io.harness.cvng.core.beans.SimpleVerificationAdditionalInfo;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.data.structure.CollectionUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class VerificationJobInstanceAnalysisServiceImpl implements VerificationJobInstanceAnalysisService {
  private static final String DEMO_URL_TEMPLATE_PATH =
      "/io/harness/cvng/analysis/service/impl/$provider_$verification_type_deployment_analysis_demo_template_$status.json";
  private static final Set<String> DEMO_CONTROL_HOSTS =
      new HashSet<>(Arrays.asList("harness-deployment-5f67d57589-4jc4c", "harness-deployment-5f67d57589-gp4pd",
          "harness-deployment-7445f86dbf-h8blt"));
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private HostRecordService hostRecordService;
  @Inject private ActivityService activityService;

  @Override
  public Optional<Risk> getLatestRiskScore(String accountId, String verificationJobInstanceId) {
    Optional<Risk> recentHighestTimeSeriesRiskScore =
        deploymentTimeSeriesAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId);
    Optional<Risk> latestLogRiskScore =
        deploymentLogAnalysisService.getRecentHighestRiskScore(accountId, verificationJobInstanceId);
    if (recentHighestTimeSeriesRiskScore.isPresent() && latestLogRiskScore.isPresent()) {
      return Optional.of(CVNGObjectUtils.max(
          recentHighestTimeSeriesRiskScore.get(), latestLogRiskScore.get(), Comparator.naturalOrder()));
    } else if (recentHighestTimeSeriesRiskScore.isPresent()) {
      return recentHighestTimeSeriesRiskScore;
    } else if (latestLogRiskScore.isPresent()) {
      return latestLogRiskScore;
    } else {
      return Optional.empty();
    }
  }

  @Override
  public LoadTestAdditionalInfo getLoadTestAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance) {
    LoadTestAdditionalInfoBuilder loadTestSummaryBuilder = LoadTestAdditionalInfo.builder();
    String currentDeploymentTag =
        activityService.getDeploymentTagFromActivity(accountId, verificationJobInstance.getUuid());
    loadTestSummaryBuilder.currentDeploymentTag(currentDeploymentTag)
        .currentStartTime(verificationJobInstance.getStartTime().toEpochMilli());
    TestVerificationJob testVerificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
    String baselineVerificationJobInstanceId = testVerificationJob.getBaselineVerificationJobInstanceId();
    if (baselineVerificationJobInstanceId != null) {
      VerificationJobInstance baselineVerificationJobInstance =
          verificationJobInstanceService.getVerificationJobInstance(baselineVerificationJobInstanceId);
      String baselineDeploymentTag =
          activityService.getDeploymentTagFromActivity(accountId, baselineVerificationJobInstance.getUuid());
      loadTestSummaryBuilder.baselineDeploymentTag(baselineDeploymentTag)
          .baselineStartTime(baselineVerificationJobInstance.getStartTime().toEpochMilli());
    }
    return loadTestSummaryBuilder.build();
  }

  @Override
  public SimpleVerificationAdditionalInfo getSimpleVerificationAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance) {
    return SimpleVerificationAdditionalInfo.builder().build();
  }

  @Override
  public CanaryBlueGreenAdditionalInfo getCanaryBlueGreenAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance) {
    List<TransactionMetricInfo> transactionMetricInfoList =
        deploymentTimeSeriesAnalysisService.getTransactionMetricInfos(accountId, verificationJobInstance.getUuid());
    List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysis =
        deploymentTimeSeriesAnalysisService.getLatestDeploymentTimeSeriesAnalysis(
            accountId, verificationJobInstance.getUuid(), DeploymentTimeSeriesAnalysisFilter.builder().build());
    List<DeploymentLogAnalysis> deploymentLogAnalysis = deploymentLogAnalysisService.getLatestDeploymentLogAnalysis(
        accountId, verificationJobInstance.getUuid(), DeploymentLogAnalysisFilter.builder().build());

    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
    Set<String> oldHosts = new HashSet<>();
    if (verificationJobInstance.getServiceInstanceDetails() != null
        && verificationJobInstance.getServiceInstanceDetails().isShouldUseNodesFromCD()) {
      oldHosts = new HashSet<>(CollectionUtils.emptyIfNull(
          verificationJobInstance.getServiceInstanceDetails().getServiceInstancesBeforeDeployment()));
    } else if (preDeploymentTimeRange.isPresent()) {
      Set<String> verificationTaskIds =
          verificationTaskService.maybeGetVerificationTaskIds(accountId, verificationJobInstance.getUuid());
      oldHosts = hostRecordService.get(
          verificationTaskIds, preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());
    }

    CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo =
        getCanaryBlueGreenAdditionalInfo(verificationJobInstance);

    updateDeploymentVerificationHostInfoFromAnalyses(
        deploymentTimeSeriesAnalysis, deploymentLogAnalysis, oldHosts, canaryBlueGreenAdditionalInfo);
    updateHostInfoWithAnomalousCount(canaryBlueGreenAdditionalInfo, transactionMetricInfoList, deploymentLogAnalysis);

    canaryBlueGreenAdditionalInfo.setTrafficSplitPercentage(
        getTrafficSplitPercentage((CanaryBlueGreenVerificationJob) verificationJobInstance.getResolvedJob()));
    canaryBlueGreenAdditionalInfo.setFieldNames();

    return canaryBlueGreenAdditionalInfo;
  }

  private CanaryBlueGreenAdditionalInfo getCanaryBlueGreenAdditionalInfo(
      VerificationJobInstance verificationJobInstance) {
    VerificationJobType verificationJobType = verificationJobInstance.getResolvedJob().getType();
    switch (verificationJobType) {
      case CANARY:
        return new CanaryAdditionalInfo();
      case BLUE_GREEN:
      case ROLLING:
        return new BlueGreenAdditionalInfo();
      case AUTO: {
        return getBlueGreenAdditionalInfoForAutoVerificationType(verificationJobInstance);
      }
      default:
        throw new IllegalStateException("Unsupported verificationJobType " + verificationJobType.name());
    }
  }

  private static CanaryBlueGreenAdditionalInfo getBlueGreenAdditionalInfoForAutoVerificationType(
      VerificationJobInstance verificationJobInstance) {
    AppliedDeploymentAnalysisType appliedDeploymentAnalysisType =
        getAppliedDeploymentAnalysisTypeForAutoVerificationType(verificationJobInstance);
    if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
      return new CanaryAdditionalInfo();
    } else {
      return new BlueGreenAdditionalInfo();
    }
  }

  public static AppliedDeploymentAnalysisType getAppliedDeploymentAnalysisTypeForAutoVerificationType(
      VerificationJobInstance verificationJobInstance) {
    Map<String, AppliedDeploymentAnalysisType> appliedDeploymentAnalysisTypeMap =
        verificationJobInstance.getAppliedDeploymentAnalysisTypeMap();
    if (Objects.nonNull(appliedDeploymentAnalysisTypeMap) && appliedDeploymentAnalysisTypeMap.size() > 0) {
      int numberOfCanaryAnalyses = getNumberOfCanaryAnalyses(appliedDeploymentAnalysisTypeMap);
      if (numberOfCanaryAnalyses > appliedDeploymentAnalysisTypeMap.size() / 2) {
        return AppliedDeploymentAnalysisType.CANARY;
      } else {
        return AppliedDeploymentAnalysisType.ROLLING;
      }
    } else {
      return AppliedDeploymentAnalysisType.ROLLING;
    }
  }

  private static int getNumberOfCanaryAnalyses(
      Map<String, AppliedDeploymentAnalysisType> appliedDeploymentAnalysisTypeMap) {
    int numberOfCanaryAnalyses = 0;
    for (AppliedDeploymentAnalysisType appliedDeploymentAnalysisType : appliedDeploymentAnalysisTypeMap.values()) {
      if (appliedDeploymentAnalysisType == AppliedDeploymentAnalysisType.CANARY) {
        numberOfCanaryAnalyses++;
      }
    }
    return numberOfCanaryAnalyses;
  }

  @Override
  public void addDemoAnalysisData(
      String verificationTaskId, CVConfig cvConfig, VerificationJobInstance verificationJobInstance) {
    String demoTemplatePath = getDemoTemplatePath(verificationJobInstance.getVerificationStatus(), cvConfig.getType());
    if (cvConfig.getVerificationType() == VerificationType.TIME_SERIES) {
      deploymentTimeSeriesAnalysisService.addDemoMetricsAnalysisData(
          verificationTaskId, cvConfig, verificationJobInstance, verificationJobInstance.getVerificationStatus());
    } else {
      deploymentLogAnalysisService.addDemoAnalysisData(
          verificationTaskId, cvConfig, verificationJobInstance, demoTemplatePath);
      Optional<TimeRange> preDeploymentTimeRange =
          verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
      if (preDeploymentTimeRange.isPresent()) {
        hostRecordService.save(HostRecordDTO.builder()
                                   .verificationTaskId(verificationTaskId)
                                   .hosts(DEMO_CONTROL_HOSTS)
                                   .startTime(preDeploymentTimeRange.get().getStartTime())
                                   .endTime(preDeploymentTimeRange.get().getEndTime())
                                   .build());
      }
    }
  }

  public String getDemoTemplatePath(ActivityVerificationStatus verificationStatus, DataSourceType dataSourceType) {
    String path = DEMO_URL_TEMPLATE_PATH
                      .replace("$status",
                          verificationStatus == ActivityVerificationStatus.VERIFICATION_PASSED ? "success" : "failure")
                      .replace("$verification_type", dataSourceType.getVerificationType().name().toLowerCase());
    path = path.replace("$provider", dataSourceType.getDemoTemplatePrefix());
    return path;
  }

  private void populatePrimaryAndCanaryHostInfoForTimeseries(
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysisList, Map<String, HostSummaryInfo> controlMap,
      Map<String, HostSummaryInfo> testMap) {
    for (DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis : deploymentTimeSeriesAnalysisList) {
      for (HostInfo hostInfo : deploymentTimeSeriesAnalysis.getHostSummaries()) {
        // Primary nodes should always be no analysis. (Need to override risk in case it's present in both primary and
        // canary)
        if (hostInfo.isPrimary()) {
          HostSummaryInfo hostSummaryInfo =
              HostSummaryInfo.builder().hostName(hostInfo.getHostName()).risk(Risk.NO_ANALYSIS).build();
          controlMap.put(hostInfo.getHostName(), hostSummaryInfo);
        }

        // In case multiple analysis for a test node (possible when using multiple providers), use the one with higher
        // risk
        if (hostInfo.isCanary()) {
          HostSummaryInfo hostSummaryInfo =
              HostSummaryInfo.builder().hostName(hostInfo.getHostName()).risk(hostInfo.getRisk()).build();
          if (!testMap.keySet().contains(hostInfo.getHostName())
              || testMap.get(hostInfo.getHostName()).getRisk().isLessThanEq(hostSummaryInfo.getRisk())) {
            testMap.put(hostInfo.getHostName(), hostSummaryInfo);
          }
        }
      }
    }
  }

  private void populatePrimaryAndCanaryHostInfoForLogs(List<DeploymentLogAnalysis> deploymentLogAnalysisList,
      Map<String, HostSummaryInfo> controlMap, Map<String, HostSummaryInfo> testMap, Set<String> oldHosts) {
    for (String host : oldHosts) {
      HostSummaryInfo hostSummaryInfo = HostSummaryInfo.builder().hostName(host).risk(Risk.NO_ANALYSIS).build();
      controlMap.put(host, hostSummaryInfo);
    }
    for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalysisList) {
      for (HostSummary hostInfo : deploymentLogAnalysis.getHostSummaries()) {
        // In case multiple analysis for a test node (possible when using multiple providers), use the one with higher
        // risk
        HostSummaryInfo hostSummaryInfo = HostSummaryInfo.builder()
                                              .hostName(hostInfo.getHost())
                                              .risk(hostInfo.getResultSummary().getRiskLevel())
                                              .build();
        if (!testMap.keySet().contains(hostInfo.getHost())
            || testMap.get(hostInfo.getHost()).getRisk().isLessThanEq(hostSummaryInfo.getRisk())) {
          testMap.put(hostInfo.getHost(), hostSummaryInfo);
        }
      }
    }
  }

  private void updateDeploymentVerificationHostInfoFromAnalyses(
      List<DeploymentTimeSeriesAnalysis> deploymentTimeSeriesAnalysisList,
      List<DeploymentLogAnalysis> deploymentLogAnalysisList, Set<String> oldHosts,
      CanaryBlueGreenAdditionalInfo additionalInfo) {
    Map<String, HostSummaryInfo> controlMap = new HashMap<>();
    Map<String, HostSummaryInfo> testMap = new HashMap<>();

    if (isNotEmpty(deploymentTimeSeriesAnalysisList)) {
      populatePrimaryAndCanaryHostInfoForTimeseries(deploymentTimeSeriesAnalysisList, controlMap, testMap);
    }
    if (isNotEmpty(deploymentLogAnalysisList)) {
      populatePrimaryAndCanaryHostInfoForLogs(deploymentLogAnalysisList, controlMap, testMap, oldHosts);
    }

    additionalInfo.setPrimary(new HashSet<>(controlMap.values()));
    additionalInfo.setCanary(new HashSet<>(testMap.values()));
  }

  private void updateHostInfoWithAnomalousCount(CanaryBlueGreenAdditionalInfo canaryBlueGreenAdditionalInfo,
      List<TransactionMetricInfo> TransactionMetricInfoList, List<DeploymentLogAnalysis> deploymentLogAnalysisList) {
    canaryBlueGreenAdditionalInfo.getCanary().forEach(hostSummaryInfo -> {
      int anomalousMetricsCount[] = new int[] {0};
      int anomalousLogClustersCount[] = new int[] {0};
      for (TransactionMetricInfo transactionMetricInfo : TransactionMetricInfoList) {
        anomalousMetricsCount[0] += transactionMetricInfo.getNodes()
                                        .stream()
                                        .filter(hostData
                                            -> hostData.getHostName().isPresent()
                                                && hostData.getHostName().get().equals(hostSummaryInfo.getHostName())
                                                && hostData.getRisk().isGreaterThanEq(Risk.OBSERVE))
                                        .count();
      }
      for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalysisList) {
        if (deploymentLogAnalysis.getHostSummaries() != null) {
          deploymentLogAnalysis.getHostSummaries()
              .stream()
              .filter(hostSummary -> hostSummary.getHost().equals(hostSummaryInfo.getHostName()))
              .forEach(hostSummary
                  -> anomalousLogClustersCount[0] +=
                  hostSummary.getResultSummary()
                      .getTestClusterSummaries()
                      .stream()
                      .filter(clusterSummary -> clusterSummary.getRiskLevel().isGreaterThanEq(Risk.OBSERVE))
                      .count());
        }
      }
      hostSummaryInfo.setAnomalousMetricsCount(anomalousMetricsCount[0]);
      hostSummaryInfo.setAnomalousLogClustersCount(anomalousLogClustersCount[0]);
    });
  }

  private TrafficSplitPercentage getTrafficSplitPercentage(
      CanaryBlueGreenVerificationJob canaryBlueGreenVerificationJob) {
    Integer trafficSplitPercentage = canaryBlueGreenVerificationJob.getTrafficSplitPercentage();
    if (trafficSplitPercentage != null) {
      return TrafficSplitPercentage.builder()
          .preDeploymentPercentage(100 - trafficSplitPercentage)
          .postDeploymentPercentage(trafficSplitPercentage)
          .build();
    }
    return null;
  }
}
