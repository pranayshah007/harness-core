/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.channelDetails;

import io.harness.notification.channeldetails.NotificationChannel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CVNGEmailChannelSpec.class, name = "Email")
  , @JsonSubTypes.Type(value = CVNGSlackChannelSpec.class, name = "Slack"),
      @JsonSubTypes.Type(value = CVNGPagerDutyChannelSpec.class, name = "Pagerduty"),
      @JsonSubTypes.Type(value = CVNGMSTeamsChannelSpec.class, name = "Msteams")
})
public abstract class CVNGNotificationChannelSpec {
  @JsonIgnore public abstract CVNGNotificationChannelType getType();
  public abstract NotificationChannel toNotificationChannel(String accountId, String orgIdentifier,
      String projectIdentifier, String templateId, Map<String, String> templateData);
}
