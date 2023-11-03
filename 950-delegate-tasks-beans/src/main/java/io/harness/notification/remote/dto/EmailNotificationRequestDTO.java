/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import io.harness.notification.NotificationChannelType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "EmailNotificationRequest", description = "Email request to be send")
public class EmailNotificationRequestDTO extends NotificationRequestDTO {
  @NotNull Set<String> toRecipients;
  @NotNull Set<String> ccRecipients;
  @NotNull String subject;
  @NotNull String body;

  @Builder
  public EmailNotificationRequestDTO(
      @NotNull String accountId, Set<String> toRecipients, Set<String> ccRecipients, String subject, String body) {
    super(accountId);
    this.toRecipients = toRecipients;
    this.ccRecipients = ccRecipients;
    this.subject = subject;
    this.body = body;
  }

  @Override
  public NotificationChannelType getType() {
    return NotificationChannelType.EMAIL;
  }
}
