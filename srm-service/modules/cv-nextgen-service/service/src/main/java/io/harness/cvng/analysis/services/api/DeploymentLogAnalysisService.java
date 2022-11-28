/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.ErrorAnalysisSummary;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.LogsAnalysisSummary;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListWithCountDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DeploymentLogAnalysisService {
  void save(DeploymentLogAnalysis deploymentLogAnalysisDTO);

  List<DeploymentLogAnalysis> getAnalysisResults(String verificationTaskId);

  List<LogAnalysisClusterChartDTO> getLogAnalysisClusters(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter);

  LogAnalysisRadarChartListWithCountDTO getRadarChartLogAnalysisResult(String accountId,
      String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams);

  List<LogAnalysisRadarChartClusterDTO> getRadarChartLogAnalysisClusters(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter);

  Optional<Risk> getRecentHighestRiskScore(String accountId, String verificationJobInstanceId);

  DeploymentLogAnalysis getRecentHighestDeploymentLogAnalysis(String accountId, String verificationJobInstanceId);

  LogsAnalysisSummary getAnalysisSummary(String accountId, List<String> verificationJobInstanceIds);

  ErrorAnalysisSummary getErrorAnalysisSummary(String accountId, List<String> verificationJobInstanceIds);

  PageResponse<LogAnalysisClusterDTO> getLogAnalysisResult(String accountId, String verificationJobInstanceId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams);

  LogAnalysisClusterWithCountDTO getLogAnalysisResultV2(String accountId, String verificationJobInstanceId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams);

  List<DeploymentLogAnalysis> getLatestDeploymentLogAnalysis(
      String accountId, String verificationJobInstanceId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter);

  String getLogDemoTemplate(String verificationTaskId);

  void addDemoAnalysisData(String verificationTaskId, CVConfig cvConfig,
      VerificationJobInstance verificationJobInstance, String demoTemplatePath);

  Set<String> getNodeNames(String accountId, String verificationJobInstanceId);
}
