/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.SmtpConfig;
import io.harness.notification.entities.NotificationSetting;
import io.harness.notification.remote.SmtpConfigResponse;

import java.util.List;
import java.util.Optional;

@OwnedBy(PL)
public interface NotificationSettingsService {
  List<String> getNotificationRequestForUserGroups(List<NotificationRequest.UserGroup> notificationUserGroups,
      NotificationChannelType notificationChannelType, String accountId);
  List<String> getNotificationSettingsForGroups(
      List<String> userGroups, NotificationChannelType notificationChannelType, String accountId);
  Optional<NotificationSetting> getNotificationSetting(String accountId);
  boolean getSendNotificationViaDelegate(String accountId);
  Optional<SmtpConfig> getSmtpConfig(String accountId);
  NotificationSetting setSendNotificationViaDelegate(String accountId, boolean sendNotificationViaDelegate);
  NotificationSetting setSmtpConfig(String accountId, SmtpConfig smtpConfig);

  SmtpConfigResponse getSmtpConfigResponse(String accountId);
}
