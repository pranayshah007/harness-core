/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.notification.service.ChannelServiceImpl;
import io.harness.notification.service.MailServiceImpl;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class EmailResourceImpl implements EmailResource {
  private final MailServiceImpl mailService;
  private final ChannelServiceImpl channelService;

  public ResponseDTO<NotificationTaskResponse> sendEmail(EmailDTO emailDTO) {
    log.info("Received email request for notificationId: {}", emailDTO.getNotificationId());
    NotificationTaskResponse result = mailService.sendEmail(emailDTO);
    return ResponseDTO.newResponse(result);
  }
}