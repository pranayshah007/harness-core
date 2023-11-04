/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.notification.NotificationServiceConstants.TEST_WEBHOOK_TEMPLATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.WebhookChannel;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.service.api.ChannelService;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ChannelResourceImpl implements ChannelResource {
  private final ChannelService channelService;

  public ResponseDTO<Boolean> testNotificationSetting(NotificationSettingDTO notificationSettingDTO) {
    log.info("Received test notification request for {} - notificationId: {}", notificationSettingDTO.getType(),
        notificationSettingDTO.getNotificationId());
    boolean result = channelService.sendTestNotification(notificationSettingDTO);
    return ResponseDTO.newResponse(result);
  }

  public ResponseDTO<NotificationTaskResponse> sendNotification(byte[] notificationRequestBytes)
      throws InvalidProtocolBufferException {
    NotificationRequest notificationRequest = NotificationRequest.parseFrom(notificationRequestBytes);
    log.info("Received test notification request for {} - notificationId: {}", notificationRequest.getChannelCase(),
        notificationRequest.getId());
    NotificationTaskResponse taskResponse = channelService.sendSync(notificationRequest);
    return ResponseDTO.newResponse(taskResponse);
  }
}