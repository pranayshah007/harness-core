/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service.api;

import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.notification.NotificationRequest;
import io.harness.notification.remote.dto.NotificationSettingDTO;

public interface ChannelService {
  NotificationProcessingResponse send(NotificationRequest notificationRequest);
  boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO);
}
