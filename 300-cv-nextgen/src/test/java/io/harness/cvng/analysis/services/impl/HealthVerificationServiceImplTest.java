/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.services.api.HealthVerificationHeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.HealthVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HealthVerificationServiceImplTest extends CvNextGenTestBase {
  @Inject private HealthVerificationService healthVerificationService;

  @Mock private VerificationTaskService verificationTaskService;
  @Mock private VerificationJobInstanceService verificationJobInstanceService;
  @Mock private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Mock private LogAnalysisService logAnalysisService;
  @Mock private CVConfigService cvConfigService;
  @Mock private HealthVerificationHeatMapService healthVerificationHeatMapService;

  private String projectIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private String accountId;
  private String verificationTaskId;
  private String verificationJobInstanceId;
  private String cvConfigId;
  private long startTime = 1603780200000l;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    verificationTaskId = generateUuid();
    envIdentifier = generateUuid();
    serviceIdentifier = generateUuid();
    accountId = generateUuid();
    projectIdentifier = generateUuid();
    verificationJobInstanceId = generateUuid();
    cvConfigId = generateUuid();

    MockitoAnnotations.initMocks(this);

    FieldUtils.writeField(healthVerificationService, "cvConfigService", cvConfigService, true);
    FieldUtils.writeField(healthVerificationService, "verificationTaskService", verificationTaskService, true);
    FieldUtils.writeField(
        healthVerificationService, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(healthVerificationService, "logAnalysisService", logAnalysisService, true);
    FieldUtils.writeField(healthVerificationService, "timeSeriesAnalysisService", timeSeriesAnalysisService, true);
    FieldUtils.writeField(
        healthVerificationService, "healthVerificationHeatMapService", healthVerificationHeatMapService, true);

    when(verificationTaskService.getCVConfigId(verificationTaskId)).thenReturn(cvConfigId);
    when(verificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId)).thenReturn(cvConfigId);
    when(cvConfigService.get(cvConfigId)).thenReturn(getAppDCVConfig());
    when(verificationJobInstanceService.getEmbeddedCVConfig(eq(cvConfigId), any())).thenReturn(getAppDCVConfig());
    when(verificationTaskService.get(verificationTaskId))
        .thenReturn(VerificationTask.builder()
                        .taskInfo(DeploymentInfo.builder()
                                      .cvConfigId(cvConfigId)
                                      .verificationJobInstanceId(verificationJobInstanceId)
                                      .build())
                        .build());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAggregateActivityAnalysis_noAnalysisDoneTimeSeries() {
    Instant start = Instant.ofEpochMilli(startTime);
    Instant end = Instant.ofEpochMilli(startTime).plus(Duration.ofMinutes(15));
    healthVerificationService.aggregateActivityAnalysis(
        verificationTaskId, start, end, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);

    verify(timeSeriesAnalysisService, times(1)).getLatestTimeSeriesRiskSummary(cvConfigId, start, end);
    verify(healthVerificationHeatMapService, times(0)).updateRisk(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAggregateActivityAnalysis_noAnalysisDoneLogs() {
    CVConfig cvConfig = getSplunkConfig();
    cvConfig.setUuid(cvConfigId);
    when(verificationJobInstanceService.getEmbeddedCVConfig(eq(cvConfigId), any())).thenReturn(cvConfig);

    Instant start = Instant.ofEpochMilli(startTime);
    Instant end = Instant.ofEpochMilli(startTime).plus(Duration.ofMinutes(15));
    healthVerificationService.aggregateActivityAnalysis(
        verificationTaskId, start, end, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);

    verify(logAnalysisService, times(1)).getLatestAnalysisForVerificationTaskId(cvConfigId, start, end);
    verify(healthVerificationHeatMapService, times(0)).updateRisk(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAggregateActivityAnalysis_withAnalysisTimeSeries() {
    Instant start = Instant.ofEpochMilli(startTime);
    Instant end = Instant.ofEpochMilli(startTime).plus(Duration.ofMinutes(15));

    TimeSeriesRiskSummary riskSummary = createRiskSummary(2, 2);
    riskSummary.setOverallRisk(1.0);
    riskSummary.setAnalysisEndTime(start.plus(Duration.ofMinutes(5)));

    when(timeSeriesAnalysisService.getLatestTimeSeriesRiskSummary(cvConfigId, start, end)).thenReturn(riskSummary);

    healthVerificationService.aggregateActivityAnalysis(
        verificationTaskId, start, end, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);

    verify(timeSeriesAnalysisService, times(1)).getLatestTimeSeriesRiskSummary(cvConfigId, start, end);
    verify(logAnalysisService, times(0)).getLatestAnalysisForVerificationTaskId(cvConfigId, start, end);
    verify(healthVerificationHeatMapService, times(1))
        .updateRisk(verificationTaskId, 1.0, start.plus(Duration.ofMinutes(5)), HealthVerificationPeriod.PRE_ACTIVITY);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAggregateActivityAnalysis_withAnalysisLogs() {
    CVConfig cvConfig = getSplunkConfig();
    cvConfig.setUuid(cvConfigId);
    when(verificationJobInstanceService.getEmbeddedCVConfig(eq(cvConfigId), any())).thenReturn(cvConfig);

    Instant start = Instant.ofEpochMilli(startTime);
    Instant end = Instant.ofEpochMilli(startTime).plus(Duration.ofMinutes(15));

    LogAnalysisResult result = LogAnalysisResult.builder().logAnalysisResults(getResults(12345l, 23456l)).build();
    result.setOverallRisk(0.3);
    result.setAnalysisEndTime(start.plus(Duration.ofMinutes(5)));

    when(logAnalysisService.getLatestAnalysisForVerificationTaskId(cvConfigId, start, end)).thenReturn(result);

    healthVerificationService.aggregateActivityAnalysis(
        verificationTaskId, start, end, Instant.ofEpochMilli(0), HealthVerificationPeriod.PRE_ACTIVITY);

    verify(timeSeriesAnalysisService, times(0)).getLatestTimeSeriesRiskSummary(cvConfigId, start, end);
    verify(logAnalysisService, times(1)).getLatestAnalysisForVerificationTaskId(cvConfigId, start, end);
    verify(healthVerificationHeatMapService, times(1))
        .updateRisk(verificationTaskId, 0.3, start.plus(Duration.ofMinutes(5)), HealthVerificationPeriod.PRE_ACTIVITY);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateProgress() {
    Instant start = Instant.ofEpochMilli(startTime);
    Instant end = Instant.ofEpochMilli(startTime).plus(Duration.ofMinutes(15));
    when(verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId))
        .thenReturn(
            builderFactory.verificationJobInstanceBuilder()
                .accountId(accountId)
                .deploymentStartTime(start.plus(Duration.ofMinutes(3)))
                .startTime(start.plus(Duration.ofMinutes(5)))
                .uuid(verificationJobInstanceId)
                .resolvedJob(
                    HealthVerificationJob.builder()
                        .accountId(accountId)
                        .orgIdentifier(generateUuid())
                        .projectIdentifier(projectIdentifier)
                        .identifier("identifier")
                        .duration(VerificationJob.RuntimeParameter.builder().value("5m").isRuntimeParam(false).build())
                        .envIdentifier(
                            VerificationJob.RuntimeParameter.builder().isRuntimeParam(false).value("e0").build())
                        .serviceIdentifier(
                            VerificationJob.RuntimeParameter.builder().isRuntimeParam(false).value("s0").build())
                        .build())
                .build());

    healthVerificationService.updateProgress(
        verificationTaskId, start.plus(Duration.ofMinutes(10)), AnalysisStatus.RUNNING, false);
    ArgumentCaptor<VerificationJobInstance.AnalysisProgressLog> captor =
        ArgumentCaptor.forClass(VerificationJobInstance.AnalysisProgressLog.class);
    verify(verificationJobInstanceService).logProgress(captor.capture());
    VerificationJobInstance.AnalysisProgressLog progressLog = captor.getValue();
    assertThat(progressLog.getAnalysisStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(progressLog.getEndTime()).isEqualTo(start.plus(Duration.ofMinutes(10)));
    assertThat(progressLog.getVerificationTaskId()).isEqualTo(verificationTaskId);
  }

  private CVConfig getAppDCVConfig() {
    AppDynamicsCVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setUuid(cvConfigId);
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    return cvConfig;
  }

  private CVConfig getSplunkConfig() {
    SplunkCVConfig cvConfig = builderFactory.splunkCVConfigBuilder().build();
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setAccountId(accountId);
    cvConfig.setUuid(cvConfigId + "-2");
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    return cvConfig;
  }

  private TimeSeriesRiskSummary createRiskSummary(int numMetrics, int numTxns) {
    TimeSeriesRiskSummary riskSummary = TimeSeriesRiskSummary.builder().verificationTaskId(verificationTaskId).build();
    List<TimeSeriesRiskSummary.TransactionMetricRisk> transactionMetricRisks = new ArrayList<>();
    for (int j = 0; j < numMetrics; j++) {
      for (int k = 0; k < numTxns; k++) {
        TimeSeriesRiskSummary.TransactionMetricRisk transactionMetricRisk =
            TimeSeriesRiskSummary.TransactionMetricRisk.builder()
                .metricName("metric-" + j)
                .transactionName("group-" + k)
                .metricRisk(j % 2 == 0 ? 1 : 0)
                .build();
        transactionMetricRisks.add(transactionMetricRisk);
      }
    }
    riskSummary.setTransactionMetricRiskList(transactionMetricRisks);
    return riskSummary;
  }

  private List<LogAnalysisResult.AnalysisResult> getResults(long... labels) {
    List<LogAnalysisResult.AnalysisResult> results = new ArrayList<>();
    for (int i = 0; i < labels.length; i++) {
      LogAnalysisResult.AnalysisResult analysisResult =
          LogAnalysisResult.AnalysisResult.builder()
              .count(3)
              .label(labels[i])
              .tag(i % 2 == 0 ? LogAnalysisResult.LogAnalysisTag.KNOWN : LogAnalysisResult.LogAnalysisTag.UNKNOWN)
              .build();
      results.add(analysisResult);
    }
    return results;
  }
}
