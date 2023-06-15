/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.notification.NotificationServiceConstants.MAILSERVICE;
import static io.harness.notification.NotificationServiceConstants.MSTEAMSSERVICE;
import static io.harness.notification.NotificationServiceConstants.PAGERDUTYSERVICE;
import static io.harness.notification.NotificationServiceConstants.SLACKSERVICE;
import static io.harness.notification.NotificationServiceConstants.WEBHOOKSERVICE;

import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.service.api.ChannelService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumMap;
import java.util.Map;

public class ChannelServiceImpl implements ChannelService {
  Map<NotificationChannelType, ChannelService> implementationMapping = new EnumMap<>(NotificationChannelType.class);
  Map<NotificationRequest.ChannelCase, ChannelService> protoImplementationMapping =
      new EnumMap<>(NotificationRequest.ChannelCase.class);

  @Inject
  public ChannelServiceImpl(@Named(MAILSERVICE) ChannelService mailService,
      @Named(SLACKSERVICE) ChannelService slackService, @Named(PAGERDUTYSERVICE) ChannelService pagerDutyService,
      @Named(MSTEAMSSERVICE) ChannelService msTeamsService, @Named(WEBHOOKSERVICE) ChannelService webhookService) {
    implementationMapping.put(NotificationChannelType.EMAIL, mailService);
    implementationMapping.put(NotificationChannelType.PAGERDUTY, pagerDutyService);
    implementationMapping.put(NotificationChannelType.SLACK, slackService);
    implementationMapping.put(NotificationChannelType.MSTEAMS, msTeamsService);
    implementationMapping.put(NotificationChannelType.WEBHOOK, webhookService);

    protoImplementationMapping.put(NotificationRequest.ChannelCase.EMAIL, mailService);
    protoImplementationMapping.put(NotificationRequest.ChannelCase.PAGERDUTY, pagerDutyService);
    protoImplementationMapping.put(NotificationRequest.ChannelCase.SLACK, slackService);
    protoImplementationMapping.put(NotificationRequest.ChannelCase.MSTEAM, msTeamsService);
    protoImplementationMapping.put(NotificationRequest.ChannelCase.WEBHOOK, webhookService);
  }

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    return protoImplementationMapping.get(notificationRequest.getChannelCase()).send(notificationRequest);
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    return implementationMapping.get(notificationSettingDTO.getType()).sendTestNotification(notificationSettingDTO);
  }
}
