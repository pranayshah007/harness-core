/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.notification.NotificationChannelType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmailSettingDTO.class, name = "EMAIL")
  , @JsonSubTypes.Type(value = MSTeamSettingDTO.class, name = "MSTEAMS"),
      @JsonSubTypes.Type(value = SlackSettingDTO.class, name = "SLACK"),
      @JsonSubTypes.Type(value = PagerDutySettingDTO.class, name = "PAGERDUTY"),
      @JsonSubTypes.Type(value = WebhookSettingDTO.class, name = "WEBHOOK"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "NotificationSetting", description = "Details of the Notification settings configured in Harness.")
public abstract class NotificationSettingDTO {
  @Schema(description = "Account Identifier.") @NotNull String accountId;
  @Schema(description = "Recipient of the notification.") @NotNull String recipient;
  @Schema(description = "Identifier of the notification.") @NotNull String notificationId;

  public NotificationSettingDTO(String accountId, String recipient) {
    this.accountId = accountId;
    this.recipient = recipient;
    this.notificationId = generateUuid();
  }

  public abstract NotificationChannelType getType();
}
