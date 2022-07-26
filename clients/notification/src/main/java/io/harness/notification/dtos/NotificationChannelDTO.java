package io.harness.notification.dtos;

import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationChannelDTO {
  String accountId;
  List<NotificationRequest.UserGroup> userGroups;
  String templateId;
  Map<String, String> templateData;
  Team team;
  List<String> emailRecipients;
  List<String> webhookUrls;
}
