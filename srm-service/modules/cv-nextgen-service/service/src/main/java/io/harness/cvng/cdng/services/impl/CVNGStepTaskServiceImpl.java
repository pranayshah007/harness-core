/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.activity.beans.DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary;
import io.harness.cvng.activity.beans.DeploymentActivitySummaryDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterChartDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisClusterWithCountDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartClusterDTO;
import io.harness.cvng.analysis.beans.LogAnalysisRadarChartListWithCountDTO;
import io.harness.cvng.analysis.beans.TransactionMetricInfoSummaryPageDTO;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.activity.ActivityStatusDTO;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskKeys;
import io.harness.cvng.cdng.entities.CVNGStepTask.Status;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.cdng.services.impl.CVNGStep.CVNGResponseData;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.beans.params.filterParams.DeploymentTimeSeriesAnalysisFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.DeploymentLogsFilter;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVNGStepTaskServiceImpl implements CVNGStepTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private NextGenService nextGenService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private Map<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMap;
  @Inject private CVNGLogService cvngLogService;

  @Override
  public void create(CVNGStepTask cvngStepTask) {
    log.info("Creating CVNGStepTask {}", cvngStepTask);
    cvngStepTask.validate();
    hPersistence.save(cvngStepTask);
  }

  @Override
  public void notifyCVNGStep(CVNGStepTask entity) {
    if (entity.isSkip()) {
      waitNotifyEngine.doneWith(entity.getCallbackId(), CVNGResponseData.builder().skip(true).build());
      markDone(entity.getUuid());
    } else {
      DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
          getDeploymentVerificationJobInstanceSummary(entity);
      ActivityStatusDTO activityStatusDTO =
          ActivityStatusDTO.builder()
              .durationMs(deploymentVerificationJobInstanceSummary.getDurationMs())
              .remainingTimeMs(deploymentVerificationJobInstanceSummary.getRemainingTimeMs())
              .progressPercentage(deploymentVerificationJobInstanceSummary.getProgressPercentage())
              .activityId(entity.getCallbackId())
              .status(deploymentVerificationJobInstanceSummary.getStatus())
              .build();
      // send final progress even if the status is a final status.
      waitNotifyEngine.progressOn(entity.getCallbackId(),
          CVNGResponseData.builder()
              .activityId(entity.getCallbackId())
              .verifyStepExecutionId(entity.getCallbackId())
              .activityStatusDTO(activityStatusDTO)
              .build());
      if (ActivityVerificationStatus.getFinalStates().contains(activityStatusDTO.getStatus())) {
        waitNotifyEngine.doneWith(entity.getCallbackId(),
            CVNGResponseData.builder()
                .activityId(entity.getCallbackId())
                .verifyStepExecutionId(entity.getCallbackId())
                .activityStatusDTO(activityStatusDTO)
                .build());
        markDone(entity.getUuid());
      }
    }
  }

  @Override
  public CVNGStepTask getByCallBackId(String callBackId) {
    return hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callBackId).get();
  }

  @Override
  public DeploymentActivitySummaryDTO getDeploymentSummary(String callbackId) {
    CVNGStepTask stepTask = getByCallBackId(callbackId);
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        getDeploymentVerificationJobInstanceSummary(stepTask);
    deploymentVerificationJobInstanceSummary.setTimeSeriesAnalysisSummary(
        deploymentTimeSeriesAnalysisService.getAnalysisSummary(Arrays.asList(stepTask.getVerificationJobInstanceId())));
    deploymentVerificationJobInstanceSummary.setLogsAnalysisSummary(deploymentLogAnalysisService.getAnalysisSummary(
        stepTask.getAccountId(), Arrays.asList(stepTask.getVerificationJobInstanceId())));
    deploymentVerificationJobInstanceSummary.setErrorAnalysisSummary(
        deploymentLogAnalysisService.getErrorAnalysisSummary(
            stepTask.getAccountId(), Arrays.asList(stepTask.getVerificationJobInstanceId())));
    return DeploymentActivitySummaryDTO.builder()
        .deploymentVerificationJobInstanceSummary(deploymentVerificationJobInstanceSummary)
        .serviceIdentifier(stepTask.getServiceIdentifier())
        .serviceName(getServiceNameFromStep(stepTask))
        .envIdentifier(stepTask.getEnvironmentIdentifier())
        .envName(deploymentVerificationJobInstanceSummary.getEnvironmentName())
        .deploymentTag(stepTask.getDeploymentTag())
        .build();
  }

  private DeploymentVerificationJobInstanceSummary getDeploymentVerificationJobInstanceSummary(CVNGStepTask stepTask) {
    List<String> verificationJobInstanceIds = Arrays.asList(stepTask.getVerificationJobInstanceId());
    DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        verificationJobInstanceService.getDeploymentVerificationJobInstanceSummary(verificationJobInstanceIds);
    deploymentVerificationJobInstanceSummary.setActivityId(stepTask.getCallbackId());
    deploymentVerificationJobInstanceSummary.setActivityStartTime(stepTask.getCreatedAt());
    return deploymentVerificationJobInstanceSummary;
  }

  @Override
  public TransactionMetricInfoSummaryPageDTO getDeploymentActivityTimeSeriesData(String accountId, String callbackId,
      DeploymentTimeSeriesAnalysisFilter deploymentTimeSeriesAnalysisFilter, PageParams pageParams) {
    return deploymentTimeSeriesAnalysisService.getMetrics(accountId,
        getByCallBackId(callbackId).getVerificationJobInstanceId(), deploymentTimeSeriesAnalysisFilter, pageParams);
  }

  @Override
  public Set<HealthSourceDTO> healthSources(String accountId, String callBackId) {
    Set<HealthSourceDTO> healthSourceDTOS = new HashSet<>();
    List<VerificationJobInstance> verificationJobInstances =
        verificationJobInstanceService.get(Arrays.asList(getByCallBackId(callBackId).getVerificationJobInstanceId()));
    verificationJobInstances.forEach(verificationJobInstance -> {
      if (verificationJobInstance.getCvConfigMap() != null) {
        verificationJobInstance.getCvConfigMap().forEach(
            (s, cvConfig)
                -> healthSourceDTOS.add(HealthSourceDTO.builder()
                                            .name(cvConfig.getMonitoringSourceName())
                                            .identifier(cvConfig.getFullyQualifiedIdentifier())
                                            .type(cvConfig.getType())
                                            .verificationType(cvConfig.getVerificationType())
                                            .build()));
      }
    });
    return healthSourceDTOS;
  }

  @Override
  public List<LogAnalysisClusterChartDTO> getDeploymentActivityLogAnalysisClusters(
      String accountId, String callbackId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    return deploymentLogAnalysisService.getLogAnalysisClusters(
        accountId, getByCallBackId(callbackId).getVerificationJobInstanceId(), deploymentLogAnalysisFilter);
  }

  @Override
  public PageResponse<LogAnalysisClusterDTO> getDeploymentActivityLogAnalysisResult(String accountId, String callbackId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    return deploymentLogAnalysisService.getLogAnalysisResult(accountId,
        getByCallBackId(callbackId).getVerificationJobInstanceId(), label, deploymentLogAnalysisFilter, pageParams);
  }

  @Override
  public LogAnalysisClusterWithCountDTO getDeploymentActivityLogAnalysisResultV2(String accountId, String callbackId,
      Integer label, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    return deploymentLogAnalysisService.getLogAnalysisResultV2(accountId,
        getByCallBackId(callbackId).getVerificationJobInstanceId(), label, deploymentLogAnalysisFilter, pageParams);
  }

  @Override
  public LogAnalysisRadarChartListWithCountDTO getDeploymentActivityRadarChartLogAnalysisResult(String accountId,
      String callBackId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter, PageParams pageParams) {
    return deploymentLogAnalysisService.getRadarChartLogAnalysisResult(
        accountId, callBackId, deploymentLogAnalysisFilter, pageParams);
  }

  @Override
  public List<LogAnalysisRadarChartClusterDTO> getDeploymentActivityRadarCartLogAnalysisClusters(
      String accountId, String callBackId, DeploymentLogAnalysisFilter deploymentLogAnalysisFilter) {
    return deploymentLogAnalysisService.getRadarChartLogAnalysisClusters(
        accountId, callBackId, deploymentLogAnalysisFilter);
  }

  @Override
  public List<String> getTransactionNames(String accountId, String callBackId) {
    return deploymentTimeSeriesAnalysisService.getTransactionNames(
        accountId, getByCallBackId(callBackId).getVerificationJobInstanceId());
  }

  @Override
  public Set<String> getNodeNames(String accountId, String callBackId) {
    Set<String> nodeNames = new HashSet<>();
    nodeNames.addAll(deploymentTimeSeriesAnalysisService.getNodeNames(
        accountId, getByCallBackId(callBackId).getVerificationJobInstanceId()));
    nodeNames.addAll(deploymentLogAnalysisService.getNodeNames(
        accountId, getByCallBackId(callBackId).getVerificationJobInstanceId()));
    return nodeNames;
  }

  @Override
  public List<VerificationJobInstance.ProgressLog> getExecutionLogs(String accountId, String callBackId) {
    return verificationJobInstanceService.getProgressLogs(getByCallBackId(callBackId).getVerificationJobInstanceId());
  }

  @Override
  public PageResponse<CVNGLogDTO> getCVNGLogs(
      String accountId, String callBackId, DeploymentLogsFilter deploymentLogsFilter, PageParams pageParams) {
    return cvngLogService.getCVNGLogs(
        accountId, getByCallBackId(callBackId).getVerificationJobInstanceId(), deploymentLogsFilter, pageParams);
  }

  private String getServiceNameFromStep(CVNGStepTask step) {
    return nextGenService
        .getService(
            step.getAccountId(), step.getOrgIdentifier(), step.getProjectIdentifier(), step.getServiceIdentifier())
        .getName();
  }

  private void markDone(String uuid) {
    updateStatus(uuid, Status.DONE);
  }

  private void updateStatus(String cvngStepTaskId, Status status) {
    UpdateOperations<CVNGStepTask> updateOperations =
        hPersistence.createUpdateOperations(CVNGStepTask.class).set(CVNGStepTaskKeys.status, status);
    Query<CVNGStepTask> query =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.uuid, cvngStepTaskId);
    hPersistence.update(query, updateOperations);
  }
}
