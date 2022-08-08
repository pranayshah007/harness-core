/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.notification.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.templates.PredefinedTemplate;

import java.util.List;

public class FakeNotificationClient implements NotificationClient {
  @Override
  public NotificationResult sendNotificationAsync(NotificationChannel notificationChannel) {
    return NotificationResultWithoutStatus.builder().notificationId("notificationId").build();
  }

  @Override
  public List<NotificationResult> sendBulkNotificationAsync(List<NotificationChannel> notificationChannels) {
    throw new UnsupportedOperationException("mocked method - TODO");
  }

  @Override
  public boolean testNotificationChannel(NotificationSettingDTO notificationSettingDTO) {
    return false;
  }

  @Override
  public TemplateDTO saveNotificationTemplate(Team team, PredefinedTemplate template, Boolean harnessManaged) {
    throw new UnsupportedOperationException("mocked method - TODO");
  }
}
