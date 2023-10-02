/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service.api;

import io.harness.ng.beans.PageRequest;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.entities.Notification;

import java.util.Optional;
import org.springframework.data.domain.Page;

public interface NotificationService {
  boolean processNewMessage(NotificationRequest notificationRequest);

  void processRetries(Notification notification);

  Optional<Notification> getnotification(String notificationId);

  Page<Notification> list(Team team, PageRequest pageRequest);

  void deleteByAccountIdentifier(String accountIdentifier);
}
