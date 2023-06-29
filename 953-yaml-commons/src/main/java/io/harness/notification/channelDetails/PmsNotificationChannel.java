/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = PmsEmailChannel.class, name = NotificationChannelType.EMAIL)
  , @JsonSubTypes.Type(value = PmsSlackChannel.class, name = NotificationChannelType.SLACK),
      @JsonSubTypes.Type(value = PmsPagerDutyChannel.class, name = NotificationChannelType.PAGERDUTY),
      @JsonSubTypes.Type(value = PmsMSTeamChannel.class, name = NotificationChannelType.MSTEAMS),
      @JsonSubTypes.Type(value = PmsWebhookChannel.class, name = NotificationChannelType.WEBHOOK)
})
// Move this class to a common module like 878. Also, rename it accordingly.
public abstract class PmsNotificationChannel {
  public abstract NotificationChannel toNotificationChannel(String accountId, String orgIdentifier,
      String projectIdentifier, String templateId, Map<String, String> templateData, Ambiance ambiance);
}
