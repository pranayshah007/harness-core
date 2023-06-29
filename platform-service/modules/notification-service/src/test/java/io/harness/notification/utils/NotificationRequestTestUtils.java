/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.notification.NotificationRequest.newBuilder;
import static io.harness.notification.NotificationServiceConstants.TEST_MAIL_TEMPLATE;
import static io.harness.notification.NotificationServiceConstants.TEST_MSTEAMS_TEMPLATE;
import static io.harness.notification.NotificationServiceConstants.TEST_PD_TEMPLATE;
import static io.harness.notification.NotificationServiceConstants.TEST_SLACK_TEMPLATE;
import static io.harness.notification.NotificationServiceConstants.TEST_WEBHOOK_TEMPLATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.NotificationRequest;
import io.harness.notification.NotificationRequest.ChannelCase;
import io.harness.notification.Team;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.SlackChannel;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class NotificationRequestTestUtils {
  public static NotificationRequest getDummyNotificationRequest(ChannelCase channelCase) {
    NotificationRequest.Builder notificationRequestBuilder =
        newBuilder().setAccountId("kmpySmUISimoRrJL6NL73w").setTeam(Team.CD).setId("1");

    switch (channelCase) {
      case EMAIL:
        notificationRequestBuilder.setEmail(NotificationRequest.Email.newBuilder()
                                                .setTemplateId(TEST_MAIL_TEMPLATE)
                                                .putAllTemplateData(Collections.emptyMap())
                                                .addAllEmailIds(Collections.singleton("email@harness.io")));
        break;
      case SLACK:
        notificationRequestBuilder.setSlack(NotificationRequest.Slack.newBuilder()
                                                .setTemplateId(TEST_SLACK_TEMPLATE)
                                                .putAllTemplateData(Collections.emptyMap())
                                                .addAllSlackWebHookUrls(Collections.singleton("slack-webhookurl")));
        break;
      case WEBHOOK:
        notificationRequestBuilder.setWebhook(NotificationRequest.Webhook.newBuilder()
                                                  .setTemplateId(TEST_WEBHOOK_TEMPLATE)
                                                  .putAllTemplateData(Collections.emptyMap())
                                                  .addAllUrls(Collections.singleton("webhook-url")));
        break;
      case MSTEAM:
        notificationRequestBuilder.setMsTeam(NotificationRequest.MSTeam.newBuilder()
                                                 .setTemplateId(TEST_MSTEAMS_TEMPLATE)
                                                 .putAllTemplateData(Collections.emptyMap())
                                                 .addAllMsTeamKeys(Collections.singleton("msteam-webhookurl")));
        break;
      case PAGERDUTY:
        notificationRequestBuilder.setPagerDuty(NotificationRequest.PagerDuty.newBuilder()
                                                    .setTemplateId(TEST_PD_TEMPLATE)
                                                    .putAllTemplateData(Collections.emptyMap())
                                                    .addAllPagerDutyIntegrationKeys(Collections.singleton("pd-key")));
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + channelCase);
    }
    return notificationRequestBuilder.build();
  }

  public static Notification getDummyNotification(String id) {
    return Notification.builder()
        .id(id)
        .accountIdentifier("kmpySmUISimoRrJL6NL73w")
        .team(Team.OTHER)
        .channel(SlackChannel.builder().slackWebHookUrls(Collections.singletonList("slack-webhookurl")).build())
        .build();
  }
}
