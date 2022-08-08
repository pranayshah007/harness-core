/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.notifications;

import io.harness.notification.NotificationChannelType;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmailNotificationChannel.class, name = "EMAIL")
  , @JsonSubTypes.Type(value = SlackNotificationChannel.class, name = "SLACK"),
      @JsonSubTypes.Type(value = MicrosoftTeamsNotificationChannel.class, name = "MICROSOFT_TEAMS")
})
@Schema(name = "NotificationChannel", description = "The Cloud Cost Notification Channel definition")
public interface CCMNotificationChannel {
  NotificationChannelType getNotificationChannelType();
  List<String> getChannelUrls();
}
