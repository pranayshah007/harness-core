/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.LogFeedbackEntity;
import io.harness.cvng.core.entities.LogFeedbackHistoryEntity;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogFeedbackServiceImpl implements LogFeedbackService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;

  private void updateDeploymentLogAnalysis(ProjectParams projectParams, LogFeedback logFeedback) {
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().clusterId(logFeedback.getClusterId()).build();
    List<DeploymentLogAnalysis> deploymentLogAnalyses = deploymentLogAnalysisService.getLatestDeploymentLogAnalysis(
        projectParams.getAccountIdentifier(), logFeedback.getVerificationJobInstanceId(), deploymentLogAnalysisFilter);
    UpdateOperations<DeploymentLogAnalysis> updateOperations =
        hPersistence.createUpdateOperations(DeploymentLogAnalysis.class);
    for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalyses) {
      ResultSummary resultSummary = deploymentLogAnalysis.getResultSummary();
      ResultSummary.ResultSummaryBuilder resultSummaryBuilder = resultSummary.toBuilder();
      List<ClusterSummary> clusterSummaryList = resultSummary.getTestClusterSummaries();
      List<ClusterSummary> updatedClusterSummaryList = new ArrayList<>();
      for (ClusterSummary clusterSummary : clusterSummaryList) {
        ClusterSummary.ClusterSummaryBuilder clusterSummaryBuilder = clusterSummary.toBuilder();
        clusterSummaryBuilder.feedback(logFeedback);
        updatedClusterSummaryList.add(clusterSummaryBuilder.build());
      }
      resultSummaryBuilder.testClusterSummaries(updatedClusterSummaryList);
      updateOperations.set(DeploymentLogAnalysis.DeploymentLogAnalysisKeys.resultSummary, resultSummaryBuilder.build());
      hPersistence.update(deploymentLogAnalysis, updateOperations);
    }
  }

  @Override
  public LogFeedback create(ProjectParams projectParams, LogFeedback logFeedback) {
    UserPrincipal userPrincipal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
    LogFeedbackEntity.LogFeedbackEntityBuilder logFeedbackEntityBuilder =
        LogFeedbackEntity.builder()
            .feedbackScore(logFeedback.getFeedbackScore().toString())
            .feedbackId(UUID.randomUUID().toString())
            .sampleMessage(logFeedback.getSampleMessage())
            .description(logFeedback.getDescription())
            .serviceIdentifier(logFeedback.getServiceIdentifier())
            .environmentIdentifier(logFeedback.getEnvironmentIdentifier())
            .accountIdentifier(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .createdByUser(userPrincipal.getUsername())
            .createdAt(System.currentTimeMillis());
    hPersistence.save(logFeedbackEntityBuilder.build());
    createHistory(projectParams, userPrincipal.getEmail(), logFeedbackEntityBuilder.build());
    updateDeploymentLogAnalysis(projectParams, logFeedback);
    return getLogFeedbackFromFeedbackEntity(logFeedbackEntityBuilder.build());
  }

  @Override
  public LogFeedback update(ProjectParams projectParams, String feedbackId, LogFeedback logFeedback) {
    UserPrincipal userPrincipal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
    LogFeedbackEntity logFeedbackEntity = getLogFeedback(projectParams, feedbackId);
    logFeedbackEntity.setFeedbackScore(logFeedback.getFeedbackScore().toString());
    logFeedbackEntity.setDescription(logFeedbackEntity.getDescription());
    UpdateOperations<LogFeedbackEntity> updateOperations = hPersistence.createUpdateOperations(LogFeedbackEntity.class);
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.description, logFeedback.getDescription());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.feedbackScore, logFeedback.getFeedbackScore());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.updatedByUser, userPrincipal.getUsername());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.lastUpdatedAt, System.currentTimeMillis());
    hPersistence.update(logFeedbackEntity, updateOperations);
    updateHistory(projectParams, userPrincipal.getEmail(), logFeedbackEntity);
    updateDeploymentLogAnalysis(projectParams, logFeedback);
    return logFeedback;
  }

  @Override
  public boolean delete(ProjectParams projectParams, String feedbackId) {
    LogFeedbackEntity.LogFeedbackEntityBuilder logFeedbackEntityBuilder =
        LogFeedbackEntity.builder().feedbackId(feedbackId);
    return hPersistence.delete(logFeedbackEntityBuilder.build());
  }

  @Override
  public LogFeedback get(ProjectParams projectParams, String feedbackId) {
    LogFeedbackEntity logFeedbackEntity = getLogFeedback(projectParams, feedbackId);
    if (logFeedbackEntity == null)
      return null;
    return LogFeedback.builder()
        .feedbackId(feedbackId)
        .sampleMessage(logFeedbackEntity.getSampleMessage())
        .feedbackScore(LogFeedback.FeedbackScore.valueOf(logFeedbackEntity.getFeedbackScore()))
        .serviceIdentifier(logFeedbackEntity.getServiceIdentifier())
        .environmentIdentifier(logFeedbackEntity.getEnvironmentIdentifier())
        .description(logFeedbackEntity.getDescription())
        .createdAt(logFeedbackEntity.getCreatedAt())
        .lastUpdatedAt(logFeedbackEntity.getLastUpdatedAt())
        .createdBy(logFeedbackEntity.getCreatedByUser())
        .lastUpdatedBy(logFeedbackEntity.getUpdatedByUser())
        .build();
  }

  public void createHistory(ProjectParams projectParams, String userId, LogFeedbackEntity logFeedbackEntity) {
    LogFeedbackHistoryEntity.LogFeedbackHistoryEntityBuilder logFeedbackHistoryEntityBuilder =
        LogFeedbackHistoryEntity.builder();

    logFeedbackHistoryEntityBuilder.historyId(UUID.randomUUID().toString())
        .feedbackId(logFeedbackEntity.getFeedbackId())
        .logFeedbackEntity(logFeedbackEntity)
        .createdByUser(userId)
        .accountIdentifier(projectParams.getAccountIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier());

    hPersistence.save(logFeedbackHistoryEntityBuilder.build());
  }

  public void updateHistory(ProjectParams projectParams, String userId, LogFeedbackEntity logFeedbackEntity) {
    LogFeedbackHistoryEntity.LogFeedbackHistoryEntityBuilder logFeedbackHistoryEntityBuilder =
        LogFeedbackHistoryEntity.builder();

    logFeedbackHistoryEntityBuilder.historyId(UUID.randomUUID().toString())
        .feedbackId(logFeedbackEntity.getFeedbackId())
        .logFeedbackEntity(logFeedbackEntity)
        .updatedByUser(userId)
        .accountIdentifier(projectParams.getAccountIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier());

    hPersistence.save(logFeedbackHistoryEntityBuilder.build());
  }

  @Override
  public List<LogFeedbackHistory> history(ProjectParams projectParams, String feedbackId) {
    List<LogFeedbackHistoryEntity> logFeedbackHistoryEntities =
        hPersistence.createQuery(LogFeedbackHistoryEntity.class)
            .filter(LogFeedbackHistoryEntity.LogFeedbackHistoryKeys.feedbackId, feedbackId)
            .filter(
                LogFeedbackHistoryEntity.LogFeedbackHistoryKeys.accountIdentifier, projectParams.getAccountIdentifier())
            .filter(
                LogFeedbackHistoryEntity.LogFeedbackHistoryKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(LogFeedbackHistoryEntity.LogFeedbackHistoryKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .asList();
    return getLogFeedbackHistoryList(logFeedbackHistoryEntities);
  }

  @Override
  public List<LogFeedback> list(String envIdentifier, String serviceIdentifier) {
    List<LogFeedbackEntity> logFeedbackEntityList =
        hPersistence.createQuery(LogFeedbackEntity.class)
            .filter(LogFeedbackEntity.LogFeedbackKeys.serviceIdentifier, serviceIdentifier)
            .filter(LogFeedbackEntity.LogFeedbackKeys.environmentIdentifier, envIdentifier)
            .asList();
    return getLogFeedbackList(logFeedbackEntityList);
  }

  private LogFeedback getLogFeedback(LogFeedbackEntity logFeedbackEntity) {
    LogFeedback.LogFeedbackBuilder logFeedbackBuilder =
        LogFeedback.builder()
            .feedbackId(logFeedbackEntity.getFeedbackId())
            .feedbackScore(LogFeedback.FeedbackScore.valueOf(logFeedbackEntity.getFeedbackScore()))
            .description(logFeedbackEntity.getDescription())
            .serviceIdentifier(logFeedbackEntity.getServiceIdentifier())
            .environmentIdentifier(logFeedbackEntity.getEnvironmentIdentifier())
            .sampleMessage(logFeedbackEntity.getSampleMessage());
    return logFeedbackBuilder.build();
  }

  private List<LogFeedback> getLogFeedbackList(List<LogFeedbackEntity> logFeedbackEntityList) {
    List<LogFeedback> logFeedbackList = new ArrayList<>();
    for (LogFeedbackEntity logFeedbackEntity : logFeedbackEntityList) {
      logFeedbackList.add(getLogFeedbackFromFeedbackEntity(logFeedbackEntity));
    }
    return logFeedbackList;
  }

  private List<LogFeedbackHistory> getLogFeedbackHistoryList(
      List<LogFeedbackHistoryEntity> logFeedbackHistoryEntities) {
    List<LogFeedbackHistory> logFeedbackHistoryList = new ArrayList<>();
    for (LogFeedbackHistoryEntity logFeedbackHistoryEntity : logFeedbackHistoryEntities) {
      LogFeedbackHistory.LogFeedbackHistoryBuilder logFeedbackHistoryBuilder =
          LogFeedbackHistory.builder()
              .logFeedback(getLogFeedback(logFeedbackHistoryEntity.getLogFeedbackEntity()))
              .createdBy(logFeedbackHistoryEntity.getCreatedByUser())
              .updatedBy(logFeedbackHistoryEntity.getUpdatedByUser());
      logFeedbackHistoryList.add(logFeedbackHistoryBuilder.build());
    }
    return logFeedbackHistoryList;
  }

  public LogFeedbackEntity getLogFeedback(ProjectParams projectParams, String feedbackId) {
    return hPersistence.createQuery(LogFeedbackEntity.class)
        .filter(LogFeedbackEntity.LogFeedbackKeys.feedbackId, feedbackId)
        .get();
  }

  public LogFeedbackEntity getLogFeedbackEntity(ProjectParams projectParams, LogFeedback logFeedback) {
    LogFeedbackEntity.LogFeedbackEntityBuilder logFeedbackEntityBuilder =
        LogFeedbackEntity.builder()
            .projectIdentifier(projectParams.getProjectIdentifier())
            .accountIdentifier(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .feedbackId(logFeedback.getFeedbackId())
            .description(logFeedback.getDescription())
            .feedbackScore(logFeedback.getFeedbackScore().toString());
    return logFeedbackEntityBuilder.build();
  }

  public LogFeedback getLogFeedbackFromFeedbackEntity(LogFeedbackEntity logFeedbackEntity) {
    LogFeedback.LogFeedbackBuilder logFeedbackBuilder =
        LogFeedback.builder()
            .description(logFeedbackEntity.getDescription())
            .feedbackScore(LogFeedback.FeedbackScore.valueOf(logFeedbackEntity.getFeedbackScore()))
            .feedbackId(logFeedbackEntity.getFeedbackId())
            .environmentIdentifier(logFeedbackEntity.getEnvironmentIdentifier())
            .serviceIdentifier(logFeedbackEntity.getServiceIdentifier())
            .createdBy(logFeedbackEntity.getCreatedByUser())
            .lastUpdatedBy(logFeedbackEntity.getUpdatedByUser())
            .createdAt(logFeedbackEntity.getCreatedAt())
            .lastUpdatedAt(logFeedbackEntity.getLastUpdatedAt())
            .sampleMessage(logFeedbackEntity.getSampleMessage());
    return logFeedbackBuilder.build();
  }
}
