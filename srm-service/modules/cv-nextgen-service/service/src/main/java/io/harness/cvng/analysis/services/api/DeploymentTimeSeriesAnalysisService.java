/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.TimeSeriesAnalysisSummary;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.beans.TransactionMetricInfo;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.cdng.beans.v2.AppliedDeploymentAnalysisType;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysis;
import io.harness.cvng.cdng.beans.v2.MetricsAnalysisOverview;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DeploymentTimeSeriesAnalysisService {
  void save(DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis);
  TransactionMetricInfoSummaryPageDTO getMetrics(String accountId, String verificationJobInstanceId,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, PageParams pageParams);
  List<TransactionMetricInfo> getTransactionMetricInfos(String accountId, String verificationJobInstanceId);
  List<DeploymentTimeSeriesAnalysis> getAnalysisResults(String verificationTaskId);
  boolean isAnalysisFailFastForLatestTimeRange(String verificationTaskId);
  Optional<Risk> getRecentHighestRiskScore(String accountId, String verificationJobInstanceId);

  DeploymentTimeSeriesAnalysis getRecentHighestDeploymentTimeSeriesAnalysis(
      String accountId, String verificationJobInstanceId);

  List<DeploymentTimeSeriesAnalysis> getLatestDeploymentTimeSeriesAnalysis(String accountId,
      String verificationJobInstanceId, DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter);

  TimeSeriesAnalysisSummary getAnalysisSummary(List<String> verificationJobInstanceIds);

  String getTimeSeriesDemoTemplate(String verificationTaskId);

  void addDemoAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, String demoTemplatePath);

  List<String> getTransactionNames(String accountId, String verificationJobInstanceId);
  Set<String> getNodeNames(String accountId, String verificationJobInstanceId);

  List<MetricsAnalysis> getFilteredMetricAnalysesForVerifyStepExecutionId(String accountId,
      String verifyStepExecutionId, DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter);

  MetricsAnalysisOverview getMetricsAnalysisOverview(String verifyStepExecutionId);

  void addDemoMetricsAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, ActivityVerificationStatus activityVerificationStatus);

  // TODO: Add UTs for getControlDataTimeRange getTestDataTimeRange
  Optional<TimeRange> getControlDataTimeRange(AppliedDeploymentAnalysisType appliedDeploymentAnalysisType,
      VerificationJobInstance verificationJobInstance, DeploymentTimeSeriesAnalysis timeSeriesAnalysis);

  TimeRange getTestDataTimeRange(
      VerificationJobInstance verificationJobInstance, DeploymentTimeSeriesAnalysis timeSeriesAnalysis);
}
