/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.entities;

public enum NotificationEntity {
  PIPELINE(NotificationModule.PLATFORM, "Pipeline"),
  DELEGATE(NotificationModule.PLATFORM, "Delegate"),
  CONNECTOR(NotificationModule.PLATFORM, "Connector");

  private final NotificationModule notificationModule;
  private final String displayName;

  NotificationEntity(NotificationModule notificationModule, String displayName) {
    this.notificationModule = notificationModule;
    this.displayName = displayName;
  }

  public String getDisplayName(NotificationEntity notificationEntity) {
    return notificationEntity.name();
  }
}
