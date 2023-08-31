/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.notification.NotificationServiceConstants.MAILSERVICE;
import static io.harness.notification.NotificationServiceConstants.MSTEAMSSERVICE;
import static io.harness.notification.NotificationServiceConstants.PAGERDUTYSERVICE;
import static io.harness.notification.NotificationServiceConstants.SLACKSERVICE;
import static io.harness.notification.NotificationServiceConstants.WEBHOOKSERVICE;

import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.service.api.ChannelService;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumMap;
import java.util.Map;

public class ChannelServiceImpl implements ChannelService {
  Map<NotificationChannelType, ChannelService> implementationMapping = new EnumMap<>(NotificationChannelType.class);
  Map<NotificationRequest.ChannelCase, ChannelService> protoImplementationMapping =
      new EnumMap<>(NotificationRequest.ChannelCase.class);
  Map<NotificationChannelType, String> settingsMap = new EnumMap<>(NotificationChannelType.class);
  Map<NotificationRequest.ChannelCase, String> protoSettingsMap = new EnumMap<>(NotificationRequest.ChannelCase.class);
  private final NGSettingsClient ngSettingsClient;

  @Inject
  public ChannelServiceImpl(@Named(MAILSERVICE) ChannelService mailService,
      @Named(SLACKSERVICE) ChannelService slackService, @Named(PAGERDUTYSERVICE) ChannelService pagerDutyService,
      @Named(MSTEAMSSERVICE) ChannelService msTeamsService, @Named(WEBHOOKSERVICE) ChannelService webhookService,
      NGSettingsClient ngSettingsClient) {
    this.ngSettingsClient = ngSettingsClient;
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

    settingsMap.put(NotificationChannelType.SLACK, SettingIdentifiers.ENABLE_SLACK_NOTIFICATION_IDENTIFIER);
    settingsMap.put(NotificationChannelType.MSTEAMS, SettingIdentifiers.ENABLE_MSTEAM_NOTIFICATION_IDENTIFIER);
    settingsMap.put(NotificationChannelType.PAGERDUTY, SettingIdentifiers.ENABLE_PAGERDUTY_NOTIFICATION_IDENTIFIER);
    settingsMap.put(NotificationChannelType.WEBHOOK, SettingIdentifiers.ENABLE_WEBHOOK_NOTIFICATION_IDENTIFIER);

    protoSettingsMap.put(
        NotificationRequest.ChannelCase.SLACK, SettingIdentifiers.ENABLE_SLACK_NOTIFICATION_IDENTIFIER);
    protoSettingsMap.put(
        NotificationRequest.ChannelCase.MSTEAM, SettingIdentifiers.ENABLE_MSTEAM_NOTIFICATION_IDENTIFIER);
    protoSettingsMap.put(
        NotificationRequest.ChannelCase.PAGERDUTY, SettingIdentifiers.ENABLE_PAGERDUTY_NOTIFICATION_IDENTIFIER);
    protoSettingsMap.put(
        NotificationRequest.ChannelCase.WEBHOOK, SettingIdentifiers.ENABLE_WEBHOOK_NOTIFICATION_IDENTIFIER);
  }

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    String enableChannelSettingId = protoSettingsMap.get(notificationRequest.getChannelCase());
    if (isChannelTypeEnabled(enableChannelSettingId, notificationRequest.getAccountId())) {
      return protoImplementationMapping.get(notificationRequest.getChannelCase()).send(notificationRequest);
    }
    return NotificationProcessingResponse.trivialResponseWithNoRetries;
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    String enableChannelSettingId = settingsMap.get(notificationSettingDTO.getType());
    if (isChannelTypeEnabled(enableChannelSettingId, notificationSettingDTO.getAccountId())) {
      return implementationMapping.get(notificationSettingDTO.getType()).sendTestNotification(notificationSettingDTO);
    }
    return false;
  }

  public boolean isChannelTypeEnabled(String settingId, String accountId) {
    if (isNotEmpty(settingId)) {
      return Boolean.parseBoolean(
          NGRestUtils.getResponse(ngSettingsClient.getSetting(settingId, accountId, null, null)).getValue());
    }
    // there is no setting for email, settingId will be null
    return true;
  }
}
