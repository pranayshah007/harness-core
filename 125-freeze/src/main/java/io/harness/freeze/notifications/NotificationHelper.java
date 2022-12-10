/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.notifications;

import io.harness.freeze.beans.FreezeEvent;
import io.harness.freeze.beans.FreezeNotificationChannelWrapper;
import io.harness.freeze.beans.FreezeNotifications;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.notification.FreezeEventType;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationHelper {
  @Inject NotificationClient notificationClient;

  public void sendNotification(FreezeConfigEntity entity) throws IOException {
    String yaml = entity.getYaml();
    FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(yaml);
    FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
    for (FreezeNotifications freezeNotifications : freezeInfoConfig.getNotifications()) {
      if (!freezeNotifications.isEnabled()) {
        continue;
      }
      FreezeNotificationChannelWrapper wrapper = freezeNotifications.getNotificationChannelWrapper().getValue();
      if (wrapper.getType() != null) {
        String templateId = getNotificationTemplate(wrapper.getType());
        for (FreezeEvent freezeEvent : freezeNotifications.getEvents()) {
          Map<String, String> notificationContent = constructTemplateData(
              freezeEvent.getType(), entity.getAccountId(), entity.getOrgIdentifier(), entity.getIdentifier());
          NotificationChannel channel = wrapper.getNotificationChannel().toNotificationChannel(entity.getAccountId(),
              entity.getOrgIdentifier(), entity.getProjectIdentifier(), templateId, notificationContent,
              Ambiance.newBuilder().setExpressionFunctorToken(0).build());
          try {
            notificationClient.sendNotificationAsync(channel);
          } catch (Exception ex) {
            log.error("Failed to send notification ", ex);
          }
        }
      }
    }
  }

  Map<String, String> constructTemplateData(
      FreezeEventType freezeEventType, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    // TODO - Create a Map with details
    return Collections.emptyMap();
  }

  private String getNotificationTemplate(String channelType) {
    return String.format("freeze_%s_alert", channelType.toLowerCase());
  }
}
