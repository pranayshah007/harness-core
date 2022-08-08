/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.notificationclient;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationResultWithStatus extends NotificationResult {
  private NotificationStatus status;
  private String errorMessage;

  @Builder
  public NotificationResultWithStatus(NotificationStatus status, String errorMessage, String notificationId) {
    super(notificationId);
    this.status = status;
    this.errorMessage = errorMessage;
  }
}
