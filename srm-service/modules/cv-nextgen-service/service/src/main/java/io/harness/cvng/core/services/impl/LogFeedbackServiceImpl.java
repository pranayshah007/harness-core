package io.harness.cvng.core.services.impl;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.LogFeedbackEntity;
import io.harness.cvng.core.entities.LogFeedbackHistoryEntity;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogFeedbackServiceImpl implements LogFeedbackService {
  @Inject private HPersistence hPersistence;

  @Override
  public LogFeedback create(ProjectParams projectParams, String userId, LogFeedback logFeedback) {
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
            .projectIdentifier(projectParams.getProjectIdentifier());
    hPersistence.save(logFeedbackEntityBuilder.build());
    createHistory(projectParams, userId, logFeedbackEntityBuilder.build());
    return getLogFeedbackFromFeedbackEntity(logFeedbackEntityBuilder.build());
  }

  @Override
  public LogFeedback update(ProjectParams projectParams, String userId, String feedbackId, LogFeedback logFeedback) {
    LogFeedbackEntity logFeedbackEntity = getLogFeedback(projectParams, feedbackId);
    logFeedbackEntity.setFeedbackScore(logFeedback.getFeedbackScore().toString());
    logFeedbackEntity.setDescription(logFeedbackEntity.getDescription());
    UpdateOperations<LogFeedbackEntity> updateOperations = hPersistence.createUpdateOperations(LogFeedbackEntity.class);
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.description, logFeedback.getDescription());
    updateOperations.set(LogFeedbackEntity.LogFeedbackKeys.feedbackScore, logFeedback.getFeedbackScore());
    hPersistence.update(logFeedbackEntity, updateOperations);
    updateHistory(projectParams, userId, logFeedbackEntity);
    return logFeedback;
  }

  @Override
  public boolean delete(ProjectParams projectParams, String userId, String feedbackId) {
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
            .sampleMessage(logFeedbackEntity.getSampleMessage());
    return logFeedbackBuilder.build();
  }
}
