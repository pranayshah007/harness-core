/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExportExecutionsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.export.request.ExportExecutionsRequest.ExportExecutionsRequestKeys;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.execution.export.request.ExportExecutionsRequestLimitChecks.LimitCheck;
import io.harness.persistence.HPersistence;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.CountOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ExportExecutionsRequestService {
  private static final long EXPIRE_MILLIS = 12L * 60 * 60 * 1000; // 12 hours

  private static final long MAX_QUEUED_REQUESTS = 3;
  private static final long MAX_WORKFLOW_EXECUTIONS = 1000;

  @Inject private WingsPersistence wingsPersistence;

  public ExportExecutionsRequest get(@NotNull String accountId, @NotNull String requestId) {
    ExportExecutionsRequest request = wingsPersistence.createQuery(ExportExecutionsRequest.class)
                                          .filter(ExportExecutionsRequestKeys.accountId, accountId)
                                          .filter(ExportExecutionsRequestKeys.uuid, requestId)
                                          .get();
    if (request == null) {
      throw new InvalidRequestException(format("Unknown export executions request [%s]", requestId));
    }

    if (request.getStatus() == Status.READY && request.getExpiresAt() > 0
        && request.getExpiresAt() <= System.currentTimeMillis()) {
      // This request has expired but the cleanup handler for it has not run yet.
      request.setStatus(Status.EXPIRED);
      request.setFileId(null);
    }
    return request;
  }

  public String queueExportExecutionRequest(@NotNull String accountId, @NotNull Query<WorkflowExecution> query,
      @NotNull ExportExecutionsUserParams userParams) {
    ExportExecutionsRequest request = prepareInitialRequest(accountId, query, userParams);
    validateLimitChecks(request, query);
    return wingsPersistence.save(request);
  }

  private ExportExecutionsRequest prepareInitialRequest(@NotNull String accountId,
      @NotNull Query<WorkflowExecution> query, @NotNull ExportExecutionsUserParams userParams) {
    query = updateQuery(accountId, query);
    List<String> userGroupIds = !userParams.isNotifyOnlyTriggeringUser() && userParams.getUserGroupIds() != null
        ? userParams.getUserGroupIds().stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toList())
        : null;
    ExportExecutionsRequest exportExecutionsRequest =
        ExportExecutionsRequest.builder()
            .accountId(accountId)
            .outputFormat(userParams.getOutputFormat() == null ? ExportExecutionsRequest.OutputFormat.JSON
                                                               : userParams.getOutputFormat())
            .query(ExportExecutionsRequestQuery.fromQuery(query))
            .notifyOnlyTriggeringUser(userParams.isNotifyOnlyTriggeringUser())
            .userGroupIds(EmptyPredicate.isEmpty(userGroupIds) ? null : userGroupIds)
            .status(Status.QUEUED)
            .totalExecutions(-1)
            .expiresAt(System.currentTimeMillis() + EXPIRE_MILLIS)
            .createdByType(userParams.getCreatedByType() == null ? CreatedByType.USER : userParams.getCreatedByType())
            .nextIteration(System.currentTimeMillis() - 1)
            .build();
    validateExportExecutionsRequest(exportExecutionsRequest);
    return exportExecutionsRequest;
  }

  private Query<WorkflowExecution> updateQuery(@NotNull String accountId, @NotNull Query<WorkflowExecution> query) {
    return query.filter(WorkflowExecutionKeys.accountId, accountId)
        .field(WorkflowExecutionKeys.pipelineExecutionId)
        .doesNotExist()
        .field(WorkflowExecutionKeys.status)
        .in(ExecutionStatus.finalStatuses())
        .field(WorkflowExecutionKeys.endTs)
        .lessThanOrEq(System.currentTimeMillis());
  }

  private void validateLimitChecks(@NotNull ExportExecutionsRequest request, @NotNull Query<WorkflowExecution> query) {
    ExportExecutionsRequestLimitChecks limitChecks = prepareLimitChecks(request.getAccountId(), query);
    limitChecks.validate();
    request.setTotalExecutions(limitChecks.getExecutionCount().getValue());
  }

  public ExportExecutionsRequestLimitChecks prepareLimitChecks(
      @NotNull String accountId, @NotNull Query<WorkflowExecution> query) {
    long currQueuedRequests = wingsPersistence.createAnalyticsQuery(ExportExecutionsRequest.class)
                                  .filter(ExportExecutionsRequestKeys.accountId, accountId)
                                  .filter(ExportExecutionsRequestKeys.status, Status.QUEUED)
                                  .count(new CountOptions().readPreference(ReadPreference.secondaryPreferred()));

    query = updateQuery(accountId, query);
    // Only count till a max of MAX_WORKFLOW_EXECUTIONS * 5. Otherwise the count query will be expensive.
    long totalWorkflowExecutions = query.count(new CountOptions()
                                                   .limit((int) MAX_WORKFLOW_EXECUTIONS * 5)
                                                   .readPreference(ReadPreference.secondaryPreferred()));
    return ExportExecutionsRequestLimitChecks.builder()
        .queuedRequests(LimitCheck.builder().limit(MAX_QUEUED_REQUESTS).value(currQueuedRequests).build())
        .executionCount(LimitCheck.builder().limit(MAX_WORKFLOW_EXECUTIONS).value(totalWorkflowExecutions).build())
        .build();
  }

  public void readyRequest(@NotNull ExportExecutionsRequest request, @NotNull String fileId) {
    try {
      Query<ExportExecutionsRequest> query = wingsPersistence.createQuery(ExportExecutionsRequest.class)
                                                 .filter(ExportExecutionsRequestKeys.accountId, request.getAccountId())
                                                 .filter(ExportExecutionsRequestKeys.uuid, request.getUuid())
                                                 .filter(ExportExecutionsRequestKeys.status, Status.QUEUED);
      UpdateOperations<ExportExecutionsRequest> updateOperations =
          wingsPersistence.createUpdateOperations(ExportExecutionsRequest.class)
              .set(ExportExecutionsRequestKeys.status, Status.READY)
              .set(ExportExecutionsRequestKeys.fileId, fileId);
      UpdateResults updateResults = wingsPersistence.update(query, updateOperations);

      if (updateResults.getUpdatedCount() != 1) {
        throw new ExportExecutionsException("Export executions request not updated");
      }

      request.setStatus(Status.READY);
      request.setFileId(fileId);
    } catch (Exception ex) {
      throw new ExportExecutionsException("Unable to mark export executions request as ready", ex);
    }
  }

  public void failRequest(@NotNull ExportExecutionsRequest request, String errorMessage) {
    if (errorMessage == null) {
      errorMessage = "";
    }

    try {
      // NOTE: Here we are failing a request at QUEUED state, so it will not contain a fileId.
      Query<ExportExecutionsRequest> query = wingsPersistence.createQuery(ExportExecutionsRequest.class)
                                                 .filter(ExportExecutionsRequestKeys.accountId, request.getAccountId())
                                                 .filter(ExportExecutionsRequestKeys.uuid, request.getUuid())
                                                 .filter(ExportExecutionsRequestKeys.status, Status.QUEUED);
      UpdateOperations<ExportExecutionsRequest> updateOperations =
          wingsPersistence.createUpdateOperations(ExportExecutionsRequest.class)
              .unset(ExportExecutionsRequestKeys.fileId)
              .set(ExportExecutionsRequestKeys.status, Status.FAILED)
              .set(ExportExecutionsRequestKeys.errorMessage, errorMessage);
      wingsPersistence.update(query, updateOperations);

      request.setFileId(null);
      request.setStatus(Status.FAILED);
      request.setErrorMessage(errorMessage);
    } catch (Exception ex) {
      throw new ExportExecutionsException("Unable to mark export executions request as failed", ex);
    }
  }

  public String expireRequest(@NotNull ExportExecutionsRequest request) {
    String fileId = null;
    try {
      Query<ExportExecutionsRequest> query = wingsPersistence.createQuery(ExportExecutionsRequest.class)
                                                 .filter(ExportExecutionsRequestKeys.accountId, request.getAccountId())
                                                 .filter(ExportExecutionsRequestKeys.uuid, request.getUuid())
                                                 .filter(ExportExecutionsRequestKeys.status, Status.READY);
      UpdateOperations<ExportExecutionsRequest> updateOperations =
          wingsPersistence.createUpdateOperations(ExportExecutionsRequest.class)
              .unset(ExportExecutionsRequestKeys.fileId)
              .set(ExportExecutionsRequestKeys.status, Status.EXPIRED);
      ExportExecutionsRequest oldRequest =
          wingsPersistence.findAndModify(query, updateOperations, HPersistence.returnOldOptions);

      if (oldRequest != null) {
        fileId = oldRequest.getFileId();
      }

      request.setFileId(null);
      request.setStatus(Status.EXPIRED);
    } catch (Exception ex) {
      throw new ExportExecutionsException("Unable to mark export executions request as expired", ex);
    }

    return fileId;
  }

  private void validateExportExecutionsRequest(@NotNull ExportExecutionsRequest request) {
    notNullCheck("Export executions request has null accountId", request.getAccountId());
    notNullCheck("Export executions request has null output format", request.getOutputFormat());
    notNullCheck("Export executions request has null query", request.getQuery());
  }

  public long getTotalRequestsInLastDay(@NotNull String accountId) {
    return wingsPersistence.createAnalyticsQuery(ExportExecutionsRequest.class)
        .filter(ExportExecutionsRequestKeys.accountId, accountId)
        .field(ExportExecutionsRequestKeys.createdAt)
        .greaterThan(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli())
        .count(new CountOptions().readPreference(ReadPreference.secondaryPreferred()));
  }
}
