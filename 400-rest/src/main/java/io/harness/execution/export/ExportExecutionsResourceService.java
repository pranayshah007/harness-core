/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExportExecutionsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.export.formatter.JsonFormatter;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestLimitChecks;
import io.harness.execution.export.request.ExportExecutionsRequestService;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.execution.export.request.ExportExecutionsUserParams;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.security.UserGroup;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ExportExecutionsResourceService {
  @Inject private ExportExecutionsRequestService exportExecutionsRequestService;
  @Inject private ExportExecutionsFileService exportExecutionsFileService;
  @Inject private ExportExecutionsRequestHelper exportExecutionsRequestHelper;
  @Inject private UserGroupService userGroupService;
  @Inject private LimitConfigurationService limitConfigurationService;

  private final JsonFormatter jsonFormatter = new JsonFormatter();

  public ExportExecutionsRequestLimitChecks getLimitChecks(
      @NotNull String accountId, @NotNull Query<WorkflowExecution> query) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return exportExecutionsRequestService.prepareLimitChecks(accountId, query);
    }
  }

  public ExportExecutionsRequestSummary export(
      @NotNull String accountId, @NotNull Query<WorkflowExecution> query, ExportExecutionsUserParams userParams) {
    // The query passed to this function must be an authorized query:
    // 1. If the request comes from UI we convert the page request to query and authorize that query
    // 2. If the request comes from GraphQL we use WingsPersistence::createAuthorizedQuery to create a new query
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      validateUserParams(userParams);
      checkRateLimits(accountId);
      String requestId = exportExecutionsRequestService.queueExportExecutionRequest(accountId, query, userParams);
      try (AutoLogContext ignore2 = new ExportExecutionsRequestLogContext(requestId, OVERRIDE_ERROR)) {
        return getStatus(accountId, requestId);
      }
    }
  }

  public String getStatusJson(@NotNull String accountId, @NotNull String requestId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ExportExecutionsRequestLogContext(requestId, OVERRIDE_ERROR)) {
      return jsonFormatter.getOutputString(getStatus(accountId, requestId));
    }
  }

  private ExportExecutionsRequestSummary getStatus(@NotNull String accountId, @NotNull String requestId) {
    ExportExecutionsRequest request = exportExecutionsRequestService.get(accountId, requestId);
    return exportExecutionsRequestHelper.prepareSummary(request);
  }

  public StreamingOutput downloadFile(@NotNull String accountId, @NotNull String requestId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ExportExecutionsRequestLogContext(requestId, OVERRIDE_ERROR)) {
      ExportExecutionsRequest request = exportExecutionsRequestService.get(accountId, requestId);
      switch (request.getStatus()) {
        case QUEUED:
          throwWeApplicationException(request, Response.Status.ACCEPTED);
          break;
        case READY:
          if (request.getFileId() == null) {
            throw new ExportExecutionsException(
                "Unexpected error while trying to download file for export executions request");
          }

          return outputStream -> exportExecutionsFileService.downloadFileToStream(request.getFileId(), outputStream);
        case FAILED:
        case EXPIRED:
          throwWeApplicationException(request, Response.Status.GONE);
          break;
        default:
          throw new ExportExecutionsException(
              format("Unknown status [%s] while trying to download file for export executions request",
                  request.getStatus().name()));
      }

      return null;
    }
  }

  private void throwWeApplicationException(@NotNull ExportExecutionsRequest request, Response.Status status) {
    throw new WebApplicationException(
        Response.status(status)
            .entity(jsonFormatter.getOutputString(exportExecutionsRequestHelper.prepareSummary(request)))
            .build());
  }

  private void validateUserParams(ExportExecutionsUserParams userParams) {
    if (userParams == null) {
      throw new InvalidRequestException("No user params provided to export executions request");
    }

    if (EmptyPredicate.isEmpty(userParams.getUserGroupIds())) {
      return;
    }

    if (userParams.isNotifyOnlyTriggeringUser()) {
      throw new InvalidRequestException(
          "Both \"notifyOnlyTriggeringUser\" and \"userGroupIds\" can't be set simultaneously.");
    }

    List<UserGroup> userGroups = userGroupService.fetchUserGroupNamesFromIds(userParams.getUserGroupIds());
    if (EmptyPredicate.isEmpty(userGroups)) {
      throw new InvalidRequestException("Invalid user groups provided");
    }

    Set<String> userGroupIdsSet = new HashSet<>(userParams.getUserGroupIds());
    userGroupIdsSet.removeAll(userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toList()));
    if (EmptyPredicate.isNotEmpty(userGroupIdsSet)) {
      throw new InvalidRequestException(format("Invalid user groups: [%s]", String.join(",", userGroupIdsSet)));
    }
  }

  private void checkRateLimits(@NotNull String accountId) {
    ConfiguredLimit<StaticLimit> configuredLimit =
        limitConfigurationService.getOrDefault(accountId, ActionType.EXPORT_EXECUTIONS_REQUEST);
    if (configuredLimit == null) {
      log.error("No export executions request rate limit configured");
      return;
    }

    long requestsInLastDay = exportExecutionsRequestService.getTotalRequestsInLastDay(accountId);
    if (requestsInLastDay >= configuredLimit.getLimit().getCount()) {
      throw new InvalidRequestException(
          "Number of export executions requests from this account has reached its daily limit");
    }
  }
}
