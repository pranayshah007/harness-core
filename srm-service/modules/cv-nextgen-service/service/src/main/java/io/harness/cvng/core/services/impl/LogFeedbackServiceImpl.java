/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterSummary.ClusterSummaryBuilder;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ResultSummary.ResultSummaryBuilder;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis.DeploymentLogAnalysisKeys;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedback.LogFeedbackBuilder;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.LogFeedbackHistory.LogFeedbackHistoryBuilder;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.filterParams.DeploymentLogAnalysisFilter;
import io.harness.cvng.core.entities.LogFeedbackEntity;
import io.harness.cvng.core.entities.LogFeedbackEntity.LogFeedbackEntityBuilder;
import io.harness.cvng.core.entities.LogFeedbackHistoryEntity;
import io.harness.cvng.core.entities.LogFeedbackHistoryEntity.LogFeedbackHistoryEntityBuilder;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogFeedbackServiceImpl implements LogFeedbackService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;

  private void updateDeploymentLogAnalysis(ProjectPathParams projectParams, LogFeedback logFeedback) {
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().clusterId(logFeedback.getClusterId()).build();
    List<DeploymentLogAnalysis> deploymentLogAnalyses = deploymentLogAnalysisService.getLatestDeploymentLogAnalysis(
        projectParams.getAccountIdentifier(), logFeedback.getVerificationJobInstanceId(), deploymentLogAnalysisFilter);
    UpdateOperations<DeploymentLogAnalysis> updateOperations =
        hPersistence.createUpdateOperations(DeploymentLogAnalysis.class);
    for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalyses) {
      ResultSummary resultSummary = deploymentLogAnalysis.getResultSummary();
      ResultSummaryBuilder resultSummaryBuilder = resultSummary.toBuilder();
      List<ClusterSummary> clusterSummaryList = resultSummary.getTestClusterSummaries();
      List<ClusterSummary> updatedClusterSummaryList = new ArrayList<>();
      for (ClusterSummary clusterSummary : clusterSummaryList) {
        String expectedClusterUUID =
            UUID.nameUUIDFromBytes((deploymentLogAnalysis.getVerificationTaskId() + ":" + clusterSummary.getLabel())
                                       .getBytes(StandardCharsets.UTF_8))
                .toString();
        ClusterSummaryBuilder clusterSummaryBuilder = clusterSummary.toBuilder();
        if (expectedClusterUUID.equals(logFeedback.getClusterId())) {
          clusterSummaryBuilder.feedback(logFeedback);
        }
        updatedClusterSummaryList.add(clusterSummaryBuilder.build());
      }
      resultSummaryBuilder.testClusterSummaries(updatedClusterSummaryList);
      updateOperations.set(DeploymentLogAnalysisKeys.resultSummary, resultSummaryBuilder.build());
      hPersistence.update(deploymentLogAnalysis, updateOperations);
    }
  }

  private LogFeedback updateSampleMessage(ProjectPathParams projectParams, LogFeedback logFeedback) {
    LogFeedbackBuilder logFeedbackBuilder = logFeedback.toBuilder();
    DeploymentLogAnalysisFilter deploymentLogAnalysisFilter =
        DeploymentLogAnalysisFilter.builder().clusterId(logFeedback.getClusterId()).build();
    List<DeploymentLogAnalysis> deploymentLogAnalyses = deploymentLogAnalysisService.getLatestDeploymentLogAnalysis(
        projectParams.getAccountIdentifier(), logFeedback.getVerificationJobInstanceId(), deploymentLogAnalysisFilter);
    for (DeploymentLogAnalysis deploymentLogAnalysis : deploymentLogAnalyses) {
      for (DeploymentLogAnalysisDTO.Cluster cluster : deploymentLogAnalysis.getClusters()) {
        String expectedClusterUUID =
            UUID.nameUUIDFromBytes((deploymentLogAnalysis.getVerificationTaskId() + ":" + cluster.getLabel())
                                       .getBytes(StandardCharsets.UTF_8))
                .toString();
        if (expectedClusterUUID.equals(logFeedback.getClusterId())) {
          logFeedbackBuilder.sampleMessage(cluster.getText());
          return logFeedbackBuilder.build();
        }
      }
    }
    return logFeedbackBuilder.build();
  }

  @Override
  public LogFeedback create(ProjectPathParams projectParams, LogFeedback logFeedback) {
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(logFeedback.getVerificationJobInstanceId());
    UserPrincipal userPrincipal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
    logFeedback = updateSampleMessage(projectParams, logFeedback);
    logFeedback = logFeedback.toBuilder()
                      .feedbackId(UUID.randomUUID().toString())
                      .createdAt(System.currentTimeMillis())
                      .updatedAt(System.currentTimeMillis())
                      .createdBy(userPrincipal.getUsername())
                      .updatedBy(userPrincipal.getUsername())
                      .build();
    LogFeedbackEntityBuilder logFeedbackEntityBuilder =
        LogFeedbackEntity.builder()
            .feedbackScore(logFeedback.getFeedbackScore().toString())
            .feedbackId(logFeedback.getFeedbackId())
            .sampleMessage(logFeedback.getSampleMessage())
            .description(logFeedback.getDescription())
            .serviceIdentifier(verificationJobInstance.getResolvedJob().getServiceIdentifier())
            .environmentIdentifier(verificationJobInstance.getResolvedJob().getEnvIdentifier())
            .accountIdentifier(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .createdByUser(logFeedback.getCreatedBy())
            .createdAt(logFeedback.getCreatedAt())
            .updatedByUser(logFeedback.getUpdatedBy())
            .lastUpdatedAt(logFeedback.getUpdatedAt());
    hPersistence.save(logFeedbackEntityBuilder.build());
    createHistory(
        projectParams, userPrincipal.getEmail(), getLogFeedbackFromFeedbackEntity(logFeedbackEntityBuilder.build()));
    updateDeploymentLogAnalysis(projectParams, logFeedback);
    return getLogFeedbackFromFeedbackEntity(logFeedbackEntityBuilder.build());
  }

  @Override
  public LogFeedback update(ProjectPathParams projectParams, String feedbackId, LogFeedback logFeedback) {
    UserPrincipal userPrincipal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
    long updateTimeStamp = System.currentTimeMillis();
    LogFeedbackBuilder logFeedbackBuilder = logFeedback.toBuilder();
    logFeedbackBuilder.updatedBy(userPrincipal.getUsername());
    logFeedbackBuilder.updatedAt(updateTimeStamp);
    logFeedbackBuilder.feedbackId(feedbackId);
    LogFeedbackEntity logFeedbackEntity = getLogFeedback(feedbackId);
    logFeedbackEntity.setFeedbackScore(logFeedback.getFeedbackScore().toString());
    logFeedbackEntity.setDescription(logFeedback.getDescription());
    UpdateOperations<LogFeedbackEntity> updateOperations = hPersistence.createUpdateOperations(LogFeedbackEntity.class);
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.description, logFeedback.getDescription());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.feedbackScore, logFeedback.getFeedbackScore());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.updatedByUser, userPrincipal.getUsername());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.lastUpdatedAt, updateTimeStamp);
    hPersistence.update(logFeedbackEntity, updateOperations);
    updateHistory(projectParams, userPrincipal.getEmail(), logFeedbackBuilder.build());
    updateDeploymentLogAnalysis(projectParams, logFeedbackBuilder.build());
    return logFeedback;
  }

  @Override
  public boolean delete(ProjectPathParams projectParams, String feedbackId) {
    LogFeedbackEntityBuilder logFeedbackEntityBuilder = LogFeedbackEntity.builder().feedbackId(feedbackId);
    return hPersistence.delete(logFeedbackEntityBuilder.build());
  }

  @Override
  public LogFeedback get(ProjectPathParams projectParams, String feedbackId) {
    LogFeedbackEntity logFeedbackEntity = getLogFeedback(feedbackId);
    if (logFeedbackEntity == null) {
      return null;
    }
    return LogFeedback.builder()
        .feedbackId(feedbackId)
        .sampleMessage(logFeedbackEntity.getSampleMessage())
        .feedbackScore(LogFeedback.FeedbackScore.valueOf(logFeedbackEntity.getFeedbackScore()))
        .serviceIdentifier(logFeedbackEntity.getServiceIdentifier())
        .environmentIdentifier(logFeedbackEntity.getEnvironmentIdentifier())
        .description(logFeedbackEntity.getDescription())
        .createdAt(logFeedbackEntity.getCreatedAt())
        .updatedAt(logFeedbackEntity.getLastUpdatedAt())
        .createdBy(logFeedbackEntity.getCreatedByUser())
        .updatedBy(logFeedbackEntity.getUpdatedByUser())
        .build();
  }

  public void createHistory(ProjectPathParams projectParams, String userId, LogFeedback logFeedbackEntity) {
    LogFeedbackHistoryEntityBuilder logFeedbackHistoryEntityBuilder = LogFeedbackHistoryEntity.builder();

    logFeedbackHistoryEntityBuilder.historyId(UUID.randomUUID().toString())
        .feedbackId(logFeedbackEntity.getFeedbackId())
        .logFeedbackEntity(logFeedbackEntity)
        .createdByUser(userId)
        .updatedByUser(userId)
        .accountIdentifier(projectParams.getAccountIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier());

    hPersistence.save(logFeedbackHistoryEntityBuilder.build());
  }

  public void updateHistory(ProjectPathParams projectParams, String userId, LogFeedback logFeedbackEntity) {
    LogFeedbackHistoryEntityBuilder logFeedbackHistoryEntityBuilder = LogFeedbackHistoryEntity.builder();

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
  public List<LogFeedbackHistory> history(ProjectPathParams projectParams, String feedbackId) {
    List<LogFeedbackHistoryEntity> logFeedbackHistoryEntities =
        hPersistence.createQuery(LogFeedbackHistoryEntity.class)
            .filter(LogFeedbackHistoryEntity.LogFeedbackHistoryKeys.feedbackId, feedbackId)
            .asList();
    return getLogFeedbackHistoryList(logFeedbackHistoryEntities);
  }

  @Override
  public List<LogFeedback> list(String serviceIdentifier, String envIdentifier) {
    List<LogFeedbackEntity> logFeedbackEntityList =
        hPersistence.createQuery(LogFeedbackEntity.class)
            .filter(LogFeedbackEntity.LogFeedbackKeys.serviceIdentifier, serviceIdentifier)
            .filter(LogFeedbackEntity.LogFeedbackKeys.environmentIdentifier, envIdentifier)
            .asList();
    return getLogFeedbackList(logFeedbackEntityList);
  }

  private LogFeedback getLogFeedback(LogFeedbackEntity logFeedbackEntity) {
    LogFeedbackBuilder logFeedbackBuilder =
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
      LogFeedbackHistoryBuilder logFeedbackHistoryBuilder =
          LogFeedbackHistory.builder()
              .logFeedback(logFeedbackHistoryEntity.getLogFeedbackEntity())
              .createdBy(logFeedbackHistoryEntity.getCreatedByUser())
              .updatedBy(logFeedbackHistoryEntity.getUpdatedByUser());
      logFeedbackHistoryList.add(logFeedbackHistoryBuilder.build());
    }
    return logFeedbackHistoryList;
  }

  public LogFeedbackEntity getLogFeedback(String feedbackId) {
    return hPersistence.createQuery(LogFeedbackEntity.class)
        .filter(LogFeedbackEntity.LogFeedbackKeys.feedbackId, feedbackId)
        .get();
  }

  public LogFeedbackEntity getLogFeedbackEntity(ProjectParams projectParams, LogFeedback logFeedback) {
    LogFeedbackEntityBuilder logFeedbackEntityBuilder = LogFeedbackEntity.builder()
                                                            .projectIdentifier(projectParams.getProjectIdentifier())
                                                            .accountIdentifier(projectParams.getAccountIdentifier())
                                                            .orgIdentifier(projectParams.getOrgIdentifier())
                                                            .feedbackId(logFeedback.getFeedbackId())
                                                            .description(logFeedback.getDescription())
                                                            .feedbackScore(logFeedback.getFeedbackScore().toString());
    return logFeedbackEntityBuilder.build();
  }

  public LogFeedback getLogFeedbackFromFeedbackEntity(LogFeedbackEntity logFeedbackEntity) {
    LogFeedbackBuilder logFeedbackBuilder =
        LogFeedback.builder()
            .description(logFeedbackEntity.getDescription())
            .feedbackScore(LogFeedback.FeedbackScore.valueOf(logFeedbackEntity.getFeedbackScore()))
            .feedbackId(logFeedbackEntity.getFeedbackId())
            .environmentIdentifier(logFeedbackEntity.getEnvironmentIdentifier())
            .serviceIdentifier(logFeedbackEntity.getServiceIdentifier())
            .createdBy(logFeedbackEntity.getCreatedByUser())
            .updatedBy(logFeedbackEntity.getUpdatedByUser())
            .createdAt(logFeedbackEntity.getCreatedAt())
            .updatedAt(logFeedbackEntity.getLastUpdatedAt())
            .sampleMessage(logFeedbackEntity.getSampleMessage());
    return logFeedbackBuilder.build();
  }
}
