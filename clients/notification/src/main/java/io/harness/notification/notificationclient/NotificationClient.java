/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.notificationclient;

import io.harness.notification.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.templates.PredefinedTemplate;

import java.util.List;

public interface NotificationClient {
  NotificationResult sendNotificationAsync(NotificationChannel notificationChannel);
  List<NotificationResult> sendBulkNotificationAsync(List<NotificationChannel> notificationChannels);
  boolean testNotificationChannel(NotificationSettingDTO notificationSettingDTO);
  TemplateDTO saveNotificationTemplate(Team team, PredefinedTemplate template, Boolean harnessManaged);
}
