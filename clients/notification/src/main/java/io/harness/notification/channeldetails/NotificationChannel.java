/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channeldetails;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmailChannel.class, name = NotificationChannelType.EMAIL)
  , @JsonSubTypes.Type(value = SlackChannel.class, name = NotificationChannelType.SLACK),
      @JsonSubTypes.Type(value = PagerDutyChannel.class, name = NotificationChannelType.PAGERDUTY),
      @JsonSubTypes.Type(value = MSTeamChannel.class, name = NotificationChannelType.MSTEAMS),
      @JsonSubTypes.Type(value = WebhookChannel.class, name = NotificationChannelType.WEBHOOK)
})
@Slf4j
public abstract class NotificationChannel {
  String accountId;
  List<NotificationRequest.UserGroup> userGroups;
  String templateId;
  Map<String, String> templateData;
  Team team;

  public abstract NotificationRequest buildNotificationRequest();
}
