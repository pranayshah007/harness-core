/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.service.api.NotificationManagementService;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NotificationManagementServiceImpl implements NotificationManagementService {
  @Override
  public NotificationRule create(NotificationRule notificationRule) {
    return null;
  }

  @Override
  public NotificationRule update(NotificationRule notificationRule) {
    return null;
  }

  @Override
  public NotificationRule get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String notificationRuleNameIdentifier) {
    return null;
  }

  @Override
  public List<NotificationRule> list(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public boolean delete(NotificationChannel notificationChannel) {
    return false;
  }

  @Override
  public NotificationChannel create(NotificationChannel notificationChannel) {
    return null;
  }

  @Override
  public NotificationChannel update(NotificationChannel notificationChannel) {
    return null;
  }

  @Override
  public boolean deleteNotificationChannel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name) {
    return false;
  }

  @Override
  public NotificationChannel notificationChannel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name) {
    return null;
  }

  @Override
  public List<NotificationChannel> getNotificationChannelList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }
}
