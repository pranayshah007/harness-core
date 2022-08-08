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
import io.harness.notification.notificationclient.NotificationClient;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import lombok.val;

public class OnPremSignupNotificationHelper implements SignupNotificationHelper {
  private final NotificationClient notificationClient;

  @Inject
  public OnPremSignupNotificationHelper(NotificationClient notificationClient) {
    this.notificationClient = notificationClient;
  }

  @Override
  public void sendSignupNotification(UserInfo userInfo, EmailType emailType, String defaultTemplateId, String url) {
    val builder = EmailChannel.builder()
                      .accountId(userInfo.getDefaultAccountId())
                      .team(Team.GTM)
                      .recipients(Lists.newArrayList(userInfo.getEmail()))
                      .templateId(defaultTemplateId)
                      .templateData(ImmutableMap.of("url", url))
                      .userGroups(Collections.emptyList());
    notificationClient.sendNotificationAsync(builder.build());
  }
}