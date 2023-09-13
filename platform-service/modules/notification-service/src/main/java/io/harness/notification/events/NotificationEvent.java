/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.events;

public enum NotificationEvent {
  DELEGATE_DOWN(NotificationEventGroup.DELEGATE),
  DELEGATE_EXPIRED(NotificationEventGroup.DELEGATE),
  DELEGATE_ABOUT_TO_EXPIRE(NotificationEventGroup.DELEGATE),

  CONNECTOR_DOWN(NotificationEventGroup.CONNECTOR),

  PIPELINE_STARTED(NotificationEventGroup.PIPELINE),
  PIPELINE_FAILED(NotificationEventGroup.PIPELINE),
  PIPELINE_END(NotificationEventGroup.PIPELINE),
  PIPELINE_SUCCESS(NotificationEventGroup.PIPELINE);

  private final NotificationEventGroup notificationEventGroup;

  NotificationEvent(NotificationEventGroup notificationEventGroup) {
    this.notificationEventGroup = notificationEventGroup;
  }
}

