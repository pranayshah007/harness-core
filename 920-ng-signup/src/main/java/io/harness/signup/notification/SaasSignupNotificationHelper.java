/*
  * Copyright 2021 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Shield 1.0.0
license
  * that can be found in the licenses directory at the root of this
repository, also available at
  *
https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
  */

package io.harness.signup.notification;

import io.harness.ng.core.user.UserInfo;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.signup.SignupNotificationConfiguration;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SaasSignupNotificationHelper implements SignupNotificationHelper {
  private final NotificationClient notificationClient;
  private final SignupNotificationConfiguration notificationConfiguration;
  private final LoadingCache<EmailType, Boolean> cache;

  @Inject
  public SaasSignupNotificationHelper(NotificationClient notificationClient,
      SignupNotificationTemplateLoader cacheLoader, SignupNotificationConfiguration notificationConfiguration) {
    this.notificationClient = notificationClient;
    this.notificationConfiguration = notificationConfiguration;
    cache = CacheBuilder
                .newBuilder()

                .maximumSize(notificationConfiguration.getTemplates().size())

                .expireAfterWrite(notificationConfiguration.getExpireDurationInMinutes(), TimeUnit.MINUTES)
                .build(cacheLoader);
  }

  public void sendSignupNotification(UserInfo userInfo, EmailType emailType, String defaultTemplateId, String url) {
    String templateId = decideTemplateId(emailType, defaultTemplateId);

    EmailChannelBuilder builder = EmailChannel
                                      .builder()

                                      .accountId(userInfo.getDefaultAccountId())
                                      .team(Team.GTM)

                                      .recipients(Lists.newArrayList(userInfo.getEmail()))
                                      .templateId(templateId)

                                      .templateData(ImmutableMap.of("url", url))

                                      .userGroups(Collections.emptyList());
    notificationClient.sendNotificationAsync(builder.build());
  }

  private String decideTemplateId(EmailType emailType, String defaultTemplateId) {
    String currentId = defaultTemplateId;
    try {
      EmailInfo emailInfo = notificationConfiguration.getTemplates().get(emailType);
      if (cache.get(emailType)) {
        currentId = emailInfo.getTemplateId();
      } else {
        log.warn("Template id {} is not saved in notification service, use {} instead.", emailInfo.getTemplateId(),
            defaultTemplateId);
      }
    } catch (ExecutionException e) {
      log.warn(
          String.format("Failed to get template %s from cache, fall over to use %s", emailType, defaultTemplateId), e);
    }
    return currentId;
  }
}