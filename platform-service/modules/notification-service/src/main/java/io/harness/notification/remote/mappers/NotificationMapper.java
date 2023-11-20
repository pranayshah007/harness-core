/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.mappers;

import io.harness.notification.NotificationRequest;
import io.harness.notification.entities.Channel;
import io.harness.notification.entities.EmailChannel;
import io.harness.notification.entities.MicrosoftTeamsChannel;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.PagerDutyChannel;
import io.harness.notification.entities.SlackChannel;
import io.harness.notification.entities.WebhookChannel;
import io.harness.notification.remote.dto.NotificationDTO;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class NotificationMapper {
  private static final String TEMPLATE_IDENTIFIER = "TEMPLATE_IDENTIFIER";
  public static Notification toNotification(NotificationRequest notificationRequest) {
    try {
      return Notification.builder()
          .id(notificationRequest.getId())
          .accountIdentifier(notificationRequest.getAccountId())
          .team(notificationRequest.getTeam())
          .channel(channelDetailsProtoToMongo(notificationRequest))
          .build();
    } catch (Exception e) {
      log.error(
          "Error converting notification request to notification for persistence, check consistency of mongo entity and protobuf schema, {}",
          notificationRequest, e);
      return null;
    }
  }

  public static Notification toNotification(
      NotificationRule notificationRule, NotificationChannel notificationChannel) {
    try {
      return Notification.builder()
          .id(notificationRule.getUuid())
          .accountIdentifier(notificationRule.getAccountIdentifier())
          .channel(notificationChannel)
          .build();
    } catch (Exception e) {
      log.error("Error converting notification rule to notification for persistence, {}", notificationRule, e);
      return null;
    }
  }

  public static NotificationRequest constructNotificationRequest(
      NotificationRule notificationRule, NotificationChannel notificationChannel, Map<String, String> templateData) {
    NotificationRequest.Builder notificationRequestBuilder =
        NotificationRequest.newBuilder().setAccountId(notificationRule.getAccountIdentifier());
    switch (notificationChannel.getNotificationChannelType()) {
      case EMAIL:
        EmailChannel emailChannel = (EmailChannel) notificationChannel.getChannel();
        emailChannel.setTemplateData(templateData);
        String templateId = templateData != null && templateData.get(TEMPLATE_IDENTIFIER) != null
            ? templateData.get(TEMPLATE_IDENTIFIER)
            : "templateId";
        emailChannel.setTemplateData(templateData);
        emailChannel.setTemplateId(templateId);
        notificationRequestBuilder.setEmail((NotificationRequest.Email) emailChannel.toObjectofProtoSchema());
        break;
      case SLACK:
        SlackChannel slackChannel = (SlackChannel) notificationChannel.getChannel();
        slackChannel.setTemplateData(templateData);
        notificationRequestBuilder.setSlack((NotificationRequest.Slack) slackChannel.toObjectofProtoSchema());
        break;
      case PAGERDUTY:
        PagerDutyChannel pagerDutyChannel = (PagerDutyChannel) notificationChannel.getChannel();
        pagerDutyChannel.setTemplateData(templateData);
        String pagerTemplateId = templateData != null && templateData.get(TEMPLATE_IDENTIFIER) != null
            ? templateData.get(TEMPLATE_IDENTIFIER)
            : "templateId";
        pagerDutyChannel.setTemplateId(pagerTemplateId);
        notificationRequestBuilder.setPagerDuty(
            (NotificationRequest.PagerDuty) pagerDutyChannel.toObjectofProtoSchema());
        break;
      case MSTEAMS:
        MicrosoftTeamsChannel msTeamChannel = (MicrosoftTeamsChannel) notificationChannel.getChannel();
        msTeamChannel.setTemplateData(templateData);
        String msTeamTemplateId = templateData != null && templateData.get(TEMPLATE_IDENTIFIER) != null
            ? templateData.get(TEMPLATE_IDENTIFIER)
            : "templateId";
        msTeamChannel.setTemplateId(msTeamTemplateId);
        notificationRequestBuilder.setMsTeam((NotificationRequest.MSTeam) msTeamChannel.toObjectofProtoSchema());
        break;
      case WEBHOOK:
        WebhookChannel webhookChannel = (WebhookChannel) notificationChannel.getChannel();
        webhookChannel.setTemplateData(templateData);
        notificationRequestBuilder.setWebhook((NotificationRequest.Webhook) webhookChannel.toObjectofProtoSchema());
        break;
      default:
        log.error("Channel type of the notification trigger request unidentified {}",
            notificationChannel.getNotificationChannelType());
    }
    return notificationRequestBuilder.build();
  }

  private static Channel channelDetailsProtoToMongo(NotificationRequest notificationRequest) {
    switch (notificationRequest.getChannelCase()) {
      case EMAIL:
        return EmailChannel.toEmailEntity(notificationRequest.getEmail());
      case SLACK:
        return SlackChannel.toSlackEntity(notificationRequest.getSlack());
      case PAGERDUTY:
        return PagerDutyChannel.toPagerDutyEntity(notificationRequest.getPagerDuty());
      case MSTEAM:
        return MicrosoftTeamsChannel.toMicrosoftTeamsEntity(notificationRequest.getMsTeam());
      case WEBHOOK:
        return WebhookChannel.toWebhookEntity(notificationRequest.getWebhook());
      default:
        log.error("Channel type of the notification request unidentified {}", notificationRequest.getChannelCase());
    }
    return null;
  }

  public static NotificationRequest toNotificationRequest(Notification notification) {
    try {
      NotificationRequest.Builder builder = NotificationRequest.newBuilder()
                                                .setId(notification.getId())
                                                .setAccountId(notification.getAccountIdentifier())
                                                .setTeam(notification.getTeam());
      setChannel(builder, notification);
      return builder.build();
    } catch (Exception e) {
      log.error(
          "Error converting notification to notificatino request, check consistency of mongo entity and protobuf schema, {}",
          notification, e);
      return null;
    }
  }

  private static void setChannel(NotificationRequest.Builder builder, Notification notification) {
    Object channelDetails = notification.getChannel().toObjectofProtoSchema();
    if (channelDetails instanceof NotificationRequest.Email) {
      builder.setEmail((NotificationRequest.Email) channelDetails);
    } else if (channelDetails instanceof NotificationRequest.Slack) {
      builder.setSlack((NotificationRequest.Slack) channelDetails);
    } else if (channelDetails instanceof NotificationRequest.PagerDuty) {
      builder.setPagerDuty((NotificationRequest.PagerDuty) channelDetails);
    } else if (channelDetails instanceof NotificationRequest.MSTeam) {
      builder.setMsTeam((NotificationRequest.MSTeam) channelDetails);
    } else if (channelDetails instanceof NotificationRequest.Webhook) {
      builder.setWebhook((NotificationRequest.Webhook) channelDetails);
    }
  }

  public static Optional<NotificationDTO> toDTO(Notification notification) {
    if (notification == null) {
      return Optional.empty();
    }
    return Optional.of(NotificationDTO.builder()
                           .accountIdentifier(notification.getAccountIdentifier())
                           .channelType(notification.getChannel().getChannelType())
                           .id(notification.getId())
                           .processingResponses(notification.getProcessingResponses())
                           .retries(notification.getRetries())
                           .team(notification.getTeam())
                           .build());
  }
}
