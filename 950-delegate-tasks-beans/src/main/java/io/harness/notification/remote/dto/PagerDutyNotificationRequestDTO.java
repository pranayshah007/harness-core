/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import io.harness.notification.NotificationChannelType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "PagerDutyNotificationRequest", description = "Pager Duty request to be send")
public class PagerDutyNotificationRequestDTO extends NotificationRequestDTO {
  @NotNull @NotEmpty String key;
  @NotNull String message;
  Map<String, String> links;

  @Builder
  public PagerDutyNotificationRequestDTO(
      @NotNull String accountId, String key, String message, Map<String, String> links) {
    super(accountId);
    this.key = key;
    this.message = message;
    this.links = links;
  }

  @Override
  public NotificationChannelType getType() {
    return NotificationChannelType.PAGERDUTY;
  }
}
