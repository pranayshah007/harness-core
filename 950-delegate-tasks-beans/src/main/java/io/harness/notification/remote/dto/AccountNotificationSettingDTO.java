/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import io.harness.notification.SmtpConfig;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "AccountNotificationSetting", description = "This has the Account Notification settings.")
public class AccountNotificationSettingDTO {
  @Schema(description = "Account Identifier.") @NotNull String accountId;
  @Schema(description = "Specify if notifications should be sent through Delegate.")
  Boolean sendNotificationViaDelegate;
  @Schema(description = "SMTP configuration.") SmtpConfig smtpConfig;
}
