/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import io.harness.notification.NotificationChannelType;
import io.harness.notification.Team;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "Notification", description = "Details of the Notification settings configured in Harness.")
public class NotificationDTO {
  @Schema(description = "Identifier of the notification.") String id;
  @Schema(description = "Account Identifier.") String accountIdentifier;
  @Schema(description = "Team associated with the notification.") Team team;
  @Schema(description = "Channel type of notification. We currently support SLACK, EMAIL, PAGERDUTY and MSTEAMS.")
  NotificationChannelType channelType;
  @Schema(description = "Boolean responses of whether or not the notification is sent.")
  List<Boolean> processingResponses;
  @Schema(description = "The number of times the notification was resent.") int retries;
}
