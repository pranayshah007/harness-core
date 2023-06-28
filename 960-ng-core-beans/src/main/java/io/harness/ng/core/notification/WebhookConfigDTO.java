/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.notification;

import io.harness.notification.NotificationChannelType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("WEBHOOK")
public class WebhookConfigDTO extends NotificationSettingConfigDTO {
  @NotNull String webhookUrl;

  @Builder
  public WebhookConfigDTO(String webhookUrl) {
    this.webhookUrl = webhookUrl;
    this.type = NotificationChannelType.WEBHOOK;
  }

  @Override
  public Optional<String> getSetting() {
    return Optional.ofNullable(webhookUrl);
  }
}
