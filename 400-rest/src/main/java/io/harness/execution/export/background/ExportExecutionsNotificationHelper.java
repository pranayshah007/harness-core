/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.EXPORT_EXECUTIONS_FAILED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.EXPORT_EXECUTIONS_READY_NOTIFICATION;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.ExportExecutionsUtils;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.sanitizer.HtmlInputSanitizer;

import software.wings.beans.EntityType;
import software.wings.beans.ExportExecutionsNotification;
import software.wings.beans.Notification;
import software.wings.service.intfc.NotificationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ExportExecutionsNotificationHelper {
  private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d");
  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm z");

  @Inject private ExportExecutionsRequestHelper exportExecutionsRequestHelper;
  @Inject private NotificationService notificationService;
  @Inject private HtmlInputSanitizer userNameSanitizer;

  void dispatch(@NotNull ExportExecutionsRequest request) {
    if (request.isNotifyOnlyTriggeringUser()) {
      dispatchToTriggeringUser(request);
    } else {
      dispatchToUserGroup(request);
    }
  }

  private void dispatchToTriggeringUser(@NotNull ExportExecutionsRequest request) {
    if (request.getCreatedBy() == null) {
      return;
    }

    Notification notification = prepareNotification(request);
    notificationService.sendNotificationToTriggeredByUserOnly(notification, request.getCreatedBy());
  }

  private void dispatchToUserGroup(@NotNull ExportExecutionsRequest request) {
    if (isEmpty(request.getUserGroupIds())) {
      return;
    }

    Notification notification = prepareNotification(request);
    notificationService.sendNotificationAsync(notification,
        Collections.singletonList(aNotificationRule().withUserGroupIds(request.getUserGroupIds()).build()));
  }

  private Notification prepareNotification(@NotNull ExportExecutionsRequest request) {
    if (request.getStatus() != Status.READY && request.getStatus() != Status.FAILED) {
      log.warn(
          format("Invalid status [%s] found while notifying users of export executions request", request.getStatus()));
      return null;
    }

    boolean failed = request.getStatus() == Status.FAILED;
    Map<String, String> placeholderValues = preparePlaceholderValues(request, failed);
    return ExportExecutionsNotification.builder()
        .accountId(request.getAccountId())
        .appId(GLOBAL_APP_ID)
        .entityId(request.getUuid())
        .entityType(EntityType.EXPORT_EXECUTIONS_REQUEST)
        .notificationTemplateId(
            failed ? EXPORT_EXECUTIONS_FAILED_NOTIFICATION.name() : EXPORT_EXECUTIONS_READY_NOTIFICATION.name())
        .notificationTemplateVariables(placeholderValues)
        .build();
  }

  private Map<String, String> preparePlaceholderValues(@NotNull ExportExecutionsRequest request, boolean failed) {
    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("REQUEST_ID", request.getUuid());
    placeholderValues.put("USER_NAME",
        request.getCreatedBy() == null || request.getCreatedBy().getName() == null
            ? "unknown"
            : userNameSanitizer.sanitizeInput(request.getCreatedBy().getName()));
    placeholderValues.put("TRIGGERED_AT", formatTimestamp(request.getCreatedAt()));
    placeholderValues.put("TRIGGERED_AT_SECS", formatTimestampAsSecs(request.getCreatedAt()));

    if (failed) {
      placeholderValues.put(
          "ERROR_MESSAGE", isEmpty(request.getErrorMessage()) ? "unknown" : request.getErrorMessage());
    } else {
      ExportExecutionsRequestSummary summary = exportExecutionsRequestHelper.prepareSummary(request);
      placeholderValues.put("DOWNLOAD_LINK", summary.getDownloadLink());
      placeholderValues.put("EXPIRES_AT", formatTimestamp(request.getExpiresAt()));
      placeholderValues.put("EXPIRES_AT_SECS", formatTimestampAsSecs(request.getExpiresAt()));
    }

    return placeholderValues;
  }

  private String formatTimestamp(long ts) {
    ZonedDateTime zonedDateTime = ExportExecutionsUtils.prepareZonedDateTime(ts);
    if (zonedDateTime == null) {
      return "";
    }

    return format("%s at %s", dateFormatter.format(zonedDateTime), timeFormatter.format(zonedDateTime));
  }

  private static String formatTimestampAsSecs(long ts) {
    return String.valueOf(ts / 1000);
  }
}
