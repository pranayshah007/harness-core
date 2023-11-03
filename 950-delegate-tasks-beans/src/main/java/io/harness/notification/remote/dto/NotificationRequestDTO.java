/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.notification.NotificationChannelType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmailNotificationRequestDTO.class, name = "EMAIL")
  , @JsonSubTypes.Type(value = MSTeamNotificationRequestDTO.class, name = "MSTEAMS"),
      @JsonSubTypes.Type(value = SlackNotificationRequestDTO.class, name = "SLACK"),
      @JsonSubTypes.Type(value = PagerDutyNotificationRequestDTO.class, name = "PAGERDUTY"),
      @JsonSubTypes.Type(value = WebhookNotificationRequestDTO.class, name = "WEBHOOK")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "NotificationRequest", description = "Notification Request to be send")
public abstract class NotificationRequestDTO {
  @Schema(description = "Account Identifier.") @NotNull String accountId;
  @Schema(description = "Identifier of the notification.") @JsonIgnore @NotNull String notificationId = generateUuid();

  public NotificationRequestDTO(String accountId) {
    this.accountId = accountId;
    this.notificationId = generateUuid();
  }

  public abstract NotificationChannelType getType();
}
