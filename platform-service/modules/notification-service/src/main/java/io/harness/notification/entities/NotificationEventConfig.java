/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.notification.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.eventmetadata.NotificationEventParameters;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Data
@Builder
@EqualsAndHashCode()
@JsonTypeName("NotificationEventConfig")
@FieldNameConstants(innerTypeName = "NotificationEventConfigKeys")
public class NotificationEventConfig {
  @NotNull NotificationEntity notificationEntity;
  NotificationEvent notificationEvent;
  @JsonTypeInfo(use = NAME, property = "notificationEntity", include = EXTERNAL_PROPERTY, visible = true)
  NotificationEventParameters notificationEventParameters;
  List<NotificationChannel> notificationChannels;
}
