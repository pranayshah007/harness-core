/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.entities;

public enum NotificationEvent {
  // Pipeline notification events
  PIPELINE_START(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "PipelineStart"),
  PIPELINE_SUCCESS(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "PipelineSuccess"),
  PIPELINE_FAILED(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "PipelineFailed"),
  PIPELINE_PAUSED(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "PipelinePaused"),
  STAGE_SUCCESS(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "StageSuccess"),
  STAGE_START(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "StageStart"),
  STAGE_FAILED(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "StageFailed"),
  STEP_FAILED(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "StepFailed"),
  PIPELINE_END(NotificationEntity.PIPELINE, NotificationModule.PLATFORM, "PipelineEnd"),

  // Delegate events
  DELEGATE_DOWN(NotificationEntity.DELEGATE, NotificationModule.PLATFORM, "DelegateDown"),
  DELEGATE_EXPIRED(NotificationEntity.DELEGATE, NotificationModule.PLATFORM, "DelegateExpired"),
  DELEGATE_ABOUT_TO_EXPIRE(NotificationEntity.DELEGATE, NotificationModule.PLATFORM, "DelegateAboutToExpire"),

  // connector events
  CONNECTOR_DOWN(NotificationEntity.CONNECTOR, NotificationModule.PLATFORM, "ConnectorDown");

  private final NotificationEntity notificationEntity;
  private final NotificationModule notificationModule;
  private final String displayName;

  NotificationEvent(NotificationEntity notificationEntity, NotificationModule notificationModule, String displayName) {
    this.notificationEntity = notificationEntity;
    this.notificationModule = notificationModule;
    this.displayName = displayName;
  }

  public String getDisplayName(NotificationEvent notificationEvent) {
    return notificationEvent.displayName;
  }
}
